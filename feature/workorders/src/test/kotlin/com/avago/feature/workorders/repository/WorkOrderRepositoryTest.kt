package com.avago.feature.workorders.repository

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.dao.SyncQueueDao
import com.avago.core.data.db.dao.WoAssignmentDao
import com.avago.core.data.db.dao.WoChecklistItemDao
import com.avago.core.data.db.dao.WoCommentDao
import com.avago.core.data.db.dao.WorkOrderDao
import com.avago.core.data.db.entity.WoAssignmentEntity
import com.avago.core.data.db.entity.WoChecklistItemEntity
import com.avago.core.data.db.entity.WoCommentEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for WorkOrderRepository, covering:
 *   - insert vs update operation routing for work orders (serverVersion routing)
 *   - softDelete enqueues delete operation
 *   - sub-entities (assignments, checklist items, comments) enqueue correctly
 *   - queueId format mirrors iOS: "entityType_entityId"
 *   - syncStatus is always "pending" on enqueue
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkOrderRepositoryTest {

    private val mockDbFactory = mockk<DatabaseFactory>()
    private val mockDb = mockk<AvagoDatabase>(relaxed = true)
    private val mockSyncQueueDao = mockk<SyncQueueDao>(relaxed = true)
    private val mockWorkOrderDao = mockk<WorkOrderDao>(relaxed = true)
    private val mockAssignmentDao = mockk<WoAssignmentDao>(relaxed = true)
    private val mockChecklistDao = mockk<WoChecklistItemDao>(relaxed = true)
    private val mockCommentDao = mockk<WoCommentDao>(relaxed = true)

    private lateinit var repository: WorkOrderRepository

    companion object {
        private const val ACCOUNT_ID = "acct-001"
    }

    @BeforeEach
    fun setUp() {
        coEvery { mockDbFactory.get(ACCOUNT_ID) } returns mockDb
        every { mockDb.syncQueueDao() } returns mockSyncQueueDao
        every { mockDb.workOrderDao() } returns mockWorkOrderDao
        every { mockDb.woAssignmentDao() } returns mockAssignmentDao
        every { mockDb.woChecklistItemDao() } returns mockChecklistDao
        every { mockDb.woCommentDao() } returns mockCommentDao
        coEvery { mockSyncQueueDao.enqueueWithDedup(any()) } just Runs
        coEvery { mockWorkOrderDao.upsert(any()) } just Runs
        coEvery { mockWorkOrderDao.softDelete(any(), any()) } just Runs
        coEvery { mockAssignmentDao.upsert(any()) } just Runs
        coEvery { mockChecklistDao.upsert(any()) } just Runs
        coEvery { mockChecklistDao.softDelete(any()) } just Runs
        coEvery { mockCommentDao.upsert(any()) } just Runs

        repository = WorkOrderRepository(mockDbFactory)
    }

    // ─── Work Order: insert vs update routing ─────────────────────────────────

    @Test
    fun `upsert new WO (serverVersion=0) enqueues insert`() = runTest {
        val wo = buildWo(serverVersion = 0L)

        repository.upsert(ACCOUNT_ID, wo)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "insert" &&
                    it.entityId == wo.woId &&
                    it.entityType == "work_order"
            })
        }
    }

    @Test
    fun `upsert existing WO (serverVersion=5) enqueues update`() = runTest {
        val wo = buildWo(serverVersion = 5L)

        repository.upsert(ACCOUNT_ID, wo)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.operation == "update" })
        }
    }

    @Test
    fun `WO queueId is work_order underscore woId`() = runTest {
        val wo = buildWo(woId = "wo-xyz-001", serverVersion = 0L)

        repository.upsert(ACCOUNT_ID, wo)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.queueId == "work_order_wo-xyz-001" })
        }
    }

    @Test
    fun `WO upsert calls dao upsert`() = runTest {
        val wo = buildWo()

        repository.upsert(ACCOUNT_ID, wo)

        coVerify { mockWorkOrderDao.upsert(wo) }
    }

    // ─── Work Order: soft delete ──────────────────────────────────────────────

    @Test
    fun `softDelete WO enqueues delete operation`() = runTest {
        repository.softDelete(ACCOUNT_ID, "wo-001")

        coVerify { mockWorkOrderDao.softDelete("wo-001", any()) }
        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "delete" &&
                    it.entityId == "wo-001" &&
                    it.entityType == "work_order"
            })
        }
    }

    @Test
    fun `softDelete WO syncStatus is pending`() = runTest {
        repository.softDelete(ACCOUNT_ID, "wo-002")

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.syncStatus == "pending" })
        }
    }

    // ─── Assignment ───────────────────────────────────────────────────────────

    @Test
    fun `upsertAssignment enqueues update (always update — assignments created server-side)`() = runTest {
        val assignment = buildAssignment(serverVersion = 0L)

        repository.upsertAssignment(ACCOUNT_ID, assignment)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "update" &&
                    it.entityType == "wo_assignment" &&
                    it.entityId == assignment.assignmentId
            })
        }
    }

    @Test
    fun `assignment queueId is wo_assignment underscore assignmentId`() = runTest {
        val assignment = buildAssignment(assignmentId = "asgn-abc")

        repository.upsertAssignment(ACCOUNT_ID, assignment)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.queueId == "wo_assignment_asgn-abc" })
        }
    }

    @Test
    fun `upsertAssignment calls dao upsert`() = runTest {
        val assignment = buildAssignment()

        repository.upsertAssignment(ACCOUNT_ID, assignment)

        coVerify { mockAssignmentDao.upsert(assignment) }
    }

    // ─── Checklist ────────────────────────────────────────────────────────────

    @Test
    fun `upsertChecklistItem enqueues update`() = runTest {
        val item = buildChecklistItem()

        repository.upsertChecklistItem(ACCOUNT_ID, item)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "update" &&
                    it.entityType == "wo_checklist_item" &&
                    it.entityId == item.itemId
            })
        }
    }

    @Test
    fun `deleteChecklistItem calls dao softDelete and enqueues delete`() = runTest {
        repository.deleteChecklistItem(ACCOUNT_ID, "item-001")

        coVerify { mockChecklistDao.softDelete("item-001") }
        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "delete" &&
                    it.entityType == "wo_checklist_item" &&
                    it.entityId == "item-001"
            })
        }
    }

    @Test
    fun `checklist item queueId is wo_checklist_item underscore itemId`() = runTest {
        val item = buildChecklistItem(itemId = "chk-999")

        repository.upsertChecklistItem(ACCOUNT_ID, item)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.queueId == "wo_checklist_item_chk-999" })
        }
    }

    // ─── Comment ──────────────────────────────────────────────────────────────

    @Test
    fun `upsertComment enqueues update`() = runTest {
        val comment = buildComment()

        repository.upsertComment(ACCOUNT_ID, comment)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match {
                it.operation == "update" &&
                    it.entityType == "wo_comment" &&
                    it.entityId == comment.commentId
            })
        }
    }

    @Test
    fun `comment queueId is wo_comment underscore commentId`() = runTest {
        val comment = buildComment(commentId = "cmnt-999")

        repository.upsertComment(ACCOUNT_ID, comment)

        coVerify {
            mockSyncQueueDao.enqueueWithDedup(match { it.queueId == "wo_comment_cmnt-999" })
        }
    }

    @Test
    fun `upsertComment calls dao upsert`() = runTest {
        val comment = buildComment()

        repository.upsertComment(ACCOUNT_ID, comment)

        coVerify { mockCommentDao.upsert(comment) }
    }

    // ─── syncStatus invariant ─────────────────────────────────────────────────

    @Test
    fun `all enqueued items have syncStatus pending`() = runTest {
        repository.upsert(ACCOUNT_ID, buildWo(serverVersion = 0L))
        repository.upsertAssignment(ACCOUNT_ID, buildAssignment())
        repository.upsertChecklistItem(ACCOUNT_ID, buildChecklistItem())
        repository.upsertComment(ACCOUNT_ID, buildComment())

        coVerify(exactly = 4) {
            mockSyncQueueDao.enqueueWithDedup(match { it.syncStatus == "pending" })
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildWo(
        woId: String = "wo-001",
        serverVersion: Long = 0L,
    ) = WorkOrderEntity(
        woId = woId,
        accountId = ACCOUNT_ID,
        assetId = null,
        locationId = null,
        title = "Fix pump",
        description = null,
        category = null,
        priority = null,
        status = "draft",
        requesterId = null,
        assignedTo = null,
        dispatcherNotes = null,
        requiredSkills = null,
        estimatedEffortMinutes = null,
        actualEffortMinutes = null,
        failureCode = null,
        completionNotes = null,
        partsNeeded = null,
        logId = null,
        dueDate = null,
        startedAt = null,
        completedAt = null,
        timerStartedAt = null,
        laborCost = null,
        partsCost = null,
        totalCost = null,
        currency = null,
        baseAmount = null,
        exchangeRateUsed = null,
        attributes = null,
        createdBy = null,
        approvalState = null,
        jobId = null,
        woKind = null,
        rrule = null,
        endType = null,
        endCount = null,
        endDate = null,
        meterType = null,
        meterDue = null,
        meterInterval = null,
        parentWoId = null,
        occurrenceDate = null,
        scheduleId = null,
        lastCompletedAt = null,
        timezone = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
        serverVersion = serverVersion,
        seq = null,
    )

    private fun buildAssignment(
        assignmentId: String = "asgn-001",
        serverVersion: Long = 1L,
    ) = WoAssignmentEntity(
        assignmentId = assignmentId,
        woId = "wo-001",
        accountId = ACCOUNT_ID,
        technicianId = "tech-001",
        assignedBy = null,
        assignedAt = 1_000L,
        unassignedAt = null,
        scheduledStart = null,
        scheduledEnd = null,
        status = "active",
        notes = null,
        ekEventIdentifier = null,
        isDirty = false,
        serverVersion = serverVersion,
        seq = null,
    )

    private fun buildChecklistItem(
        itemId: String = "item-001",
        serverVersion: Long = 0L,
    ) = WoChecklistItemEntity(
        itemId = itemId,
        woId = "wo-001",
        title = "Check oil",
        isCompleted = false,
        completedAt = null,
        displayOrder = 0L,
        serverVersion = serverVersion,
        seq = null,
    )

    private fun buildComment(
        commentId: String = "cmnt-001",
        serverVersion: Long = 0L,
    ) = WoCommentEntity(
        commentId = commentId,
        woId = "wo-001",
        authorId = "user-001",
        body = "Work done",
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
        serverVersion = serverVersion,
        seq = null,
    )
}
