package com.avago.core.sync

import android.content.Context
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.dao.InspectionDefectDao
import com.avago.core.data.db.dao.SyncMetadataDao
import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.entity.InspectionDefectEntity
import com.avago.core.data.db.entity.SyncMetadataEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.ui.AvagoToast
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** Local apply + outbox queue + drain behaviour of [DefectRegisterSync]. */
class DefectRegisterSyncTest {

    private val context = mockk<Context>(relaxed = true)
    private val dbFactory = mockk<DatabaseFactory>()
    private val db = mockk<AvagoDatabase>()
    private val queue = mockk<SyncQueueDao>(relaxed = true)
    private val defects = mockk<InspectionDefectDao>(relaxed = true)
    private val meta = mockk<SyncMetadataDao>(relaxed = true)
    private val client = mockk<AvagoServiceClient>()
    private val toast = mockk<AvagoToast>(relaxed = true)
    private lateinit var sync: DefectRegisterSync

    @BeforeEach
    fun setUp() {
        coEvery { dbFactory.get(ACCOUNT) } returns db
        every { db.syncQueueDao() } returns queue
        every { db.inspectionDefectDao() } returns defects
        every { db.syncMetadataDao() } returns meta
        coEvery { queue.getPendingByEntity(any(), any()) } returns null
        coEvery { queue.hasPendingPush(any(), any()) } returns null
        sync = DefectRegisterSync(context, dbFactory, client, toast).apply {
            inTransaction = { _, block -> block() }
            now = { NOW }
        }
    }

    private fun op(status: String, base: String = "open", derived: Boolean = false, note: String? = null, logId: String? = null) =
        DefectOutbox.Op("asset-1", "brakes", status, note, base, derived, logId, "Brakes")

    private fun serverRow(id: String, status: String, bucket: String = "NR") = JsonObject(
        mapOf(
            "defect_id" to JsonPrimitive(id),
            "asset_id" to JsonPrimitive("asset-1"),
            "item_key" to JsonPrimitive("brakes"),
            "status" to JsonPrimitive(status),
            "bucket" to JsonPrimitive(bucket),
            "last_seen_log_id" to JsonPrimitive("log-1"),
        ),
    )

    private fun cached(id: String, status: String, bucket: String = "NR") =
        DefectOutbox.toEntity(serverRow(id, status, bucket), 1L)!!

    private fun queued(id: String, o: DefectOutbox.Op, queueId: String = UUID.randomUUID().toString()) = SyncQueueEntity(
        queueId = queueId,
        entityType = DefectOutbox.ENTITY_TYPE,
        entityId = id,
        operation = "update",
        serverVersion = null,
        payload = o.toJson(),
        syncStatus = "pending",
        attempts = 0,
        lastError = null,
        createdAt = 1,
        updatedAt = 1,
    )

    // ── Local apply + queue ───────────────────────────────────────────────

    @Test
    fun `status change updates the cached row and queues it with a fresh idempotency id`() = runTest {
        coEvery { defects.getById("d1") } returns cached("d1", "open")
        val row = slot<SyncQueueEntity>()
        coEvery { queue.upsert(capture(row)) } returns Unit

        assertTrue(sync.enqueueStatusChange(ACCOUNT, "d1", op("deferred")))

        with(row.captured) {
            assertEquals(DefectOutbox.ENTITY_TYPE, entityType)
            assertEquals("d1", entityId)
            assertEquals("update", operation)
            assertEquals("pending", syncStatus)
            assertEquals(queueId, UUID.fromString(queueId).toString())
            val payload = DefectOutbox.Op.fromJson(payload)!!
            assertEquals("deferred", payload.status)
            assertEquals("open", payload.baseStatus)
        }
        coVerify { defects.upsert(match { it.defectId == "d1" && it.status == "deferred" && it.cachedAt == NOW }) }
    }

    @Test
    fun `a later change keeps the baseline and replaces the queued row`() = runTest {
        val earlier = queued("d1", op("deferred"), queueId = "q-old")
        coEvery { queue.getPendingByEntity(DefectOutbox.ENTITY_TYPE, "d1") } returns earlier
        coEvery { defects.getById("d1") } returns cached("d1", "deferred")
        val row = slot<SyncQueueEntity>()
        coEvery { queue.upsert(capture(row)) } returns Unit

        sync.enqueueStatusChange(ACCOUNT, "d1", op("repaired", base = "deferred", note = "pads"))

        coVerify { queue.softDelete("q-old") }
        assertNotEquals("q-old", row.captured.queueId)
        val payload = DefectOutbox.Op.fromJson(row.captured.payload)!!
        assertEquals("open", payload.baseStatus)
        assertEquals("pads", payload.resolutionNote)
    }

