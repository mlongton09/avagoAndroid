package com.avago.feature.rentalsops.push

import android.content.Context
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.sync.RentalOpsSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point for rental FCM messages, called by the app's FirebaseMessagingService before its
 * generic dispatch: `if (RentalPushRouter.handles(type)) { rentalOpsPush.handle(msg.data); return }`.
 * Always nudges sync so the local store (and widget) catch up, even when the user muted the
 * category.
 */
@Singleton
class RentalOpsPushHandler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: RentalOpsPushPrefs,
    private val notifier: RentalOpsNotifier,
    private val scheduler: RentalOpsSyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun handles(type: String?): Boolean = RentalPushRouter.handles(type)

    fun handle(data: Map<String, String>) {
        scheduler.requestPush()
        val spec = RentalPushRouter.route(data) ?: return
        scope.launch {
            try {
                if (!prefs.isEnabled(spec.category)) return@launch
                val channel = when (spec.category) {
                    RentalPushRouter.Category.TASKS, RentalPushRouter.Category.TRANSFERS -> RentalOpsNotifier.CHANNEL_TASKS
                    else -> RentalOpsNotifier.CHANNEL_ALERTS
                }
                val fallbackTitle = ctx.getString(
                    when (spec.category) {
                        RentalPushRouter.Category.TASKS -> R.string.rentalops_push_task_title
                        RentalPushRouter.Category.TRANSFERS -> R.string.rentalops_push_transfer_title
                        RentalPushRouter.Category.ASSIGNMENTS -> R.string.rentalops_push_assign_title
                        RentalPushRouter.Category.DUE -> R.string.rentalops_push_due_title
                        RentalPushRouter.Category.OTHER -> R.string.rentalops_push_other_title
                    },
                )
                notifier.post(
                    channel = channel,
                    key = spec.notificationKey,
                    title = spec.title ?: fallbackTitle,
                    text = spec.body ?: ctx.getString(R.string.rentalops_push_tap_to_open),
                    route = spec.route,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[RentalOps] push handling failed for ${spec.event}")
            }
        }
    }
}
