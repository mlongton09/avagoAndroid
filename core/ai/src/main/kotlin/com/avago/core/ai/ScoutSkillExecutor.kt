package com.avago.core.ai

import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HITL-off execution path for Android Scout skills.
 *
 * When the user has disabled Human-in-the-Loop in preferences, [ScoutViewModel]
 * calls [executeIfPossible] instead of routing to a form. The executor builds
 * the same entity the form's Save handler would have built, persists it locally,
 * and enqueues a SyncQueue row so the change ships to the server on the next
 * SyncEngine cycle — no separate push call needed from here.
 *
 * Returns `true` when execution succeeded, `false` to signal "fall back to
 * HITL form" (skill not supported here, or a required field was missing).
 *
 * Mirrors iOS ScoutSkillExecutor.swift.
 */
@Singleton
class ScoutSkillExecutor @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) {

    suspend fun executeIfPossible(skillName: String, fields: Map<String, String?>): Boolean {
        val accountId = identityManager.getActiveAccountId() ?: return false

        return try {
            when (skillName) {
                "log-entry-create", "fuel-log", "inspection-from-voice" ->
                    executeAddLogEntry(fields, accountId)

                "work-order-create" ->
                    executeAddWorkOrder(fields, accountId)

                "reschedule" ->
                    executeEditWorkOrder(fields, accountId)

                "asset-create" ->
                    executeAddAsset(fields, accountId)

                "work-order-action" ->
                    executeWorkOrderAction(fields, accountId)

                "work-order-assign" ->
                    executeWorkOrderAssign(fields, accountId)

                else -> false
            }
        } catch (e: Exception) {
            Timber.w(e, "ScoutSkillExecutor: exception executing '$skillName'")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Per-skill handlers
    // -------------------------------------------------------------------------

    private suspend fun executeAddLogEntry(fields: Map<String, String?>, accountId: String): Boolean {
        val title = fields["title"]?.trim() ?: return false
        val assetId = fields["asset_id"] ?: return false
        if (title.isEmpty() || assetId.isEmpty()) return false

        val now = System.currentTimeMillis()
        val entryId = UUID.randomUUID().toString()
        val category = fields["category"] ?: "service"

        val entity = LogEntity(
            entryId = entryId,
            assetId = assetId,
            accountId = accountId,
            title = title,
            entryDate = parseDate(fields["log_date"]) ?: now,
            odometerValue = fields["meter"]?.toDoubleOrNull() ?: fields["odometer"]?.toDoubleOrNull(),
            category = category,
            cost = fields["cost"]?.toDoubleOrNull(),
            performedBy = fields["performed_by"],
            performedByUserId = fields["performed_by_user_id"],
            notes = fields["notes"]?.ifBlank { null },
            data = null,
            attributes = null,
            costMode = "total",
            costItems = null,
            costLabor = null,
            costTax = null,
            currency = fields["currency"] ?: "USD",
            baseAmount = fields["cost"]?.toDoubleOrNull(),
            exchangeRateUsed = 1.0,
            configId = null,
            configVersion = null,
            serviceId = null,
            costMisc = null,
            parentId = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            serverVersion = 0L,
            seq = null,
        )

        val db = dbFactory.get(accountId)
        db.logDao().upsert(entity)
        db.syncQueueDao().enqueueWithDedup(syncEntry("log", entryId, "insert", 0L, now))
        Timber.d("ScoutSkillExecutor: logged '$title'")
        return true
    }

    private suspend fun executeAddWorkOrder(fields: Map<String, String?>, accountId: String): Boolean {
        val title = fields["title"]?.trim() ?: return false
        if (title.isEmpty()) return false

        val now = System.currentTimeMillis()
        val woId = UUID.randomUUID().toString()
        val allowedPriorities = setOf("critical", "high", "medium", "low")
        val priority = fields["priority"]?.lowercase()?.let { if (it in allowedPriorities) it else null } ?: "medium"

        val entity = WorkOrderEntity(
            woId = woId,
            accountId = accountId,
            assetId = fields["asset_id"],
            locationId = fields["location_id"],
            title = title,
            description = fields["description"],
            category = fields["category"],
            priority = priority,
            status = "pending_review",
            requesterId = null,
            assignedTo = fields["assigned_to"],
            dispatcherNotes = null,
            requiredSkills = null,
            estimatedEffortMinutes = fields["estimated_effort_minutes"]?.toLongOrNull(),
            actualEffortMinutes = null,
            failureCode = null,
            completionNotes = null,
            partsNeeded = null,
            logId = null,
            dueDate = parseDate(fields["due_date"]),
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
            createdBy = identityManager.getActiveAccountId(),
            approvalState = null,
            jobId = null,
            woKind = "standard",
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
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            serverVersion = 0L,
            seq = null,
        )

        val db = dbFactory.get(accountId)
        db.workOrderDao().upsert(entity)
        db.syncQueueDao().enqueueWithDedup(syncEntry("work_order", woId, "insert", 0L, now))
        Timber.d("ScoutSkillExecutor: created WO '$title'")
        return true
    }

    private suspend fun executeEditWorkOrder(fields: Map<String, String?>, accountId: String): Boolean {
        val woId = fields["wo_id"] ?: return false
        val db = dbFactory.get(accountId)
        val existing = db.workOrderDao().getById(woId) ?: return false

        val now = System.currentTimeMillis()
        var changed = false

        var updated = existing
        fields["due_date"]?.let { ds -> parseDate(ds)?.let { updated = updated.copy(dueDate = it); changed = true } }
        fields["title"]?.trim()?.takeIf { it.isNotEmpty() }?.let { updated = updated.copy(title = it); changed = true }
        fields["description"]?.trim()?.let { updated = updated.copy(description = it); changed = true }
        fields["priority"]?.trim()?.takeIf { it.isNotEmpty() }?.let { updated = updated.copy(priority = it); changed = true }
        fields["category"]?.trim()?.takeIf { it.isNotEmpty() }?.let { updated = updated.copy(category = it); changed = true }

        if (!changed) return false

        updated = updated.copy(updatedAt = now)
        db.workOrderDao().upsert(updated)
        db.syncQueueDao().enqueueWithDedup(syncEntry("work_order", woId, "update", existing.serverVersion, now))
        Timber.d("ScoutSkillExecutor: edited WO $woId")
        return true
    }

    private suspend fun executeAddAsset(fields: Map<String, String?>, accountId: String): Boolean {
        val name = fields["name"]?.trim() ?: return false
        if (name.isEmpty()) return false

        val now = System.currentTimeMillis()
        val assetId = UUID.randomUUID().toString()

        val entity = AssetEntity(
            assetId = assetId,
            accountId = accountId,
            name = name,
            make = fields["make"],
            model = fields["model"],
            year = fields["year"]?.toLongOrNull(),
            assetType = fields["asset_type"],
            meterType = null,
            avatarColor = null,
            avatarInitial = name.firstOrNull()?.uppercaseChar()?.toString(),
            addressLine1 = null,
            addressLine2 = null,
            city = null,
            state = null,
            postalCode = null,
            country = null,
            locationId = fields["location_id"],
            attributes = null,
            isFreSample = false,
            parentAssetId = fields["parent_asset_id"],
            path = null,
            depth = 0L,
            childCount = 0L,
            isRental = false,
            rentalRate = null,
            rentalRateUnit = null,
            purchasePrice = null,
            salvageValue = null,
            usefulLifeMonths = null,
            depreciationMethod = null,
            placedInServiceDate = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            serverVersion = 0L,
            seq = null,
        )

        val db = dbFactory.get(accountId)
        db.assetDao().upsert(entity)
        db.syncQueueDao().enqueueWithDedup(syncEntry("asset", assetId, "insert", 0L, now))
        Timber.d("ScoutSkillExecutor: added asset '$name'")
        return true
    }

    private suspend fun executeWorkOrderAction(fields: Map<String, String?>, accountId: String): Boolean {
        val woId = fields["wo_id"] ?: return false
        val db = dbFactory.get(accountId)
        val existing = db.workOrderDao().getById(woId) ?: return false

        val now = System.currentTimeMillis()
        val actionToStatus = mapOf(
            "complete" to "complete",
            "start"    to "in_progress",
            "pause"    to "on_hold",
            "cancel"   to "cancelled",
            "reopen"   to "pending_review",
        )
        val newStatus = fields["action"]?.lowercase()?.let { actionToStatus[it] }
            ?: fields["status"]
            ?: existing.status

        val updated = existing.copy(
            status = newStatus,
            actualEffortMinutes = fields["actual_effort_minutes"]?.toLongOrNull() ?: existing.actualEffortMinutes,
            partsCost = fields["actual_parts_cost"]?.toDoubleOrNull()
                ?: fields["parts_cost"]?.toDoubleOrNull()
                ?: existing.partsCost,
            updatedAt = now,
        )

        db.workOrderDao().upsert(updated)
        db.syncQueueDao().enqueueWithDedup(syncEntry("work_order", woId, "update", existing.serverVersion, now))
        Timber.d("ScoutSkillExecutor: work-order-action on $woId → $newStatus")
        return true
    }

    private suspend fun executeWorkOrderAssign(fields: Map<String, String?>, accountId: String): Boolean {
        val woId = fields["wo_id"] ?: return false
        val assignedTo = fields["assigned_to"] ?: return false
        val db = dbFactory.get(accountId)
        val existing = db.workOrderDao().getById(woId) ?: return false

        val now = System.currentTimeMillis()
        val updated = existing.copy(
            assignedTo = assignedTo,
            updatedAt = now,
        )

        db.workOrderDao().upsert(updated)
        db.syncQueueDao().enqueueWithDedup(syncEntry("work_order", woId, "update", existing.serverVersion, now))
        Timber.d("ScoutSkillExecutor: assigned WO $woId to $assignedTo")
        return true
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun syncEntry(
        type: String,
        id: String,
        operation: String,
        serverVersion: Long,
        now: Long,
    ) = SyncQueueEntity(
        queueId = "${type}_$id",
        entityType = type,
        entityId = id,
        operation = operation,
        serverVersion = serverVersion,
        payload = null,
        syncStatus = "pending",
        attempts = 0L,
        lastError = null,
        createdAt = now,
        updatedAt = now,
    )

    private val ymdFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    private fun parseDate(v: String?): Long? {
        if (v == null) return null
        return try { isoFormat.parse(v)?.time }
            catch (_: Exception) {
                try { ymdFormat.parse(v)?.time }
                catch (_: Exception) { null }
            }
    }
}
