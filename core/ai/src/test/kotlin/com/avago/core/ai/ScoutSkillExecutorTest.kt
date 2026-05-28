package com.avago.core.ai

import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.dao.AssetDao
import com.avago.core.data.db.dao.LogDao
import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.dao.WorkOrderDao
import com.avago.core.data.db.entity.WorkOrderEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScoutSkillExecutorTest {

    private val mockDbFactory = mockk<DatabaseFactory>()
    private val mockIdentity = mockk<IdentityManager>()
    private val mockDb = mockk<AvagoDatabase>()
    private val mockLogDao = mockk<LogDao>(relaxed = true)
    private val mockWorkOrderDao = mockk<WorkOrderDao>(relaxed = true)
    private val mockAssetDao = mockk<AssetDao>(relaxed = true)
    private val mockSyncQueueDao = mockk<SyncQueueDao>(relaxed = true)

    private lateinit var executor: ScoutSkillExecutor

    @BeforeEach
    fun setUp() {
        every { mockIdentity.getActiveAccountId() } returns "acct-001"
        coEvery { mockDbFactory.get(any()) } returns mockDb
        every { mockDb.logDao() } returns mockLogDao
        every { mockDb.workOrderDao() } returns mockWorkOrderDao
        every { mockDb.assetDao() } returns mockAssetDao
        every { mockDb.syncQueueDao() } returns mockSyncQueueDao
        executor = ScoutSkillExecutor(mockDbFactory, mockIdentity)
    }

    // ── log-entry-create ──────────────────────────────────────────────────────

    @Test
    fun `log-entry-create with title and asset_id upserts and enqueues`() = runTest {
        val fields = mapOf("title" to "Oil change", "asset_id" to "ast-1", "cost" to "89.99")
        val result = executor.executeIfPossible("log-entry-create", fields)
        assertTrue(result)
        coVerify(exactly = 1) { mockLogDao.upsert(match { it.title == "Oil change" && it.cost == 89.99 }) }
        coVerify(exactly = 1) { mockSyncQueueDao.enqueueWithDedup(match { it.entityType == "log" && it.operation == "insert" }) }
    }

    @Test
    fun `fuel-log routes to same handler as log-entry-create`() = runTest {
        val fields = mapOf("title" to "Fuel fill-up", "asset_id" to "ast-1")
        assertTrue(executor.executeIfPossible("fuel-log", fields))
        coVerify(exactly = 1) { mockLogDao.upsert(match { it.title == "Fuel fill-up" }) }
    }

    @Test
    fun `inspection-from-voice routes to same handler as log-entry-create`() = runTest {
        val fields = mapOf("title" to "Daily inspection", "asset_id" to "ast-1")
        assertTrue(executor.executeIfPossible("inspection-from-voice", fields))
    }

    @Test
    fun `log-entry-create returns false when title is missing`() = runTest {
        assertFalse(executor.executeIfPossible("log-entry-create", mapOf("asset_id" to "ast-1")))
    }

    @Test
    fun `log-entry-create returns false when asset_id is missing`() = runTest {
        assertFalse(executor.executeIfPossible("log-entry-create", mapOf("title" to "Oil change")))
    }

    // ── work-order-create ─────────────────────────────────────────────────────

    @Test
    fun `work-order-create with title upserts WO with pending_review status`() = runTest {
        val fields = mapOf(
            "title" to "Inspect brakes",
            "asset_id" to "ast-99",
            "priority" to "high",
            "due_date" to "2026-06-01",
        )
        assertTrue(executor.executeIfPossible("work-order-create", fields))
        coVerify {
            mockWorkOrderDao.upsert(match {
                it.title == "Inspect brakes" &&
                it.status == "pending_review" &&
                it.priority == "high" &&
                it.assetId == "ast-99"
            })
        }
        coVerify { mockSyncQueueDao.enqueueWithDedup(match { it.operation == "insert" && it.entityType == "work_order" }) }
    }

    @Test
    fun `work-order-create rejects invalid priority and defaults to medium`() = runTest {
        val fields = mapOf("title" to "Fix leak", "priority" to "urgent")
        assertTrue(executor.executeIfPossible("work-order-create", fields))
        coVerify { mockWorkOrderDao.upsert(match { it.priority == "medium" }) }
    }

    @Test
    fun `work-order-create returns false when title is blank`() = runTest {
        assertFalse(executor.executeIfPossible("work-order-create", mapOf("title" to "   ")))
    }

    // ── reschedule ────────────────────────────────────────────────────────────

    private fun woFixture(woId: String = "wo-1") = WorkOrderEntity(
        woId = woId, accountId = "acct-001", assetId = null, locationId = null,
        title = "Old Title", description = null, category = null, priority = "medium",
        status = "in_progress", requesterId = null, assignedTo = null, dispatcherNotes = null,
        requiredSkills = null, estimatedEffortMinutes = null, actualEffortMinutes = null,
        failureCode = null, completionNotes = null, partsNeeded = null, logId = null,
        dueDate = null, startedAt = null, completedAt = null, timerStartedAt = null,
        laborCost = null, partsCost = null, totalCost = null, currency = null,
        baseAmount = null, exchangeRateUsed = null, attributes = null, createdBy = null,
        approvalState = null, jobId = null, woKind = "standard", rrule = null, endType = null,
        endCount = null, endDate = null, meterType = null, meterDue = null, meterInterval = null,
        parentWoId = null, occurrenceDate = null, scheduleId = null, lastCompletedAt = null,
        timezone = null, createdAt = 1000L, updatedAt = 1000L, deletedAt = null,
        serverVersion = 3L, seq = null,
    )

    @Test
    fun `reschedule updates due_date and enqueues update`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        val fields = mapOf("wo_id" to "wo-1", "due_date" to "2026-07-15")
        assertTrue(executor.executeIfPossible("reschedule", fields))
        coVerify { mockWorkOrderDao.upsert(match { it.dueDate != null }) }
        coVerify { mockSyncQueueDao.enqueueWithDedup(match { it.operation == "update" && it.serverVersion == 3L }) }
    }

    @Test
    fun `reschedule returns false when wo_id not found in DB`() = runTest {
        coEvery { mockWorkOrderDao.getById(any()) } returns null
        assertFalse(executor.executeIfPossible("reschedule", mapOf("wo_id" to "missing", "due_date" to "2026-07-01")))
    }

    @Test
    fun `reschedule returns false when no fields changed`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        assertFalse(executor.executeIfPossible("reschedule", mapOf("wo_id" to "wo-1")))
    }

    // ── work-order-action ─────────────────────────────────────────────────────

    @Test
    fun `work-order-action with complete transitions status to complete`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        val fields = mapOf("wo_id" to "wo-1", "action" to "complete", "actual_effort_minutes" to "120")
        assertTrue(executor.executeIfPossible("work-order-action", fields))
        coVerify {
            mockWorkOrderDao.upsert(match {
                it.status == "complete" && it.actualEffortMinutes == 120L
            })
        }
    }

    @Test
    fun `work-order-action start maps to in_progress`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        assertTrue(executor.executeIfPossible("work-order-action", mapOf("wo_id" to "wo-1", "action" to "start")))
        coVerify { mockWorkOrderDao.upsert(match { it.status == "in_progress" }) }
    }

    @Test
    fun `work-order-action pause maps to on_hold`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        assertTrue(executor.executeIfPossible("work-order-action", mapOf("wo_id" to "wo-1", "action" to "pause")))
        coVerify { mockWorkOrderDao.upsert(match { it.status == "on_hold" }) }
    }

    @Test
    fun `work-order-action cancel maps to cancelled`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        assertTrue(executor.executeIfPossible("work-order-action", mapOf("wo_id" to "wo-1", "action" to "cancel")))
        coVerify { mockWorkOrderDao.upsert(match { it.status == "cancelled" }) }
    }

    @Test
    fun `work-order-action returns false when wo_id missing`() = runTest {
        assertFalse(executor.executeIfPossible("work-order-action", mapOf("action" to "complete")))
    }

    // ── work-order-assign ─────────────────────────────────────────────────────

    @Test
    fun `work-order-assign updates assignedTo field`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        val fields = mapOf("wo_id" to "wo-1", "assigned_to" to "Sarah")
        assertTrue(executor.executeIfPossible("work-order-assign", fields))
        coVerify { mockWorkOrderDao.upsert(match { it.assignedTo == "Sarah" }) }
        coVerify { mockSyncQueueDao.enqueueWithDedup(match { it.operation == "update" }) }
    }

    @Test
    fun `work-order-assign returns false when assigned_to missing`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        assertFalse(executor.executeIfPossible("work-order-assign", mapOf("wo_id" to "wo-1")))
    }

    // ── asset-create ──────────────────────────────────────────────────────────

    @Test
    fun `asset-create with name upserts asset and enqueues`() = runTest {
        val fields = mapOf("name" to "Site Truck", "make" to "Ford", "model" to "F-350", "year" to "2019")
        assertTrue(executor.executeIfPossible("asset-create", fields))
        coVerify {
            mockAssetDao.upsert(match {
                it.name == "Site Truck" && it.make == "Ford" && it.model == "F-350" && it.year == 2019L
            })
        }
        coVerify { mockSyncQueueDao.enqueueWithDedup(match { it.entityType == "asset" && it.operation == "insert" }) }
    }

    @Test
    fun `asset-create returns false when name is blank`() = runTest {
        assertFalse(executor.executeIfPossible("asset-create", mapOf("name" to "")))
    }

    // ── unknown skill ─────────────────────────────────────────────────────────

    @Test
    fun `unknown skill returns false without touching DB`() = runTest {
        assertFalse(executor.executeIfPossible("chat-qa", mapOf("query" to "what is overdue")))
        coVerify(exactly = 0) { mockLogDao.upsert(any()) }
        coVerify(exactly = 0) { mockWorkOrderDao.upsert(any()) }
    }

    // ── no active account ─────────────────────────────────────────────────────

    @Test
    fun `returns false when no active account`() = runTest {
        every { mockIdentity.getActiveAccountId() } returns null
        assertFalse(executor.executeIfPossible("log-entry-create", mapOf("title" to "Test", "asset_id" to "ast-1")))
    }

    // ── date parsing ──────────────────────────────────────────────────────────

    @Test
    fun `ISO date string is parsed correctly for work-order-create`() = runTest {
        val fields = mapOf("title" to "Test WO", "due_date" to "2026-06-15T00:00:00Z")
        assertTrue(executor.executeIfPossible("work-order-create", fields))
        coVerify { mockWorkOrderDao.upsert(match { it.dueDate != null }) }
    }

    @Test
    fun `YYYY-MM-DD date string is parsed correctly for reschedule`() = runTest {
        coEvery { mockWorkOrderDao.getById("wo-1") } returns woFixture()
        val fields = mapOf("wo_id" to "wo-1", "due_date" to "2026-09-01")
        assertTrue(executor.executeIfPossible("reschedule", fields))
        coVerify { mockWorkOrderDao.upsert(match { it.dueDate != null }) }
    }
}
