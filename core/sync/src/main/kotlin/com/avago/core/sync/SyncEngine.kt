package com.avago.core.sync

import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.CycleCountEntity
import com.avago.core.data.db.entity.CycleCountLineEntity
import com.avago.core.data.db.entity.DocEntity
import com.avago.core.data.db.entity.GrnEntity
import com.avago.core.data.db.entity.GrnLineEntity
import com.avago.core.data.db.entity.InventoryEntity
import com.avago.core.data.db.entity.LocationEntity
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.PartIssueEntity
import com.avago.core.data.db.entity.PartIssueLineEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.data.db.entity.PoLineEntity
import com.avago.core.data.db.entity.PurchaseOrderEntity
import com.avago.core.data.db.entity.RolePermissionDefaultsEntity
import com.avago.core.data.db.entity.AccountRolePermissionsEntity
import com.avago.core.data.db.entity.ScheduleEntity
import com.avago.core.data.db.entity.StockingLevelEntity
import com.avago.core.data.db.entity.SyncMetadataEntity
import com.avago.core.data.db.entity.TechProfileEntity
import com.avago.core.data.db.entity.UserEntity
import com.avago.core.data.db.entity.VendorEntity
import com.avago.core.data.db.entity.WoAssignmentEntity
import com.avago.core.data.db.entity.WoChecklistItemEntity
import com.avago.core.data.db.entity.WoCommentEntity
import com.avago.core.data.db.entity.WoTemplateEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkException
import com.avago.core.network.model.SyncOperation
import com.avago.core.network.model.SyncPushRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val identity: IdentityManager,
    private val dbFactory: DatabaseFactory,
    private val client: AvagoServiceClient,
    private val payloadBuilder: SyncPayloadBuilder,
    // Use Provider<> to break circular dependency with SyncConflictCoordinator
    private val conflictCoordinator: Provider<SyncConflictCoordinator>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Entity types pulled in canonical order. */
    private val pullEntityTypes = listOf(
        "asset", "log", "log_cost_line", "schedule",
        "work_order", "wo_assignment", "wo_checklist_item", "wo_comment", "wo_template",
        "tech_profile",
        "inventory", "part", "stocking_level",
        "vendor", "purchase_order", "po_line",
        "grn", "grn_line",
        "cycle_count", "cycle_count_line",
        "part_issue", "part_issue_line",
        "doc", "photo",
        "user", "location",
        "role_permission_defaults", "account_role_permissions",
    )

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /** Full push + pull cycle. Returns immediately if already syncing. */
    suspend fun sync(): SyncResult {
        if (!mutex.tryLock()) {
            Timber.d("[SyncEngine] sync() skipped — already in progress")
            return SyncResult.Partial(0, 0)
        }
        return try {
            runSync(pullAfterPush = true)
        } finally {
            mutex.unlock()
        }
    }

    /** Push-only cycle (called after DAO writes). Returns immediately if already syncing. */
    suspend fun pushIfNeeded(): SyncResult {
        if (!mutex.tryLock()) {
            Timber.d("[SyncEngine] pushIfNeeded() skipped — already in progress")
            return SyncResult.Partial(0, 0)
        }
        return try {
            runSync(pullAfterPush = false)
        } finally {
            mutex.unlock()
        }
    }

    /** Reset in-flight items on connectivity loss. */
    suspend fun handleConnectivityLost() {
        val accountId = identity.getActiveAccountId() ?: return
        val db = dbFactory.get(accountId)
        db.syncQueueDao().resetInFlightToPending()
        _state.value = SyncState.Idle
        Timber.d("[SyncEngine] handleConnectivityLost: reset in-flight items to pending")
    }

    /** On launch: reset stale in-flight items left from a crash. */
    suspend fun handleStaleInFlight() {
        val accountId = identity.getActiveAccountId() ?: return
        val db = dbFactory.get(accountId)
        db.syncQueueDao().resetInFlightToPending()
        Timber.d("[SyncEngine] handleStaleInFlight: reset stale in-flight items")
    }

    /** Expose active account ID for SyncConflictCoordinator to use. */
    fun activeAccountId(): String? = identity.getActiveAccountId()

    // ---------------------------------------------------------------------------
    // Internal sync cycle
    // ---------------------------------------------------------------------------

    private suspend fun runSync(pullAfterPush: Boolean): SyncResult {
        val accountId = identity.getActiveAccountId()
        if (accountId == null) {
            Timber.w("[SyncEngine] No active account — skipping sync")
            return SyncResult.Failed(IllegalStateException("No active account"))
        }

        val db = dbFactory.get(accountId)
        var pushedCount = 0

        // -----------------------------------------------------------------------
        // Push phase
        // -----------------------------------------------------------------------
        _state.value = SyncState.Pushing

        // Reset stale in-flight items before starting (crash recovery)
        db.syncQueueDao().resetInFlightToPending()

        val pending = db.syncQueueDao().pendingItemsList()
        if (pending.isNotEmpty()) {
            val queueIds = pending.map { it.queueId }
            db.syncQueueDao().markInFlight(queueIds)

            val operations = pending.mapNotNull { item ->
                val payload = payloadBuilder.buildPayload(accountId, item.entityType, item.entityId)
                if (payload == null) {
                    Timber.w("[SyncEngine] No payload for ${item.entityType}/${item.entityId} — skipping")
                    null
                } else {
                    SyncOperation(
                        entity_type = item.entityType,
                        entity_id = item.entityId,
                        operation = item.operation,
                        payload = payload,
                        idempotency_key = item.queueId,
                    )
                }
            }

            if (operations.isNotEmpty()) {
                Timber.d("[SyncEngine] Pushing ${operations.size} operation(s)")
                try {
                    val response = client.syncPush(accountId, SyncPushRequest(operations))
                    val itemByEntityId = pending.associateBy { it.entityId.lowercase() }

                    for (result in response.results) {
                        val entityIdLower = result.entity_id.lowercase()
                        val item = itemByEntityId[entityIdLower] ?: continue

                        when {
                            result.success -> {
                                db.syncQueueDao().markSuccess(item.queueId)
                                pushedCount++
                                Timber.d("[SyncEngine] Push success: ${item.entityType} ${item.entityId}")
                            }
                            result.conflict -> {
                                db.syncQueueDao().markConflict(item.queueId)
                                val conflict = SyncConflict(
                                    queueId = item.queueId,
                                    entityType = item.entityType,
                                    entityId = item.entityId,
                                    operation = item.operation,
                                    displayName = item.entityId,
                                    conflictMessage = result.error ?: "Conflict",
                                )
                                conflictCoordinator.get().addConflict(conflict)
                                Timber.d("[SyncEngine] Push conflict: ${item.entityType} ${item.entityId}")
                            }
                            else -> {
                                db.syncQueueDao().markError(item.queueId, result.error ?: "Unknown error")
                                Timber.w("[SyncEngine] Push error: ${item.entityType} ${item.entityId} — ${result.error}")
                            }
                        }
                    }
                } catch (e: NetworkException) {
                    db.syncQueueDao().resetInFlightToPending()
                    Timber.e(e, "[SyncEngine] Push HTTP failed")
                    _state.value = SyncState.Error(e.message)
                    return SyncResult.Failed(e)
                } catch (e: Exception) {
                    db.syncQueueDao().resetInFlightToPending()
                    Timber.e(e, "[SyncEngine] Push failed")
                    _state.value = SyncState.Error(e.message ?: "Unknown error")
                    return SyncResult.Failed(e)
                }
            }
        }

        if (!pullAfterPush) {
            _state.value = SyncState.Idle
            return SyncResult.Partial(pushedCount, 0)
        }

        // -----------------------------------------------------------------------
        // Pull phase
        // -----------------------------------------------------------------------
        _state.value = SyncState.Pulling
        var pulledCount = 0

        for (entityType in pullEntityTypes) {
            try {
                var lastSeq = db.syncMetadataDao().getWatermark(entityType)
                // Ensure the metadata row exists so we can update it
                if (lastSeq == 0L) {
                    db.syncMetadataDao().upsert(SyncMetadataEntity(entityType, 0L, 0L))
                }

                var hasMore = true
                while (hasMore) {
                    val response = client.syncPull(accountId, entityType, lastSeq)
                    Timber.d("[SyncEngine] Pull $entityType: ${response.items.size} item(s), hasMore=${response.has_more}, maxSeq=${response.max_seq}")

                    for (item in response.items) {
                        upsertPulledItem(accountId, entityType, item)
                        pulledCount++
                    }

                    db.syncMetadataDao().updateWatermark(entityType, response.max_seq)
                    lastSeq = response.max_seq
                    hasMore = response.has_more && response.items.isNotEmpty()
                }
            } catch (e: Exception) {
                Timber.e(e, "[SyncEngine] Pull $entityType failed")
                // Continue with other entity types — partial sync is better than none
            }
        }

        _state.value = SyncState.Idle
        return SyncResult.Success
    }

    // ---------------------------------------------------------------------------
    // Pull upsert dispatch
    // ---------------------------------------------------------------------------

    private suspend fun upsertPulledItem(accountId: String, entityType: String, item: JsonObject) {
        val db = dbFactory.get(accountId)
        try {
            when (entityType) {
                "asset" -> {
                    val now = System.currentTimeMillis()
                    db.assetDao().upsert(
                        AssetEntity(
                            assetId = item.str("asset_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            name = item.str("name") ?: "",
                            make = item.str("make"),
                            model = item.str("model"),
                            year = item.lng("year"),
                            assetType = item.str("asset_type"),
                            meterType = item.str("meter_type"),
                            avatarColor = item.str("avatar_color"),
                            avatarInitial = item.str("avatar_initial"),
                            addressLine1 = item.str("address_line1"),
                            addressLine2 = item.str("address_line2"),
                            city = item.str("city"),
                            state = item.str("state"),
                            postalCode = item.str("postal_code"),
                            country = item.str("country"),
                            locationId = item.str("location_id"),
                            attributes = item.str("attributes"),
                            isFreSample = item.bool("is_fre_sample") ?: false,
                            parentAssetId = item.str("parent_asset_id"),
                            path = item.str("path"),
                            depth = item.lng("depth") ?: 0L,
                            childCount = item.lng("child_count") ?: 0L,
                            isRental = item.bool("is_rental") ?: false,
                            rentalRate = item.dbl("rental_rate"),
                            rentalRateUnit = item.str("rental_rate_unit"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "log" -> {
                    val now = System.currentTimeMillis()
                    db.logDao().upsert(
                        LogEntity(
                            entryId = item.str("entry_id") ?: item.str("log_id") ?: return,
                            assetId = item.str("asset_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            title = item.str("title") ?: "",
                            entryDate = isoToMs(item.str("log_date") ?: item.str("entry_date")) ?: now,
                            odometerValue = item.dbl("odometer_value") ?: item.dbl("meter"),
                            category = item.str("category"),
                            cost = item.dbl("cost"),
                            performedBy = item.str("performed_by"),
                            performedByUserId = item.str("performed_by_user_id"),
                            notes = item.str("notes"),
                            data = item.str("data"),
                            attributes = item.str("attributes"),
                            costMode = item.str("cost_mode"),
                            costItems = item.dbl("cost_items"),
                            costLabor = item.dbl("cost_labor"),
                            costTax = item.dbl("cost_tax"),
                            currency = item.str("currency"),
                            baseAmount = item.dbl("base_amount"),
                            exchangeRateUsed = item.dbl("exchange_rate_used"),
                            configId = item.str("config_id"),
                            configVersion = item.lng("config_version"),
                            parentId = item.str("parent_id"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "log_cost_line" -> {
                    val now = System.currentTimeMillis()
                    db.logCostLineDao().upsert(
                        LogCostLineEntity(
                            lineId = item.str("line_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            logId = item.str("log_id") ?: return,
                            kind = item.str("kind") ?: "part",
                            displayOrder = item.lng("display_order") ?: 0L,
                            inventoryId = item.str("inventory_id"),
                            userId = item.str("user_id"),
                            description = item.str("description"),
                            quantity = item.dbl("quantity") ?: 0.0,
                            unitCost = item.dbl("unit_cost") ?: 0.0,
                            taxAmount = item.dbl("tax_amount"),
                            glCode = item.str("gl_code"),
                            notes = item.str("notes"),
                            woId = item.str("wo_id"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "schedule" -> {
                    val now = System.currentTimeMillis()
                    db.scheduleDao().upsert(
                        ScheduleEntity(
                            scheduleId = item.str("schedule_id") ?: return,
                            assetId = item.str("asset_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            title = item.str("title") ?: "",
                            category = item.str("category"),
                            scheduleType = item.str("schedule_type") ?: "calendar",
                            rrule = item.str("rrule"),
                            endType = item.str("end_type"),
                            endCount = item.lng("end_count"),
                            endDate = isoToMs(item.str("end_date")),
                            meterType = item.str("meter_type"),
                            meterDue = item.dbl("meter_due"),
                            meterInterval = item.dbl("meter_interval"),
                            lastCompletedAt = isoToMs(item.str("last_completed_at")),
                            nextDueAt = isoToMs(item.str("next_due_at")),
                            isActive = item.bool("is_active") ?: true,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "work_order" -> {
                    val now = System.currentTimeMillis()
                    db.workOrderDao().upsert(
                        WorkOrderEntity(
                            woId = item.str("wo_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            assetId = item.str("asset_id"),
                            locationId = item.str("location_id"),
                            title = item.str("title") ?: "",
                            description = item.str("description"),
                            category = item.str("category"),
                            priority = item.str("priority"),
                            status = item.str("status") ?: "draft",
                            requesterId = item.str("requester_id"),
                            assignedTo = item.str("assigned_to"),
                            dispatcherNotes = item.str("dispatcher_notes"),
                            requiredSkills = item.str("required_skills"),
                            estimatedEffortMinutes = item.lng("estimated_effort_minutes"),
                            actualEffortMinutes = item.lng("actual_effort_minutes"),
                            failureCode = item.str("failure_code"),
                            completionNotes = item.str("completion_notes"),
                            partsNeeded = item.str("parts_needed"),
                            logId = item.str("log_id"),
                            dueDate = isoToMs(item.str("due_date")),
                            startedAt = isoToMs(item.str("started_at")),
                            completedAt = isoToMs(item.str("completed_at")),
                            timerStartedAt = null,
                            laborCost = item.dbl("labor_cost"),
                            partsCost = item.dbl("parts_cost"),
                            totalCost = item.dbl("total_cost"),
                            currency = item.str("currency"),
                            baseAmount = item.dbl("base_amount"),
                            exchangeRateUsed = item.dbl("exchange_rate_used"),
                            attributes = item.str("attributes"),
                            createdBy = item.str("created_by"),
                            approvalState = item.str("approval_state"),
                            jobId = item.str("job_id"),
                            woKind = item.str("wo_kind"),
                            rrule = item.str("rrule"),
                            endType = item.str("end_type"),
                            endCount = item.lng("end_count"),
                            endDate = isoToMs(item.str("end_date")),
                            meterType = item.str("meter_type"),
                            meterDue = item.dbl("meter_due"),
                            meterInterval = item.dbl("meter_interval"),
                            parentWoId = item.str("parent_wo_id"),
                            occurrenceDate = item.str("occurrence_date"),
                            scheduleId = item.str("schedule_id"),
                            lastCompletedAt = isoToMs(item.str("last_completed_at")),
                            timezone = item.str("timezone"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "wo_assignment" -> {
                    val now = System.currentTimeMillis()
                    db.woAssignmentDao().upsert(
                        WoAssignmentEntity(
                            assignmentId = item.str("assignment_id") ?: return,
                            woId = item.str("wo_id") ?: return,
                            technicianId = item.str("tech_id") ?: item.str("technician_id") ?: return,
                            assignedAt = isoToMs(item.str("assigned_at") ?: item.str("created_at")) ?: now,
                            unassignedAt = isoToMs(item.str("unassigned_at")),
                            status = item.str("status") ?: "pending",
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "wo_checklist_item" -> {
                    val now = System.currentTimeMillis()
                    db.woChecklistItemDao().upsert(
                        WoChecklistItemEntity(
                            itemId = item.str("checklist_item_id") ?: item.str("item_id") ?: return,
                            woId = item.str("wo_id") ?: return,
                            title = item.str("title") ?: "",
                            isCompleted = item.bool("completed") ?: item.bool("is_completed") ?: false,
                            completedAt = isoToMs(item.str("completed_at")),
                            displayOrder = item.lng("step_order") ?: item.lng("display_order") ?: 0L,
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "wo_comment" -> {
                    val now = System.currentTimeMillis()
                    db.woCommentDao().upsert(
                        WoCommentEntity(
                            commentId = item.str("comment_id") ?: return,
                            woId = item.str("wo_id") ?: return,
                            authorId = item.str("author_id") ?: return,
                            body = item.str("body") ?: "",
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "wo_template" -> {
                    val now = System.currentTimeMillis()
                    db.woTemplateDao().upsert(
                        WoTemplateEntity(
                            templateId = item.str("template_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            title = item.str("name") ?: item.str("title") ?: "",
                            description = item.str("description"),
                            category = item.str("category"),
                            checklistItems = item.str("checklist_json") ?: item.str("checklist_items"),
                            estimatedEffortMinutes = item.lng("estimated_effort_minutes"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "tech_profile" -> {
                    val now = System.currentTimeMillis()
                    db.techProfileDao().upsert(
                        TechProfileEntity(
                            techId = item.str("tech_profile_id") ?: item.str("tech_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            userId = item.str("user_id") ?: return,
                            skills = item.str("skills"),
                            certifications = item.str("certifications"),
                            hourlyRate = item.dbl("hourly_rate"),
                            currency = item.str("rate_currency") ?: item.str("currency"),
                            availability = item.str("availability"),
                            speedFactor = item.dbl("speed_factor"),
                            currentLocationLat = null,
                            currentLocationLng = null,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "inventory" -> {
                    val now = System.currentTimeMillis()
                    db.inventoryDao().upsert(
                        InventoryEntity(
                            inventoryId = item.str("inventory_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            partId = item.str("part_id") ?: return,
                            locationId = item.str("location_id"),
                            quantityOnHand = item.dbl("quantity_on_hand") ?: 0.0,
                            status = item.str("status") ?: "active",
                            lastTransactionId = item.str("last_transaction_id"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "part" -> {
                    val now = System.currentTimeMillis()
                    db.partDao().upsert(
                        PartEntity(
                            partId = item.str("part_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            sku = item.str("sku"),
                            name = item.str("name") ?: "",
                            description = item.str("description"),
                            category = item.str("category"),
                            unitOfMeasure = item.str("unit_of_measure"),
                            defaultVendorId = item.str("default_vendor_id"),
                            cost = item.dbl("cost"),
                            currency = item.str("currency"),
                            attributes = item.str("attributes"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "stocking_level" -> {
                    val now = System.currentTimeMillis()
                    db.stockingLevelDao().upsert(
                        StockingLevelEntity(
                            stockingLevelId = item.str("stocking_level_id") ?: return,
                            partId = item.str("part_id") ?: return,
                            locationId = item.str("location_id") ?: return,
                            minQty = item.dbl("min_qty"),
                            maxQty = item.dbl("max_qty"),
                            reorderQty = item.dbl("reorder_qty"),
                            safetyStock = item.dbl("safety_stock"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "vendor" -> {
                    val now = System.currentTimeMillis()
                    db.vendorDao().upsert(
                        VendorEntity(
                            vendorId = item.str("vendor_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            name = item.str("name") ?: "",
                            email = item.str("email"),
                            phone = item.str("phone"),
                            address = item.str("address"),
                            paymentTerms = item.str("payment_terms"),
                            taxId = item.str("tax_id"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "purchase_order" -> {
                    val now = System.currentTimeMillis()
                    db.purchaseOrderDao().upsert(
                        PurchaseOrderEntity(
                            poId = item.str("po_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            poNumber = item.str("po_number"),
                            vendorId = item.str("vendor_id"),
                            status = item.str("status") ?: "draft",
                            currency = item.str("currency"),
                            subtotal = item.dbl("subtotal"),
                            taxTotal = item.dbl("tax_total"),
                            shippingCost = item.dbl("shipping_cost"),
                            discount = item.dbl("discount"),
                            grandTotal = item.dbl("grand_total"),
                            baseGrandTotal = item.dbl("base_grand_total"),
                            exchangeRateUsed = item.dbl("exchange_rate_used"),
                            expectedDelivery = item.str("expected_delivery"),
                            shipToLocationId = item.str("ship_to_location_id"),
                            workOrderId = item.str("work_order_id"),
                            assetId = item.str("asset_id"),
                            requestedBy = item.str("requested_by"),
                            approvedBy = item.str("approved_by"),
                            approvedAt = isoToMs(item.str("approved_at")),
                            orderedAt = isoToMs(item.str("ordered_at")),
                            closedAt = isoToMs(item.str("closed_at")),
                            notes = item.str("notes"),
                            vendorInvoiceNo = item.str("vendor_invoice_no"),
                            createdBy = item.str("created_by"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "po_line" -> {
                    val now = System.currentTimeMillis()
                    db.poLineDao().upsert(
                        PoLineEntity(
                            poLineId = item.str("po_line_id") ?: return,
                            poId = item.str("po_id") ?: return,
                            partId = item.str("part_id"),
                            description = item.str("description"),
                            quantity = item.dbl("quantity") ?: 0.0,
                            unitCost = item.dbl("unit_cost"),
                            currency = item.str("currency"),
                            glCode = item.str("gl_code"),
                            receivedQty = item.dbl("received_qty"),
                            displayOrder = item.lng("display_order") ?: 0L,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "grn" -> {
                    val now = System.currentTimeMillis()
                    db.grnDao().upsert(
                        GrnEntity(
                            grnId = item.str("grn_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            poId = item.str("po_id"),
                            grnNumber = item.str("grn_number"),
                            receivedAt = isoToMs(item.str("received_at")),
                            receivedBy = item.str("received_by"),
                            receivedAtLocationId = item.str("received_at_location_id"),
                            carrier = item.str("carrier"),
                            trackingNumber = item.str("tracking_number"),
                            packingSlipNo = item.str("packing_slip_no"),
                            notes = item.str("notes"),
                            hasDiscrepancy = item.bool("has_discrepancy") ?: false,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "grn_line" -> {
                    val now = System.currentTimeMillis()
                    db.grnLineDao().upsert(
                        GrnLineEntity(
                            grnLineId = item.str("grn_line_id") ?: return,
                            grnId = item.str("grn_id") ?: return,
                            poLineId = item.str("po_line_id"),
                            partId = item.str("part_id"),
                            quantityReceived = item.dbl("quantity_received") ?: 0.0,
                            quantityExpected = item.dbl("quantity_expected"),
                            varianceReason = item.str("variance_reason"),
                            notes = item.str("notes"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "cycle_count" -> {
                    val now = System.currentTimeMillis()
                    db.cycleCountDao().upsert(
                        CycleCountEntity(
                            cycleCountId = item.str("cycle_count_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            locationId = item.str("location_id") ?: return,
                            status = item.str("status") ?: "open",
                            scopeType = item.str("scope_type"),
                            scopeValue = item.str("scope_value"),
                            startedAt = isoToMs(item.str("started_at")),
                            lockedAt = isoToMs(item.str("locked_at")),
                            completedAt = isoToMs(item.str("completed_at")),
                            startedBy = item.str("started_by"),
                            lockedBy = item.str("locked_by"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "cycle_count_line" -> {
                    val now = System.currentTimeMillis()
                    db.cycleCountLineDao().upsert(
                        CycleCountLineEntity(
                            lineId = item.str("line_id") ?: return,
                            cycleCountId = item.str("cycle_count_id") ?: return,
                            inventoryId = item.str("inventory_id") ?: return,
                            partId = item.str("part_id"),
                            expectedQty = item.dbl("expected_qty"),
                            countedQty = item.dbl("counted_qty"),
                            variance = item.dbl("variance"),
                            isCounted = item.bool("is_counted") ?: false,
                            countedAt = isoToMs(item.str("counted_at")),
                            countedBy = item.str("counted_by"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "part_issue" -> {
                    val now = System.currentTimeMillis()
                    db.partIssueDao().upsert(
                        PartIssueEntity(
                            issueId = item.str("issue_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            locationId = item.str("location_id"),
                            fromLocationId = item.str("from_location_id"),
                            toLocationId = item.str("to_location_id"),
                            issueType = item.str("issue_type") ?: "issue",
                            issuedAt = isoToMs(item.str("issued_at")) ?: now,
                            issuedBy = item.str("issued_by"),
                            referenceId = item.str("reference_id"),
                            referenceType = item.str("reference_type"),
                            notes = item.str("notes"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "part_issue_line" -> {
                    val now = System.currentTimeMillis()
                    db.partIssueLineDao().upsert(
                        PartIssueLineEntity(
                            lineId = item.str("line_id") ?: return,
                            issueId = item.str("issue_id") ?: return,
                            partId = item.str("part_id") ?: return,
                            inventoryId = item.str("inventory_id"),
                            quantity = item.dbl("quantity") ?: 0.0,
                            unitCost = item.dbl("unit_cost"),
                            notes = item.str("notes"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "doc" -> {
                    val now = System.currentTimeMillis()
                    db.docDao().upsert(
                        DocEntity(
                            docId = item.str("doc_id") ?: return,
                            assetId = item.str("asset_id"),
                            accountId = item.str("account_id") ?: accountId,
                            name = item.str("name") ?: "",
                            docType = item.str("doc_type") ?: "document",
                            mimeType = item.str("mime_type"),
                            storageKey = item.str("storage_key"),
                            downloadUrl = item.str("download_url"),
                            ocrRawText = item.str("ocr_raw_text"),
                            ocrExtractedJson = item.str("ocr_extracted_json"),
                            vendor = item.str("vendor"),
                            total = item.dbl("total"),
                            currency = item.str("currency"),
                            purchaseDate = isoToMs(item.str("purchase_date")),
                            uploadedBy = item.str("uploaded_by"),
                            uploadedAt = isoToMs(item.str("uploaded_at")),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "photo" -> {
                    val now = System.currentTimeMillis()
                    db.photoDao().upsert(
                        PhotoEntity(
                            photoId = item.str("photo_id") ?: return,
                            entityId = item.str("entity_id") ?: return,
                            entityType = item.str("entity_type") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            storageKey = item.str("storage_key"),
                            downloadUrl = item.str("download_url"),
                            sortOrder = item.lng("sort_order") ?: 0L,
                            isPrimary = item.bool("is_primary") ?: false,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at") ?: item.str("created_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "user" -> {
                    val now = System.currentTimeMillis()
                    db.userDao().upsert(
                        UserEntity(
                            userId = item.str("user_id") ?: return,
                            accountId = item.str("account_id"),
                            displayName = item.str("display_name"),
                            email = item.str("email"),
                            photoUrl = item.str("photo_url"),
                            role = item.str("role"),
                            isActive = item.bool("is_active") ?: true,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "location" -> {
                    val now = System.currentTimeMillis()
                    db.locationDao().upsert(
                        LocationEntity(
                            locationId = item.str("location_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            name = item.str("name") ?: "",
                            address = item.str("address") ?: item.str("address_line1"),
                            city = item.str("city"),
                            state = item.str("state"),
                            postalCode = item.str("postal_code"),
                            country = item.str("country"),
                            latitude = item.dbl("latitude"),
                            longitude = item.dbl("longitude"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "role_permission_defaults" -> {
                    db.rolePermissionDefaultsDao().upsert(
                        RolePermissionDefaultsEntity(
                            roleKey = item.str("role_key") ?: return,
                            permissions = item.str("permissions") ?: "{}",
                        )
                    )
                }

                "account_role_permissions" -> {
                    val now = System.currentTimeMillis()
                    db.accountRolePermissionsDao().upsert(
                        AccountRolePermissionsEntity(
                            id = item.str("id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            roleKey = item.str("role_key") ?: return,
                            permissions = item.str("permissions") ?: "{}",
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                else -> Timber.w("[SyncEngine] Unknown entity type in pull: $entityType")
            }
        } catch (e: Exception) {
            Timber.e(e, "[SyncEngine] Failed to upsert $entityType item")
        }
    }

    // ---------------------------------------------------------------------------
    // JsonObject parsing helpers
    // ---------------------------------------------------------------------------

    private fun JsonObject.str(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return try { element.jsonPrimitive.content } catch (_: Exception) { null }
    }

    private fun JsonObject.lng(key: String): Long? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return try { element.jsonPrimitive.longOrNull } catch (_: Exception) { null }
    }

    private fun JsonObject.dbl(key: String): Double? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return try { element.jsonPrimitive.doubleOrNull } catch (_: Exception) { null }
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return try {
            val primitive = element.jsonPrimitive
            primitive.booleanOrNull
                ?: primitive.longOrNull?.let { it != 0L }
        } catch (_: Exception) { null }
    }

    /** Parse ISO-8601 string to epoch milliseconds. Returns null if the string is null or blank. */
    private fun isoToMs(iso: String?): Long? {
        if (iso.isNullOrBlank() || iso == "null") return null
        return try {
            Instant.parse(iso).toEpochMilliseconds()
        } catch (e: Exception) {
            null
        }
    }
}
