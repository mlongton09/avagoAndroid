package com.avago.push

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avago.core.network.AvagoServiceClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MarkReadReceiver : BroadcastReceiver() {
    @Inject lateinit var serviceClient: AvagoServiceClient

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getStringExtra("thread_id") ?: return
        val notificationId = intent.getIntExtra("notification_id", threadId.hashCode())
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                serviceClient.markThreadRead(threadId, "")
                context.getSystemService(NotificationManager::class.java).cancel(notificationId)
                Timber.d("MarkRead: marked thread $threadId read")
            } catch (e: Exception) {
                Timber.e(e, "MarkRead: failed for thread $threadId")
            }
        }
    }
}
