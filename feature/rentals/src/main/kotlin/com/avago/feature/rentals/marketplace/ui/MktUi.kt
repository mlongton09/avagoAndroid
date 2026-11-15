package com.avago.feature.rentals.marketplace.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.domain.DistanceUnit
import com.avago.feature.rentals.marketplace.domain.MktCardPrice
import com.avago.feature.rentals.marketplace.domain.MktDistance
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktMeetupStatus
import com.avago.feature.rentals.marketplace.domain.MktSlot
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Staff permissions of the owner side (same keys as web and iOS). */
const val PERMISSION_MARKETPLACE_MANAGE = "rental.marketplace_manage"
const val PERMISSION_MEETUP_RESPOND = "rental.meetup_respond"

/** Light, dark, 200 % font and German previews (docs/rentals-ux-polish-android.md). */
@Preview(name = "light", showBackground = true, widthDp = 380)
@Preview(name = "dark", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "200% font", showBackground = true, widthDp = 380, fontScale = 2f)
@Preview(name = "German", showBackground = true, widthDp = 380, locale = "de")
internal annotation class MktPreviews

@StringRes
internal fun mktErrorRes(e: MktError, owner: Boolean = false): Int = when (e) {
    MktError.Network -> R.string.mkt_err_network
    MktError.RateLimited -> R.string.mkt_err_rate_limited
    MktError.Unauthorized -> R.string.mkt_err_unauthorized
    MktError.NameRequired -> R.string.mkt_err_name_required
    MktError.LocationRequired -> R.string.mkt_location_required
    MktError.PlaceNotFound -> R.string.mkt_err_place_not_found_title
    MktError.InvalidSlots -> R.string.mkt_err_invalid_slots
    MktError.SlotUnavailable -> R.string.mkt_err_slot_unavailable
    MktError.LeadTime -> R.string.mkt_err_lead_time
    MktError.InvalidRange -> R.string.mkt_err_invalid_range
    MktError.TooLong -> R.string.mkt_err_too_long
    MktError.Unavailable -> R.string.mkt_err_unavailable
    MktError.StorefrontDisabled -> R.string.mkt_err_storefront_disabled
    MktError.FulfilmentUnavailable -> R.string.mkt_err_fulfilment_unavailable
    MktError.MeetupUnavailable -> if (owner) R.string.mkt_admin_err_meetup_unavailable else R.string.mkt_err_meetup_unavailable
    MktError.MeetupExists -> R.string.mkt_err_meetup_exists
    MktError.InvalidState -> if (owner) R.string.mkt_admin_err_invalid_state else R.string.mkt_err_invalid_state
    MktError.BookingUnavailable -> if (owner) R.string.mkt_admin_err_booking_unavailable else R.string.mkt_err_booking_unavailable
    MktError.ClientUuidConflict -> R.string.mkt_err_client_uuid_conflict
    MktError.InvalidValue -> R.string.mkt_err_invalid_value
    MktError.SlotPassed -> R.string.mkt_err_slot_passed
    MktError.VersionConflict -> if (owner) R.string.mkt_admin_err_version_conflict else R.string.mkt_err_version_conflict
    MktError.StorefrontRequired -> R.string.mkt_admin_err_storefront_required
    MktError.LocationCoordinatesRequired -> R.string.mkt_admin_err_location_coordinates_required
    MktError.NotFound -> R.string.mkt_err_unavailable_title
    MktError.Forbidden -> R.string.mkt_no_permission_title
    MktError.InvalidEmail -> R.string.mkt_err_invalid_email
    MktError.DatesRequired -> R.string.mkt_err_dates_required
    MktError.AddressRequired -> R.string.mkt_err_address_required
    MktError.PickSlot -> R.string.mkt_err_pick_slot
    MktError.CounterStart -> R.string.mkt_inbox_err_start
    MktError.CounterPast -> R.string.mkt_inbox_err_past
    MktError.CounterEnd -> R.string.mkt_inbox_err_end
    MktError.Generic -> R.string.mkt_err_generic
}

@Composable
internal fun mktErrorText(e: MktError, owner: Boolean = false): String = stringResource(mktErrorRes(e, owner))

@StringRes
internal fun categoryRes(category: String?): Int? = when (category) {
    "tools" -> R.string.mkt_cat_tools
    "construction" -> R.string.mkt_cat_construction
    "landscaping" -> R.string.mkt_cat_landscaping
    "vehicles" -> R.string.mkt_cat_vehicles
    "trailers" -> R.string.mkt_cat_trailers
    "rv_camping" -> R.string.mkt_cat_rv_camping
    "boats_water" -> R.string.mkt_cat_boats_water
    "recreation" -> R.string.mkt_cat_recreation
    "party_events" -> R.string.mkt_cat_party_events
    "audio_video" -> R.string.mkt_cat_audio_video
    "farm" -> R.string.mkt_cat_farm
    "snow_winter" -> R.string.mkt_cat_snow_winter
    "other" -> R.string.mkt_cat_other
    else -> null
}

@Composable
internal fun categoryLabel(category: String?): String = categoryRes(category)?.let { stringResource(it) } ?: category.orEmpty()

/** Renter-facing meet-up status ("Waiting for owner"). */
@StringRes
internal fun renterStatusRes(status: String): Int = when (status) {
    MktMeetupStatus.COUNTERED -> R.string.mkt_status_countered
    MktMeetupStatus.ACCEPTED -> R.string.mkt_status_accepted
    MktMeetupStatus.DECLINED -> R.string.mkt_status_declined
    MktMeetupStatus.CANCELLED -> R.string.mkt_status_cancelled
    MktMeetupStatus.EXPIRED -> R.string.mkt_status_expired
    else -> R.string.mkt_status_pending
}