    @Test
    fun `undoing a queued change cancels it and restores the row`() = runTest {
        coEvery { queue.getPendingByEntity(DefectOutbox.ENTITY_TYPE, "d1") } returns queued("d1", op("deferred"), "q-old")
        coEvery { defects.getById("d1") } returns cached("d1", "deferred", bucket = "M")

        assertFalse(sync.enqueueStatusChange(ACCOUNT, "d1", op("open", base = "deferred")))

        coVerify { queue.softDelete("q-old") }
        coVerify(exactly = 0) { queue.upsert(any()) }
        // Reopening a Monitor-bucket row shows it as monitoring, like the server.
        coVerify { defects.upsert(match { it.status == "monitoring" }) }
    }

    @Test
    fun `derived defects queue without a cached register row`() = runTest {
        val row = slot<SyncQueueEntity>()
        coEvery { queue.upsert(capture(row)) } returns Unit

        assertTrue(sync.enqueueStatusChange(ACCOUNT, "asset-1:brakes", op("deferred", derived = true, logId = "log-1")))

        assertEquals("asset-1:brakes", row.captured.entityId)
        assertTrue(DefectOutbox.Op.fromJson(row.captured.payload)!!.derived)
        coVerify(exactly = 0) { defects.upsert(any()) }
    }

    // ── Drain ─────────────────────────────────────────────────────────────

    @Test
    fun `drain patches with the queue id as idempotency key and caches the result`() = runTest {
        val item = queued("d1", op("deferred"), "q-1")
        coEvery { client.getDefects(ACCOUNT, "asset-1", "all") } returns NetworkResult.Success(JsonArray(listOf(serverRow("d1", "open"))))
        coEvery { client.patchDefect(ACCOUNT, "d1", "deferred", null, "q-1") } returns NetworkResult.Success(serverRow("d1", "deferred"))

        assertEquals(1, sync.drain(ACCOUNT, listOf(item)))

        coVerify { queue.markInFlight(listOf("q-1")) }
        coVerify { queue.markSuccess("q-1") }
        coVerify { defects.upsert(match { it.defectId == "d1" && it.status == "deferred" }) }
        verify(exactly = 0) { toast.warning(any()) }
    }

    @Test
    fun `server wins a status conflict with a non-blocking notice`() = runTest {
        val item = queued("d1", op("deferred"), "q-1")
        coEvery { client.getDefects(ACCOUNT, "asset-1", "all") } returns NetworkResult.Success(JsonArray(listOf(serverRow("d1", "repaired"))))

        assertEquals(0, sync.drain(ACCOUNT, listOf(item)))

        coVerify(exactly = 0) { client.patchDefect(any(), any(), any(), any(), any()) }
        coVerify { queue.markSuccess("q-1") }
        coVerify { defects.upsert(match { it.status == "repaired" }) }
        verify { toast.warning(any()) }
    }

    @Test
    fun `a refused patch restores the server row - 409 as a conflict, other 4xx as rejected`() = runTest {
        for ((code, notice) in listOf(409 to com.avago.core.ui.R.string.sync_defect_conflict, 422 to com.avago.core.ui.R.string.sync_defect_rejected)) {
            val item = queued("d1", op("deferred"), "q-$code")
            coEvery { client.getDefects(ACCOUNT, "asset-1", "all") } returns NetworkResult.Success(JsonArray(listOf(serverRow("d1", "open"))))
            coEvery { client.patchDefect(ACCOUNT, "d1", "deferred", null, "q-$code") } returns NetworkResult.Error(code, "refused")

            assertEquals(0, sync.drain(ACCOUNT, listOf(item)))

            coVerify { queue.markSuccess("q-$code") }
            verify { context.getString(notice, *anyVararg()) }
        }
        coVerify(exactly = 2) { defects.upsert(match { it.defectId == "d1" && it.status == "open" }) }
        coVerify(exactly = 0) { queue.markError(any(), any()) }
    }

