package com.avago.push

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.avago.core.sync.SyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AvagoFcmService : FirebaseMessagingService() {

    @Inject lateinit var workManager: WorkManager

    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed")
        // Phase 2: register token with AvagoSvc via PUT /devices/:id
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val type = msg.data["type"] ?: return
        Timber.d("FCM received type=$type")
        when (type) {
            "sync_nudge" -> enqueueSyncWorker()
            "chat_message" -> enqueueSyncWorker() // chat also triggers a sync
        }
    }

    private fun enqueueSyncWorker() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork("sync-nudge", ExistingWorkPolicy.KEEP, request)
    }
}
