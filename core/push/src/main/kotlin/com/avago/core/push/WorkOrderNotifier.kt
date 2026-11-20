package com.avago.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.avago.core.data.v2.V2Stores
import com.avago.core.data.v2.V2SyncResult
import com.avago.core.data.v2.repo.V2WorkOrderAlerts
import com.avago.core.data.v2.repo.WorkOrderAlert
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.core.sync.v2.V2PullListener
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local work-order notifications after a v2 pull: a job assigned to me, a comment on my work
 * order, a work order awaiting my approval ([V2WorkOrderAlerts]). The server only sends silent
 * sync hints for these, so the device announces what the pull brought in. Each event is announced
 * once per device (the announced keys are kept in preferences, per user).
 */
@Singleton
class WorkOrderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionsManager,
) : V2PullListener {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override suspend fun onPulled(stores: V2Stores, result: V2SyncResult.Synced) {
        if (result.upserted.keys.none { it in WATCHED }) return
        val userId = stores.identity.userId
        val selection = stores.business.read { conn ->
            V2WorkOrderAlerts.select(conn, result.upserted, userId, permissions.can(Permissions.WO_APPROVE))
        }
        val key = "$KEY_ANNOUNCED.${stores.identity.accountId}.$userId"
        val announced = prefs.getString(key, null)?.split(SEPARATOR)?.filter(String::isNotEmpty).orEmpty()
        val (fresh, next) = selection.unannounced(announced)
        if (next != announced) prefs.edit().putString(key, next.joinToString(SEPARATOR)).apply()
        if (fresh.isEmpty() || !NotificationPermissionHelper.isGranted(context)) return
        ensureChannels()
        fresh.forEach(::post)
    }

    private fun post(alert: WorkOrderAlert) {
        val (channel, title, text) = when (alert.kind) {
            WorkOrderAlert.Kind.ASSIGNED -> Triple(CHANNEL_ASSIGNMENTS, context.getString(R.string.push_wo_assigned_title), alert.workOrderTitle)
            WorkOrderAlert.Kind.COMMENT -> Triple(
                CHANNEL_COMMENTS,
                context.getString(R.string.push_wo_comment_title, alert.workOrderTitle),
                alert.text?.takeIf(String::isNotBlank)?.take(MAX_TEXT) ?: context.getString(R.string.push_wo_comment_media),
            )
            WorkOrderAlert.Kind.NEEDS_APPROVAL -> Triple(
                CHANNEL_APPROVALS,
                context.getString(R.string.push_wo_approval_title),
                context.getString(R.string.push_wo_approval_text, alert.workOrderTitle),
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_WORK_ORDER_ID, alert.workOrderId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return
        val id = alert.key.hashCode()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "WorkOrderNotifier: notification permission revoked")
        }
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            CHANNEL_ASSIGNMENTS to R.string.push_channel_wo_assignments,
            CHANNEL_COMMENTS to R.string.push_channel_wo_comments,
            CHANNEL_APPROVALS to R.string.push_channel_approvals,
        ).filter { manager.getNotificationChannel(it.first) == null }.forEach { (id, name) ->
            manager.createNotificationChannel(NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_HIGH))
        }
    }

    companion object {
        /** Intent extra with the work order to open (also set by FCM work-order pushes). */
        const val EXTRA_WORK_ORDER_ID = "wo_id"

        /** Shared with the FCM work-order assignment and approval pushes. */
        const val CHANNEL_ASSIGNMENTS = "wo_assignments"
        const val CHANNEL_APPROVALS = "approvals"
        const val CHANNEL_COMMENTS = "wo_comments"

        private val WATCHED = setOf("work_order_assignments", "comments", "work_orders")
        private const val PREFS_NAME = "wo_notifications"
        private const val KEY_ANNOUNCED = "announced"
        private const val SEPARATOR = "\n"
        private const val MAX_TEXT = 200
    }
}