    @Test
    fun `transient failures stay queued for bounded retry`() = runTest {
        val item = queued("d1", op("deferred"), "q-1")
        coEvery { client.getDefects(ACCOUNT, "asset-1", "all") } returns NetworkResult.Success(JsonArray(listOf(serverRow("d1", "open"))))
        coEvery { client.patchDefect(any(), any(), any(), any(), any()) } returns NetworkResult.Error(503, "unavailable")

        sync.drain(ACCOUNT, listOf(item))

        coVerify { queue.markError("q-1", any()) }
        coVerify(exactly = 0) { queue.markSuccess(any()) }
    }

    @Test
    fun `offline register fetch keeps every change queued`() = runTest {
        val items = listOf(queued("d1", op("deferred"), "q-1"), queued("d2", op("repaired"), "q-2"))
        coEvery { client.getDefects(any(), any(), any()) } returns NetworkResult.Error(-1, "offline")

        sync.drain(ACCOUNT, items)

        coVerify { queue.markError("q-1", any()) }
        coVerify { queue.markError("q-2", any()) }
        coVerify(exactly = 0) { queue.markSuccess(any()) }
    }

    @Test
    fun `derived change resolves to the register row once its inspection synced`() = runTest {
        val item = queued("asset-1:brakes", op("deferred", derived = true, logId = "log-1"), "q-1")
        coEvery { client.getDefects(ACCOUNT, "asset-1", "all") } returns NetworkResult.Success(JsonArray(listOf(serverRow("srv-9", "open"))))
        coEvery { client.patchDefect(ACCOUNT, "srv-9", "deferred", null, "q-1") } returns NetworkResult.Success(serverRow("srv-9", "deferred"))

        assertEquals(1, sync.drain(ACCOUNT, listOf(item)))
        coVerify { queue.markSuccess("q-1") }
    }

    @Test
    fun `derived change waits while its inspection is not registered`() = runTest {
        val item = queued("asset-1:brakes", op("deferred", derived = true, logId = "log-new"), "q-1")
        coEvery { client.getDefects(ACCOUNT, "asset-1", "all") } returns NetworkResult.Success(JsonArray(emptyList()))

        sync.drain(ACCOUNT, listOf(item))

        coVerify { queue.markError("q-1", any()) }
    }

    @Test
    fun `missing register drops queued changes, restores rows and records it`() = runTest {
        val item = queued("d1", op("deferred"), "q-1")
        coEvery { client.getDefects(any(), any(), any()) } returns NetworkResult.Error(405, "method not allowed")
        coEvery { defects.getById("d1") } returns cached("d1", "deferred")

        sync.drain(ACCOUNT, listOf(item))

        coVerify { queue.markSuccess("q-1") }
        coVerify { defects.upsert(match { it.status == "open" }) }
        coVerify { meta.upsert(SyncMetadataEntity(DefectOutbox.META_KEY, DefectOutbox.REGISTER_UNSUPPORTED, NOW)) }
        verify { toast.warning(any()) }
    }

    // ── Pull ──────────────────────────────────────────────────────────────

    @Test
    fun `pull stores the snapshot without overwriting rows with queued changes`() = runTest {
        coEvery { client.getDefects(ACCOUNT, null, null) } returns NetworkResult.Success(
            JsonArray(listOf(serverRow("d1", "open"), serverRow("d2", "open"))),
        )
        coEvery { queue.unsyncedEntityIds(DefectOutbox.ENTITY_TYPE) } returns listOf("d2")
        val rows = slot<List<InspectionDefectEntity>>()
        coEvery { defects.replaceSnapshot(capture(rows), any()) } returns Unit

        assertTrue(sync.pull(ACCOUNT))

        assertEquals(listOf("d1", "d2"), rows.captured.map { it.defectId })
        coVerify { defects.replaceSnapshot(any(), setOf("d2")) }
        coVerify { meta.upsert(SyncMetadataEntity(DefectOutbox.META_KEY, DefectOutbox.REGISTER_SUPPORTED, NOW)) }
    }

    @Test
    fun `pull failure keeps the cached register`() = runTest {
        coEvery { client.getDefects(ACCOUNT, null, null) } returns NetworkResult.Error(-1, "offline")

        assertFalse(sync.pull(ACCOUNT))

        coVerify(exactly = 0) { defects.replaceSnapshot(any(), any()) }
        coVerify(exactly = 0) { meta.upsert(any()) }
    }

    private companion object {
        const val ACCOUNT = "acct-1"
        const val NOW = 1_000_000L
    }
}
