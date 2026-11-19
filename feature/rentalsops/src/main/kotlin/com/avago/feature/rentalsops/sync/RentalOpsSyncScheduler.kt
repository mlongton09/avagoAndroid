package com.avago.feature.rentalsops.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.avago.core.sync.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the shared [SyncWorker] once the device has a network. Unique + KEEP so bursts of
 * driver taps collapse into one run: it sends the queued rental commands and pulls the
 * resulting changes.
 */
@Singleton
class RentalOpsSyncScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    fun requestPush() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        runCatching {
            WorkManager.getInstance(ctx).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, request)
        }.onFailure { Timber.w(it, "[RentalOps] could not enqueue sync") }
    }

    companion object {
        const val UNIQUE = "rentalops-push"
    }
}
