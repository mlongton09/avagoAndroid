package com.avago.feature.rentalsops.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.avago.feature.rentalsops.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts rental notifications on two channels:
 * - `rentalops_tasks` (high): assigned/changed driver tasks and transfers.
 * - `rentalops_alerts` (default): due/overdue, assignments and rejected offline actions.
 *
 * Tapping opens the app's launcher activity with [RentalOpsRoutes.EXTRA_ROUTE]; MainActivity
 * validates the route with [RentalOpsRoutes.isSafe] before navigating.
 */
@Singleton
class RentalOpsNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TASKS, ctx.getString(R.string.rentalops_channel_tasks), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.rentalops_channel_tasks_desc)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, ctx.getString(R.string.rentalops_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = ctx.getString(R.string.rentalops_channel_alerts_desc)
            },
        )
    }

    fun post(channel: String, key: String, title: String, text: String, route: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.d("[RentalOps] notifications not permitted; dropping $key")
            return
        }
        ensureChannels()
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            action = Intent.ACTION_VIEW
            putExtra(RentalOpsRoutes.EXTRA_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return
        val id = key.hashCode()
        val pi = PendingIntent.getActivity(ctx, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(if (channel == CHANNEL_TASKS) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (channel == CHANNEL_TASKS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.notify(id, n) }
            .onFailure { Timber.w(it, "[RentalOps] notify failed") }
    }

    /** An offline action the server refused (shown once per queue row). */
    fun postRejected(queueId: String, what: String, reason: String, route: String) {
        post(
            CHANNEL_ALERTS,
            "rentalops:rejected:$queueId",
            ctx.getString(R.string.rentalops_rejected_title, what),
            reason,
            route,
        )
    }

    companion object {
        const val CHANNEL_TASKS = "rentalops_tasks"
        const val CHANNEL_ALERTS = "rentalops_alerts"
    }
}
