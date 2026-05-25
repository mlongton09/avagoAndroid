package com.avago.feature.workorders.repository

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.db.entity.TechProfileEntity
import com.avago.core.data.db.entity.WoAssignmentEntity
import com.avago.core.data.db.entity.WoChecklistItemEntity
import com.avago.core.data.db.entity.WoCommentEntity
import com.avago.core.data.db.entity.WoTemplateEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkOrderRepository @Inject constructor(
    private val dbFactory: DatabaseFactory,
) {

    // ---------------------------------------------------------------------------
    // Work Orders
    // ---------------------------------------------------------------------------

    suspend fun observeAll(accountId: String): Flow<List<WorkOrderEntity>> =
        dbFactory.get(accountId).workOrderDao().observeAll(accountId)

    suspend fun getById(accountId: String, woId: String): WorkOrderEntity? =
        dbFactory.get(accountId).workOrderDao().getById(woId)

    suspend fun upsert(accountId: String, entity: WorkOrderEntity) {
        val db = dbFactory.get(accountId)
        db.workOrderDao().upsert(entity)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "work_order",
            entityId = entity.woId,
            serverVersion = entity.serverVersion,
            operation = "update",
        )
        Timber.d("[WoRepository] upserted ${entity.woId}")
    }

    suspend fun softDelete(accountId: String, woId: String) {
        val db = dbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.workOrderDao().softDelete(woId, now)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "work_order",
            entityId = woId,
            serverVersion = 0L,
            operation = "delete",
        )
        Timber.d("[WoRepository] soft-deleted $woId")
    }

    // ---------------------------------------------------------------------------
    // Assignments
    // ---------------------------------------------------------------------------

    suspend fun observeAssignments(accountId: String): Flow<List<WoAssignmentEntity>> =
        dbFactory.get(accountId).woAssignmentDao().observeAll(accountId)

    suspend fun observeAssignmentsForWo(
        accountId: String,
        woId: String,
    ): Flow<List<WoAssignmentEntity>> =
        observeAssignments(accountId).map { list -> list.filter { it.woId == woId && it.unassignedAt == null } }

    suspend fun upsertAssignment(accountId: String, entity: WoAssignmentEntity) {
        val db = dbFactory.get(accountId)
        db.woAssignmentDao().upsert(entity)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "wo_assignment",
            entityId = entity.assignmentId,
            serverVersion = entity.serverVersion,
            operation = "update",
        )
    }

    // ---------------------------------------------------------------------------
    // Checklist
    // ---------------------------------------------------------------------------

    suspend fun observeChecklistForWo(
        accountId: String,
        woId: String,
    ): Flow<List<WoChecklistItemEntity>> =
        dbFactory.get(accountId).woChecklistItemDao().observeAll(accountId)
            .map { list -> list.filter { it.woId == woId }.sortedBy { it.displayOrder } }

    suspend fun upsertChecklistItem(accountId: String, entity: WoChecklistItemEntity) {
        val db = dbFactory.get(accountId)
        db.woChecklistItemDao().upsert(entity)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "wo_checklist_item",
            entityId = entity.itemId,
            serverVersion = entity.serverVersion,
            operation = "update",
        )
    }

    suspend fun deleteChecklistItem(accountId: String, itemId: String) {
        val db = dbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.woChecklistItemDao().softDelete(itemId)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "wo_checklist_item",
            entityId = itemId,
            serverVersion = 0L,
            operation = "delete",
        )
    }

    // ---------------------------------------------------------------------------
    // Comments
    // ---------------------------------------------------------------------------

    suspend fun observeCommentsForWo(
        accountId: String,
        woId: String,
    ): Flow<List<WoCommentEntity>> =
        dbFactory.get(accountId).woCommentDao().observeAll(accountId)
            .map { list -> list.filter { it.woId == woId }.sortedByDescending { it.createdAt } }

    suspend fun upsertComment(accountId: String, entity: WoCommentEntity) {
        val db = dbFactory.get(accountId)
        db.woCommentDao().upsert(entity)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "wo_comment",
            entityId = entity.commentId,
            serverVersion = entity.serverVersion,
            operation = "update",
        )
    }

    // ---------------------------------------------------------------------------
    // Cost Lines
    // ---------------------------------------------------------------------------

    suspend fun observeCostLinesForWo(accountId: String, woId: String): Flow<List<LogCostLineEntity>> =
        dbFactory.get(accountId).logCostLineDao().observeForWo(accountId, woId)

    suspend fun upsertCostLine(accountId: String, entity: LogCostLineEntity) {
        val db = dbFactory.get(accountId)
        db.logCostLineDao().upsert(entity)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "log_cost_line",
            entityId = entity.lineId,
            serverVersion = entity.serverVersion,
            operation = "update",
        )
    }

    suspend fun deleteCostLine(accountId: String, lineId: String) {
        val db = dbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.logCostLineDao().softDelete(lineId, now)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "log_cost_line",
            entityId = lineId,
            serverVersion = 0L,
            operation = "delete",
        )
    }

    // ---------------------------------------------------------------------------
    // Templates
    // ---------------------------------------------------------------------------

    suspend fun observeTemplates(accountId: String): Flow<List<WoTemplateEntity>> =
        dbFactory.get(accountId).woTemplateDao().observeAll(accountId)

    // ---------------------------------------------------------------------------
    // Tech Profiles
    // ---------------------------------------------------------------------------

    suspend fun observeTechProfiles(accountId: String): Flow<List<TechProfileEntity>> =
        dbFactory.get(accountId).techProfileDao().observeAll(accountId)

    // ---------------------------------------------------------------------------
    // Sync helpers
    // ---------------------------------------------------------------------------

    private suspend fun enqueueSyncPush(
        accountId: String,
        entityType: String,
        entityId: String,
        serverVersion: Long,
        operation: String = "update",
    ) {
        val db = dbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.syncQueueDao().enqueueWithDedup(
            SyncQueueEntity(
                queueId = "${entityType}_${entityId}",
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                serverVersion = serverVersion,
                payload = null,
                syncStatus = "pending",
                attempts = 0L,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}
