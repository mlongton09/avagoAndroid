package com.avago.feature.rentalsops.push

import android.content.Context
import androidx.annotation.StringRes
import com.avago.core.auth.IdentityManager
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
 * generic dispatch: `if (rentalOpsPush.handles(type)) { rentalOpsPush.handle(msg.data); return }`.
 *
 * The server sends data-only messages, so the banner is built here: the localized string for
 * `title_loc_key` / `body_loc_key` when the app ships it, else the server's English
 * `title` / `body`. Always nudges sync so the local store catches up, even when the event is
 * muted on this device.
 */
@Singleton
class RentalOpsPushHandler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val identity: IdentityManager,
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
                // The server already skips users who switched the event off (notification-prefs);
                // the local mirror also covers drive-only users who cannot reach that endpoint.
                if (!prefs.isEnabled(spec.event)) return@launch
                val text = PushText.resolve(spec)
                val title = text.titleRes?.let(ctx::getString) ?: spec.title ?: ctx.getString(R.string.rentalops_push_fallback_title)
                val body = when {
                    text.bodyRes != null && text.bodyTakesNumber && spec.orderNumber != null -> ctx.getString(text.bodyRes, spec.orderNumber)
                    text.bodyRes != null && !text.bodyTakesNumber -> ctx.getString(text.bodyRes)
                    // Localized title shown: do not mix in the server's English body.
                    text.bodyRes != null || text.titleRes != null -> ctx.getString(R.string.rentalops_push_tap_to_open)
                    else -> spec.body ?: ctx.getString(R.string.rentalops_push_tap_to_open)
                }
                // A push for another signed-in account must not deep-link into the active one.
                val active = identity.getActiveAccountId()
                val route = spec.route.takeIf { spec.accountId == null || spec.accountId == active }
                notifier.post(spec.category.channelId, spec.notificationKey, title, body, route)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[RentalOps] push handling failed for ${spec.event}")
            }
        }
    }
}

/**
 * Server loc keys (stage C §7) → app strings. Bodies carry the order number (`number`, the
 * first `loc_args` entry) as `%1$s`; when the push has no number the server's text is used.
 */
internal object PushText {
    data class Text(@StringRes val titleRes: Int?, @StringRes val bodyRes: Int?, val bodyTakesNumber: Boolean)

    private val TITLES: Map<String, Int> = mapOf(
        "rental_booking_request_title" to R.string.rental_booking_request_title,
        "rental_off_rent_requested_title" to R.string.rental_off_rent_requested_title,
        "rental_overdue_title" to R.string.rental_overdue_title,
        "rental_cycle_invoiced_title" to R.string.rental_cycle_invoiced_title,
        "rental_task_assigned_delivery_title" to R.string.rental_task_assigned_delivery_title,
        "rental_task_assigned_pickup_title" to R.string.rental_task_assigned_pickup_title,
        "rental_task_assigned_transfer_title" to R.string.rental_task_assigned_transfer_title,
        "rental_task_completed_delivery_title" to R.string.rental_task_completed_delivery_title,
        "rental_task_completed_pickup_title" to R.string.rental_task_completed_pickup_title,
        "rental_task_completed_transfer_title" to R.string.rental_task_completed_transfer_title,
        "rental_task_failed_delivery_title" to R.string.rental_task_failed_delivery_title,
        "rental_task_failed_pickup_title" to R.string.rental_task_failed_pickup_title,
        "rental_task_failed_transfer_title" to R.string.rental_task_failed_transfer_title,
        // Wave 2 (G2 / G3): no order number in these pushes.
        "rental_doc_expiring_title" to R.string.rental_doc_expiring_title,
        "rental_credit_application_title" to R.string.rental_credit_application_title,
        "rental_dunning_hold_title" to R.string.rental_dunning_hold_title,
        "rental_subrental_return_due_title" to R.string.rental_subrental_return_due_title,
    )

    /** Body key → (string, takes the order number). */
    private val BODIES: Map<String, Pair<Int, Boolean>> = mapOf(
        "rental_booking_request_body" to (R.string.rental_booking_request_body to true),
        "rental_off_rent_requested_body" to (R.string.rental_off_rent_requested_body to true),
        "rental_overdue_body" to (R.string.rental_overdue_body to true),
        "rental_cycle_invoiced_body" to (R.string.rental_cycle_invoiced_body to false),
        "rental_task_assigned_delivery_body" to (R.string.rental_task_assigned_delivery_body to true),
        "rental_task_assigned_pickup_body" to (R.string.rental_task_assigned_pickup_body to true),
        "rental_task_assigned_transfer_body" to (R.string.rental_task_assigned_transfer_body to true),
        "rental_task_completed_delivery_body" to (R.string.rental_task_completed_delivery_body to true),
        "rental_task_completed_pickup_body" to (R.string.rental_task_completed_pickup_body to true),
        "rental_task_completed_transfer_body" to (R.string.rental_task_completed_transfer_body to true),
        "rental_task_failed_delivery_body" to (R.string.rental_task_failed_delivery_body to true),
        "rental_task_failed_pickup_body" to (R.string.rental_task_failed_pickup_body to true),
        "rental_task_failed_transfer_body" to (R.string.rental_task_failed_transfer_body to true),
    )

    /** Every server loc key the app ships (30 = 17 titles + 13 bodies; wave 2 titles only). */
    val KEYS: Set<String> get() = TITLES.keys + BODIES.keys

    fun resolve(spec: RentalPushRouter.Spec): Text {
        val body = spec.bodyLocKey?.let(BODIES::get)
        return Text(spec.titleLocKey?.let(TITLES::get), body?.first, body?.second ?: false)
    }
}
