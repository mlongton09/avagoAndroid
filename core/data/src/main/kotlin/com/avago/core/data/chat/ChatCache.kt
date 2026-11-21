package com.avago.core.data.chat

import com.avago.core.data.db.entity.ChatAccountRosterEntity
import com.avago.core.data.db.entity.ChatMentionEntity
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.data.db.entity.ChatThreadMemberEntity
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2Database
import com.avago.core.data.v2.V2Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * The REST chat presentation cache inside the per-user/account chat file
 * (`chat-<user>-<account>.sqlite`, L42), replacing the retired device-wide Room `chat.db`.
 *
 * The contract chat tables of that file stay owned by the v2 chat stream
 * ([com.avago.core.data.v2.V2SyncEngine]); this client-only table keeps what the REST endpoints
 * return for the UI (author names, link previews, reaction and receipt aggregates), one JSON
 * document per thread, message, mention, roster entry and thread member. It is a disposable cache:
 * a contract rebuild drops it and it refills from REST. Writes go through [V2Database.write] and
 * notify observers per kind.
 */
class ChatCache internal constructor(private val db: V2Database) {

    // -- threads ------------------------------------------------------------------------------

    /** Visible threads: not deleted, not archived; favourites first, then the latest activity. */
    fun observeThreads(): Flow<List<ChatThreadEntity>> = db.observe(setOf(changeName(THREAD))) { conn ->
        conn.docs(ChatThreadEntity.serializer(), "SELECT doc FROM $TABLE WHERE kind = ?", listOf(THREAD))
            .filter { it.deletedAt == null && !it.isArchived }
            .sortedWith(
                compareByDescending<ChatThreadEntity> { it.isFavorite }
                    .thenByDescending { it.lastMessageAt ?: Long.MIN_VALUE },
            )
    }.distinctUntilChanged()

    fun observeThread(threadId: String): Flow<ChatThreadEntity?> = db.observe(setOf(changeName(THREAD))) { conn ->
        conn.doc(THREAD, threadId, ChatThreadEntity.serializer())
    }.distinctUntilChanged()

    suspend fun thread(threadId: String): ChatThreadEntity? =
        db.read { it.doc(THREAD, threadId, ChatThreadEntity.serializer()) }

    suspend fun putThread(thread: ChatThreadEntity) = db.write { tx -> putThread(tx, thread) }

    /** Rewrites a cached thread; false when it is not cached. */
    suspend fun updateThread(
        threadId: String,
        transform: (ChatThreadEntity) -> ChatThreadEntity,
    ): Boolean = db.write { tx ->
        val current = tx.connection.doc(THREAD, threadId, ChatThreadEntity.serializer()) ?: return@write false
        putThread(tx, transform(current))
        true
    }

    // -- messages -----------------------------------------------------------------------------

    /** Every live message of a thread (replies included), oldest first. */
    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>> =
        db.observe(setOf(changeName(MESSAGE))) { conn ->
            conn.docs(
                ChatMessageEntity.serializer(),
                "SELECT doc FROM $TABLE WHERE kind = ? AND thread_id = ? " +
                    "AND json_extract(doc, '$.deletedAt') IS NULL " +
                    "ORDER BY sort_at, id",
                listOf(MESSAGE, threadId),
            )
        }.distinctUntilChanged()

    fun observeReplies(threadId: String, parentMessageId: String): Flow<List<ChatMessageEntity>> =
        db.observe(setOf(changeName(MESSAGE))) { conn ->
            conn.docs(
                ChatMessageEntity.serializer(),
                "SELECT doc FROM $TABLE WHERE kind = ? AND thread_id = ? " +
                    "AND json_extract(doc, '$.parentMessageId') = ? " +
                    "AND json_extract(doc, '$.deletedAt') IS NULL ORDER BY sort_at, id",
                listOf(MESSAGE, threadId, parentMessageId),
            )
        }.distinctUntilChanged()

    fun observePinnedMessage(threadId: String): Flow<ChatMessageEntity?> =
        db.observe(setOf(changeName(MESSAGE))) { conn ->
            conn.docs(
                ChatMessageEntity.serializer(),
                "SELECT doc FROM $TABLE WHERE kind = ? AND thread_id = ? AND json_extract(doc, '$.isPinned') = 1 " +
                    "AND json_extract(doc, '$.deletedAt') IS NULL ORDER BY sort_at DESC LIMIT 1",
                listOf(MESSAGE, threadId),
            ).firstOrNull()
        }.distinctUntilChanged()

    /** Live messages whose body mentions `@username`, newest first. */
    fun observeMessagesMentioning(username: String): Flow<List<ChatMessageEntity>> =
        db.observe(setOf(changeName(MESSAGE))) { conn ->
            conn.docs(
                ChatMessageEntity.serializer(),
                "SELECT doc FROM $TABLE WHERE kind = ? AND json_extract(doc, '$.bodyMd') LIKE '%@' || ? || '%' " +
                    "AND json_extract(doc, '$.deletedAt') IS NULL ORDER BY sort_at DESC, id",
                listOf(MESSAGE, username),
            )
        }.distinctUntilChanged()

