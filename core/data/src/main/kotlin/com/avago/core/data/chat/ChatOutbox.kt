package com.avago.core.data.chat

import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.v2.V2Database
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2Transaction
import com.avago.core.data.v2.scalarLong
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/** What a chat outbox row asks the server to do (`outbox.operation`). */
enum class ChatOutboxOperation(val wire: String) {
    SEND("send"),
    REPLY("reply"),
    EDIT("edit"),
    DELETE("delete"),
    ;

    companion object {
        fun fromWire(wire: String): ChatOutboxOperation = entries.first { it.wire == wire }
    }
}

/** One row of the contract `outbox` table of the chat file. */
data class ChatOutboxEntry(
    /** Client message id: the optimistic message id and the `client_ref` of a send/reply. */
    val id: String,
    val threadId: String,
    val operation: ChatOutboxOperation,
    /** The message an edit/delete targets; rewritten to the server id once its send is acknowledged. */
    val targetMessageId: String?,
    val payload: JsonObject,
    val causalOrder: Long,
    val state: String,
    val attempts: Int,
    val nextAttemptAt: Long?,
    val lastError: String?,
) {
    fun text(key: String): String? = payload[key]?.jsonPrimitive?.contentOrNull
}

/** The server's answer to one outbox row. */
sealed interface ChatOutboxOutcome {
    /** Done. [message] is the server echo (send/reply/edit); null for a delete. */
    data class Acknowledged(val message: ChatMessageEntity?) : ChatOutboxOutcome

    /** Transport failure or 5xx/408/429: may or may not have reached the server; retried in order. */
    data class Retry(val error: String) : ChatOutboxOutcome

    /** Permanent refusal (4xx, 401): never retried; the optimistic effect is undone or marked failed. */
    data class Rejected(val error: String) : ChatOutboxOutcome
}

/** Where a drain pass stopped. */
sealed interface ChatOutboxDrain {
    /** Nothing left to send. */
    data object Idle : ChatOutboxDrain

    /** The head row is backing off; drain again at [atMillis]. */
    data class WaitUntil(val atMillis: Long) : ChatOutboxDrain
}

/**
 * The chat send queue in the contract `outbox` table of the per-user/account chat file (plan 4.1,
 * L42): sends, replies, edits and deletes are applied optimistically to the [ChatCache] and
 * queued with a monotonic `causal_order`, then delivered strictly in that order by [drain]: a row
 * that must be retried blocks every later row, so a message is never overtaken by a later send or
 * by an edit of itself. A send carries its row id as `client_ref`, which the server deduplicates
 * (CHAT-009), so an ambiguous failure (`sent_unknown`) is safely re-sent. Rows leave the queue when
 * acknowledged; rejected rows stay as `rejected` and do not block.
 */
class ChatOutboxQueue internal constructor(private val db: V2Database, private val userId: String) {

    /**
     * Queues a send ([ChatOutboxOperation.SEND]) or reply ([ChatOutboxOperation.REPLY]) and shows
     * [optimistic] (whose `messageId` must be a fresh lowercase UUID) as "sending". Returns its id.
     */
    suspend fun enqueueSend(
        operation: ChatOutboxOperation,
        optimistic: ChatMessageEntity,
        payload: JsonObject,
        now: Long,
    ): String {
        require(operation == ChatOutboxOperation.SEND || operation == ChatOutboxOperation.REPLY)
        db.write { tx ->
            insert(tx, optimistic.messageId, optimistic.threadId, operation, null, payload, now)
            ChatCache.putMessage(tx, optimistic.copy(outboxStatus = STATUS_SENDING))
        }
        return optimistic.messageId
    }

    /**
     * Edits [messageId] to [body]: a send that was never transmitted is rewritten in place, otherwise
     * an edit row is queued after everything already queued. The cache shows the new body at once.
     */
    suspend fun enqueueEdit(threadId: String, messageId: String, body: String, now: Long) = db.write { tx ->
        val pendingSend = untransmittedSend(tx, messageId)
        if (pendingSend != null) {
            val payload = JsonObject(pendingSend.payload + (KEY_BODY to JsonPrimitive(body)))
            tx.execute("UPDATE outbox SET payload = ? WHERE id = ?", listOf(payload.toString(), messageId))
            ChatCache.updateMessage(tx, messageId) {
                it.copy(bodyMd = body, bodyPreview = body.take(PREVIEW_LENGTH), updatedAt = now)
            }
        } else {
            val previous = ChatCache.updateMessageReturningPrevious(tx, messageId) {
                it.copy(
                    bodyMd = body,
                    bodyPreview = body.take(PREVIEW_LENGTH),
                    editedAt = now,
                    updatedAt = now,
                    outboxStatus = STATUS_SENDING,
                )
            }
            val payload = buildJsonObject {
                put(KEY_BODY, body)
                previous?.let { put(KEY_PREVIOUS_BODY, it.bodyMd) }
                previous?.editedAt?.let { put(KEY_PREVIOUS_EDITED_AT, it) }
            }
            insert(tx, newId(), threadId, ChatOutboxOperation.EDIT, messageId, payload, now)
        }
    }

