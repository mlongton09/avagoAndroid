package com.avago.core.sync

import androidx.room.withTransaction
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.ConfigEntity
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
import com.avago.core.data.db.entity.ItemEntity
import com.avago.core.data.db.entity.GlAccountEntity
import com.avago.core.data.db.entity.JobEntity
import com.avago.core.data.db.entity.ServiceEntity
import com.avago.core.data.db.entity.AssetLocationHistoryEntity
import com.avago.core.data.db.entity.RoleLabelCacheEntity
import com.avago.core.data.db.entity.EventEntity
import com.avago.core.data.db.entity.LabelTemplateEntity
import com.avago.core.data.db.entity.WoTemplateEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.data.db.entity.BinEntity
import com.avago.core.data.db.entity.VendorPartEntity
import com.avago.core.data.db.entity.TechLaborRateEntity
import com.avago.core.data.db.entity.ReorderSuggestionEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkException
import com.avago.core.network.model.SyncOperation
import com.avago.core.network.model.SyncPullResponse
import com.avago.core.network.model.SyncPushRequest
import com.avago.core.network.model.SyncPushResponse
import com.avago.core.ui.AvagoToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlin.random.Random

@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val identity: IdentityManager,
    private val dbFactory: DatabaseFactory,
    private val client: AvagoServiceClient,
    private val payloadBuilder: SyncPayloadBuilder,
    // Use Provider<> to break circular dependency with SyncConflictCoordinator
    private val conflictCoordinator: Provider<SyncConflictCoordinator>,
    private val toast: AvagoToast,
    @ApplicationScope private val scope: CoroutineScope,
    // Provider<> breaks potential circular dependency since DeltaPushApplier uses DatabaseFactory
    private val deltaApplier: Provider<DeltaPushApplier>,
    // Provider<> avoids circular dependency; PreferencesSync depends on AvagoServiceClient
    private val preferencesSync: Provider<PreferencesSync>,
    // Provider<> avoids potential circular dependency from PhotoUploader's own dependencies
    private val photoUploader: Provider<PhotoUploader>,
    private val syncGate: SyncGate,
    private val connectivity: ConnectivityMonitor,
) {
    private val mutex = Mutex()

    // Set to true when sync() is called while another cycle is already running.
    // finishSync checks this and kicks off another cycle so late-arriving nudges
    // (e.g. rapid WO + wo_assignment + WO pushes) aren't silently dropped.
    // Mirrors iOS SyncEngine.resyncRequested.
    private val resyncRequested = AtomicBoolean(false)

    // Set to true when a sync cycle finishes with an error; cleared on success.
    // ConnectivityMonitor observes this to trigger a re-sync on connectivity recovery.
    // Mirrors iOS SyncEngine.lastSyncFailed.
    private val lastSyncFailed = AtomicBoolean(false)

    // Epoch-ms after which rate-limited syncs may resume. Persisted to SharedPreferences
    // so app restarts respect an active backoff and don't fire a request that extends the
    // server's rate-limit window further.
    private val rateLimitPrefs by lazy {
        appContext.getSharedPreferences("sync_rate_limit", Context.MODE_PRIVATE)
    }
    private val rateLimitedUntilMs = AtomicLong(
        rateLimitPrefs.getLong("rate_limited_until_ms", 0L)
    )

    private fun setRateLimitedUntil(epochMs: Long) {
        rateLimitedUntilMs.set(epochMs)
        rateLimitPrefs.edit().putLong("rate_limited_until_ms", epochMs).apply()
    }

    /** Milliseconds remaining in the active rate-limit window, or 0 if not limited. */
    val rateLimitedMsRemaining: Long
        get() = maxOf(0L, rateLimitedUntilMs.get() - System.currentTimeMillis())

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    // Emits the account_id when a non-stale 403 is received — the account is permanently
    // inaccessible. Mirrors iOS forceReprovision() triggered from AvagoServiceClient+Sync.
    private val _accountGoneEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val accountGoneEvents: kotlinx.coroutines.flow.SharedFlow<String> = _accountGoneEvents

    init {
        // Observe connectivity: when the path recovers after a failure, trigger a sync.
        // Mirrors iOS SyncEngine.setupPathMonitor → lastSyncFailed guard.
        scope.launch {
            connectivity.networkStatus.collect { reachable ->
                if (reachable && lastSyncFailed.get()) {
                    Timber.d("[SyncEngine] Connectivity restored — triggering sync")
                    sync()
                }
            }
        }
    }

    // entityType → (sqlTable, pkColumn, jsonPkKey)
    // Used for: (1) optimistic concurrency version check on pull, (2) server_version update on push.
    // json_pk_key is the JSON field that carries the entity ID from the server response.
    private val entityVersionInfo: Map<String, Triple<String, String, String>> = mapOf(
        "asset"             to Triple("assets",             "asset_id",          "asset_id"),
        "log"               to Triple("log",                "log_id",            "log_id"),
        "log_cost_line"     to Triple("log_cost_lines",     "line_id",           "line_id"),
        "schedule"          to Triple("schedules",          "schedule_id",       "schedule_id"),
        "work_order"        to Triple("work_orders",        "wo_id",             "wo_id"),
        "wo_assignment"     to Triple("wo_assignments",     "assignment_id",     "assignment_id"),
        "wo_checklist_item" to Triple("wo_checklist_items", "checklist_item_id", "checklist_item_id"),
        "wo_comment"        to Triple("wo_comments",        "comment_id",        "comment_id"),
        "wo_template"       to Triple("wo_templates",       "template_id",       "template_id"),
        "tech_profile"      to Triple("tech_profiles",      "tech_id",           "tech_profile_id"),
        "inventory"         to Triple("inventory",          "inventory_id",      "inventory_id"),
        "part"              to Triple("parts",              "part_id",           "part_id"),
        "stocking_level"    to Triple("stocking_levels",    "stocking_level_id", "stocking_level_id"),
        "vendor"            to Triple("vendors",            "vendor_id",         "vendor_id"),
        "purchase_order"    to Triple("purchase_orders",    "po_id",             "po_id"),
        "po_line"           to Triple("po_lines",           "po_line_id",        "po_line_id"),
        "grn"               to Triple("grns",               "grn_id",            "grn_id"),
        "grn_line"          to Triple("grn_lines",          "grn_line_id",       "grn_line_id"),
        "cycle_count"       to Triple("cycle_counts",       "cycle_count_id",    "cycle_count_id"),
        "cycle_count_line"  to Triple("cycle_count_lines",  "line_id",           "line_id"),
        "part_issue"        to Triple("part_issues",        "issue_id",          "issue_id"),
        "part_issue_line"   to Triple("part_issue_lines",   "line_id",           "line_id"),
        "doc"               to Triple("docs",               "doc_id",            "doc_id"),
        "photo"             to Triple("photos",             "photo_id",          "photo_id"),
        "location"          to Triple("locations",          "location_id",       "location_id"),
        "item"              to Triple("items",              "item_id",           "item_id"),
        "gl_account"        to Triple("gl_accounts",        "gl_account_id",     "gl_account_id"),
        "job"               to Triple("jobs",               "job_id",            "job_id"),
        "service"           to Triple("services",           "service_id",        "service_id"),
        "bin"                to Triple("bins",                "bin_id",            "bin_id"),
        "vendor_part"        to Triple("vendor_parts",        "vendor_part_id",    "vendor_part_id"),
        "tech_labor_rate"    to Triple("tech_labor_rates",    "rate_id",           "rate_id"),
        "reorder_suggestion" to Triple("reorder_suggestions", "suggestion_id",     "suggestion_id"),
    )

    // Entity types for which a pending local push must block the pull upsert, preventing
    // a stale server snapshot from overwriting uncommitted user edits.
    // Mirrors iOS SyncEngine hasPendingPush guard (asset, log, work_order).
    private val pendingPushGuardTypes = setOf("asset", "log", "work_order")

    // Priority entities are pulled first so the asset list and work orders are visible
    // as soon as possible. The sync gate opens after these complete.
    private val priorityPullTypes = listOf(
        "asset", "log", "log_cost_line",
        "work_order", "wo_assignment", "wo_checklist_item", "wo_comment", "wo_template",
        "tech_profile", "location", "config",
    )

    // Secondary entities are pulled after the gate opens; the UI is already populated by then.
    private val secondaryPullTypes = listOf(
        "inventory", "part", "stocking_level",
        "vendor", "purchase_order", "po_line",
        "grn", "grn_line",
        "cycle_count", "cycle_count_line",
        "part_issue", "part_issue_line",
        "doc", "photo",
        "label_template",
        "item", "gl_account", "job", "service",
        "bin", "vendor_part", "tech_labor_rate", "reorder_suggestion",
    )

    private val pullEntityTypes get() = priorityPullTypes + secondaryPullTypes

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Full push + pull cycle. Safe to call from any coroutine.
     * If a sync is already in progress, flags a re-sync to run after it completes so
     * late-arriving nudges (e.g. rapid WO + wo_assignment pushes) aren't silently dropped.
     * Mirrors iOS SyncEngine.sync().
     */
    suspend fun sync(): SyncResult {
        if (!mutex.tryLock()) {
            resyncRequested.set(true)
            Timber.d("[SyncEngine] sync() queued — already in progress")
            return SyncResult.Partial(0, 0)
        }
        return try {
            val result = runSync(pullAfterPush = true)
            lastSyncFailed.set(result is SyncResult.Failed)
            result
        } finally {
            mutex.unlock()
            // One or more nudges arrived while we were syncing. Run another cycle so the
            // late writes get pulled this session, not on the next scheduled sync.
            if (resyncRequested.getAndSet(false) && !lastSyncFailed.get()) {
                Timber.d("[SyncEngine] Re-sync requested by nudge during prior cycle — launching")
                scope.launch { sync() }
            }
        }
    }

    /**
     * Push-only cycle (called after DAO writes). Returns immediately if already syncing.
     * Mirrors iOS SyncEngine.pushIfNeeded().
     */
    suspend fun pushIfNeeded(): SyncResult {
        if (!mutex.tryLock()) {
            resyncRequested.set(true)
            Timber.d("[SyncEngine] pushIfNeeded() queued — already in progress")
            return SyncResult.Partial(0, 0)
        }
        return try {
            val result = runSync(pullAfterPush = false)
            lastSyncFailed.set(result is SyncResult.Failed)
            result
        } finally {
            mutex.unlock()
        }
    }

    /** Reset in-flight items on connectivity loss. Mirrors iOS SyncEngine.handleConnectivityLost(). */
    suspend fun handleConnectivityLost() {
        resyncRequested.set(false)
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

    /** Reset all sync watermarks for [accountId] so the next sync performs a full re-pull. */
    suspend fun resetAllWatermarks(accountId: String) {
        val db = dbFactory.get(accountId)
        pullEntityTypes.forEach { entityType ->
            db.syncMetadataDao().resetWatermark(entityType)
        }
        // Also clear any rate-limit backoff so the next sign-in can sync immediately.
        setRateLimitedUntil(0L)
        Timber.d("[SyncEngine] resetAllWatermarks: cleared watermarks and rate-limit backoff for $accountId")
    }

    /**
     * Self-healing fallback for empty config tables — mirrors iOS forceConfigResyncIfMissing().
     * Call from any UI screen whose config-backed picker finds zero rows. Resets the config
     * watermark and triggers a one-shot sync so pickers repopulate without a full reinstall.
     * Guard prevents multiple calls from racing within the same app launch.
     */
    private val configResyncGuard = AtomicBoolean(false)

    suspend fun forceConfigResyncIfMissing() {
        if (!configResyncGuard.compareAndSet(false, true)) return
        val accountId = identity.getActiveAccountId() ?: run {
            configResyncGuard.set(false)
            return
        }
        val db = dbFactory.get(accountId)
        if (db.configDao().count() > 0) {
            configResyncGuard.set(false)
            return
        }
        Timber.w("[SyncEngine] Config table is empty — resetting watermark and re-pulling")
        db.syncMetadataDao().resetWatermark("config")
        sync()
    }

    /** Clear any rate-limit backoff so the next sync runs immediately. Call on sign-in. */
    fun clearRateLimitBackoff() {
        setRateLimitedUntil(0L)
        Timber.d("[SyncEngine] clearRateLimitBackoff: backoff cleared")
    }

    /** Expose active account ID for SyncConflictCoordinator to use. */
    fun activeAccountId(): String? = identity.getActiveAccountId()

    // ---------------------------------------------------------------------------
    // Internal sync cycle
    // ---------------------------------------------------------------------------

    private suspend fun runSync(pullAfterPush: Boolean): SyncResult {
        // Wait for auth initialisation to complete. initOnLaunch() always sets
        // isInitialized = true (even on failure) via its finally block, so this
        // never blocks indefinitely. Without this guard, the foreground-sync
        // lifecycle observer and observeAccountChangesForSync() can both fire
        // before tokens are in the store, producing a flood of 401s.
        identity.isInitialized.filter { it }.first()

        val accountId = identity.getActiveAccountId()
        if (accountId == null) {
            Timber.d("[SyncEngine] No active account — skipping sync")
            return SyncResult.Partial(0, 0)
        }

        val backoffUntil = rateLimitedUntilMs.get()
        if (backoffUntil > System.currentTimeMillis()) {
            val remainingSec = (backoffUntil - System.currentTimeMillis()) / 1000
            Timber.w("[SyncEngine] Rate-limited — skipping sync, ${remainingSec}s remaining")
            return SyncResult.Partial(0, 0)
        }

        val db = dbFactory.get(accountId)
        var pushedCount = 0

        // -----------------------------------------------------------------------
        // Push phase
        // -----------------------------------------------------------------------
        _state.value = SyncState.Pushing

        // Recycle items left in_flight from a prior interrupted session, and retry
        // transient errors (e.g. server temporarily rejecting an entity type).
        // Mirrors iOS SyncEngine.runPush: resetInFlightToPending + resetErrorsToPending.
        db.syncQueueDao().resetInFlightToPending()
        db.syncQueueDao().resetErrorsToPending()

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
                        // Send local server_version for server-side optimistic concurrency.
                        // force=false: normal path; server rejects if a higher version exists.
                        server_version = item.serverVersion ?: 0L,
                        force = false,
                    )
                }
            }

            if (operations.isNotEmpty()) {
                Timber.d("[SyncEngine] Pushing ${operations.size} operation(s)")
                try {
                    val response = withRetry<SyncPushResponse> { client.syncPush(accountId, SyncPushRequest(operations)) }
                    // Build case-insensitive lookup (server returns lowercase UUIDs).
                    val itemByEntityId = pending.associateBy { it.entityId.lowercase() }

                    for (result in response.results) {
                        val entityIdLower = result.entity_id.lowercase()
                        val item = itemByEntityId[entityIdLower] ?: continue

                        when {
                            result.success -> {
                                // Only overwrite local server_version when the server actually
                                // told us one. If it didn't, keep the local version so the next
                                // pull's version-guard still works correctly.
                                // Mirrors iOS SyncQueueDAO.markSuccess nextVersion logic.
                                val nextVersion = if ((result.server_version ?: 0L) > 0L)
                                    result.server_version!! else (item.serverVersion ?: 1L)
                                db.syncQueueDao().markSuccess(item.queueId)
                                updateEntityServerVersion(db, item.entityType, item.entityId, nextVersion)
                                pushedCount++
                                Timber.d("[SyncEngine] Push success: ${item.entityType} ${item.entityId} v$nextVersion")
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
                    if (e.code == 429) {
                        val waitSec = e.retryAfterSeconds ?: 900L
                        setRateLimitedUntil(System.currentTimeMillis() + waitSec * 1_000L)
                        Timber.w("[SyncEngine] Push got 429 — backing off ${waitSec}s")
                    } else {
                        Timber.e(e, "[SyncEngine] Push HTTP failed")
                    }
                    _state.value = SyncState.Error(e.message)
                    return SyncResult.Failed(e)
                } catch (e: Exception) {
                    db.syncQueueDao().resetInFlightToPending()
                    Timber.e(e, "[SyncEngine] Push failed")
                    _state.value = SyncState.Error(e.message ?: "Unknown error")
                    toast.error("Sync failed. Changes will retry when reconnected.")
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
        val pulledCount = AtomicLong(0L)

        // Pulls all entity types concurrently (mirrors iOS pullDataEntitiesInParallel).
        // supervisorScope lets each entity pull fail independently; fatal errors (403/429)
        // are collected and returned after all tasks finish rather than aborting mid-flight.
        suspend fun pullGroup(types: List<String>): SyncResult.Failed? {
            val fatalError = java.util.concurrent.atomic.AtomicReference<SyncResult.Failed?>(null)
            supervisorScope {
                types.map { entityType ->
                    async {
                        try {
                            var lastSeq = db.syncMetadataDao().getWatermark(entityType) ?: 0L
                            if (lastSeq == 0L) {
                                db.syncMetadataDao().upsert(SyncMetadataEntity(entityType, 0L, 0L))
                            }

                            var hasMore = true
                            while (hasMore) {
                                val response = withRetry<SyncPullResponse> { client.syncPull(accountId, entityType, lastSeq) }
                                Timber.d("[SyncEngine] Pull $entityType: ${response.items.size} item(s), hasMore=${response.has_more}, maxSeq=${response.max_seq}")

                                db.withTransaction {
                                    for (item in response.items) {
                                        upsertPulledItem(accountId, entityType, item)
                                        pulledCount.incrementAndGet()
                                    }
                                    db.syncMetadataDao().updateWatermark(entityType, response.max_seq)
                                }
                                lastSeq = response.max_seq
                                hasMore = response.has_more && response.items.isNotEmpty()
                            }
                        } catch (e: NetworkException) {
                            if (e.code == 403) {
                                if (e.stalePermissions) {
                                    Timber.w("[SyncEngine] Pull $entityType for $accountId got 403 stale-permissions — refreshing cache")
                                    client.notifyPermissionsStale(accountId)
                                } else {
                                    // Emit the account_id whose pull just 403'd — NOT the
                                    // active account. A stale account in the manifest
                                    // (from a previous user / revoked membership) would
                                    // otherwise sign out whichever account happens to be
                                    // active, blowing away the live session.
                                    Timber.w("[SyncEngine] Pull $entityType for $accountId got 403 — account gone, signalling re-auth")
                                    _accountGoneEvents.tryEmit(accountId)
                                }
                                fatalError.compareAndSet(null, SyncResult.Failed(e))
                            } else if (e.code == 429) {
                                val waitSec = e.retryAfterSeconds ?: 900L
                                val until = System.currentTimeMillis() + waitSec * 1_000L
                                setRateLimitedUntil(until)
                                Timber.w("[SyncEngine] Pull $entityType got 429 — backing off ${waitSec}s")
                                _state.value = SyncState.Error("Rate limited")
                                lastSyncFailed.set(true)
                                fatalError.compareAndSet(null, SyncResult.Failed(e))
                            } else {
                                Timber.e(e, "[SyncEngine] Pull $entityType failed (HTTP ${e.code}) — continuing")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "[SyncEngine] Pull $entityType failed — continuing")
                        }
                    }
                }.awaitAll()
            }
            return fatalError.get()
        }

        // Pull asset/WO/log/location entities first so the landing screens show data immediately.
        pullGroup(priorityPullTypes)?.let { return it }
        // Open the gate now — asset list is populated and the UI can render while the
        // slower secondary entities (inventory, docs, vendor, etc.) continue downloading.
        deltaApplier.get().markFirstSyncComplete(accountId)
        syncGate.open()

        // Pull remaining entity types in the background while the UI is already responsive.
        pullGroup(secondaryPullTypes)?.let { return it }

        // Pull cross-device user preferences (non-fatal if this fails)
        try {
            preferencesSync.get().refreshFromServer(accountId)
        } catch (e: Exception) {
            Timber.w(e, "[SyncEngine] PreferencesSync.refreshFromServer failed — ignoring")
        }

        // Kick off photo uploads now that we have the latest server state
        try {
            photoUploader.get().sweep(accountId)
        } catch (e: Exception) {
            Timber.e(e, "[SyncEngine] PhotoUploader sweep failed")
        }

        setRateLimitedUntil(0L)
        _state.value = SyncState.Idle
        return SyncResult.Success
    }

    // ---------------------------------------------------------------------------
    // Pull upsert dispatch
    // ---------------------------------------------------------------------------

    private suspend fun upsertPulledItem(accountId: String, entityType: String, item: JsonObject) {
        val db = dbFactory.get(accountId)

        // Optimistic concurrency: skip if the server sent an older or equal snapshot.
        // hasPendingPush guard: for asset/log/work_order, skip if a local edit is queued
        // to push — replaying an older server snapshot would overwrite the user's change.
        // Mirrors iOS SyncEngine.upsertItem server_version check + hasPendingPush guard.
        val incomingVersion = item.lng("server_version") ?: 0L
        val versionInfo = entityVersionInfo[entityType]
        if (versionInfo != null) {
            val (table, pkCol, jsonPkKey) = versionInfo
            val entityId = item.str(jsonPkKey)
            if (entityId != null) {
                var localVersion = 0L
                try {
                    localVersion = queryLocalServerVersion(db, table, pkCol, entityId)
                    if (incomingVersion > 0L && incomingVersion <= localVersion) {
                        Timber.v("[SyncEngine] Skip pull $entityType $entityId — v$incomingVersion <= local v$localVersion")
                        return
                    }
                } catch (_: Exception) { /* db query failed — proceed with upsert */ }
                if (entityType in pendingPushGuardTypes) {
                    try {
                        if (db.syncQueueDao().hasPendingPush(entityType, entityId) != null) {
                            Timber.d("[SyncEngine] Skip pull $entityType $entityId — pending push in queue")
                            return
                        }
                    } catch (_: Exception) { /* proceed */ }
                }
                // Soft-delete guard: if the server record is already deleted and we have no
                // local row, skip — no point inserting a ghost deleted row.
                // Mirrors iOS SyncEngine soft-delete-aware path.
                if (item.str("deleted_at") != null && localVersion == 0L) {
                    Timber.v("[SyncEngine] Skip pull $entityType $entityId — soft-deleted, not local")
                    return
                }
            }
        }

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
                            purchasePrice = item.dbl("purchase_price"),
                            salvageValue = item.dbl("salvage_value"),
                            usefulLifeMonths = item.lng("useful_life_months"),
                            depreciationMethod = item.str("depreciation_method"),
                            placedInServiceDate = isoToMs(item.str("placed_in_service_date")),
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
                            entryId = item.str("log_id") ?: return,
                            assetId = item.str("asset_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            title = item.str("title") ?: "",
                            entryDate = isoToMs(item.str("log_date")) ?: now,
                            odometerValue = item.dbl("meter"),
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
                            serviceId = item.str("service_id"),
                            costMisc = item.dbl("cost_misc"),
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
                            timezone = item.str("timezone"),
                            notes = item.str("notes"),
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
                            accountId = item.str("account_id") ?: accountId,
                            technicianId = item.str("tech_id") ?: item.str("technician_id") ?: return,
                            assignedBy = item.str("assigned_by"),
                            assignedAt = isoToMs(item.str("assigned_at") ?: item.str("created_at")) ?: now,
                            unassignedAt = isoToMs(item.str("unassigned_at")),
                            scheduledStart = isoToMs(item.str("scheduled_start")),
                            scheduledEnd = isoToMs(item.str("scheduled_end")),
                            status = item.str("status") ?: "pending",
                            notes = item.str("notes"),
                            ekEventIdentifier = item.str("ek_event_identifier"),
                            isDirty = item.bool("is_dirty") ?: false,
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
                            displayName = item.str("display_name"),
                            skills = item.str("skills"),
                            certifications = item.str("certifications"),
                            hourlyRate = item.dbl("hourly_rate"),
                            currency = item.str("rate_currency") ?: item.str("currency"),
                            availability = item.str("availability"),
                            speedFactor = item.dbl("speed_factor"),
                            maxActiveWos = item.lng("max_active_wos")?.toInt(),
                            isAvailable = item.bool("is_available") ?: true,
                            homeLocationId = item.str("home_location_id"),
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
                            binId = item.str("bin_id"),
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
                            sku = item.str("part_number"),
                            name = item.str("part_name") ?: "",
                            description = item.str("description"),
                            category = item.str("category"),
                            unitOfMeasure = item.str("unit_of_measure"),
                            defaultVendorId = item.str("default_vendor_id"),
                            cost = item.dbl("unit_cost"),
                            currency = item.str("currency"),
                            attributes = item.str("attributes"),
                            manufacturer = item.str("manufacturer"),
                            reorderQuantity = item.dbl("reorder_quantity"),
                            status = item.str("status"),
                            entityType = item.str("entity_type"),
                            entityId = item.str("entity_id"),
                            quantity = item.dbl("quantity"),
                            gtin = item.str("gtin"),
                            serialNumber = item.str("serial_number"),
                            notes = item.str("notes"),
                            baseAmount = item.dbl("base_amount"),
                            exchangeRateUsed = item.dbl("exchange_rate_used"),
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
                            vendorCode = item.str("vendor_code"),
                            contactName = item.str("contact_name"),
                            email = item.str("email"),
                            phone = item.str("phone"),
                            fax = item.str("fax"),
                            website = item.str("website"),
                            address = item.str("address"),
                            accountNumber = item.str("account_number"),
                            paymentTerms = item.str("payment_terms"),
                            defaultCurrency = item.str("default_currency"),
                            taxId = item.str("tax_id"),
                            rating = item.dbl("rating"),
                            preferred = item.bool("preferred") ?: false,
                            active = item.bool("active") ?: true,
                            qboVendorId = item.str("qbo_vendor_id"),
                            notes = item.str("notes"),
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
                            entityId = item.str("entity_id"),
                            entityType = item.str("entity_type"),
                            accountId = item.str("account_id") ?: accountId,
                            name = item.str("title") ?: item.str("name") ?: "",
                            docType = item.str("document_type") ?: item.str("doc_type"),
                            mimeType = item.str("mime_type"),
                            storageKey = item.str("storage_key"),
                            downloadUrl = item.str("download_url"),
                            fileHash = item.str("file_hash"),
                            fileSize = item.lng("file_size"),
                            ocrRawText = item.str("ocr_raw_text"),
                            ocrExtractedJson = item.str("ocr_extracted_json"),
                            vendor = item.str("vendor"),
                            total = item.dbl("total_amount") ?: item.dbl("total"),
                            currency = item.str("currency"),
                            purchaseDate = isoToMs(item.str("purchase_date")),
                            warrantyEndDate = isoToMs(item.str("warranty_end_date")),
                            notes = item.str("notes"),
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
                            shortCode = item.str("short_code"),
                            address = item.str("address"),
                            city = item.str("city"),
                            state = item.str("state"),
                            postalCode = item.str("postal_code"),
                            country = item.str("country"),
                            latitude = item.dbl("latitude"),
                            longitude = item.dbl("longitude"),
                            timezone = item.str("timezone"),
                            isPrimary = item.bool("is_primary") ?: false,
                            archived = item.bool("archived") ?: false,
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

                "label_template" -> {
                    val now = System.currentTimeMillis()
                    db.labelTemplateDao().upsert(
                        LabelTemplateEntity(
                            id = item.str("id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            name = item.str("name") ?: "",
                            templateType = item.str("template_type"),
                            content = item.str("content"),
                            widthMm = item.dbl("width_mm"),
                            heightMm = item.dbl("height_mm"),
                            deletedAt = isoToMs(item.str("deleted_at")),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverSeq = item.lng("server_seq") ?: item.lng("seq") ?: 0L,
                        )
                    )
                }

                "item" -> {
                    val now = System.currentTimeMillis()
                    db.itemDao().upsert(
                        ItemEntity(
                            itemId = item.str("item_id") ?: return,
                            logId = item.str("log_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            partId = item.str("part_id"),
                            description = item.str("name") ?: item.str("description"),
                            quantity = item.dbl("quantity") ?: 0.0,
                            unitCost = item.dbl("unit_price") ?: item.dbl("unit_cost"),
                            currency = item.str("currency"),
                            notes = item.str("notes"),
                            productionDate = isoToMs(item.str("production_date")),
                            partNumber = item.str("part_number"),
                            gtin = item.str("gtin"),
                            manufacturerId = item.str("manufacturer_id"),
                            serialNumber = item.str("serial_number"),
                            revision = item.str("revision"),
                            modelNumber = item.str("model_number"),
                            lotNumber = item.str("lot_number"),
                            country = item.str("country"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "gl_account" -> {
                    val now = System.currentTimeMillis()
                    db.glAccountDao().upsert(
                        GlAccountEntity(
                            glAccountId = item.str("gl_account_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            glCode = item.str("gl_code") ?: return,
                            name = item.str("name") ?: "",
                            accountType = item.str("account_type"),
                            description = item.str("description"),
                            isActive = item.bool("is_active") ?: true,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "job" -> {
                    val now = System.currentTimeMillis()
                    db.jobDao().upsert(
                        JobEntity(
                            jobId = item.str("job_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            assetId = item.str("asset_id"),
                            title = item.str("title") ?: "",
                            description = item.str("description"),
                            status = item.str("status") ?: "open",
                            jobType = item.str("job_type"),
                            priority = item.str("priority"),
                            assignedTo = item.str("assigned_to"),
                            dueDate = isoToMs(item.str("due_date")),
                            startedAt = isoToMs(item.str("started_at")),
                            completedAt = isoToMs(item.str("completed_at")),
                            notes = item.str("notes"),
                            attributes = item.str("attributes"),
                            createdBy = item.str("created_by"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "service" -> {
                    val now = System.currentTimeMillis()
                    db.serviceDao().upsert(
                        ServiceEntity(
                            serviceId = item.str("service_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            assetId = item.str("asset_id") ?: return,
                            logId = item.str("log_id"),
                            serviceType = item.str("service_type"),
                            providerName = item.str("provider_name"),
                            providerId = item.str("provider_id"),
                            scheduledAt = isoToMs(item.str("scheduled_at")),
                            completedAt = isoToMs(item.str("completed_at")),
                            cost = item.dbl("cost"),
                            currency = item.str("currency"),
                            notes = item.str("notes"),
                            status = item.str("status"),
                            attributes = item.str("attributes"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "bin" -> {
                    val now = System.currentTimeMillis()
                    db.binDao().upsert(
                        BinEntity(
                            binId = item.str("bin_id") ?: return,
                            locationId = item.str("location_id") ?: return,
                            name = item.str("name") ?: "",
                            code = item.str("code"),
                            barcode = item.str("barcode"),
                            aisle = item.str("aisle"),
                            shelf = item.str("shelf"),
                            slot = item.str("slot"),
                            active = item.bool("active") ?: true,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "vendor_part" -> {
                    val now = System.currentTimeMillis()
                    db.vendorPartDao().upsert(
                        VendorPartEntity(
                            vendorPartId = item.str("vendor_part_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            vendorId = item.str("vendor_id") ?: return,
                            partId = item.str("part_id") ?: return,
                            vendorSku = item.str("vendor_sku"),
                            unitCost = item.dbl("unit_cost"),
                            moq = item.dbl("moq"),
                            packSize = item.dbl("pack_size"),
                            leadDays = item.lng("lead_days")?.toInt(),
                            isPreferred = item.bool("is_preferred") ?: false,
                            currency = item.str("currency"),
                            notes = item.str("notes"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "tech_labor_rate" -> {
                    val now = System.currentTimeMillis()
                    db.techLaborRateDao().upsert(
                        TechLaborRateEntity(
                            rateId = item.str("rate_id") ?: return,
                            techId = item.str("tech_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            roleKey = item.str("role_key"),
                            hourlyRate = item.dbl("hourly_rate") ?: 0.0,
                            currency = item.str("currency") ?: "USD",
                            effectiveDate = isoToMs(item.str("effective_date")),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            serverVersion = item.lng("server_version") ?: 0L,
                        )
                    )
                }

                "reorder_suggestion" -> {
                    val now = System.currentTimeMillis()
                    db.reorderSuggestionDao().upsert(
                        ReorderSuggestionEntity(
                            suggestionId = item.str("suggestion_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            partId = item.str("part_id") ?: return,
                            quantityOnHand = item.dbl("quantity_on_hand") ?: 0.0,
                            reorderQty = item.dbl("reorder_qty"),
                            suggestedQty = item.dbl("suggested_qty") ?: 0.0,
                            preferredVendorId = item.str("preferred_vendor_id"),
                            status = item.str("status") ?: "open",
                            reason = item.str("reason"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                        )
                    )
                }

                "asset_location_history" -> {
                    val now = System.currentTimeMillis()
                    db.assetLocationHistoryDao().upsert(
                        AssetLocationHistoryEntity(
                            historyId = item.str("history_id") ?: return,
                            assetId = item.str("asset_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            fromLocationId = item.str("from_location_id"),
                            toLocationId = item.str("to_location_id"),
                            movedBy = item.str("moved_by"),
                            movedAt = isoToMs(item.str("moved_at")) ?: now,
                            reason = item.str("reason"),
                            notes = item.str("notes"),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "role_label_cache" -> {
                    val now = System.currentTimeMillis()
                    db.roleLabelCacheDao().upsert(
                        RoleLabelCacheEntity(
                            roleKey = item.str("role_key") ?: return,
                            label = item.str("label") ?: "",
                            description = item.str("description"),
                            color = item.str("color"),
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                        )
                    )
                }

                "event" -> {
                    val now = System.currentTimeMillis()
                    db.eventDao().upsert(
                        EventEntity(
                            eventId = item.str("event_id") ?: return,
                            accountId = item.str("account_id") ?: accountId,
                            entityId = item.str("entity_id"),
                            entityType = item.str("entity_type"),
                            title = item.str("title") ?: "",
                            description = item.str("description"),
                            eventType = item.str("event_type"),
                            startsAt = isoToMs(item.str("starts_at")),
                            endsAt = isoToMs(item.str("ends_at")),
                            allDay = item.bool("all_day") ?: false,
                            locationId = item.str("location_id"),
                            createdBy = item.str("created_by"),
                            attendees = item.str("attendees"),
                            ekEventIdentifier = item.str("ek_event_identifier"),
                            attributes = item.str("attributes"),
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
                            deletedAt = isoToMs(item.str("deleted_at")),
                            serverVersion = item.lng("server_version") ?: 0L,
                            seq = item.lng("seq"),
                        )
                    )
                }

                "config" -> {
                    val now = System.currentTimeMillis()
                    val configId = item.str("config_id") ?: run {
                        Timber.w("[SyncEngine] config: missing config_id, skipping")
                        return
                    }
                    // Server sends: type (=config_type), subtype, asset_type, config (JSON object).
                    // Android schema: scope = type, key = subtype[_asset_type].
                    val scope = item.str("type") ?: item.str("scope") ?: run {
                        Timber.w("[SyncEngine] config $configId: missing type/scope, skipping")
                        return
                    }
                    val subtype = item.str("subtype")
                    val assetType = item.str("asset_type")
                    val key = listOfNotNull(
                        subtype?.ifBlank { null },
                        assetType?.ifBlank { null },
                    ).joinToString("_").ifBlank {
                        Timber.w("[SyncEngine] config $configId: empty derived key, skipping")
                        return
                    }
                    val configElement = item["config"] ?: run {
                        Timber.w("[SyncEngine] config $configId: missing 'config' field, skipping")
                        return
                    }
                    db.configDao().upsert(
                        ConfigEntity(
                            configId = configId,
                            accountId = item.str("account_id"),
                            scope = scope,
                            key = key,
                            value = configElement.toString(),
                            version = item.lng("version") ?: 0L,
                            createdAt = isoToMs(item.str("created_at")) ?: now,
                            updatedAt = isoToMs(item.str("updated_at")) ?: now,
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
    // Retry / resiliency helpers
    // ---------------------------------------------------------------------------

    /**
     * Retry [block] up to [maxAttempts] times on transient network errors.
     * Delays: 50–150 ms / 500–750 ms / 2000–2500 ms — matching iOS SyncEngine retryDelayMs.
     */
    private suspend fun <T> withRetry(maxAttempts: Int = 3, block: suspend () -> T): T {
        var lastError: Exception? = null
        for (attempt in 0 until maxAttempts) {
            try { return block() } catch (e: Exception) {
                lastError = e
                if (attempt >= maxAttempts - 1 || !isRetryable(e)) throw e
                val delay = retryDelayMs(attempt)
                Timber.w("[SyncEngine] Transient error (attempt ${attempt + 1}/$maxAttempts), retry in ${delay}ms — ${e.message}")
                kotlinx.coroutines.delay(delay)
            }
        }
        throw lastError!!
    }

    private fun retryDelayMs(attempt: Int): Long = when (attempt) {
        0    -> 50L   + Random.nextLong(0, 100)  // 50–150 ms
        1    -> 500L  + Random.nextLong(0, 250)  // 500–750 ms
        else -> 2000L + Random.nextLong(0, 500)  // 2000–2500 ms
    }

    private fun isRetryable(e: Exception): Boolean = when {
        e is SocketTimeoutException                    -> true
        e is ConnectException                          -> true
        e is IOException                               -> true
        e is NetworkException && e.code >= 500         -> true
        else                                           -> false
    }

    // ---------------------------------------------------------------------------
    // Server version helpers
    // ---------------------------------------------------------------------------

    /** Read the locally-stored server_version for a single row. Returns 0 if absent. */
    private suspend fun queryLocalServerVersion(
        db: AvagoDatabase, table: String, pkCol: String, id: String,
    ): Long = withContext(Dispatchers.IO) {
        try {
            db.openHelper.readableDatabase.query(
                "SELECT server_version FROM $table WHERE $pkCol = ? LIMIT 1",
                arrayOf(id)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex("server_version")
                    if (col >= 0) cursor.getLong(col) else 0L
                } else 0L
            }
        } catch (_: Exception) { 0L }
    }

    /**
     * Stamp the locally-stored server_version after a successful push.
     * Mirrors iOS SyncQueueDAO.markSuccess tableMap update.
     */
    private suspend fun updateEntityServerVersion(
        db: AvagoDatabase, entityType: String, entityId: String, serverVersion: Long,
    ) {
        val (table, pkCol, _) = entityVersionInfo[entityType] ?: return
        withContext(Dispatchers.IO) {
            try {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE $table SET server_version = ? WHERE $pkCol = ?",
                    arrayOf<Any?>(serverVersion, entityId)
                )
            } catch (_: Exception) { }
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
        } catch (_: Exception) {
            null
        }
    }
}