    suspend fun message(messageId: String): ChatMessageEntity? =
        db.read { it.doc(MESSAGE, messageId, ChatMessageEntity.serializer()) }

    suspend fun lastMessage(threadId: String): ChatMessageEntity? = db.read { conn ->
        conn.docs(
            ChatMessageEntity.serializer(),
            "SELECT doc FROM $TABLE WHERE kind = ? AND thread_id = ? AND json_extract(doc, '$.deletedAt') IS NULL " +
                "ORDER BY sort_at DESC, id DESC LIMIT 1",
            listOf(MESSAGE, threadId),
        ).firstOrNull()
    }

    suspend fun putMessages(messages: List<ChatMessageEntity>) {
        if (messages.isEmpty()) return
        db.write { tx -> messages.forEach { putMessage(tx, it) } }
    }

    /** Rewrites a cached message; false when it is not cached. */
    suspend fun updateMessage(messageId: String, transform: (ChatMessageEntity) -> ChatMessageEntity): Boolean =
        db.write { tx -> updateMessage(tx, messageId, transform) }

    // -- mentions of me -----------------------------------------------------------------------

    fun observeUnreadMentions(): Flow<List<ChatMentionEntity>> = db.observe(setOf(changeName(MENTION))) { conn ->
        conn.docs(
            ChatMentionEntity.serializer(),
            "SELECT doc FROM $TABLE WHERE kind = ? AND json_extract(doc, '$.isRead') = 0 ORDER BY sort_at DESC, id",
            listOf(MENTION),
        )
    }.distinctUntilChanged()

    fun observeUnreadMentionCount(): Flow<Int> = db.observe(setOf(changeName(MENTION))) { conn ->
        val sql = "SELECT count(*) AS n FROM $TABLE WHERE kind = ? AND json_extract(doc, '$.isRead') = 0"
        (conn.query(sql, listOf(MENTION)).first()["n"] as Number).toInt()
    }.distinctUntilChanged()

    suspend fun putMentions(mentions: List<ChatMentionEntity>) {
        if (mentions.isEmpty()) return
        db.write { tx ->
            mentions.forEach {
                put(tx, MENTION, it.mentionId, it.threadId, it.createdAt, encode(ChatMentionEntity.serializer(), it))
            }
        }
    }

    suspend fun markMentionRead(mentionId: String) = db.write { tx ->
        tx.execute(
            "UPDATE $TABLE SET doc = json_set(doc, '$.isRead', json('true')) WHERE kind = ? AND id = ?",
            listOf(MENTION, mentionId),
        )
        tx.markChanged(changeName(MENTION))
    }

    suspend fun markAllMentionsReadForThread(threadId: String) = db.write { tx ->
        tx.execute(
            "UPDATE $TABLE SET doc = json_set(doc, '$.isRead', json('true')) WHERE kind = ? AND thread_id = ?",
            listOf(MENTION, threadId),
        )
        tx.markChanged(changeName(MENTION))
    }

    // -- account roster -----------------------------------------------------------------------

    /** Active roster entries by display name (the @mention autocomplete source). */
    fun observeRoster(): Flow<List<ChatAccountRosterEntity>> = db.observe(setOf(changeName(ROSTER))) { conn ->
        conn.docs(
            ChatAccountRosterEntity.serializer(),
            "SELECT doc FROM $TABLE WHERE kind = ? AND json_extract(doc, '$.isActive') = 1 " +
                "ORDER BY json_extract(doc, '$.displayName'), id",
            listOf(ROSTER),
        )
    }.distinctUntilChanged()

    suspend fun putRoster(entries: List<ChatAccountRosterEntity>) {
        if (entries.isEmpty()) return
        db.write { tx ->
            entries.forEach { put(tx, ROSTER, it.rosterId, null, 0, encode(ChatAccountRosterEntity.serializer(), it)) }
        }
    }

    // -- thread members -----------------------------------------------------------------------

    fun observeThreadMembers(threadId: String): Flow<List<ChatThreadMemberEntity>> =
        db.observe(setOf(changeName(MEMBER))) { conn ->
            conn.docs(
                ChatThreadMemberEntity.serializer(),
                "SELECT doc FROM $TABLE WHERE kind = ? AND thread_id = ? ORDER BY id",
                listOf(MEMBER, threadId),
            )
        }.distinctUntilChanged()

    suspend fun putThreadMembers(members: List<ChatThreadMemberEntity>) {
        if (members.isEmpty()) return
        db.write { tx ->
            members.forEach {
                val doc = encode(ChatThreadMemberEntity.serializer(), it)
                put(tx, MEMBER, memberId(it.threadId, it.userId), it.threadId, 0, doc)
            }
        }
    }

