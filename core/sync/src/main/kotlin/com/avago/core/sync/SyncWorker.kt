package com.avago.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = syncEngine.sync()) {
        is SyncResult.Success, is SyncResult.Partial -> Result.success()
        is SyncResult.Failed -> if (syncEngine.rateLimitedMsRemaining > 0) {
            // Retrying inside the server's Retry-After window only extends it; the next trigger runs later.
            Timber.w("SyncWorker: server asked to retry later; not retrying")
            Result.success()
        } else {
            Timber.w(result.error, "SyncWorker: sync failed; retrying")
            Result.retry()
        }
    }
}
