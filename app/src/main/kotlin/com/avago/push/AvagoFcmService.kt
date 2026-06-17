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
            // Change 1/5: @mention in WO comment
            "mention", "wo_comment_mention" -> handleMentionPush(msg)
            // Change 13: asset status change
            "asset_status" -> handleAssetStatusPush(msg)
            // Change 20: work permit signature completed
            "work_permit_signature" -> handleWorkPermitSignaturePush(msg)
            // Change 23: work permit status changed
            "work_permit" -> handleWorkPermitPush(msg)
            // Change 8/29: WO assignment
            "wo_assignment" -> handleWoAssignmentPush(msg)
            // Change 83: WO team assignment
            "wo_team_assignment" -> handleWoTeamAssignmentPush(msg)
            // Change 34: RCA report
            "rca_report" -> handleRcaReportPush(msg)
            // Change 11/log cost: cost line approval
            "log_cost_line" -> handleLogCostLinePush(msg)
            // Change 124/125: owner assignment
            "owner_assignment" -> handleOwnerAssignmentPush(msg)
            // Change 127/149: job budget alert
            "job_budget_alert" -> handleJobBudgetAlertPush(msg)
            // Change 131/132: GPS transfer completed
            "location_history" -> handleLocationHistoryPush(msg)
            // Change 146: part transfer request status
            "part_transfer_request" -> handlePartTransferPush(msg)
            // Change 100: PO comment
            "po_comment" -> handlePoCommentPush(msg)
            // Change 110/117: sync conflict
            "sync_conflict" -> handleSyncConflictPush(msg)
            // Change 70/137: chat thread / broadcast reminders
            "broadcast_unread_reminder", "broadcast_reminder" -> handleBroadcastReminderPush(msg)
            "thread_summary_ready" -> handleThreadSummaryReadyPush(msg)
            "chat_thread" -> handleChatThreadPush(msg)
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
    // Operational push notification handlers
    // -------------------------------------------------------------------------

    /** Change 1/5: @mention in a WO comment — deep-link to the WO. */
    private fun handleMentionPush(msg: RemoteMessage) {
        val data = msg.data
        val woId = data["wo_id"] ?: run { enqueueSyncWork(); return }
        val commentId = data["comment_id"]
        val mentionedBy = data["mentioned_by"] ?: "Someone"
        val body = data["body"] ?: "mentioned you in a comment"
        val title = "Mentioned by $mentionedBy"
        showOperationalNotification(
            channelId = CHANNEL_MENTIONS,
            channelName = "Mentions",
            notifId = (woId + "mention").hashCode(),
            title = title,
            text = body,
            deepLinkExtra = "wo_id" to woId,
            extraExtras = if (commentId != null) listOf("comment_id" to commentId) else emptyList(),
        )
        enqueueSyncWork()
    }

    /** Change 13: asset status changed — deep-link to the asset. */
    private fun handleAssetStatusPush(msg: RemoteMessage) {
        val data = msg.data
        val assetId = data["asset_id"] ?: run { enqueueSyncWork(); return }
        val assetName = data["asset_name"] ?: "Asset"
        val status = data["status"] ?: "updated"
        showOperationalNotification(
            channelId = CHANNEL_ASSETS,
            channelName = "Asset Updates",
            notifId = (assetId + "status").hashCode(),
            title = "Asset Status: $status",
            text = "$assetName status changed to $status",
            deepLinkExtra = "asset_id" to assetId,
        )
        enqueueSyncWork()
    }

    /** Change 20: work permit signature recorded — deep-link to the permit/WO. */
    private fun handleWorkPermitSignaturePush(msg: RemoteMessage) {
        val data = msg.data
        val permitId = data["permit_id"] ?: run { enqueueSyncWork(); return }
        val woId = data["wo_id"]
        val signerName = data["signer_name"] ?: "Someone"
        val allSigned = data["all_signed"]?.toBooleanStrictOrNull() ?: false
        val text = if (allSigned) "All signatures collected for permit" else "$signerName signed the work permit"
        showOperationalNotification(
            channelId = CHANNEL_WORK_PERMITS,
            channelName = "Work Permits",
            notifId = (permitId + "sig").hashCode(),
            title = "Permit Signature",
            text = text,
            deepLinkExtra = if (woId != null) "wo_id" to woId else "permit_id" to permitId,
        )
        enqueueSyncWork()
    }

    /** Change 23: work permit status changed — deep-link to WO. */
    private fun handleWorkPermitPush(msg: RemoteMessage) {
        val data = msg.data
        val permitId = data["permit_id"] ?: run { enqueueSyncWork(); return }
        val woId = data["wo_id"]
        val status = data["status"] ?: "updated"
        showOperationalNotification(
            channelId = CHANNEL_WORK_PERMITS,
            channelName = "Work Permits",
            notifId = permitId.hashCode(),
            title = "Work Permit: $status",
            text = "Permit status changed to $status",
            deepLinkExtra = if (woId != null) "wo_id" to woId else "permit_id" to permitId,
        )
        enqueueSyncWork()
    }

    /** Change 8/29: technician assigned to a WO — deep-link to WO. */
    private fun handleWoAssignmentPush(msg: RemoteMessage) {
        val data = msg.data
        val woId = data["wo_id"] ?: run { enqueueSyncWork(); return }
        val assignedBy = data["assigned_by"] ?: "Dispatcher"
        val woTitle = data["wo_title"] ?: "Work Order"
        showOperationalNotification(
            channelId = CHANNEL_ASSIGNMENTS,
            channelName = "Work Order Assignments",
            notifId = (woId + "assign").hashCode(),
            title = "Assigned: $woTitle",
            text = "Assigned to you by $assignedBy",
            deepLinkExtra = "wo_id" to woId,
        )
        enqueueSyncWork()
    }

    /** Change 83: team assigned to a WO. */
    private fun handleWoTeamAssignmentPush(msg: RemoteMessage) {
        val data = msg.data
        val woId = data["wo_id"] ?: run { enqueueSyncWork(); return }
        val teamName = data["team_name"] ?: "Your team"
        val woTitle = data["wo_title"] ?: "Work Order"
        showOperationalNotification(
            channelId = CHANNEL_ASSIGNMENTS,
            channelName = "Work Order Assignments",
            notifId = (woId + "teamassign").hashCode(),
            title = "Team Assignment: $woTitle",
            text = "$teamName was assigned to this work order",
            deepLinkExtra = "wo_id" to woId,
        )
        enqueueSyncWork()
    }

    /** Change 34: RCA report created or updated. */
    private fun handleRcaReportPush(msg: RemoteMessage) {
        val data = msg.data
        val reportId = data["report_id"] ?: run { enqueueSyncWork(); return }
        val woId = data["wo_id"]
        val assetId = data["asset_id"]
        val status = data["status"] ?: "updated"
        showOperationalNotification(
            channelId = CHANNEL_GENERAL,
            channelName = "General",
            notifId = reportId.hashCode(),
            title = "RCA Report: $status",
            text = "Root Cause Analysis report was $status",
            deepLinkExtra = when {
                woId != null -> "wo_id" to woId
                assetId != null -> "asset_id" to assetId
                else -> "report_id" to reportId
            },
        )
        enqueueSyncWork()
    }

    /** Change 11: log cost line pending approval / approved / rejected. */
    private fun handleLogCostLinePush(msg: RemoteMessage) {
        val data = msg.data
        val lineId = data["line_id"] ?: run { enqueueSyncWork(); return }
        val logId = data["log_id"]
        val costStatus = data["cost_status"] ?: "updated"
        showOperationalNotification(
            channelId = CHANNEL_APPROVALS,
            channelName = "Approvals",
            notifId = lineId.hashCode(),
            title = "Cost Line: $costStatus",
            text = "A cost line was $costStatus",
            deepLinkExtra = if (logId != null) "log_id" to logId else "line_id" to lineId,
        )
        enqueueSyncWork()
    }

    /** Change 124/125: owner assignment created or updated. */
    private fun handleOwnerAssignmentPush(msg: RemoteMessage) {
        val data = msg.data
        val assignmentId = data["assignment_id"] ?: run { enqueueSyncWork(); return }
        val resourceType = data["resource_type"] ?: "resource"
        val resourceId = data["resource_id"] ?: ""
        showOperationalNotification(
            channelId = CHANNEL_ASSIGNMENTS,
            channelName = "Work Order Assignments",
            notifId = assignmentId.hashCode(),
            title = "Ownership Assigned",
            text = "You were assigned as owner of a $resourceType",
            deepLinkExtra = "${resourceType}_id" to resourceId,
        )
        enqueueSyncWork()
    }

    /** Change 127/149: job budget threshold exceeded. */
    private fun handleJobBudgetAlertPush(msg: RemoteMessage) {
        val data = msg.data
        val jobId = data["job_id"] ?: run { enqueueSyncWork(); return }
        val jobTitle = data["job_title"] ?: "Job"
        val pct = data["spent_pct"] ?: "?"
        showOperationalNotification(
            channelId = CHANNEL_APPROVALS,
            channelName = "Approvals",
            notifId = (jobId + "budget").hashCode(),
            title = "Budget Alert: $jobTitle",
            text = "Spending has reached $pct% of the budget",
            deepLinkExtra = "job_id" to jobId,
        )
        enqueueSyncWork()
    }

    /** Change 131/132: asset location transfer completed. */
    private fun handleLocationHistoryPush(msg: RemoteMessage) {
        val data = msg.data
        val assetId = data["asset_id"] ?: run { enqueueSyncWork(); return }
        val assetName = data["asset_name"] ?: "Asset"
        val toLocation = data["to_location"] ?: "new location"
        showOperationalNotification(
            channelId = CHANNEL_ASSETS,
            channelName = "Asset Updates",
            notifId = (assetId + "move").hashCode(),
            title = "Asset Moved",
            text = "$assetName transferred to $toLocation",
            deepLinkExtra = "asset_id" to assetId,
        )
        enqueueSyncWork()
    }

    /** Change 146: part transfer request status changed. */
    private fun handlePartTransferPush(msg: RemoteMessage) {
        val data = msg.data
        val requestId = data["request_id"] ?: run { enqueueSyncWork(); return }
        val partName = data["part_name"] ?: "Part"
        val status = data["status"] ?: "updated"
        showOperationalNotification(
            channelId = CHANNEL_GENERAL,
            channelName = "General",
            notifId = (requestId + "transfer").hashCode(),
            title = "Part Transfer: $status",
            text = "Transfer of $partName was $status",
            deepLinkExtra = "request_id" to requestId,
        )
        enqueueSyncWork()
    }

    /** Change 100: new comment on a PO. */
    private fun handlePoCommentPush(msg: RemoteMessage) {
        val data = msg.data
        val poId = data["po_id"] ?: run { enqueueSyncWork(); return }
        val authorName = data["author_name"] ?: "Someone"
        val body = data["body"] ?: "left a comment"
        showOperationalNotification(
            channelId = CHANNEL_GENERAL,
            channelName = "General",
            notifId = (poId + "pocomment").hashCode(),
            title = "PO Comment from $authorName",
            text = body,
            deepLinkExtra = "po_id" to poId,
        )
        enqueueSyncWork()
    }

    /** Change 110/117: sync conflict requiring manual resolution. */
    private fun handleSyncConflictPush(msg: RemoteMessage) {
        val data = msg.data
        val conflictId = data["conflict_id"] ?: run { enqueueSyncWork(); return }
        val entityType = data["entity_type"] ?: "entity"
        showOperationalNotification(
            channelId = CHANNEL_GENERAL,
            channelName = "General",
            notifId = conflictId.hashCode(),
            title = "Sync Conflict",
            text = "A conflict was detected for a $entityType — tap to resolve",
            deepLinkExtra = "conflict_id" to conflictId,
        )
        enqueueSyncWork()
    }

    /** Change 70: broadcast/reminder in a chat thread. */
    private fun handleBroadcastReminderPush(msg: RemoteMessage) {
        val data = msg.data
        val threadId = data["thread_id"] ?: run { enqueueSyncWork(); return }
        val title = data["title"] ?: "Broadcast Reminder"
        val body = data["body"] ?: "You have an unread broadcast"
        showOperationalNotification(
            channelId = CHANNEL_MENTIONS,
            channelName = "Mentions",
            notifId = (threadId + "broadcast").hashCode(),
            title = title,
            text = body,
            deepLinkExtra = "thread_id" to threadId,
        )
        enqueueSyncWork()
    }

    /** Thread AI summary ready. */
    private fun handleThreadSummaryReadyPush(msg: RemoteMessage) {
        val data = msg.data
        val threadId = data["thread_id"] ?: run { enqueueSyncWork(); return }
        showOperationalNotification(
            channelId = CHANNEL_GENERAL,
            channelName = "General",
            notifId = (threadId + "summary").hashCode(),
            title = "Thread Summary Ready",
            text = "An AI summary of your thread is ready to view",
            deepLinkExtra = "thread_id" to threadId,
        )
        enqueueSyncWork()
    }

    /** New chat thread created (group/WO thread). */
    private fun handleChatThreadPush(msg: RemoteMessage) {
        val data = msg.data
        val threadId = data["thread_id"] ?: run { enqueueSyncWork(); return }
        val topic = data["topic"] ?: "a new thread"
        showOperationalNotification(
            channelId = CHANNEL_GENERAL,
            channelName = "General",
            notifId = (threadId + "newthread").hashCode(),
            title = "New Thread",
            text = "You were added to: $topic",
            deepLinkExtra = "thread_id" to threadId,
        )
        enqueueSyncWork()
    }

    /**
     * Generic operational notification helper.
     * Creates the channel on first use, posts the notification, and wires a deep-link extra.
     */
    private fun showOperationalNotification(
        channelId: String,
        channelName: String,
        notifId: Int,
        title: String,
        text: String,
        deepLinkExtra: Pair<String, String>,
        extraExtras: List<Pair<String, String>> = emptyList(),
    ) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableLights(true)
                lightColor = 0xFF1C8EF0.toInt()
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(deepLinkExtra.first, deepLinkExtra.second)
            extraExtras.forEach { (k, v) -> putExtra(k, v) }
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notifId, notification)
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

        // Deep-link intent — opens ThreadScreen for this thread,
        // scrolling to the specific message if message_id is present (Change 137).
        val hasAttachment = data["has_attachment"]?.toBooleanStrictOrNull() ?: false
        val hasAudio = data["has_audio"]?.toBooleanStrictOrNull() ?: false
        val displayBody = when {
            hasAudio -> "🎤 Voice message"
            hasAttachment -> "📎 Attachment"
            else -> body
        }
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("thread_id", threadId)
            messageId?.let { putExtra("message_id", it) }
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
            .addMessage(displayBody, System.currentTimeMillis(), person)

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
        // Operational notification channel IDs
        const val CHANNEL_MENTIONS = "mentions"
        const val CHANNEL_ASSIGNMENTS = "wo_assignments"
        const val CHANNEL_APPROVALS = "approvals"
        const val CHANNEL_ASSETS = "asset_updates"
        const val CHANNEL_WORK_PERMITS = "work_permits"
        const val CHANNEL_GENERAL = "general_ops"
    }
}