    suspend fun deleteThreadMember(threadId: String, userId: String) = db.write { tx ->
        tx.execute("DELETE FROM $TABLE WHERE kind = ? AND id = ?", listOf(MEMBER, memberId(threadId, userId)))
        tx.markChanged(changeName(MEMBER))
    }

    // -- REST delta cursor and housekeeping ---------------------------------------------------

    suspend fun restSyncCursor(): String? = db.meta(META_REST_CURSOR)

    suspend fun putRestSyncCursor(cursor: String) =
        db.write { tx -> V2Database.metaPut(tx.connection, META_REST_CURSOR, cursor) }

    /**
     * Empties the cache and the REST delta cursor (Chat settings "clear cache"); the next sync
     * refills it. The outbox is kept: unsent messages are never discarded.
     */
    suspend fun clear() = db.write { tx ->
        tx.execute("DELETE FROM $TABLE")
        V2Database.metaPut(tx.connection, META_REST_CURSOR, null)
        KINDS.forEach { tx.markChanged(changeName(it)) }
    }

    companion object {
        const val TABLE = "chat_client_cache"
        internal const val THREAD = "thread"
        internal const val MESSAGE = "message"
        internal const val MENTION = "mention"
        internal const val ROSTER = "roster"
        internal const val MEMBER = "member"
        private val KINDS = listOf(THREAD, MESSAGE, MENTION, ROSTER, MEMBER)
        internal const val META_REST_CURSOR = "chat.rest_sync_cursor"

        /** Client-only DDL, applied on every open (a contract rebuild drops every table). */
        internal val DDL = listOf(
            "CREATE TABLE IF NOT EXISTS $TABLE (kind TEXT NOT NULL, id TEXT NOT NULL, thread_id TEXT, " +
                "sort_at INTEGER NOT NULL DEFAULT 0, doc TEXT NOT NULL CHECK (json_valid(doc)), " +
                "PRIMARY KEY (kind, id)) STRICT",
            "CREATE INDEX IF NOT EXISTS ix_${TABLE}__kind_thread_sort ON $TABLE (kind, thread_id, sort_at)",
        )

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        internal fun changeName(kind: String) = "$TABLE:$kind"

        private fun memberId(threadId: String, userId: String) = "$threadId/$userId"

        private fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)

        private fun put(tx: V2Transaction, kind: String, id: String, threadId: String?, sortAt: Long, doc: String) {
            tx.execute(
                "INSERT INTO $TABLE (kind, id, thread_id, sort_at, doc) VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT (kind, id) " +
                    "DO UPDATE SET thread_id = excluded.thread_id, sort_at = excluded.sort_at, doc = excluded.doc",
                listOf(kind, id, threadId, sortAt, doc),
            )
            tx.markChanged(changeName(kind))
        }

        private fun putThread(tx: V2Transaction, thread: ChatThreadEntity) {
            val doc = encode(ChatThreadEntity.serializer(), thread)
            put(tx, THREAD, thread.threadId, thread.threadId, thread.lastMessageAt ?: 0, doc)
        }

        internal fun putMessage(tx: V2Transaction, message: ChatMessageEntity) {
            val doc = encode(ChatMessageEntity.serializer(), message)
            put(tx, MESSAGE, message.messageId, message.threadId, message.createdAt, doc)
        }

        internal fun deleteMessage(tx: V2Transaction, messageId: String) {
            tx.execute("DELETE FROM $TABLE WHERE kind = ? AND id = ?", listOf(MESSAGE, messageId))
            tx.markChanged(changeName(MESSAGE))
        }

        internal fun updateMessage(
            tx: V2Transaction,
            messageId: String,
            transform: (ChatMessageEntity) -> ChatMessageEntity,
        ): Boolean =
            updateMessageReturningPrevious(tx, messageId, transform) != null

        /** Rewrites a cached message and returns it as it was before; null when it is not cached. */
        internal fun updateMessageReturningPrevious(
            tx: V2Transaction,
            messageId: String,
            transform: (ChatMessageEntity) -> ChatMessageEntity,
        ): ChatMessageEntity? {
            val current = tx.connection.doc(MESSAGE, messageId, ChatMessageEntity.serializer()) ?: return null
            putMessage(tx, transform(current))
            return current
        }

        private fun <T> V2Connection.doc(kind: String, id: String, serializer: KSerializer<T>): T? =
            docs(serializer, "SELECT doc FROM $TABLE WHERE kind = ? AND id = ?", listOf(kind, id)).firstOrNull()

        /** Decodes the `doc` column; a document of an older shape is skipped and refills from REST. */
        private fun <T> V2Connection.docs(serializer: KSerializer<T>, sql: String, args: List<Any?>): List<T> =
            query(sql, args).mapNotNull { row ->
                runCatching { json.decodeFromString(serializer, row.getValue("doc") as String) }.getOrNull()
            }
    }
}
