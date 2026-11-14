package com.avago.core.sync

import android.content.Context
import androidx.room.withTransaction
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.entity.SyncMetadataEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.SyncOperationResult
import com.avago.core.ui.AvagoToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first adapter for the inspection-v2 open-defect register (AvagoSvc `inspection_defects`,
 * `/sync` entity `inspection_defect`, contract AvagoSvc docs/sync-defects.md).
 *
 * - **pull**: [SyncEngine] pulls `inspection_defect` through `/sync/pull` and applies each row /
 *   tombstone with [applyPulled]; [markRegisterSynced] records the register as available. [pull]
 *   (account-wide `GET /defects` snapshot) is the fallback when that feed fails, and the panel-open
 *   refresh ([pullIfStale]) when the last refresh is old.
 * - **writes**: [enqueueStatusChange] applies the change to the cached row and queues it in
 *   `sync_queue` (`inspection_defect`, client-generated UUID queue id = idempotency / mutation id).
 *   Works for derived defects too.
 * - **push**: register rows go through `/sync/push`; [applyPushResult] applies the result (server
 *   wins on conflicts: server row applied or local row removed, non-blocking toast). Derived
 *   defects (`asset:key` ids) cannot be addressed by `/sync`, so [drain] resolves them to a
 *   register row and sends them via `PATCH /defects/{id}`. Transient failures stay queued for the
 *   engine's bounded retry (`resetErrorsToPending(maxAttempts = 10)`).
 */
