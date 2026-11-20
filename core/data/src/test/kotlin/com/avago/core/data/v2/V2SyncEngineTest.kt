package com.avago.core.data.v2

import com.avago.core.data.v2.V2Fixtures.bin
import com.avago.core.data.v2.V2Fixtures.delete
import com.avago.core.data.v2.V2Fixtures.error
import com.avago.core.data.v2.V2Fixtures.location
import com.avago.core.data.v2.V2Fixtures.open
import com.avago.core.data.v2.V2Fixtures.page
import com.avago.core.data.v2.repo.V2LocationRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class V2SyncEngineTest {
    private val server = ScriptedServer()
    private val business = V2Fixtures.business()
    private val chat = V2Fixtures.chat()
    private val api = V2Api(server.transport, V2Fixtures.ACCOUNT, V2Fixtures.DEVICE)
    private val engine = V2SyncEngine(api, business, chat, appVersion = "test", clock = { 5_000 }, sleep = {})

    /** Reads through the production mappers of the location repository. */
    private val reader = object {
        suspend fun locations() = business.read { V2LocationRepository.locations(it) }
        suspend fun location(id: String) = business.read { V2LocationRepository.locationById(it, id) }
        suspend fun bin(id: String) = business.read { c -> V2LocationRepository.bins(c, null).firstOrNull { it.id == id } }
        suspend fun createdAt(id: String) = business.read { it.scalarLong("SELECT created_at FROM locations WHERE id = ?", listOf(id)) }
    }

    private val s1 = V2Uuid.v7()
    private val s2 = V2Uuid.v7()
    private val locA = V2Uuid.v7()
    private val locB = V2Uuid.v7()
    private val binA = V2Uuid.v7()

    private fun body(r: V2Request) = Json.parseToJsonElement(r.body!!).jsonObject

    private suspend fun stream(name: String) =
        business.read { it.query("SELECT cursor, bootstrap_state, status, feed_generation FROM sync_state WHERE stream = ?", listOf(name)).single() }

    /** Bootstrap: global page, then two account pages (locations, then a bin). */
    private fun scriptBootstrap(sessionId: String = s1) {
        server.opens += open(sessionId, "bootstrap", w = "9", g = "3")
        server.pages["t1"] = page(sessionId, "global", 1, "0", "3", emptyList(), next = "t2")
        server.pages["t2"] = page(sessionId, "account", 2, "0", "5", listOf(location(locA, 4), location(locB, 5)), next = "t3")
        server.pages["t3"] = page(sessionId, "account", 3, "5", "9", listOf(bin(binA, locA, 9)), next = null)
    }

    @Test
    fun `bootstrap pages through both streams, acks and commits cursors`() = runTest {
        scriptBootstrap()
        assertNull(engine.committedAccountCursor())

        val result = engine.sync()

        assertEquals(V2SyncResult.Synced("bootstrap", "9", "3", pages = 3, items = 3), result)
        assertEquals(1, server.transport.matching("POST", "/v2/devices").size)
        assertEquals("""{"mode":"bootstrap"}""", server.transport.matching("POST", "/sync/sessions").single().body)
        val ack = body(server.transport.matching("POST", "/sync/acks").single())
        assertEquals("9", ack["account_cursor"]!!.jsonPrimitive.content)
        assertEquals("3", ack["global_cursor"]!!.jsonPrimitive.content)
        assertEquals("true", ack["bootstrap_completed"]!!.jsonPrimitive.content)
        assertEquals(9L, stream("account")["cursor"])
        assertEquals(9L, engine.committedAccountCursor())
        assertEquals(3L, stream("global")["cursor"])
        assertEquals("complete", stream("account")["bootstrap_state"])
        assertNull(business.meta(V2SyncEngine.META_CHAIN))

        val locations = reader.locations()
        assertEquals(setOf(locA, locB), locations.map { it.id }.toSet())
        val a = locations.first { it.id == locA }
        assertEquals("Oslo", a.locality)
        assertEquals(59.9139, a.latitude!!, 1e-9)
        assertEquals(1767323045678L, reader.createdAt(locA))
        val b = reader.bin(binA)!!
        assertEquals(0, BigDecimal("12.5").compareTo(b.capacity))
        assertEquals("shelf", b.kind)
        assertTrue(!b.archived)
    }

    @Test
    fun `a per-account singleton applies by account key and history ages out of its window`() = runTest {
        scriptBootstrap()
        engine.sync()
        val asset = V2Uuid.v7()
        val yearMillis = 365L * 86_400_000L
        business.writeWithoutForeignKeys { tx ->
            for ((id, end) in listOf("old" to 5_000L - yearMillis - 1, "recent" to 4_000L, "open" to null)) {
                tx.execute(
                    "INSERT INTO asset_status_intervals (id, created_at, updated_at, asset_id, status_code, " +
                        "valid_during_start, valid_during_end, reason) VALUES (?, 1, 1, ?, 'online', ?, ?, ?)",
                    listOf(V2Uuid.v7(), asset, (end ?: 4_500L) - 10_000_000_000L, end, id),
                )
            }
        }
        val preferences = buildJsonObject {
            put("entity_type", "account_preferences")
            put("entity_key", V2Fixtures.ACCOUNT)
            put("seq", "10")
            put("version", 2)
            put("op", "upsert")
            put(
                "body",
                buildJsonObject {
                    put("account_id", V2Fixtures.ACCOUNT)
                    put("created_at", "2026-01-02T03:04:05Z")
                    put("updated_at", "2026-01-02T03:04:05Z")
                    put("version", 2)
                    put("currency", "NOK")
                    put("costing_method", "fifo")
                    put("disable_quotes", true)
                },
            )
        }
        server.opens += open(s2, "incremental", w = "10", g = "3", from = "9", gFrom = "3")
        server.pages["t1"] = page(s2, "account", 1, "9", "10", listOf(preferences), next = null)

        assertInstanceOf(V2SyncResult.Synced::class.java, engine.sync())

        val row = business.read { it.query("SELECT singleton, currency, costing_method, disable_quotes FROM account_preferences").single() }
        assertEquals(mapOf("singleton" to 1L, "currency" to "NOK", "costing_method" to "fifo", "disable_quotes" to 1L), row)
        val kept = business.read { c -> c.query("SELECT reason FROM asset_status_intervals").map { it["reason"] }.toSet() }
        assertEquals(setOf("recent", "open"), kept)
    }

    @Test
    fun `incremental session sends committed cursors and applies tombstones`() = runTest {
        scriptBootstrap()
        engine.sync()

        server.opens += open(s2, "incremental", w = "12", g = "3", from = "9", gFrom = "3")
        server.pages["t1"] = page(s2, "account", 1, "9", "12", listOf(delete("bins", binA, 11), location(locA, 12, name = "Renamed", version = 2)), next = null)

        val result = engine.sync()

        assertInstanceOf(V2SyncResult.Synced::class.java, result)
        assertEquals(mapOf("locations" to setOf(locA)), (result as V2SyncResult.Synced).upserted, "tombstones are not upserts")
        val openBody = body(server.transport.matching("POST", "/sync/sessions").last())
        assertEquals("incremental", openBody["mode"]!!.jsonPrimitive.content)
        assertEquals("9", openBody["account"]!!.jsonObject["cursor"]!!.jsonPrimitive.content)
        assertEquals("1", openBody["account"]!!.jsonObject["feed_generation"]!!.jsonPrimitive.content)
        assertEquals("3", openBody["global"]!!.jsonObject["cursor"]!!.jsonPrimitive.content)
        assertEquals("false", body(server.transport.matching("POST", "/sync/acks").last())["bootstrap_completed"]!!.jsonPrimitive.content)
        assertNull(reader.bin(binA))
        assertEquals("Renamed", reader.location(locA)!!.name)
        assertEquals(12L, stream("account")["cursor"])
        // The device is registered once.
        assertEquals(1, server.transport.matching("POST", "/v2/devices").size)
    }

    @Test
    fun `a failing row rolls back its whole page and keeps the staged position`() = runTest {
        server.opens += open(s1, "bootstrap", w = "9", g = "0")
        server.pages["t1"] = page(s1, "account", 1, "0", "4", listOf(location(locA, 4)), next = "t2")
        // bins.code is NOT NULL: the bin fails, and locB on the same page must not land either.
        server.pages["t2"] = page(s1, "account", 2, "4", "9", listOf(location(locB, 5), bin(binA, locA, 9, code = null)), next = null)

        val result = engine.sync()

        assertInstanceOf(V2SyncResult.Failed::class.java, result)
        assertEquals(listOf(locA), reader.locations().map { it.id })
        assertEquals("4", business.meta(V2SyncEngine.META_STAGED_ACCOUNT))
        assertEquals("in_progress", stream("account")["bootstrap_state"])
        assertEquals(0L, stream("account")["cursor"])
        assertTrue(server.transport.matching("POST", "/sync/acks").isEmpty())

        // The next run continues the chain from the staged cursor.
        server.opens += open(s2, "bootstrap", w = "9", g = "0", from = "4", firstToken = "t9")
        server.pages["t9"] = page(s2, "account", 1, "4", "9", listOf(location(locB, 5), bin(binA, locA, 9)), next = null)

        assertInstanceOf(V2SyncResult.Synced::class.java, engine.sync())
        val reopen = body(server.transport.matching("POST", "/sync/sessions").last())
        assertEquals(s1, reopen["continuation_of"]!!.jsonPrimitive.content)
        assertEquals("bootstrap", reopen["mode"]!!.jsonPrimitive.content)
        assertEquals("4", reopen["account"]!!.jsonObject["cursor"]!!.jsonPrimitive.content)
        assertEquals(setOf(locA, locB), reader.locations().map { it.id }.toSet())
    }

    @Test
    fun `a non-contiguous page fails the run and drops the chain`() = runTest {
        server.opens += open(s1, "bootstrap", w = "9", g = "0")
        server.pages["t1"] = page(s1, "account", 1, "0", "4", listOf(location(locA, 4)), next = "t2")
        server.pages["t2"] = page(s1, "account", 2, "5", "9", listOf(location(locB, 9)), next = null)

        val result = engine.sync()

        assertInstanceOf(V2ProtocolException::class.java, (result as V2SyncResult.Failed).error)
        assertNull(business.meta(V2SyncEngine.META_CHAIN))
        assertEquals(listOf(locA), reader.locations().map { it.id })
    }

    @Test
    fun `a child whose parent is on a later page is carried into that page's transaction`() = runTest {
        server.opens += open(s1, "bootstrap", w = "9", g = "0")
        server.pages["t1"] = page(s1, "account", 1, "0", "4", listOf(location(locB, 4, parent = locA)), next = "t2")
        server.pages["t2"] = page(s1, "account", 2, "4", "9", listOf(location(locA, 9)), next = null)

        assertInstanceOf(V2SyncResult.Synced::class.java, engine.sync())
        assertEquals(locA, reader.location(locB)!!.parentId)
    }

    @Test
    fun `a missing parent on the final page fails and marks bootstrap_required`() = runTest {
        server.opens += open(s1, "bootstrap", w = "9", g = "0")
        server.pages["t1"] = page(s1, "account", 1, "0", "9", listOf(bin(binA, locA, 9)), next = null)

        val result = engine.sync()

        assertTrue((result as V2SyncResult.Failed).error is V2ApplyException)
        assertNull(reader.bin(binA))
        assertEquals("bootstrap_required", stream("account")["status"])
    }

    @Test
    fun `426 on a page stops with UpgradeRequired`() = runTest {
        server.opens += open(s1, "bootstrap", w = "9", g = "0")
        server.pages["t1"] = V2Response(426, """{"error":"upgrade_required"}""")

        assertEquals(V2SyncResult.UpgradeRequired, engine.sync())
        assertEquals("upgrade_required", engine.status())
        assertTrue(server.transport.matching("POST", "/sync/acks").isEmpty())
    }

    @Test
    fun `426 on open stops with UpgradeRequired`() = runTest {
        server.opens += V2Response(426, """{"error":"upgrade_required"}""")
        assertEquals(V2SyncResult.UpgradeRequired, engine.sync())
        assertEquals("upgrade_required", engine.status())
    }

    @Test
    fun `a typed 403 on open stops with its access denial`() = runTest {
        server.opens += error(403, V2AccessDenial.USER_SUSPENDED)
        assertEquals(V2SyncResult.AccessDenied(V2AccessDenial.USER_SUSPENDED), engine.sync())
    }

    @Test
    fun `a generic 403 on a sync route reads as an inactive membership`() = runTest {
        server.opens += error(403, "forbidden")
        assertEquals(V2SyncResult.AccessDenied(V2AccessDenial.MEMBERSHIP_INACTIVE), engine.sync())
    }

    @Test
    fun `bootstrap_required on an incremental open wipes and re-bootstraps`() = runTest {
        scriptBootstrap()
        engine.sync()

        server.opens += error(409, "bootstrap_required")
        server.opens += open(s2, "bootstrap", w = "2", g = "0")
        server.pages["t1"] = page(s2, "account", 1, "0", "2", listOf(location(locB, 2)), next = null)

        assertInstanceOf(V2SyncResult.Synced::class.java, engine.sync())
        assertEquals("""{"mode":"bootstrap"}""", server.transport.matching("POST", "/sync/sessions").last().body)
        assertEquals(listOf(locB), reader.locations().map { it.id })
        assertNull(reader.bin(binA))
        assertEquals(2L, stream("account")["cursor"])
    }

    @Test
    fun `a feed generation change on open aborts the session and re-bootstraps`() = runTest {
        scriptBootstrap()
        engine.sync()

        server.opens += open(s2, "incremental", w = "20", g = "3", from = "9", gFrom = "3", fg = 2, firstToken = "x")
        val s3 = V2Uuid.v7()
        server.opens += open(s3, "bootstrap", w = "1", g = "0", fg = 2, firstToken = "b1")
        server.pages["b1"] = page(s3, "account", 1, "0", "1", listOf(location(locA, 1)), next = null)

        assertInstanceOf(V2SyncResult.Synced::class.java, engine.sync())
        assertTrue(server.transport.requests.any { it.path.endsWith("/sessions/$s2/close") })
        assertEquals(2L, stream("account")["feed_generation"])
        assertEquals(listOf(locA), reader.locations().map { it.id })
    }

    @Test
    fun `410 on a page continues with continuation_of and the staged cursors`() = runTest {
        server.opens += open(s1, "bootstrap", w = "9", g = "0")
        server.pages["t1"] = page(s1, "account", 1, "0", "4", listOf(location(locA, 4)), next = "t2")
        server.pages["t2"] = error(410, "session_expired")
        server.opens += open(s2, "bootstrap", w = "9", g = "0", from = "4", firstToken = "c1")
        server.pages["c1"] = page(s2, "account", 1, "4", "9", listOf(location(locB, 9)), next = null)

        assertInstanceOf(V2SyncResult.Synced::class.java, engine.sync())
        val reopen = body(server.transport.matching("POST", "/sync/sessions").last())
        assertEquals(s1, reopen["continuation_of"]!!.jsonPrimitive.content)
        assertEquals("4", reopen["account"]!!.jsonObject["cursor"]!!.jsonPrimitive.content)
        assertEquals(s2, business.read { c -> c.scalarText("SELECT last_session_id FROM sync_state WHERE stream = 'account'") })
        assertEquals(setOf(locA, locB), reader.locations().map { it.id }.toSet())
    }

    @Test
    fun `transient page errors retry the same token`() = runTest {
        server.opens += open(s1, "bootstrap", w = "4", g = "0")
        server.pageQueue["t1"] = ArrayDeque(listOf(error(502, "bad_gateway")))
        server.pages["t1"] = page(s1, "account", 1, "0", "4", listOf(location(locA, 4)), next = null)

        assertInstanceOf(V2SyncResult.Synced::class.java, engine.sync())
        assertEquals(2, server.transport.matching("GET", "/pages").size)
    }

    @Test
    fun `an older version never overwrites a newer row`() = runTest {
        server.opens += open(s1, "bootstrap", w = "9", g = "0")
        server.pages["t1"] = page(s1, "account", 1, "0", "4", listOf(location(locA, 4, name = "New", version = 3)), next = "t2")
        server.pages["t2"] = page(s1, "account", 2, "4", "9", listOf(location(locA, 9, name = "Old", version = 2)), next = null)

        engine.sync()

        val row = reader.location(locA)
        assertNotNull(row)
        assertEquals("New", row!!.name)
        assertEquals(3L, row.version)
    }
}
