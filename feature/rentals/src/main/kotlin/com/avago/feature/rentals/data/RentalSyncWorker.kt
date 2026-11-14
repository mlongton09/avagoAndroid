package com.avago.feature.rentals.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.avago.core.auth.IdentityManager
import com.avago.feature.rentals.widget.RentalFleetWidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background rentals sync (spec §15): drains the rentals outbox, then pulls the feeds
 * incrementally (cursor per feed, tombstones), then refreshes the home-screen widget.
 * Runs every 15 minutes on any network and once right after every local write. The weekly
 * full resync (API §11 rule 6) is owned by [RentalSync.pull].
 */
@HiltWorker
class RentalSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val identity: IdentityManager,
    private val outbox: RentalOutbox,
    private val sync: RentalSync,
    private val widget: RentalFleetWidgetUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accountId = identity.getActiveAccountId() ?: return Result.success()
        if (runAttemptCount >= MAX_RUN_ATTEMPTS) return Result.failure()
        outbox.drainAll(accountId)
        val outcome = sync.pull(accountId)
        try {
            widget.update(applicationContext, accountId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[RentalSyncWorker] widget update failed")
        }
        return when (outcome) {
            RentalSync.Outcome.Ok, RentalSync.Outcome.Disabled -> Result.success()
            is RentalSync.Outcome.Failed -> if (outcome.transient) Result.retry() else Result.success()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "avago_rental_sync"
        const val UNIQUE_NOW = "avago_rental_sync_now"
        const val MAX_RUN_ATTEMPTS = 8
    }
}

/** Enqueues [RentalSyncWorker] runs. */
@Singleton
class RentalSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Periodic 15-minute pull (idempotent: KEEP). */
    fun schedulePeriodic() {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                RentalSyncWorker.UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RentalSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build(),
            )
        }.onFailure { Timber.w(it, "[RentalSyncScheduler] periodic enqueue failed") }
    }

    /** One run as soon as the device is online (after a local write or pull-to-refresh). */
    fun syncNow() {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                RentalSyncWorker.UNIQUE_NOW,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<RentalSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }.onFailure { Timber.w(it, "[RentalSyncScheduler] enqueue failed") }
    }
}
