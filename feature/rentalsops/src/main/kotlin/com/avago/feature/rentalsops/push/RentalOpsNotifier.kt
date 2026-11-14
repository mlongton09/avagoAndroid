package com.avago.feature.rentalsops.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
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
 * Posts rental notifications. Channels:
 * - one per server push `category` ([RentalPushRouter.Category]): booking requests, pickup
 *   requests, delivery / pickup / transfer tasks (high), return due, invoices (default);
 * - `rentalops_alerts` (default): offline actions the server rejected, tasks reassigned away.
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
        // Pre-stage-C single task channel; superseded by the per-category channels.
        nm.deleteNotificationChannel(LEGACY_CHANNEL_TASKS)
        val group = NotificationChannelGroup(GROUP, ctx.getString(R.string.rentalops_channel_group))
        nm.createNotificationChannelGroup(group)
        for (c in RentalPushRouter.Category.entries) {
            val (name, desc) = channelText(c)
            nm.createNotificationChannel(
                NotificationChannel(c.channelId, ctx.getString(name), if (isHigh(c)) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = ctx.getString(desc)
                    this.group = GROUP
                },
            )
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, ctx.getString(R.string.rentalops_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = ctx.getString(R.string.rentalops_channel_alerts_desc)
                this.group = GROUP
            },
        )
    }

    private fun isHigh(c: RentalPushRouter.Category) = c in HIGH

    private fun channelText(c: RentalPushRouter.Category): Pair<Int, Int> = when (c) {
        RentalPushRouter.Category.BOOKING_REQUEST -> R.string.rentalops_channel_booking to R.string.rentalops_channel_booking_desc
        RentalPushRouter.Category.PICKUP_REQUESTED -> R.string.rentalops_channel_pickup_requested to R.string.rentalops_channel_pickup_requested_desc
        RentalPushRouter.Category.DELIVERY -> R.string.rentalops_channel_delivery to R.string.rentalops_channel_delivery_desc
        RentalPushRouter.Category.PICKUP -> R.string.rentalops_channel_pickup to R.string.rentalops_channel_pickup_desc
        RentalPushRouter.Category.TRANSFER -> R.string.rentalops_channel_transfer to R.string.rentalops_channel_transfer_desc
        RentalPushRouter.Category.RETURN_DUE -> R.string.rentalops_channel_return_due to R.string.rentalops_channel_return_due_desc
        RentalPushRouter.Category.INVOICE -> R.string.rentalops_channel_invoice to R.string.rentalops_channel_invoice_desc
    }
    /** [route] null = just open the app (e.g. a push for another account). */
    fun post(channel: String, key: String, title: String, text: String, route: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.d("[RentalOps] notifications not permitted; dropping $key")
            return
        }
        ensureChannels()
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            action = Intent.ACTION_VIEW
            route?.let { putExtra(RentalOpsRoutes.EXTRA_ROUTE, it) }
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
            .setCategory(if (channel == CHANNEL_ALERTS) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (channel in HIGH_CHANNELS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
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

    /** `403 not_assigned`: the task went to another driver; queued updates were dropped. */
    fun postReassigned(taskId: String, label: String?) {
        post(
            CHANNEL_ALERTS,
            "rentalops:reassigned:$taskId",
            ctx.getString(R.string.rentalops_reassigned_title),
            label?.let { ctx.getString(R.string.rentalops_reassigned_body_named, it) } ?: ctx.getString(R.string.rentalops_reassigned_body),
            RentalOpsRoutes.TASKS,
        )
    }

    companion object {
        const val CHANNEL_ALERTS = "rentalops_alerts"
        const val GROUP = "rentals"
        private const val LEGACY_CHANNEL_TASKS = "rentalops_tasks"
        private val HIGH = setOf(
            RentalPushRouter.Category.BOOKING_REQUEST,
            RentalPushRouter.Category.DELIVERY,
            RentalPushRouter.Category.PICKUP,
            RentalPushRouter.Category.TRANSFER,
        )
        private val HIGH_CHANNELS = HIGH.map { it.channelId }.toSet()
    }
}
