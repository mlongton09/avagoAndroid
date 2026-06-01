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
class ReactReceiver : BroadcastReceiver() {
    @Inject lateinit var serviceClient: AvagoServiceClient

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getStringExtra("thread_id") ?: return
        val messageId = intent.getStringExtra("message_id") ?: return
        val notificationId = intent.getIntExtra("notification_id", threadId.hashCode())
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                serviceClient.reactToMessage(threadId, messageId, "👍")
                context.getSystemService(NotificationManager::class.java).cancel(notificationId)
                Timber.d("React: 👍 to message $messageId in thread $threadId")
            } catch (e: Exception) {
                Timber.e(e, "React: failed for message $messageId")
            }
        }
    }
}
