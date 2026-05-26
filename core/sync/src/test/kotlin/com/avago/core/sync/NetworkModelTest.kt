package com.avago.core.sync

import com.avago.core.network.model.SyncOperation
import com.avago.core.network.model.SyncOperationResult
import com.avago.core.network.model.SyncPullResponse
import com.avago.core.network.model.SyncPushRequest
import com.avago.core.network.model.SyncPushResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the sync-related network model data classes.
 *
 * Covers field defaults, construction, and JSON serialization round-trips
 * using kotlinx.serialization.
 */
class NetworkModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ─── SyncPullResponse ─────────────────────────────────────────────────────

    @Test
    fun `SyncPullResponse defaults`() {
        val r = SyncPullResponse()
        assertTrue(r.items.isEmpty())
        assertFalse(r.has_more)
        assertEquals(0L, r.max_seq)
    }

    @Test
    fun `SyncPullResponse with items`() {
        val item = buildJsonObject { put("entity_type", "asset"); put("entity_id", "a-1") }
        val r = SyncPullResponse(items = listOf(item), has_more = true, max_seq = 42L)
        assertEquals(1, r.items.size)
        assertTrue(r.has_more)
        assertEquals(42L, r.max_seq)
    }

    @Test
    fun `SyncPullResponse JSON round-trip empty`() {
        val original = SyncPullResponse()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPullResponse>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `SyncPullResponse JSON round-trip with items`() {
        val item = buildJsonObject { put("entity_type", "log"); put("seq", 7) }
        val original = SyncPullResponse(items = listOf(item), has_more = true, max_seq = 7L)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPullResponse>(encoded)
        assertEquals(original.has_more, decoded.has_more)
        assertEquals(original.max_seq, decoded.max_seq)
        assertEquals(1, decoded.items.size)
    }

    @Test
    fun `SyncPullResponse JSON decodes has_more field`() {
        val raw = """{"items":[],"has_more":true,"max_seq":10}"""
        val decoded = json.decodeFromString<SyncPullResponse>(raw)
        assertTrue(decoded.has_more)
        assertEquals(10L, decoded.max_seq)
    }

    // ─── SyncPushResponse / SyncOperationResult ───────────────────────────────

    @Test
    fun `SyncOperationResult required fields`() {
        val r = SyncOperationResult(entity_id = "e-1", success = true)
        assertEquals("e-1", r.entity_id)
        assertTrue(r.success)
        assertFalse(r.conflict)
        assertNull(r.error)
        assertNull(r.server_version)
    }

    @Test
    fun `SyncOperationResult conflict flag`() {
        val r = SyncOperationResult(entity_id = "e-2", success = false, conflict = true, error = "conflict")
        assertFalse(r.success)
        assertTrue(r.conflict)
        assertEquals("conflict", r.error)
    }

    @Test
    fun `SyncOperationResult server_version stored`() {
        val r = SyncOperationResult(entity_id = "e-3", success = true, server_version = 5L)
        assertEquals(5L, r.server_version)
    }

    @Test
    fun `SyncPushResponse empty results`() {
        val r = SyncPushResponse(results = emptyList())
        assertTrue(r.results.isEmpty())
    }

    @Test
    fun `SyncPushResponse with results`() {
        val results = listOf(
            SyncOperationResult(entity_id = "e-1", success = true, server_version = 1L),
            SyncOperationResult(entity_id = "e-2", success = false, conflict = true),
        )
        val r = SyncPushResponse(results = results)
        assertEquals(2, r.results.size)
        assertTrue(r.results[0].success)
        assertTrue(r.results[1].conflict)
    }

    @Test
    fun `SyncPushResponse JSON round-trip`() {
        val original = SyncPushResponse(
            results = listOf(
                SyncOperationResult(entity_id = "e-1", success = true, server_version = 3L),
            )
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPushResponse>(encoded)
        assertEquals(1, decoded.results.size)
        assertEquals("e-1", decoded.results[0].entity_id)
        assertTrue(decoded.results[0].success)
        assertEquals(3L, decoded.results[0].server_version)
    }

    @Test
    fun `SyncOperationResult JSON round-trip conflict case`() {
        val original = SyncOperationResult(
            entity_id = "e-conflict",
            success = false,
            conflict = true,
            error = "version_mismatch",
            server_version = null,
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncOperationResult>(encoded)
        assertEquals(original.entity_id, decoded.entity_id)
        assertFalse(decoded.success)
        assertTrue(decoded.conflict)
        assertEquals("version_mismatch", decoded.error)
        assertNull(decoded.server_version)
    }

    // ─── SyncOperation ────────────────────────────────────────────────────────

    @Test
    fun `SyncOperation required fields`() {
        val op = SyncOperation(
            entity_type = "asset",
            entity_id = "a-1",
            operation = "upsert",
        )
        assertEquals("asset", op.entity_type)
        assertEquals("a-1", op.entity_id)
        assertEquals("upsert", op.operation)
        assertNull(op.payload)
        assertNull(op.server_version)
        assertFalse(op.force)
    }

    @Test
    fun `SyncOperation with payload`() {
        val payload = buildJsonObject { put("name", "Truck A") }
        val op = SyncOperation(
            entity_type = "asset",
            entity_id = "a-2",
            operation = "upsert",
            payload = payload,
            server_version = 7L,
        )
        assertNotNull(op.payload)
        assertEquals(7L, op.server_version)
    }

    @Test
    fun `SyncOperation force flag`() {
        val op = SyncOperation(
            entity_type = "log",
            entity_id = "l-1",
            operation = "upsert",
            force = true,
        )
        assertTrue(op.force)
    }

    @Test
    fun `SyncOperation delete operation`() {
        val op = SyncOperation(
            entity_type = "wo_comment",
            entity_id = "cmt-99",
            operation = "delete",
        )
        assertEquals("delete", op.operation)
        assertNull(op.payload)
    }

    @Test
    fun `SyncOperation JSON round-trip`() {
        val payload = buildJsonObject { put("title", "Oil Change") }
        val original = SyncOperation(
            entity_type = "log",
            entity_id = "l-10",
            operation = "upsert",
            payload = payload,
            server_version = 2L,
            force = false,
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncOperation>(encoded)
        assertEquals(original.entity_type, decoded.entity_type)
        assertEquals(original.entity_id, decoded.entity_id)
        assertEquals(original.operation, decoded.operation)
        assertEquals(original.server_version, decoded.server_version)
        assertFalse(decoded.force)
        assertNotNull(decoded.payload)
    }

    @Test
    fun `SyncPushRequest round-trip with multiple operations`() {
        val ops = listOf(
            SyncOperation(entity_type = "asset", entity_id = "a-1", operation = "upsert"),
            SyncOperation(entity_type = "log",   entity_id = "l-1", operation = "upsert"),
            SyncOperation(entity_type = "asset", entity_id = "a-2", operation = "delete"),
        )
        val request = SyncPushRequest(operations = ops)
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SyncPushRequest>(encoded)
        assertEquals(3, decoded.operations.size)
        assertEquals("asset", decoded.operations[0].entity_type)
        assertEquals("delete", decoded.operations[2].operation)
    }

    @Test
    fun `SyncOperation idempotency_key stored`() {
        val op = SyncOperation(
            entity_type = "asset",
            entity_id = "a-3",
            operation = "upsert",
            idempotency_key = "idem-key-abc",
        )
        assertEquals("idem-key-abc", op.idempotency_key)
    }

    @Test
    fun `SyncOperation idempotency_key null by default`() {
        val op = SyncOperation(entity_type = "asset", entity_id = "a-4", operation = "upsert")
        assertNull(op.idempotency_key)
    }
}
