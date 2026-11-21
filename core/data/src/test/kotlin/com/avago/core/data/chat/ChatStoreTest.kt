package com.avago.core.data.chat

import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.data.v2.JdbcV2Connection
import com.avago.core.data.v2.V2Database
import com.avago.core.data.v2.V2StoreIdentity
import com.avago.core.data.v2.V2StoreKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The chat file's cache table and ordered outbox (plan 4.1, L42) on a real SQLite through JDBC. */
class ChatStoreTest {
    @TempDir
    lateinit var root: File

    private val identity = V2StoreIdentity(
        accountId = "00000002-0000-4000-8000-4e3eb86cf041",
        userId = "00000001-0000-4000-8000-4e3eb86cf041",
        deviceId = "00000003-0000-4000-8000-4e3eb86cf041",
    )
    private val threadId = "00000004-0000-4000-8000-4e3eb86cf041"
    private var clock = 1_000L

    private fun openDb(conn: JdbcV2Connection = JdbcV2Connection()) =
        V2Database.open(conn, V2StoreKind.CHAT, identity, nowMillis = 1_000, dispatcher = Dispatchers.Unconfined)

    private fun store(db: V2Database = openDb()) =
        runBlocking { ChatStore.open(db, identity.userId, identity.accountId) }

    private fun message(id: String, body: String, at: Long = clock) = ChatMessageEntity(
        messageId = id, threadId = threadId, accountId = identity.accountId,
        senderId = identity.userId, senderName = "Me",
        bodyMd = body, bodyPreview = body, editedAt = null, linkPreviewTitle = null, linkPreviewDescription = null,
        linkPreviewImageUrl = null, linkPreviewUrl = null, photoUrl = null, createdAt = at, updatedAt = at,
    )

    private suspend fun ChatStore.send(body: String): String {
        val id = ChatOutboxQueue.newId()
        outbox.enqueueSend(
            ChatOutboxOperation.SEND,
            message(id, body),
            buildJsonObject { put(ChatOutboxQueue.KEY_BODY, body) },
            clock,
        )
        return id
    }

    private fun serverId(n: Int) = "0000000$n-1111-4000-8000-4e3eb86cf041"

    @Test
    fun `cache round-trips threads and messages and hides archived threads`() = runBlocking {
        val store = store()
        val thread = ChatThreadEntity(
            threadId = threadId, accountId = identity.accountId, threadType = "group", displayName = "Crew",
            lastMessagePreview = null, lastMessageAt = 5, unreadCount = 2, subjectSummary = null,
            createdAt = 1, updatedAt = 1,
        )
        store.cache.putThread(thread)
        store.cache.putThread(thread.copy(threadId = serverId(9), isArchived = true))
        store.cache.putMessages(listOf(message(serverId(2), "second", at = 20), message(serverId(1), "first", at = 10)))

        assertEquals(listOf(threadId), store.cache.observeThreads().first().map { it.threadId })
        assertEquals(listOf("first", "second"), store.cache.observeMessages(threadId).first().map { it.bodyMd })
        assertEquals("second", store.cache.lastMessage(threadId)?.bodyMd)

        store.cache.clear()
        assertTrue(store.cache.observeMessages(threadId).first().isEmpty())
    }

    @Test
    fun `sends are delivered in causal order and acknowledged rows leave the queue`() = runBlocking {
        val store = store()
        val first = store.send("one")
        val second = store.send("two")
        assertEquals("sending", store.cache.message(first)?.outboxStatus)

        val delivered = mutableListOf<String>()
        var n = 0
        val result = store.outbox.drain({ clock }) { entry ->
            delivered += entry.text(ChatOutboxQueue.KEY_BODY)!!
            n++
            val echo = message(serverId(n), entry.text(ChatOutboxQueue.KEY_BODY)!!).copy(clientRef = entry.id)
            ChatOutboxOutcome.Acknowledged(echo)
        }

        assertEquals(ChatOutboxDrain.Idle, result)
        assertEquals(listOf("one", "two"), delivered)
        assertTrue(store.outbox.all().isEmpty())
        assertNull(store.cache.message(first))
        assertNull(store.cache.message(second))
        val cached = store.cache.observeMessages(threadId).first()
        assertEquals(listOf(serverId(1), serverId(2)), cached.map { it.messageId })
        assertEquals(listOf(first, second), cached.map { it.clientRef })
        assertTrue(cached.all { it.outboxStatus == null })
    }

    @Test
    fun `a retried head blocks later rows and backs off`() = runBlocking {
        val store = store()
        val first = store.send("one")
        store.send("two")

        val seen = mutableListOf<String>()
        val result = store.outbox.drain({ clock }) { entry ->
            seen += entry.id
            ChatOutboxOutcome.Retry("timeout")
        }

        assertEquals(listOf(first), seen)
        assertEquals(ChatOutboxDrain.WaitUntil(clock + 2_000), result)
        val head = store.outbox.pending().first()
        assertEquals("sent_unknown", head.state)
        assertEquals(1, head.attempts)
        assertEquals("sending", store.cache.message(first)?.outboxStatus)

        repeat(ChatOutboxQueue.FAILED_AFTER_ATTEMPTS - 1) {
            clock = store.outbox.pending().first().nextAttemptAt!!
            store.outbox.drain({ clock }) { ChatOutboxOutcome.Retry("timeout") }
        }
        assertEquals("failed", store.cache.message(first)?.outboxStatus)
        assertEquals(30_000, ChatOutboxQueue.backoffMillis(ChatOutboxQueue.FAILED_AFTER_ATTEMPTS))
        assertEquals(2, store.outbox.pending().size)
    }