/** Owner-facing meet-up status ("Awaiting reply"). */
@StringRes
internal fun ownerStatusRes(status: String): Int = when (status) {
    MktMeetupStatus.COUNTERED -> R.string.mkt_owner_status_countered
    MktMeetupStatus.ACCEPTED -> R.string.mkt_owner_status_accepted
    MktMeetupStatus.DECLINED -> R.string.mkt_owner_status_declined
    MktMeetupStatus.CANCELLED -> R.string.mkt_owner_status_cancelled
    MktMeetupStatus.EXPIRED -> R.string.mkt_owner_status_expired
    else -> R.string.mkt_owner_status_pending
}

internal fun meetupTone(status: String): StatusTone = when (status) {
    MktMeetupStatus.PENDING -> StatusTone.Warning
    MktMeetupStatus.COUNTERED -> StatusTone.Info
    MktMeetupStatus.ACCEPTED -> StatusTone.Success
    MktMeetupStatus.DECLINED -> StatusTone.Danger
    else -> StatusTone.Neutral
}

internal fun meetupIcon(status: String): ImageVector = when (status) {
    MktMeetupStatus.PENDING -> Icons.Filled.Schedule
    MktMeetupStatus.COUNTERED -> Icons.Filled.SwapHoriz
    MktMeetupStatus.ACCEPTED -> Icons.Filled.CheckCircle
    MktMeetupStatus.DECLINED -> Icons.Filled.Block
    MktMeetupStatus.EXPIRED -> Icons.Filled.EventBusy
    else -> Icons.Filled.Close
}

@StringRes
internal fun orderStatusRes(status: String): Int? = when (status) {
    "draft" -> R.string.mkt_order_status_draft
    "quoted" -> R.string.mkt_order_status_quoted
    "held" -> R.string.mkt_order_status_held
    "reserved" -> R.string.mkt_order_status_reserved
    "on_rent" -> R.string.mkt_order_status_on_rent
    "off_rent_requested" -> R.string.mkt_order_status_off_rent_requested
    "returned" -> R.string.mkt_order_status_returned
    "closed" -> R.string.mkt_order_status_closed
    "cancelled" -> R.string.mkt_order_status_cancelled
    else -> null
}

@Composable
internal fun unitLabel(unit: DistanceUnit): String =
    stringResource(if (unit == DistanceUnit.MI) R.string.mkt_unit_mi else R.string.mkt_unit_km)

/** "~12 km away", or null when the server sent no distance. */
@Composable
internal fun distanceText(km: Double?, mi: Double?, unit: DistanceUnit): String? =
    MktDistance.value(km, mi, unit)?.let { stringResource(R.string.mkt_distance_away, it.toString(), unitLabel(unit)) }

internal fun money(minor: Long?, currency: String?): String = MoneyFormatter.format(minor, currency)

/** "From $45/day" or "Price on request". */
@Composable
internal fun cardPriceText(p: MktCardPrice?): String {
    if (p == null) return stringResource(R.string.mkt_price_on_request)
    val from = p.fromMinor ?: p.dayMinor ?: return stringResource(R.string.mkt_price_on_request)
    val price = money(from, p.currency)
    return when (if (p.fromMinor != null) p.fromUnit else "day") {
        "week" -> stringResource(R.string.mkt_price_from_week, price)
        "month" -> stringResource(R.string.mkt_price_from_month, price)
        else -> stringResource(R.string.mkt_price_from_day, price)
    }
}

/** "Fri, 20 Nov, 09:00 – 09:30" in [zone]. */
internal fun slotLabel(s: MktSlot, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val day = DateTimeFormatter.ofPattern("EEE, d MMM", locale).withZone(zone).format(s.start)
    val t = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    return "$day, ${t.format(s.start)} – ${t.format(s.end)}"
}

internal fun timeRange(s: MktSlot, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val t = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    return "${t.format(s.start)} – ${t.format(s.end)}"
}

internal fun dateRange(a: java.time.Instant, b: java.time.Instant, zone: ZoneId): String {
    val f = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(zone)
    return "${f.format(a)} – ${f.format(b)}"
}

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * "Use my location" with a rationale before the system prompt (§10.1): [precise] = owner map
 * position (fine + coarse), otherwise the renter search asks for approximate location only.
 * When the user has permanently denied it, a dialog offers the app settings. Returns the
 * trigger; [onGranted] runs once permission is available.
 */
@Composable
internal fun rememberLocationRequest(precise: Boolean, onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    var rationale by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val perms = remember(precise) {
        if (precise) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            granted()
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null && perms.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            if (!canAskAgain) blocked = true
        }
    }
    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false },
            title = { Text(stringResource(if (precise) R.string.mkt_owner_location_rationale_title else R.string.mkt_location_rationale_title)) },
            text = { Text(stringResource(if (precise) R.string.mkt_owner_location_rationale else R.string.mkt_location_rationale)) },
            confirmButton = {
                TextButton(onClick = { rationale = false; launcher.launch(perms) }) { Text(stringResource(R.string.mkt_continue)) }
            },
            dismissButton = { TextButton(onClick = { rationale = false }) { Text(stringResource(R.string.mkt_not_now)) } },
        )
    }
    if (blocked) {
        AlertDialog(
            onDismissRequest = { blocked = false },
            text = { Text(stringResource(if (precise) R.string.mkt_admin_geo_denied else R.string.mkt_location_blocked)) },
            confirmButton = {
                TextButton(onClick = {
                    blocked = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.mkt_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { blocked = false }) { Text(stringResource(R.string.mkt_not_now)) } },
        )
    }
    return {
        val has = perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (has) granted() else rationale = true
    }
}

/** Opens [uri] (tel:, mailto:, geo:) if any app handles it. */
internal fun Context.openUri(uri: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
