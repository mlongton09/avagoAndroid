package com.avago.core.data.v2

import com.avago.core.data.v2.V2Fixtures.error
import com.avago.core.data.v2.V2Fixtures.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class V2CommandOutboxTest {
    private val server = ScriptedServer()
    private val store = V2Fixtures.business()
    private val api = V2Api(server.transport, V2Fixtures.ACCOUNT, V2Fixtures.DEVICE)
    private var now = 1_000_000L
    private val outbox = V2CommandOutbox(store, api, V2Fixtures.identity, clock = { now })
    private val binId = V2Uuid.v7()

    private fun update(name: String, base: Long? = 3, key: String = binId, overlay: Boolean = false, deps: List<String> = emptyList(), uploads: List<V2NewUpload> = emptyList()) =
        V2NewCommand(
            operation = "bins.update",
            payload = buildJsonObject { put("name", name) },
            entityType = "bins",
            entityKey = key,
            baseVersion = base,
            dependsOn = deps,
            overlayPatch = if (overlay) buildJsonObject { put("name", name) } else null,
            uploads = uploads,
        )

    private fun committed(id: String? = null, watermark: Long?) = {
        json(
            200,
            buildJsonObject {
                id?.let { put("command_id", it) }
                put("state", "committed")
                put("result_watermark", watermark?.let { kotlinx.serialization.json.JsonPrimitive(it.toString()) } ?: JsonNull)
            },
        )
    }

    private suspend fun row(id: String) = store.read { it.query("SELECT * FROM pending_commands WHERE id = ?", listOf(id)).singleOrNull() }

    private fun sent() = server.transport.matching("POST", "/commands").map { Json.parseToJsonElement(it.body!!).jsonObject }

    @Test
    fun `request hash is the sha256 of the canonical replay fields`() {
        val payload = buildJsonObject {
            put("qty", "1.50")
            put("name", "X")
            put("tags", buildJsonArray { add("a"); add(JsonNull) })
            put("n", 3)
        }
        assertEquals(
            "6f96cce2e53320b78c898789749bc042bd5eb2323c8f0c5afbd37fed50f33c98",
            V2Canonical.requestHash("bins.update", 1, "bins", "k1", "7", payload),
        )
    }

    @Test
    fun `a committed command is acknowledged until a sync covers its watermark`() = runTest {
        val id = outbox.enqueue(update("A", overlay = true))
        assertEquals(V2Canonical.requestHash("bins.update", 1, "bins", binId, "3", buildJsonObject { put("name", "A") }), row(id)!!["request_hash"])
        server.commands += committed(id, watermark = 42)

        val result = outbox.flush()

        assertEquals(listOf(id), result.acknowledged)
        val body = sent().single()
        assertEquals(id, body["command_id"]!!.jsonPrimitive.content)
        assertEquals("3", body["base_version"]!!.jsonPrimitive.content)
        assertEquals(0, (body["depends_on"] as kotlinx.serialization.json.JsonArray).size)
        assertEquals("acknowledged", row(id)!!["state"])
        assertEquals(42L, row(id)!!["result_watermark"])
        assertEquals(1L, store.read { it.scalarLong("SELECT COUNT(*) FROM local_overlays") })
        // Nothing left to send.
        assertTrue(outbox.flush().acknowledged.isEmpty())
    }

    @Test
    fun `a watermark already covered by the applied cursor retires the command immediately`() = runTest {
        store.write { it.execute("UPDATE sync_state SET cursor = 50 WHERE stream = 'account'") }
        val id = outbox.enqueue(update("A"))
        server.commands += committed(id, watermark = 42)

        outbox.flush()

        assertNull(row(id))
    }

    @Test
    fun `a lost response is resolved by receipt and replayed with the same id and body`() = runTest {
        val id = outbox.enqueue(update("A"))
        server.commands += { throw OfflineError() }

        val first = outbox.flush()
        assertTrue(first.offline)
        assertEquals("sent_unknown", row(id)!!["state"])
        assertTrue((row(id)!!["next_attempt_at"] as Long) > now)

        // Still backing off: nothing is sent.
        assertEquals(1, outbox.flush().deferred)
        assertEquals(1, sent().size)

        now += 10 * 60_000
        server.receipts += { error(404, "not_found") }
        server.commands += committed(id, watermark = 7)
        assertEquals(listOf(id), outbox.flush().acknowledged)

        assertEquals(1, server.transport.matching("GET", "/commands/$id").size)
        val bodies = server.transport.matching("POST", "/commands").map { it.body }
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
    }

    @Test
    fun `a receipt that is already committed is not sent again`() = runTest {
        val id = outbox.enqueue(update("A"))
        server.commands += { error(503, "overloaded") }
        outbox.flush()
        assertEquals("queued", row(id)!!["state"])

        // A 5xx may have committed: the next attempt asks first.
        now += 10 * 60_000
        server.commands += { error(500, "internal") }
        outbox.flush()
        assertEquals("sent_unknown", row(id)!!["state"])

        now += 10 * 60_000
        server.receipts += committed(id, watermark = 5)
        assertEquals(listOf(id), outbox.flush().acknowledged)
        assertEquals(2, sent().size)
    }

    @Test
    fun `backoff grows exponentially and is capped`() = runTest {
        val id = outbox.enqueue(update("A"))
        val delays = mutableListOf<Long>()
        repeat(12) {
            server.commands += { error(503, "overloaded") }
            outbox.flush()
            val next = row(id)!!["next_attempt_at"] as Long
            delays += next - now
            now = next
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), delays.take(4))
        assertEquals(300_000L, delays.last())
    }

    @Test
    fun `a version conflict is surfaced and rejects queued dependents`() = runTest {
        val first = outbox.enqueue(update("A", overlay = true))
        val dependent = outbox.enqueue(update("B", key = V2Uuid.v7(), deps = listOf(first)))
        val conflictId = V2Uuid.v7()
        server.commands += {
            json(
                200,
                buildJsonObject {
                    put("command_id", first)
                    put("state", "rejected")
                    put("rejection_code", "version_conflict")
                    put("message", "bins row is at version 4")
                    put("conflict_id", conflictId)
                },
            )
        }

        val result = outbox.flush()

        assertEquals(setOf(first, dependent), result.rejected.toSet())
        assertEquals(1, sent().size)
        val failures = outbox.failures().associateBy { it.id }
        assertEquals("version_conflict", failures.getValue(first).code)
        assertEquals(conflictId, failures.getValue(first).conflictId)
        assertEquals("dependency_rejected", failures.getValue(dependent).code)
        assertEquals(0L, store.read { it.scalarLong("SELECT COUNT(*) FROM local_overlays") })

        outbox.dismissFailure(first)
        assertNull(row(first))
    }

    @Test
    fun `a conflicting edit is re-applied on the current version with its overlay and a dependent resolve`() = runTest {
        val first = outbox.enqueue(update("A", overlay = true))
        server.commands += {
            json(
                200,
                buildJsonObject {
                    put("command_id", first)
                    put("state", "rejected")
                    put("rejection_code", "version_conflict")
                    put("conflict_id", V2Uuid.v7())
                },
            )
        }
        outbox.flush()
        assertEquals(0L, store.read { it.scalarLong("SELECT COUNT(*) FROM local_overlays") })

        val queue = V2CommandQueue(store, V2Fixtures.identity, clock = { now })
        val resolveKey = V2Uuid.v7()
        val ids = queue.resendRejected(first, baseVersion = 4) { resent -> listOf(update("R", base = 1, key = resolveKey, deps = listOf(resent))) }

        assertNull(row(first))
        val (resent, resolve) = ids
        val resentRow = row(resent)!!
        assertEquals("queued", resentRow["state"])
        assertEquals(4L, resentRow["base_version"])
        assertEquals(V2Canonical.string(buildJsonObject { put("name", "A") }), resentRow["payload"])
        assertEquals(
            V2Canonical.string(buildJsonObject { put("name", "A") }),
            store.read { it.scalarText("SELECT patch FROM local_overlays WHERE command_id = ?", listOf(resent)) },
        )
        assertEquals(resent, store.read { it.scalarText("SELECT depends_on_command_id FROM command_dependencies WHERE command_id = ?", listOf(resolve)) })
        assertThrows(V2OutboxException.InvalidCommand::class.java) { kotlinx.coroutines.runBlocking { queue.resendRejected(first, 4) } }
    }

    @Test
    fun `commands wait for their dependency and for earlier commands on the same entity`() = runTest {
        val first = outbox.enqueue(update("A"))
        val sameEntity = outbox.enqueue(update("B"))
        val dependent = outbox.enqueue(update("C", key = V2Uuid.v7(), deps = listOf(first)))
        server.commands += { error(503, "overloaded") }

        val result = outbox.flush()

        assertTrue(result.offline)
        assertEquals(1, sent().size)

        now += 10 * 60_000
        server.commands += committed(first, watermark = 10)
        server.commands += committed(sameEntity, watermark = 11)
        server.commands += committed(dependent, watermark = 12)
        assertEquals(listOf(first, sameEntity, dependent), outbox.flush().acknowledged)
        val bodies = sent()
        assertEquals(listOf(first, sameEntity, dependent), bodies.drop(1).map { it["command_id"]!!.jsonPrimitive.content })
        assertEquals(first, (bodies.last()["depends_on"] as kotlinx.serialization.json.JsonArray).single().jsonPrimitive.content)
    }

    @Test
    fun `a command waits until its uploads are finalized`() = runTest {
        val upload = V2NewUpload(V2Uuid.v7(), "/files/p.jpg", "a".repeat(64), 10, "image/jpeg")
        val id = outbox.enqueue(update("A", uploads = listOf(upload)))

        assertEquals(1, outbox.flush().deferred)
        assertTrue(sent().isEmpty())

        var calls = 0
        val withUploader = V2CommandOutbox(store, api, V2Fixtures.identity, clock = { now }, uploader = { _, _, _, _, _ ->
            calls++
            "finalized"
        })
        server.commands += committed(id, watermark = 3)
        assertEquals(listOf(id), withUploader.flush().acknowledged)
        assertEquals(1, calls)
        assertEquals("finalized", store.read { it.scalarText("SELECT state FROM upload_jobs") })
    }

    @Test
    fun `426 stops the flush and keeps the command queued`() = runTest {
        val id = outbox.enqueue(update("A"))
        server.commands += { V2Response(426, """{"error":"upgrade_required"}""") }

        assertThrows(V2OutboxException.UpgradeRequired::class.java) { kotlinx.coroutines.runBlocking { outbox.flush() } }
        assertEquals("queued", row(id)!!["state"])
        assertEquals(0L, row(id)!!["attempts"])
    }

    @Test
    fun `a final 4xx is recorded and not retried`() = runTest {
        val id = outbox.enqueue(update("A"))
        server.commands += { error(409, "idempotency_conflict") }

        assertEquals(listOf(id), outbox.flush().rejected)
        assertEquals("idempotency_conflict", outbox.failures().single().code)
        now += 60 * 60_000
        assertTrue(outbox.flush().rejected.isEmpty())
        assertEquals(1, sent().size)
    }

    @Test
    fun `operations the server does not support are refused at enqueue`() = runTest {
        outbox.supportedOperations = setOf("stock.documents.create_and_post")
        assertThrows(V2OutboxException.UnsupportedOperation::class.java) { kotlinx.coroutines.runBlocking { outbox.enqueue(update("A")) } }
    }
}