    @Test
    fun `a rejected row is kept as rejected and does not block later rows`() = runBlocking {
        val store = store()
        val first = store.send("bad")
        store.send("good")

        val result = store.outbox.drain({ clock }) { entry ->
            if (entry.id == first) {
                ChatOutboxOutcome.Rejected("403")
            } else {
                ChatOutboxOutcome.Acknowledged(message(serverId(1), "good"))
            }
        }

        assertEquals(ChatOutboxDrain.Idle, result)
        assertEquals(listOf("rejected"), store.outbox.all().map { it.state })
        assertEquals("failed", store.cache.message(first)?.outboxStatus)
        assertEquals("good", store.cache.message(serverId(1))?.bodyMd)
    }

    @Test
    fun `an edit queued behind a transmitted send targets the server id once the send is acknowledged`() = runBlocking {
        val store = store()
        val local = store.send("hello")
        store.outbox.drain({ clock }) { ChatOutboxOutcome.Retry("timeout") }
        store.outbox.enqueueEdit(threadId, local, "hello!", clock)
        assertEquals(
            listOf(ChatOutboxOperation.SEND, ChatOutboxOperation.EDIT),
            store.outbox.pending().map { it.operation },
        )
        assertEquals("hello!", store.cache.message(local)?.bodyMd)

        clock += 60_000
        val targets = mutableListOf<String?>()
        store.outbox.drain({ clock }) { entry ->
            when (entry.operation) {
                ChatOutboxOperation.SEND -> ChatOutboxOutcome.Acknowledged(message(serverId(1), "hello"))
                else -> {
                    targets += entry.targetMessageId
                    ChatOutboxOutcome.Acknowledged(message(serverId(1), "hello!").copy(editedAt = clock))
                }
            }
        }

        assertEquals(listOf(serverId(1)), targets)
        val cached = store.cache.message(serverId(1))!!
        assertEquals("hello!", cached.bodyMd)
        assertEquals(clock, cached.editedAt)
        assertTrue(store.outbox.all().isEmpty())
    }

    @Test
    fun `edits and deletes of a never-transmitted send are folded into it`() = runBlocking {
        val store = store()
        val edited = store.send("draft")
        store.outbox.enqueueEdit(threadId, edited, "final", clock)
        val deleted = store.send("oops")
        store.outbox.enqueueDelete(threadId, deleted, clock)

        val pending = store.outbox.pending()
        assertEquals(listOf(edited), pending.map { it.id })
        assertEquals(JsonPrimitive("final"), pending.single().payload[ChatOutboxQueue.KEY_BODY])
        assertEquals("final", store.cache.message(edited)?.bodyMd)
        assertNull(store.cache.message(deleted))
    }

    @Test
    fun `rejected edits and deletes undo their optimistic effect`() = runBlocking {
        val store = store()
        store.cache.putMessages(listOf(message(serverId(1), "original"), message(serverId(2), "keep me")))
        store.outbox.enqueueEdit(threadId, serverId(1), "changed", clock)
        store.outbox.enqueueDelete(threadId, serverId(2), clock)
        assertEquals("changed", store.cache.message(serverId(1))?.bodyMd)
        assertEquals(clock, store.cache.message(serverId(2))?.deletedAt)

        store.outbox.drain({ clock }) { ChatOutboxOutcome.Rejected("403") }

        val edited = store.cache.message(serverId(1))!!
        assertEquals("original", edited.bodyMd)
        assertNull(edited.editedAt)
        assertNull(edited.outboxStatus)
        assertNull(store.cache.message(serverId(2))?.deletedAt)
    }

    @Test
    fun `cache and outbox survive reopening and the cache table is recreated after drift`() = runBlocking {
        val path = "jdbc:sqlite:${File(root, "chat.sqlite").path}"
        store(openDb(JdbcV2Connection(path))).apply {
            cache.putMessages(listOf(message(serverId(1), "kept")))
            send("queued")
            database.close()
        }

        store(openDb(JdbcV2Connection(path))).apply {
            assertEquals("kept", cache.message(serverId(1))?.bodyMd)
            assertEquals(1, outbox.pending().size)
            database.write { tx -> V2Database.metaPut(tx.connection, "ddl_sha256", "drifted") }
            database.close()
        }

        store(openDb(JdbcV2Connection(path))).apply {
            assertNull(cache.message(serverId(1)))
            cache.putMessages(listOf(message(serverId(2), "after rebuild")))
            assertEquals("after rebuild", cache.message(serverId(2))?.bodyMd)
            database.close()
        }
        Unit
    }
}
