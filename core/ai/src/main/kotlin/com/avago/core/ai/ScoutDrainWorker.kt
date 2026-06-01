package com.avago.core.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScoutDrainWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ScoutRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (repository.drainPending()) Result.success() else Result.retry()
}
