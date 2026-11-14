package com.avago.feature.rentals.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalOutbox
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Minimum touch target (TalkBack / accessibility). */
internal val MinTouch = 48.dp

@StringRes
internal fun fleetStatusLabel(status: FleetStatus): Int = when (status) {
    FleetStatus.AVAILABLE -> R.string.rentals_status_available
    FleetStatus.RESERVED -> R.string.rentals_status_reserved
    FleetStatus.ON_RENT -> R.string.rentals_status_on_rent
    FleetStatus.OVERDUE -> R.string.rentals_status_overdue
    FleetStatus.IN_MAINTENANCE -> R.string.rentals_status_in_maintenance
    FleetStatus.NOT_READY -> R.string.rentals_status_not_ready
}

@StringRes
internal fun orderStatusLabel(status: String): Int = when (status) {
    "draft" -> R.string.rentals_order_status_draft
    "quoted" -> R.string.rentals_order_status_quoted
    "held" -> R.string.rentals_order_status_held
    "reserved" -> R.string.rentals_order_status_reserved
    "on_rent" -> R.string.rentals_order_status_on_rent
    "off_rent_requested" -> R.string.rentals_order_status_off_rent_requested
    "returned" -> R.string.rentals_order_status_returned
    "closed" -> R.string.rentals_order_status_closed
    "cancelled" -> R.string.rentals_order_status_cancelled
    else -> R.string.rentals_order_status_unknown
}

@StringRes
internal fun actionLabel(action: String): Int = when (action) {
    "quote" -> R.string.rentals_action_quote
    "hold" -> R.string.rentals_action_hold
    "reserve" -> R.string.rentals_action_reserve
    "cancel" -> R.string.rentals_action_cancel
    "no-show" -> R.string.rentals_action_no_show
    "extend" -> R.string.rentals_action_extend
    "off-rent" -> R.string.rentals_action_off_rent
    "checkout" -> R.string.rentals_action_checkout
    "checkin" -> R.string.rentals_action_checkin
    "swap" -> R.string.rentals_action_swap
    "close" -> R.string.rentals_action_close
    else -> R.string.rentals_action_unknown
}

@StringRes
internal fun eventLabel(type: String): Int = when (type) {
    "pickup" -> R.string.rentals_event_pickup
    "return" -> R.string.rentals_event_return
    "hold" -> R.string.rentals_event_hold
    "maintenance" -> R.string.rentals_event_maintenance
    else -> R.string.rentals_event_other
}

/**
 * Localised label of a fleet readiness reason code (`readiness[]`, spec §11.1). Unknown codes
 * from a newer server fall back to the code itself, de-snaked.
 */
@StringRes
internal fun readinessReasonLabel(code: String): Int? = when (code) {
    "asset_status" -> R.string.rentals_reason_asset_status
    "open_defect" -> R.string.rentals_reason_open_defect
    "pm_overdue" -> R.string.rentals_reason_pm_overdue
    "open_work_order" -> R.string.rentals_reason_open_work_order
    "readiness_checklist" -> R.string.rentals_reason_readiness_checklist
    "maintenance_open" -> R.string.rentals_reason_maintenance_open
    else -> null
}

@Composable
internal fun readinessReasons(codes: List<String>): String =
    codes.map { code -> readinessReasonLabel(code)?.let { stringResource(it) } ?: code.replace('_', ' ') }.joinToString()

