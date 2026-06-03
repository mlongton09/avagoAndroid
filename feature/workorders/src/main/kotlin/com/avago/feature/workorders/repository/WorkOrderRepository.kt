package com.avago.feature.workorders.repository

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LocationEntity
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

    suspend fun observeAll(accountId: String): Flow<List<WorkOrderEntity>> {
        return dbFactory.get(accountId).workOrderDao().observeAll(accountId)
    }

    /**
     * Reactive count of "upcoming + mine + now" work orders, used by the
     * bottom-nav Work Orders badge (matches iOS MainTabBarController
     * `refreshWoBadge`). The 7-day upper bound is recomputed by the caller
     * on a ticker so the badge stays accurate as time advances.
     */
    suspend fun observeUpcomingMineCount(
        accountId: String,
        userId: String,
        upperBoundMillis: Long,
    ): Flow<Int> =
        dbFactory.get(accountId).workOrderDao()
            .observeUpcomingMineCount(accountId, userId, upperBoundMillis)

    suspend fun getById(accountId: String, woId: String): WorkOrderEntity? {
        return dbFactory.get(accountId).workOrderDao().getById(woId)
    }

    suspend fun getAssetById(accountId: String, assetId: String): AssetEntity? {
        return dbFactory.get(accountId).assetDao().getById(assetId)
    }

    suspend fun getLocationById(accountId: String, locationId: String): LocationEntity? {
        return dbFactory.get(accountId).locationDao().getById(locationId)
    }

    suspend fun upsert(accountId: String, entity: WorkOrderEntity) {
        val db = dbFactory.get(accountId)
        db.workOrderDao().upsert(entity)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "work_order",
            entityId = entity.woId,
            serverVersion = entity.serverVersion,
            operation = if (entity.serverVersion == 0L) "insert" else "update",
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

    suspend fun observeAssignments(accountId: String): Flow<List<WoAssignmentEntity>> {
        return dbFactory.get(accountId).woAssignmentDao().observeAll(accountId)
    }

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

    suspend fun observeCostLinesForWo(accountId: String, woId: String): Flow<List<LogCostLineEntity>> {
        return dbFactory.get(accountId).logCostLineDao().observeForWo(accountId, woId)
    }

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

    suspend fun observeTemplates(accountId: String): Flow<List<WoTemplateEntity>> {
        return dbFactory.get(accountId).woTemplateDao().observeAll(accountId)
    }

    suspend fun upsertTemplate(accountId: String, entity: WoTemplateEntity) {
        dbFactory.get(accountId).woTemplateDao().upsert(entity)
    }

    suspend fun deleteTemplate(accountId: String, templateId: String) {
        dbFactory.get(accountId).woTemplateDao().softDelete(templateId, System.currentTimeMillis())
    }

    // ---------------------------------------------------------------------------
    // Tech Profiles
    // ---------------------------------------------------------------------------

    suspend fun observeTechProfiles(accountId: String): Flow<List<TechProfileEntity>> {
        return dbFactory.get(accountId).techProfileDao().observeAll(accountId)
    }

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

    // ---------------------------------------------------------------------------
    // Asset subtitle resolution — mirrors iOS UnifiedWorkOrderCell.assetSubtitle()
    // ---------------------------------------------------------------------------

    suspend fun assetLabelFor(assetId: String?, accountId: String): String? {
        if (assetId.isNullOrBlank()) return null
        val asset = dbFactory.get(accountId).assetDao().getById(assetId) ?: return null
        return asset.woSubtitle()
    }
}

private val REAL_ESTATE = setOf(
    "residential", "multifamily", "office", "industrial", "healthcare", "restaurant",
)
private val VEHICLE_OR_MARINE = setOf(
    "light_vehicle", "motorcycle", "commercial_vehicle", "recreational_vehicle",
    "heavy_equipment", "trailer", "pleasure_craft", "commercial_vessel",
    "atv_utv", "snowmobile", "personal_watercraft", "golf_cart",
)

// Mirrors iOS assetSubtitle(forAssetId:):
//   real estate → street address (addressLine1)
//   vehicle/marine → "year make model" (year + displaySubtitle)
//   everything else → "make model" then asset name as fallback
private fun AssetEntity.woSubtitle(): String? {
    val type = assetType ?: return name.takeIf { it.isNotBlank() }
    return when {
        type in REAL_ESTATE -> addressLine1?.takeIf { it.isNotBlank() }
        type in VEHICLE_OR_MARINE -> {
            val yearStr = year?.takeIf { it > 0L }?.toString()
            val makeModel = listOfNotNull(
                make?.takeIf { it.isNotBlank() },
                model?.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            when {
                yearStr != null && makeModel.isNotBlank() -> "$yearStr $makeModel"
                yearStr != null -> yearStr
                makeModel.isNotBlank() -> makeModel
                else -> name.takeIf { it.isNotBlank() }
            }
        }
        else -> {
            val makeModel = listOfNotNull(
                make?.takeIf { it.isNotBlank() },
                model?.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            makeModel.takeIf { it.isNotBlank() } ?: name.takeIf { it.isNotBlank() }
        }
    }
}
