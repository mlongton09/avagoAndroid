package com.avago.core.sync

import android.content.Context
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.SyncMetadataEntity
import com.avago.core.network.AvagoServiceClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Applies incremental delta payloads from FCM silent pushes, avoiding a full sync when possible.
 *
 * Decision tree (matches iOS DeltaPushApplier):
 * 1. Cold-start gate: if first full sync hasn't completed, fall back to full SyncEngine.sync()
 * 2. Stale gate: if incomingSeq <= current watermark, ignore (already have this data)
 * 3. Gap gate: if gap > MAX_GAP_FOR_DELTA (500), fall back to full SyncEngine.sync()
 * 4. Sequential/gap-fill: trigger SyncEngine.sync() — iOS applies the delta inline from push
 *    payload data; Android triggers a full sync since FCM data is not forwarded here.
 *
 * Armed gate persistence: stored in sync_metadata (key = "__delta_push_armed__") so it
 * survives process restarts. Mirrors iOS SyncWatermarkStore.armDeltaApply / isDeltaApplyArmed.
 */
@Singleton
class DeltaPushApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identity: IdentityManager,
    private val dbFactory: DatabaseFactory,
    private val serviceClient: AvagoServiceClient,
    // Provider<> breaks circular dependency: SyncEngine → DeltaPushApplier → SyncEngine.
    private val syncEngine: Provider<SyncEngine>,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // In-memory fast path: avoids a DB round-trip on every incoming push after the gate arms.
    // Source of truth is sync_metadata; this cache warms on first arm or first isArmed() read.
    // Mirrors iOS SyncWatermarkStore in-SQLite gate with an in-memory cache overlay.
    private val armedAccountCache = ConcurrentHashMap<String, Boolean>()

    /** Tracks per-outcome counters keyed as "push_delta_applied_total::{entityType}::{outcome}". */
    private val metricsCounters = ConcurrentHashMap<String, AtomicLong>()

    companion object {
        private const val MAX_GAP_FOR_DELTA = 500L
        private const val TAG = "[DeltaPushApplier]"

        // Row key in sync_metadata used to persist the cold-start gate across process restarts.
        // Mirrors iOS SyncWatermarkStore.armedKey = "__delta_push_armed__".
        private const val ARMED_KEY = "__delta_push_armed__"
    }

    /**
     * Mark the first full sync as completed for [accountId].
     * Arms the delta-apply gate so subsequent pushes bypass the cold-start fallback.
     * Persisted to sync_metadata so the gate survives process restarts.
     * Mirrors iOS SyncWatermarkStore.armDeltaApply (called from SyncEngine.sync success).
     */
    suspend fun markFirstSyncComplete(accountId: String) {
        armedAccountCache[accountId] = true
        try {
            val db = dbFactory.get(accountId)
            db.syncMetadataDao().upsert(SyncMetadataEntity(ARMED_KEY, 1L, 0L))
            Timber.d("$TAG markFirstSyncComplete: armed gate persisted for accountId=$accountId")
        } catch (e: Exception) {
            Timber.e(e, "$TAG markFirstSyncComplete: failed to persist armed gate for accountId=$accountId")
        }
    }

    /**
     * Handle an incoming FCM delta push for a given entity type and sequence number.
     *
     * @param entityType the entity type string (e.g. "work_order", "asset")
     * @param incomingSeq the server sequence number carried in the push payload
     * @param accountId the account this push is scoped to
     * @return [DeltaOutcome] describing what action was taken
     */
    suspend fun handle(entityType: String, incomingSeq: Long, accountId: String): DeltaOutcome {
        // Gate 1: cold-start — first full sync not yet done.
        // Read the persisted gate (SQLite-backed, with in-memory cache).
        // Mirrors iOS DeltaPushApplier cold-start guard + SyncWatermarkStore.isDeltaApplyArmed.
        if (!isArmed(accountId)) {
            Timber.d("$TAG IgnoredColdStart: entityType=$entityType incomingSeq=$incomingSeq accountId=$accountId — gate not armed")
            scope.launch { syncEngine.get().sync() }
            incrementCounter(entityType, "fallback_cold_start")
            return DeltaOutcome.IgnoredColdStart
        }

        // Gate 2: stale — we already have this data
        val db = dbFactory.get(accountId)
        val currentWatermark = db.syncMetadataDao().getWatermark(entityType) ?: 0L
        if (incomingSeq <= currentWatermark) {
            Timber.d("$TAG IgnoredStale: entityType=$entityType incomingSeq=$incomingSeq currentWatermark=$currentWatermark accountId=$accountId")
            incrementCounter(entityType, "ignored_stale")
            return DeltaOutcome.IgnoredStale
        }

        // Gate 3: gap too large — fall back to full sync.
        // Mirrors iOS DeltaPushApplier gap > 500 → SyncEngine.shared.sync().
        val gap = incomingSeq - currentWatermark
        if (gap > MAX_GAP_FOR_DELTA) {
            val reason = "gap_too_large (gap=$gap, watermark=$currentWatermark, incoming=$incomingSeq)"
            Timber.w("$TAG FellBackToFullSync: entityType=$entityType accountId=$accountId reason=$reason")
            scope.launch { syncEngine.get().sync() }
            incrementCounter(entityType, "fell_back_to_full_sync")
            return DeltaOutcome.FellBackToFullSync(reason = "gap_too_large")
        }

        // Gate 4: sequential or small gap.
        // iOS applies the delta item inline from the push payload data (no network call).
        // Android triggers a full sync instead, since the FCM handler does not forward
        // the entity payload into this function. The sync mutex + resyncRequested flag
        // collapse concurrent pushes into a single in-flight cycle.
        // Mirrors iOS DeltaPushApplier sequential + gap-fill paths (SyncEngine.shared.sync).
        Timber.d("$TAG Applied: entityType=$entityType incomingSeq=$incomingSeq gap=$gap accountId=$accountId")
        scope.launch { syncEngine.get().sync() }
        incrementCounter(entityType, "applied")
        return DeltaOutcome.Applied
    }

    /**
     * Flush accumulated per-outcome metrics counters to the server.
     *
     * Snapshots and resets all counters atomically, then calls
     * [AvagoServiceClient.postClientMetrics]. Silently swallows errors so that
     * a telemetry failure never interrupts normal operation.
     *
     * @param accountId The active account (used only for logging; metrics are global).
     */
    suspend fun flushMetrics(accountId: String) {
        if (metricsCounters.isEmpty()) return
        val snapshot = buildMap<String, Long> {
            for ((key, counter) in metricsCounters) {
                val value = counter.getAndSet(0L)
                if (value > 0L) put(key, value)
            }
        }
        if (snapshot.isEmpty()) return
        val json = JsonObject(snapshot.mapValues { (_, v) -> JsonPrimitive(v) })
        try {
            serviceClient.postClientMetrics(json)
            Timber.d("$TAG flushMetrics: flushed ${snapshot.size} counters for accountId=$accountId")
        } catch (_: Exception) {
            // Restore counts so they are not lost on transient failure.
            for ((key, value) in snapshot) {
                metricsCounters.getOrPut(key) { AtomicLong(0L) }.addAndGet(value)
            }
            Timber.e(e, "$TAG flushMetrics: failed for accountId=$accountId")
        }
    }

    /** Check the armed gate: in-memory cache first, then sync_metadata. */
    private suspend fun isArmed(accountId: String): Boolean {
        if (armedAccountCache[accountId] == true) return true
        return try {
            val db = dbFactory.get(accountId)
            val seq = db.syncMetadataDao().getWatermark(ARMED_KEY) ?: 0L
            (seq > 0L).also { armed -> if (armed) armedAccountCache[accountId] = true }
        } catch (_: Exception) { false }
    }

    private fun incrementCounter(entityType: String, outcome: String) {
        val key = "push_delta_applied_total::${entityType}::${outcome}"
        metricsCounters.getOrPut(key) { AtomicLong(0L) }.incrementAndGet()
    }
}

/**
 * Outcome of a [DeltaPushApplier.handle] call.
 */
sealed class DeltaOutcome {
    /** The delta was within range; a sync was triggered. */
    object Applied : DeltaOutcome()

    /** The incoming sequence was already behind (or equal to) the current watermark — no action needed. */
    object IgnoredStale : DeltaOutcome()

    /**
     * The app has not yet completed its first full sync, so a full sync was triggered
     * and the delta was ignored.
     */
    object IgnoredColdStart : DeltaOutcome()

    /**
     * The gap between the current watermark and the incoming sequence exceeded the threshold,
     * so a full sync was triggered instead.
     */
    data class FellBackToFullSync(val reason: String) : DeltaOutcome()
}