    /**
     * Deletes [messageId]: a send that was never transmitted is cancelled (row and optimistic
     * message removed), otherwise the message is hidden and a delete row is queued.
     */
    suspend fun enqueueDelete(threadId: String, messageId: String, now: Long) = db.write { tx ->
        if (untransmittedSend(tx, messageId) != null) {
            tx.execute("DELETE FROM outbox WHERE id = ?", listOf(messageId))
            ChatCache.deleteMessage(tx, messageId)
        } else {
            ChatCache.updateMessage(tx, messageId) { it.copy(deletedAt = now, updatedAt = now) }
            insert(tx, newId(), threadId, ChatOutboxOperation.DELETE, messageId, JsonObject(emptyMap()), now)
        }
    }

    /** Rows still to deliver, in causal order (diagnostics and tests). */
    suspend fun pending(): List<ChatOutboxEntry> = db.read { conn ->
        conn.query("SELECT * FROM outbox WHERE state IN ($ACTIVE_STATES) ORDER BY causal_order").map(::entry)
    }

    suspend fun all(): List<ChatOutboxEntry> = db.read { conn ->
        conn.query("SELECT * FROM outbox ORDER BY causal_order").map(::entry)
    }

    /**
     * Delivers queued rows strictly in `causal_order` through [transport] until the queue is empty
     * or the head row must back off. Callers serialise drains per store; a row left `leased` by a
     * crashed process is simply delivered again (`client_ref` makes that safe).
     */
    suspend fun drain(now: () -> Long, transport: suspend (ChatOutboxEntry) -> ChatOutboxOutcome): ChatOutboxDrain {
        while (true) {
            val head = db.read { conn ->
                conn.query("SELECT * FROM outbox WHERE state IN ($ACTIVE_STATES) ORDER BY causal_order LIMIT 1")
                    .firstOrNull()
            }?.let(::entry) ?: return ChatOutboxDrain.Idle
            val due = head.nextAttemptAt ?: 0
            if (due > now()) return ChatOutboxDrain.WaitUntil(due)

            db.write { tx ->
                tx.execute("UPDATE outbox SET state = 'leased' WHERE id = ?", listOf(head.id))
                statusTarget(head)?.let { target ->
                    if (head.attempts < FAILED_AFTER_ATTEMPTS) {
                        ChatCache.updateMessage(tx, target) { it.copy(outboxStatus = STATUS_SENDING) }
                    }
                }
            }
            val outcome = try {
                transport(head)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ChatOutboxOutcome.Retry(e.message ?: e.javaClass.simpleName)
            }
            when (outcome) {
                is ChatOutboxOutcome.Acknowledged -> db.write { tx -> acknowledge(tx, head, outcome.message) }
                is ChatOutboxOutcome.Rejected -> db.write { tx -> reject(tx, head, outcome.error) }
                is ChatOutboxOutcome.Retry -> {
                    val at = now()
                    db.write { tx -> retryLater(tx, head, outcome.error, at) }
                }
            }
        }
    }

    private fun acknowledge(tx: V2Transaction, row: ChatOutboxEntry, echo: ChatMessageEntity?) {
        tx.execute("DELETE FROM outbox WHERE id = ?", listOf(row.id))
        when (row.operation) {
            ChatOutboxOperation.SEND, ChatOutboxOperation.REPLY -> {
                if (echo != null) {
                    if (echo.messageId != row.id) {
                        ChatCache.deleteMessage(tx, row.id)
                        tx.execute(
                            "UPDATE outbox SET target_message_id = ? WHERE target_message_id = ?",
                            listOf(echo.messageId, row.id),
                        )
                    }
                    ChatCache.putMessage(tx, echo.copy(outboxStatus = null, clientRef = echo.clientRef ?: row.id))
                } else {
                    ChatCache.updateMessage(tx, row.id) { it.copy(outboxStatus = null) }
                }
            }
            ChatOutboxOperation.EDIT -> ChatCache.updateMessage(tx, row.targetMessageId ?: return) { current ->
                current.copy(
                    bodyMd = echo?.bodyMd ?: current.bodyMd,
                    bodyPreview = (echo?.bodyMd ?: current.bodyMd).take(PREVIEW_LENGTH),
                    editedAt = echo?.editedAt ?: current.editedAt,
                    updatedAt = echo?.updatedAt ?: current.updatedAt,
                    outboxStatus = null,
                )
            }
            ChatOutboxOperation.DELETE ->
                ChatCache.updateMessage(tx, row.targetMessageId ?: return) { it.copy(outboxStatus = null) }
        }
    }