/** Status colours (light/dark). Always paired with a text label — never colour alone. */
@Composable
internal fun fleetStatusColors(status: FleetStatus): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return when (status) {
        FleetStatus.AVAILABLE -> if (dark) Color(0xFF14532D) to Color(0xFFBBF7D0) else Color(0xFFDCFCE7) to Color(0xFF166534)
        FleetStatus.RESERVED -> if (dark) Color(0xFF1E3A8A) to Color(0xFFBFDBFE) else Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        FleetStatus.ON_RENT -> if (dark) Color(0xFF3B0764) to Color(0xFFE9D5FF) else Color(0xFFF3E8FF) to Color(0xFF6B21A8)
        FleetStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        FleetStatus.IN_MAINTENANCE -> if (dark) Color(0xFF78350F) to Color(0xFFFDE68A) else Color(0xFFFEF3C7) to Color(0xFF92400E)
        FleetStatus.NOT_READY -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
internal fun FleetStatusChip(status: FleetStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = fleetStatusColors(status)
    Surface(shape = RoundedCornerShape(50), color = bg, modifier = modifier) {
        Text(
            text = stringResource(fleetStatusLabel(status)),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * User-facing text of a record notice: the outbox stores markers
 * ([RentalOutbox.NOTICE_SYNC_FAILED_PREFIX], [RentalOutbox.NOTICE_WARNINGS_PREFIX],
 * [RentalOutbox.NOTICE_CONFIRMED_WITH_CONFLICT]) that are localised here; anything else is
 * the server's message (shown verbatim).
 */
@Composable
internal fun noticeText(notice: String?, conflict: Boolean): String = when {
    notice == null -> stringResource(if (conflict) R.string.rentals_conflict_generic else R.string.rentals_draft_generic)
    notice == RentalOutbox.NOTICE_CONFIRMED_WITH_CONFLICT -> stringResource(R.string.rentals_confirmed_with_conflict)
    notice.startsWith(RentalOutbox.NOTICE_SYNC_FAILED_PREFIX) ->
        stringResource(R.string.rentals_sync_failed, notice.removePrefix(RentalOutbox.NOTICE_SYNC_FAILED_PREFIX))
    notice.startsWith(RentalOutbox.NOTICE_WARNINGS_PREFIX) ->
        stringResource(R.string.rentals_server_warnings, notice.removePrefix(RentalOutbox.NOTICE_WARNINGS_PREFIX))
    notice.startsWith(RentalOutbox.NOTICE_FIELD_CONFLICT_PREFIX) ->
        stringResource(
            R.string.rentals_field_conflict,
            notice.removePrefix(RentalOutbox.NOTICE_FIELD_CONFLICT_PREFIX).split(',').filter { it.isNotBlank() }
                .map { f -> conflictFieldLabel(f)?.let { stringResource(it) } ?: f.replace('_', ' ') }
                .joinToString(),
        )
    else -> notice
}

/** Label of a field named in a version-conflict notice (customer and order fields). */
@StringRes
internal fun conflictFieldLabel(field: String): Int? = when (field) {
    "name", "customer_name" -> R.string.rentals_name
    "company" -> R.string.rentals_company
    "email" -> R.string.rentals_email
    "phone" -> R.string.rentals_phone
    "address" -> R.string.rentals_address
    "date_of_birth" -> R.string.rentals_date_of_birth
    "notes" -> R.string.rentals_notes
    "preferred_currency" -> R.string.rentals_currency
    "preferred_locale" -> R.string.rentals_field_language
    "status" -> R.string.rentals_field_status
    "end_at" -> R.string.rentals_field_return_date
    else -> null
}

/** True when [notice] marks an outbox item parked after a 404/405. */
internal fun isSyncFailed(notice: String?): Boolean = notice?.startsWith(RentalOutbox.NOTICE_SYNC_FAILED_PREFIX) == true

/** Pending / draft / conflict badge for a locally changed record (spec §15). */
@Composable
internal fun SyncStateBadge(localState: String, modifier: Modifier = Modifier, failed: Boolean = false) {
    if (localState == RentalRecordEntity.STATE_SYNCED) return
    val (label, icon, bg, fg) = when {
        failed -> Quad(
            R.string.rentals_sync_failed_badge, Icons.Default.Warning,
            MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
        )
        localState == RentalRecordEntity.STATE_DRAFT -> Quad(
            R.string.rentals_sync_draft, Icons.Default.CloudOff,
            MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
        )
        localState == RentalRecordEntity.STATE_CONFLICT -> Quad(
            R.string.rentals_sync_conflict, Icons.Default.Warning,
            MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> Quad(
            R.string.rentals_sync_pending, Icons.Default.Sync,
            MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Surface(shape = RoundedCornerShape(50), color = bg, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** "Last synced …" row with pending count and a Sync now action. */
@Composable
internal fun LastSyncedRow(
    lastSyncedAt: Long?,
    pendingCount: Int,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val synced = lastSyncedAt?.let { formatDateTime(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
    val text = buildString {
        append(
            if (synced != null) stringResource(R.string.rentals_last_synced, synced)
            else stringResource(R.string.rentals_never_synced),
        )
        if (pendingCount > 0) {
            append(" · ")
            append(stringResource(R.string.rentals_pending_count, pendingCount))
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = text },
        )
        TextButton(onClick = onSyncNow, modifier = Modifier.heightIn(min = MinTouch)) {
            Text(stringResource(R.string.rentals_sync_now))
        }
    }
}

internal fun formatMoney(m: Money?): String = m?.let { MoneyFormatter.format(it) } ?: "—"

internal fun formatDateTime(t: Instant?, zone: ZoneId): String =
    t?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(zone).format(it) } ?: "—"

internal fun formatTime(t: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone).format(t)

internal fun formatDate(t: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone).format(t)

internal fun zoneOf(id: String?): ZoneId =
    id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
