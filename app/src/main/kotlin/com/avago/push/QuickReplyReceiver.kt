package com.avago.push

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class QuickReplyReceiver : BroadcastReceiver() {

    @Inject lateinit var identity: IdentityManager
    @Inject lateinit var serviceClient: AvagoServiceClient

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getStringExtra("thread_id") ?: return
        val accountId = intent.getStringExtra("account_id") ?: return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AvagoFcmService.QUICK_REPLY_KEY)
            ?.toString() ?: return

        // Use a detached scope — the receiver lifecycle is very short and we must
        // keep work alive until the network call (and notification update) complete.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Send the reply via the injected service client.
                serviceClient.sendMessage(threadId = threadId, body = replyText)

                // Update the notification to show the sent reply immediately,
                // so the user gets visual confirmation before the next sync pull.
                val notificationManager =
                    context.getSystemService(NotificationManager::class.java)

                val me = Person.Builder().setName("You").build()
                val messagingStyle = NotificationCompat.MessagingStyle(me)
                    .addMessage(replyText, System.currentTimeMillis(), me)

                val updatedNotification = NotificationCompat.Builder(context, "chat_$threadId")
                    // TODO: replace with app icons (ic_send, ic_notification)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setStyle(messagingStyle)
                    .setAutoCancel(true)
                    .setGroup("avago_chat")
                    .build()

                notificationManager.notify(threadId.hashCode(), updatedNotification)
                Timber.d("QuickReply: sent reply to thread $threadId")

            } catch (e: Exception) {
                Timber.e(e, "QuickReply: failed to send reply for thread $threadId")
            }
        }
    }
}
