package com.avago.core.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Context
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.network.AvagoServiceClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies incremental delta payloads from FCM silent pushes, avoiding a full sync when possible.
 *
 * Decision tree (matches iOS DeltaPushApplier):
 * 1. Cold-start gate: if first full sync hasn't completed, fall back to full SyncWorker
 * 2. Stale gate: if incomingSeq <= current watermark, ignore (already have this data)
 * 3. Gap gate: if gap > MAX_GAP_FOR_DELTA (500), fall back to full SyncWorker
 * 4. Sequential: apply directly (gap == 1 or gap is small and manageable)
 */
@Singleton
class DeltaPushApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identity: IdentityManager,
    private val dbFactory: DatabaseFactory,
    private val serviceClient: AvagoServiceClient,
) {

    private val firstSyncCompletedByAccount = ConcurrentHashMap<String, Boolean>()

    /** Tracks per-outcome counters keyed as "push_delta_applied_total::{entityType}::{outcome}". */
    private val metricsCounters = ConcurrentHashMap<String, AtomicLong>()

    companion object {
        private const val MAX_GAP_FOR_DELTA = 500L
        private const val TAG = "[DeltaPushApplier]"
    }

    /**
     * Mark the first full sync as completed for the given account.
     * Called by SyncEngine after a successful full pull cycle.
     */
    fun markFirstSyncComplete(accountId: String) {
        firstSyncCompletedByAccount[accountId] = true
        Timber.d("$TAG markFirstSyncComplete: accountId=$accountId")
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
        // Gate 1: cold-start — first full sync not yet done
        val firstSyncComplete = firstSyncCompletedByAccount[accountId] ?: false
        if (!firstSyncComplete) {
            Timber.d("$TAG IgnoredColdStart: entityType=$entityType incomingSeq=$incomingSeq accountId=$accountId — first sync not completed yet")
            enqueueSyncWork()
            incrementCounter(entityType, "ignored_cold_start")
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

        // Gate 3: gap too large — fall back to full sync
        val gap = incomingSeq - currentWatermark
        if (gap > MAX_GAP_FOR_DELTA) {
            val reason = "gap_too_large (gap=$gap, watermark=$currentWatermark, incoming=$incomingSeq)"
            Timber.w("$TAG FellBackToFullSync: entityType=$entityType accountId=$accountId reason=$reason")
            enqueueSyncWork()
            incrementCounter(entityType, "fell_back_to_full_sync")
            return DeltaOutcome.FellBackToFullSync(reason = "gap_too_large")
        }

        // Gate 4: sequential — enqueue targeted SyncWorker for this entity type
        Timber.d("$TAG Applied: entityType=$entityType incomingSeq=$incomingSeq gap=$gap accountId=$accountId")
        enqueueSyncWork(tag = "delta_$entityType")
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
        } catch (e: Exception) {
            // Restore counts so they are not lost on transient failure.
            for ((key, value) in snapshot) {
                metricsCounters.getOrPut(key) { AtomicLong(0L) }.addAndGet(value)
            }
            Timber.e(e, "$TAG flushMetrics: failed for accountId=$accountId")
        }
    }

    private fun incrementCounter(entityType: String, outcome: String) {
        val key = "push_delta_applied_total::${entityType}::${outcome}"
        metricsCounters.getOrPut(key) { AtomicLong(0L) }.incrementAndGet()
    }

    private fun enqueueSyncWork(tag: String = "delta_sync") {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(tag)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("avago_delta_sync", ExistingWorkPolicy.KEEP, request)
    }
}

/**
 * Outcome of a [DeltaPushApplier.handle] call.
 */
sealed class DeltaOutcome {
    /** The delta was within range and a targeted SyncWorker was enqueued. */
    object Applied : DeltaOutcome()

    /** The incoming sequence was already behind (or equal to) the current watermark — no action needed. */
    object IgnoredStale : DeltaOutcome()

    /**
     * The app has not yet completed its first full sync, so a full SyncWorker was enqueued
     * and the delta was ignored.
     */
    object IgnoredColdStart : DeltaOutcome()

    /**
     * The gap between the current watermark and the incoming sequence exceeded the threshold,
     * so a full SyncWorker was enqueued instead.
     */
    data class FellBackToFullSync(val reason: String) : DeltaOutcome()
}