@Singleton
class DefectRegisterSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbFactory: DatabaseFactory,
    private val client: AvagoServiceClient,
    private val toast: AvagoToast,
) {
    private val pullMutex = Mutex()

    /** Test seam: Room's `withTransaction` needs a real database. */
    internal var inTransaction: suspend (AvagoDatabase, suspend () -> Unit) -> Unit =
        { db, block -> db.withTransaction { block() } }

    /** Test seam for the wall clock. */
    internal var now: () -> Long = System::currentTimeMillis

    /**
     * Refresh the cached register. Returns true when a snapshot was stored. 404/405 records the
     * register as unsupported (panel derives from local history); other failures keep the cache.
     */
    suspend fun pull(accountId: String): Boolean = pullMutex.withLock {
        val db = dbFactory.get(accountId)
        migrateLegacyMeta(db)
        when (val res = client.getDefects(accountId, assetId = null)) {
            is NetworkResult.Success -> {
                val ts = now()
                val rows = res.data.mapNotNull { (it as? JsonObject)?.let { r -> DefectOutbox.toEntity(r, ts) } }
                inTransaction(db) {
                    val keep = db.syncQueueDao().unsyncedEntityIds(DefectOutbox.ENTITY_TYPE).toSet()
                    db.inspectionDefectDao().replaceSnapshot(rows, keep)
                    db.syncMetadataDao().upsert(SyncMetadataEntity(DefectOutbox.META_KEY, DefectOutbox.REGISTER_SUPPORTED, ts))
                }
                Timber.d("[DefectRegisterSync] pulled ${rows.size} register row(s)")
                true
            }
            is NetworkResult.Error -> {
                if (DefectOutbox.isNotDeployed(res.code)) {
                    db.syncMetadataDao().upsert(SyncMetadataEntity(DefectOutbox.META_KEY, DefectOutbox.REGISTER_UNSUPPORTED, now()))
                }
                Timber.w("[DefectRegisterSync] pull failed (HTTP ${res.code})")
                false
            }
            NetworkResult.Unauthorized -> false
        }
    }

    /** [pull] unless the last attempt is younger than [maxAgeMs]. Never throws. */
    suspend fun pullIfStale(accountId: String, maxAgeMs: Long = STALE_MS) {
        try {
            val meta = dbFactory.get(accountId).syncMetadataDao().getById(DefectOutbox.META_KEY)
            if (meta == null || now() - meta.lastSyncAt > maxAgeMs) pull(accountId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[DefectRegisterSync] pullIfStale failed")
        }
    }

    /**
     * Apply a lifecycle change locally and queue it for push. [defectId] is the register id, or the
     * derived id `${assetId}:${key}` when [op] is derived. A change back to the queued baseline
     * cancels the queued one. Returns true when something is queued for push.
     */
    suspend fun enqueueStatusChange(accountId: String, defectId: String, op: DefectOutbox.Op): Boolean {
        val db = dbFactory.get(accountId)
        val queue = db.syncQueueDao()
        var queued = false
        inTransaction(db) {
            val existing = queue.getPendingByEntity(DefectOutbox.ENTITY_TYPE, defectId)
            val merge = DefectOutbox.merge(existing?.payload?.let { DefectOutbox.Op.fromJson(it) }, op)
            val ts = now()
            when (merge) {
                DefectOutbox.Merge.Cancel -> existing?.let { queue.softDelete(it.queueId) }
                is DefectOutbox.Merge.Enqueue -> {
                    // Replace (not dedup-merge) so a changed body never reuses an idempotency key.
                    existing?.let { queue.softDelete(it.queueId) }
                    queue.upsert(
                        SyncQueueEntity(
                            queueId = UUID.randomUUID().toString(),
                            entityType = DefectOutbox.ENTITY_TYPE,
                            entityId = defectId,
                            operation = "update",
                            serverVersion = null,
                            payload = merge.op.toJson(),
                            syncStatus = "pending",
                            attempts = 0L,
                            lastError = null,
                            createdAt = ts,
                            updatedAt = ts,
                        ),
                    )
                    queued = true
                }
            }
            if (!op.derived) {
                val dao = db.inspectionDefectDao()
                dao.getById(defectId)?.let { row ->
                    val bucket = DefectOutbox.bucketOf(row.rowJson)
                    dao.upsert(row.copy(status = DefectOutbox.localStatus(op.status, bucket), cachedAt = ts))
                }
            }
        }
        return queued
    }

    /**
     * Push queued `inspection_defect` changes. Called by [SyncEngine] after its `/sync/push` batch
     * (so freshly pushed inspections have reached the register). Returns the number applied.
     */
    suspend fun drain(accountId: String, items: List<SyncQueueEntity>): Int {
        if (items.isEmpty()) return 0
        val db = dbFactory.get(accountId)
        val queue = db.syncQueueDao()
        queue.markInFlight(items.map { it.queueId })
        var applied = 0

        val parsed = items.map { it to DefectOutbox.Op.fromJson(it.payload) }
        parsed.filter { it.second == null }.forEach { (item, _) ->
            Timber.w("[DefectRegisterSync] dropping malformed queue row ${item.queueId}")
            queue.markSuccess(item.queueId)
        }

        for ((assetId, group) in parsed.filter { it.second != null }.groupBy { it.second!!.assetId }) {
            val rows: List<JsonObject> = when (val res = client.getDefects(accountId, assetId, status = "all")) {
                is NetworkResult.Success -> res.data.mapNotNull { it as? JsonObject }
                is NetworkResult.Error -> {
                    if (DefectOutbox.isNotDeployed(res.code)) {
                        // No register to apply to: these changes can never sync.
                        db.syncMetadataDao().upsert(SyncMetadataEntity(DefectOutbox.META_KEY, DefectOutbox.REGISTER_UNSUPPORTED, now()))
                        group.forEach { (item, op) ->
                            queue.markSuccess(item.queueId)
                            restore(db, item, op!!)
                            notify(R_REJECTED, op.label)
                        }
                    } else {
                        group.forEach { (item, _) -> queue.markError(item.queueId, "register fetch failed (HTTP ${res.code})") }
                    }
                    continue
                }
                NetworkResult.Unauthorized -> {
                    group.forEach { (item, _) -> queue.markError(item.queueId, "unauthorized") }
                    continue
                }
            }
            for ((item, maybeOp) in group) {
                val op = maybeOp!!
                when (val d = DefectOutbox.decide(item.entityId, op, rows)) {
                    is DefectOutbox.Decision.Patch -> {
                        when (val r = client.patchDefect(accountId, d.defectId, op.status, op.resolutionNote, idempotencyKey = item.queueId)) {
                            is NetworkResult.Success -> {
                                queue.markSuccess(item.queueId)
                                cache(db, r.data.takeIf { it.containsKey("defect_id") } ?: d.current)
                                applied++
                            }
                            is NetworkResult.Error -> when {
                                r.code == 404 -> {
                                    queue.markSuccess(item.queueId)
                                    db.inspectionDefectDao().deleteById(d.defectId)
                                    notify(R_REJECTED, op.label)
                                }
                                DefectOutbox.isPermanent(r.code) -> {
                                    queue.markSuccess(item.queueId)
                                    cache(db, d.current)
                                    // 409: the server refused on its own state — same server-wins notice.
                                    notify(if (r.code == 409) R_CONFLICT else R_REJECTED, op.label)
                                }
                                else -> queue.markError(item.queueId, "HTTP ${r.code}")
                            }
                            NetworkResult.Unauthorized -> queue.markError(item.queueId, "unauthorized")
                        }
                    }
                    is DefectOutbox.Decision.AlreadyApplied -> {
                        queue.markSuccess(item.queueId)
                        cache(db, d.current)
                        applied++
                    }
                    is DefectOutbox.Decision.ServerWins -> {
                        queue.markSuccess(item.queueId)
                        cache(db, d.current)
                        notify(R_CONFLICT, op.label)
                    }
                    DefectOutbox.Decision.Gone -> {
                        queue.markSuccess(item.queueId)
                        db.inspectionDefectDao().deleteById(item.entityId)
                        notify(R_REJECTED, op.label)
                    }
                    DefectOutbox.Decision.NotYetRegistered ->
                        queue.markError(item.queueId, "defect not in register yet")
                }
            }
        }
        return applied
    }

    /**
     * One-time cleanup: older builds kept the register flag (1 / -1) in the `sync_metadata` row
     * now used as the `inspection_defect` pull watermark. Move the flag to [DefectOutbox.META_KEY]
     * (marked stale so the next refresh confirms it) and drop the old row so the `/sync` feed
     * starts from seq 0.
     */
    suspend fun migrateLegacyMeta(db: AvagoDatabase) {
        val meta = db.syncMetadataDao()
        if (meta.getById(DefectOutbox.META_KEY) != null) return
        val legacyFlag = meta.getById(DefectOutbox.LEGACY_META_KEY)?.lastServerSeq
            ?.takeIf { it == DefectOutbox.REGISTER_SUPPORTED || it == DefectOutbox.REGISTER_UNSUPPORTED }
        meta.softDelete(DefectOutbox.LEGACY_META_KEY)
        meta.upsert(SyncMetadataEntity(DefectOutbox.META_KEY, legacyFlag ?: 0L, 0L))
    }

    /** The `/sync/pull` feed for `inspection_defect` completed: the register exists and is fresh. */
    suspend fun markRegisterSynced(db: AvagoDatabase) {
        db.syncMetadataDao().upsert(SyncMetadataEntity(DefectOutbox.META_KEY, DefectOutbox.REGISTER_SUPPORTED, now()))
    }

    /**
     * Apply one `/sync/pull` item: a tombstone (`deleted: true`) removes the local row; a full row
     * is cached unless a local change for it is still queued (the push result settles it).
     */
    suspend fun applyPulled(db: AvagoDatabase, item: JsonObject) {
        val id = (item["defect_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return
        if ((item["deleted"] as? JsonPrimitive)?.booleanOrNull == true) {
            db.inspectionDefectDao().deleteById(id)
            return
        }
        cache(db, item)
    }

    /**
     * Settle a queued change from its `/sync/push` [result]. Success clears it; a conflict means
     * the server kept its own state (`SERVER_WINS`): its row replaces the optimistic one, or the
     * local row is removed when the defect no longer exists, with a non-blocking notice (not a
     * SyncConflictCoordinator entry). Other failures stay queued for the engine's bounded retry.
     * Returns true when the change was applied on the server.
     */
    suspend fun applyPushResult(accountId: String, item: SyncQueueEntity, result: SyncOperationResult): Boolean {
        val db = dbFactory.get(accountId)
        val queue = db.syncQueueDao()
        val label = DefectOutbox.Op.fromJson(item.payload)?.label ?: item.entityId
        return when {
            result.success -> {
                queue.markSuccess(item.queueId)
                (result.server_row ?: result.data)?.takeIf { it.containsKey("defect_id") }?.let { cache(db, it) }
                true
            }
            result.conflict -> {
                queue.markSuccess(item.queueId)
                val row = result.server_row
                when {
                    result.server_status == "deleted" -> {
                        db.inspectionDefectDao().deleteById(item.entityId)
                        notify(R_REJECTED, label)
                    }
                    row != null -> {
                        cache(db, row)
                        notify(R_CONFLICT, label)
                    }
                    else -> {
                        // No server state reported: undo the optimistic status; the next pull refreshes it.
                        DefectOutbox.Op.fromJson(item.payload)?.let { restore(db, item, it) }
                        notify(R_CONFLICT, label)
                    }
                }
                false
            }
            else -> {
                queue.markError(item.queueId, result.error ?: "Unknown error")
                Timber.w("[DefectRegisterSync] push error ${item.entityId}: ${result.error}")
                false
            }
        }
    }

    /** Store a server row unless a newer local change for it is still queued. */
    private suspend fun cache(db: AvagoDatabase, row: JsonObject) {
        val entity = DefectOutbox.toEntity(row, now()) ?: return
        if (db.syncQueueDao().hasPendingPush(DefectOutbox.ENTITY_TYPE, entity.defectId) != null) return
        db.inspectionDefectDao().upsert(entity)
    }

    /** Undo an optimistic status on the cached row (register unavailable). */
    private suspend fun restore(db: AvagoDatabase, item: SyncQueueEntity, op: DefectOutbox.Op) {
        if (op.derived) return
        val dao = db.inspectionDefectDao()
        dao.getById(item.entityId)?.let { dao.upsert(it.copy(status = DefectOutbox.localStatus(op.baseStatus, DefectOutbox.bucketOf(it.rowJson)))) }
    }

    private fun notify(which: Int, label: String) {
        val res = if (which == R_CONFLICT) com.avago.core.ui.R.string.sync_defect_conflict else com.avago.core.ui.R.string.sync_defect_rejected
        toast.warning(context.getString(res, label))
    }

    companion object {
        /** Panel-open refresh threshold (the sync cycle refreshes on its own schedule). */
        const val STALE_MS = 5 * 60_000L
        private const val R_CONFLICT = 0
        private const val R_REJECTED = 1
    }
}