    private fun reject(tx: V2Transaction, row: ChatOutboxEntry, error: String) {
        tx.execute(
            "UPDATE outbox SET state = 'rejected', attempts = attempts + 1, last_error = ?, next_attempt_at = NULL " +
                "WHERE id = ?",
            listOf(error, row.id),
        )
        when (row.operation) {
            ChatOutboxOperation.SEND, ChatOutboxOperation.REPLY ->
                ChatCache.updateMessage(tx, row.id) { it.copy(outboxStatus = STATUS_FAILED) }
            // An edit the server refuses is undone: the cache shows the server's text again.
            ChatOutboxOperation.EDIT -> ChatCache.updateMessage(tx, row.targetMessageId ?: return) { current ->
                val previous = row.text(KEY_PREVIOUS_BODY) ?: current.bodyMd
                current.copy(
                    bodyMd = previous,
                    bodyPreview = previous.take(PREVIEW_LENGTH),
                    editedAt = row.payload[KEY_PREVIOUS_EDITED_AT]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    outboxStatus = null,
                )
            }
            // A delete the server refuses is undone: the message shows again.
            ChatOutboxOperation.DELETE -> ChatCache.updateMessage(tx, row.targetMessageId ?: return) {
                it.copy(deletedAt = null, outboxStatus = null)
            }
        }
    }

    private fun retryLater(tx: V2Transaction, row: ChatOutboxEntry, error: String, now: Long) {
        val attempts = row.attempts + 1
        tx.execute(
            "UPDATE outbox SET state = 'sent_unknown', attempts = ?, last_error = ?, next_attempt_at = ? WHERE id = ?",
            listOf(attempts, error, now + backoffMillis(attempts), row.id),
        )
        if (attempts >= FAILED_AFTER_ATTEMPTS) {
            statusTarget(row)?.let { target ->
                ChatCache.updateMessage(tx, target) { it.copy(outboxStatus = STATUS_FAILED) }
            }
        }
    }

    /** The cached message whose "sending"/"failed" badge reflects [row] (a delete hides its message). */
    private fun statusTarget(row: ChatOutboxEntry): String? = when (row.operation) {
        ChatOutboxOperation.SEND, ChatOutboxOperation.REPLY -> row.id
        ChatOutboxOperation.EDIT -> row.targetMessageId
        ChatOutboxOperation.DELETE -> null
    }

    private fun untransmittedSend(tx: V2Transaction, messageId: String): ChatOutboxEntry? =
        tx.query(
            "SELECT * FROM outbox WHERE id = ? AND state = 'queued' AND attempts = 0 " +
                "AND operation IN ('send', 'reply')",
            listOf(messageId),
        ).firstOrNull()?.let(::entry)

    private fun insert(
        tx: V2Transaction,
        id: String,
        threadId: String,
        operation: ChatOutboxOperation,
        targetMessageId: String?,
        payload: JsonObject,
        now: Long,
    ) {
        val order = (tx.connection.scalarLong("SELECT coalesce(max(causal_order), 0) FROM outbox") ?: 0L) + 1
        tx.execute(
            "INSERT INTO outbox (id, user_id, thread_id, operation, target_message_id, payload, causal_order, state, " +
                "attempts, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'queued', 0, ?)",
            listOf(id, userId, threadId, operation.wire, targetMessageId, payload.toString(), order, now),
        )
        tx.markChanged(OUTBOX)
    }

    private fun entry(row: V2Row) = ChatOutboxEntry(
        id = row.getValue("id") as String,
        threadId = row.getValue("thread_id") as String,
        operation = ChatOutboxOperation.fromWire(row.getValue("operation") as String),
        targetMessageId = row["target_message_id"] as String?,
        payload = kotlinx.serialization.json.Json.parseToJsonElement(row.getValue("payload") as String).jsonObject,
        causalOrder = (row.getValue("causal_order") as Number).toLong(),
        state = row.getValue("state") as String,
        attempts = (row.getValue("attempts") as Number).toInt(),
        nextAttemptAt = (row["next_attempt_at"] as Number?)?.toLong(),
        lastError = row["last_error"] as String?,
    )

    companion object {
        const val OUTBOX = "outbox"
        const val STATUS_SENDING = "sending"
        const val STATUS_FAILED = "failed"

        /** After this many failed attempts the message shows "failed" (delivery keeps retrying). */
        const val FAILED_AFTER_ATTEMPTS = 5

        const val KEY_BODY = "body_md"
        const val KEY_IMAGE_URLS = "image_urls"
        const val KEY_NEEDS_REPLY = "needs_reply"
        const val KEY_PARENT_MESSAGE_ID = "parent_message_id"
        private const val KEY_PREVIOUS_BODY = "previous_body_md"
        private const val KEY_PREVIOUS_EDITED_AT = "previous_edited_at"
        private const val ACTIVE_STATES = "'queued', 'leased', 'sent_unknown'"
        private const val PREVIEW_LENGTH = 120

        private const val BACKOFF_BASE_MILLIS = 2_000L
        private const val BACKOFF_MAX_DOUBLINGS = 4
        private const val BACKOFF_CAP_MILLIS = 30_000L

        /** 2 s, doubling, capped at 30 s. */
        fun backoffMillis(attempts: Int): Long =
            minOf(BACKOFF_BASE_MILLIS shl (attempts - 1).coerceIn(0, BACKOFF_MAX_DOUBLINGS), BACKOFF_CAP_MILLIS)

        fun newId(): String = UUID.randomUUID().toString()
    }
}
