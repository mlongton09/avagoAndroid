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
class MuteReceiver : BroadcastReceiver() {
    @Inject lateinit var serviceClient: AvagoServiceClient

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getStringExtra("thread_id") ?: return
        val notificationId = intent.getIntExtra("notification_id", threadId.hashCode())
        val until = System.currentTimeMillis() + 8 * 3_600_000L
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                serviceClient.setThreadMute(threadId, muted = true, hardMute = true, until = until)
                context.getSystemService(NotificationManager::class.java).cancel(notificationId)
                Timber.d("Mute: muted thread $threadId for 8h")
            } catch (e: Exception) {
                Timber.e(e, "Mute: failed for thread $threadId")
            }
        }
    }
}
