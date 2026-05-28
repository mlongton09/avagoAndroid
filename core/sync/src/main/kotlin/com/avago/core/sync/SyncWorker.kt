package com.avago.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.avago.core.network.NetworkException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker: starting sync")
        return when (val result = syncEngine.sync()) {
            is SyncResult.Success -> {
                Timber.d("SyncWorker: sync completed successfully")
                Result.success()
            }
            is SyncResult.Partial -> {
                Timber.d("SyncWorker: sync partial — pushed=${result.pushedCount}, pulled=${result.pulledCount}")
                Result.success()
            }
            is SyncResult.Failed -> {
                val error = result.error
                if (error is NetworkException && error.code == 429) {
                    // Backoff gate is already set in SyncEngine; returning success
                    // stops WorkManager from scheduling another retry that would hit
                    // the server again and extend the rate-limit window further.
                    Timber.w("SyncWorker: rate-limited — backoff gate set, not retrying")
                    Result.success()
                } else {
                    Timber.e(error, "SyncWorker: sync failed")
                    Result.retry()
                }
            }
        }
    }
}
