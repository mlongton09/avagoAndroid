package com.avago.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.avago.MainActivity
import com.avago.core.auth.IdentityManager
import com.avago.core.sync.DeltaPushApplier
import com.avago.core.sync.SyncWorker
import com.avago.feature.chat.ActiveThreadTracker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AvagoFcmService : FirebaseMessagingService() {

    @Inject lateinit var identity: IdentityManager
    @Inject lateinit var deltaApplier: DeltaPushApplier

    // Service-scoped coroutine scope; cancelled in onDestroy.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Token registration
    // -------------------------------------------------------------------------

    override fun onNewToken(token: String) {
        Timber.d("FCM: new token received")
        serviceScope.launch {
            identity.storePushToken(token)
        }
    }

    // -------------------------------------------------------------------------
    // Message dispatch
    // -------------------------------------------------------------------------

    override fun onMessageReceived(msg: RemoteMessage) {
        val type = msg.data["type"]
        Timber.d("FCM: received type=$type")
        when (type) {
            "sync_nudge" -> handleSyncNudge(msg)
            "chat_message" -> handleChatPush(msg)
            else -> enqueueSyncWork() // unknown payload: nudge sync as a safe default
        }
    }

    // -------------------------------------------------------------------------
    // Sync nudge
    // -------------------------------------------------------------------------

    private fun handleSyncNudge(msg: RemoteMessage) {
        val entityType = msg.data["entity_type"]
        val serverSeqRaw = msg.data["server_seq"]
        val accountId = identity.getActiveAccountId()

        if (entityType != null && serverSeqRaw != null && accountId != null) {
            val seq = serverSeqRaw.toLongOrNull()
            if (seq != null) {
                serviceScope.launch {
                    val outcome = deltaApplier.handle(entityType, seq, accountId)
                    Timber.d("FCM: delta outcome=$outcome entityType=$entityType seq=$seq accountId=$accountId")
                }
                return
            }
        }

        // Fall back to full sync if any field is missing or unparseable
        Timber.d("FCM: sync_nudge missing entity_type/server_seq — falling back to full sync")
        enqueueSyncWork()
    }

    internal fun enqueueSyncWork() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork("sync-nudge", ExistingWorkPolicy.KEEP, request)
        Timber.d("FCM: enqueued sync-nudge work")
    }

    // -------------------------------------------------------------------------
    // Chat push notification (rich, with quick-reply)
    // -------------------------------------------------------------------------

    private fun handleChatPush(msg: RemoteMessage) {
        val data = msg.data
        val threadId = data["thread_id"] ?: run {
            Timber.w("FCM: chat_message missing thread_id — falling back to sync nudge")
            enqueueSyncWork()
            return
        }
        val senderName = data["sender_name"] ?: "Someone"
        val body = data["body"] ?: "Sent a message"
        val messageId = data["message_id"]
        val accountId = data["account_id"] ?: run {
            Timber.w("FCM: chat_message missing account_id — falling back to sync nudge")
            enqueueSyncWork()
            return
        }
        // sender_avatar_url reserved for future use when loading bitmaps asynchronously
        @Suppress("UNUSED_VARIABLE")
        val senderAvatarUrl = data["sender_avatar_url"]

        if (ActiveThreadTracker.activeThreadId == threadId) {
            enqueueSyncWork()
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java)

        // Per-thread notification channel — one channel per thread so users can mute
        // individual conversations independently.
        val channelId = "chat_$threadId"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Chat: $senderName",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Messages in this thread"
                enableLights(true)
                lightColor = 0xFF1C8EF0.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Ensure the summary channel exists as well.
        ensureSummaryChannel(notificationManager)

        // Deep-link intent — opens ThreadScreen for this thread.
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("thread_id", threadId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            threadId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Quick-reply RemoteInput.
        val remoteInput = RemoteInput.Builder(QUICK_REPLY_KEY)
            .setLabel("Reply…")
            .build()

        // Reply pending intent — broadcasts to QuickReplyReceiver.
        val replyIntent = Intent(this, QuickReplyReceiver::class.java).apply {
            putExtra("thread_id", threadId)
            putExtra("account_id", accountId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            threadId.hashCode() + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val replyAction = NotificationCompat.Action.Builder(
            // TODO: replace with app icons (ic_send, ic_notification)
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent,
        ).addRemoteInput(remoteInput).build()

        val markReadIntent = Intent(this, MarkReadReceiver::class.java).apply {
            putExtra("thread_id", threadId)
            putExtra("account_id", accountId)
            putExtra("notification_id", threadId.hashCode())
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            this,
            threadId.hashCode() + 2,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark Read",
            markReadPendingIntent,
        ).build()

        val reactAction = if (messageId != null) {
            val reactIntent = Intent(this, ReactReceiver::class.java).apply {
                putExtra("thread_id", threadId)
                putExtra("account_id", accountId)
                putExtra("message_id", messageId)
                putExtra("notification_id", threadId.hashCode())
            }
            val reactPendingIntent = PendingIntent.getBroadcast(
                this,
                threadId.hashCode() + 3,
                reactIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_add,
                "👍",
                reactPendingIntent,
            ).build()
        } else {
            null
        }

        val muteIntent = Intent(this, MuteReceiver::class.java).apply {
            putExtra("thread_id", threadId)
            putExtra("account_id", accountId)
            putExtra("notification_id", threadId.hashCode())
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            this,
            threadId.hashCode() + 4,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val muteAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_lock_silent_mode,
            "Mute 8h",
            mutePendingIntent,
        ).build()

        // Person for MessagingStyle.
        val person = Person.Builder()
            .setName(senderName)
            .setImportant(true)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .addMessage(body, System.currentTimeMillis(), person)

        val notification = NotificationCompat.Builder(this, channelId)
            // TODO: replace with app icons (ic_send, ic_notification)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(messagingStyle)
            .setContentIntent(openPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .apply { if (reactAction != null) addAction(reactAction) }
            .addAction(muteAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY_CHAT)
            .build()

        notificationManager.notify(threadId.hashCode(), notification)
        Timber.d("FCM: showed rich notification for thread $threadId")

        // Update the inbox-style summary notification.
        showSummaryNotification(notificationManager)

        // Pull the new message into the local DB.
        enqueueSyncWork()
    }

    // -------------------------------------------------------------------------
    // Summary notification (groups multiple thread notifications)
    // -------------------------------------------------------------------------

    private fun ensureSummaryChannel(notificationManager: NotificationManager) {
        if (notificationManager.getNotificationChannel(SUMMARY_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                SUMMARY_CHANNEL_ID,
                "Chat messages",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Grouped chat notification summary"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showSummaryNotification(notificationManager: NotificationManager) {
        val summaryNotification = NotificationCompat.Builder(this, SUMMARY_CHANNEL_ID)
            // TODO: replace with app icons (ic_send, ic_notification)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New messages")
            .setContentText("From your team")
            .setGroupSummary(true)
            .setGroup(GROUP_KEY_CHAT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        const val QUICK_REPLY_KEY = "quick_reply_text"
        const val SUMMARY_NOTIFICATION_ID = 9999
        private const val SUMMARY_CHANNEL_ID = "chat_summary"
        private const val GROUP_KEY_CHAT = "avago_chat"
    }
}
