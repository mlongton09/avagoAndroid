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
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.toneColors
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

/** Semantic tone of a fleet status (shared pill / KPI colours, light + dark). */
internal fun fleetStatusTone(status: FleetStatus): StatusTone = when (status) {
    FleetStatus.AVAILABLE -> StatusTone.Success
    FleetStatus.RESERVED -> StatusTone.Info
    FleetStatus.ON_RENT -> StatusTone.Accent
    FleetStatus.OVERDUE -> StatusTone.Danger
    FleetStatus.IN_MAINTENANCE -> StatusTone.Warning
    FleetStatus.NOT_READY -> StatusTone.Neutral
}

/** Icon paired with every fleet status so colour is never the only cue. */
internal fun fleetStatusIcon(status: FleetStatus): ImageVector = when (status) {
    FleetStatus.AVAILABLE -> Icons.Default.CheckCircle
    FleetStatus.RESERVED -> Icons.Default.EventAvailable
    FleetStatus.ON_RENT -> Icons.Default.Key
    FleetStatus.OVERDUE -> Icons.Default.Warning
    FleetStatus.IN_MAINTENANCE -> Icons.Default.Build
    FleetStatus.NOT_READY -> Icons.Default.Block
}

/** Status colours (container, content). Always paired with a text label — never colour alone. */
@Composable
internal fun fleetStatusColors(status: FleetStatus): Pair<Color, Color> =
    toneColors(fleetStatusTone(status)).let { it.container to it.content }

@Composable
internal fun FleetStatusChip(status: FleetStatus, modifier: Modifier = Modifier) {
    StatusPill(stringResource(fleetStatusLabel(status)), fleetStatusTone(status), modifier, icon = fleetStatusIcon(status))
}

internal fun orderStatusTone(status: String): StatusTone = when (status) {
    "quoted", "reserved" -> StatusTone.Info
    "held", "off_rent_requested" -> StatusTone.Warning
    "on_rent" -> StatusTone.Accent
    "returned" -> StatusTone.Success
    "cancelled" -> StatusTone.Danger
    else -> StatusTone.Neutral
}

internal fun orderStatusIcon(status: String): ImageVector = when (status) {
    "draft" -> Icons.Default.Edit
    "quoted" -> Icons.Default.Description
    "held" -> Icons.Default.PauseCircle
    "reserved" -> Icons.Default.EventAvailable
    "on_rent" -> Icons.Default.Key
    "off_rent_requested" -> Icons.AutoMirrored.Filled.AssignmentReturn
    "returned" -> Icons.Default.CheckCircle
    "closed" -> Icons.Default.Lock
    "cancelled" -> Icons.Default.Cancel
    else -> Icons.AutoMirrored.Filled.HelpOutline
}

/** Order status pill (icon + text). */
@Composable
internal fun OrderStatusPill(status: String, modifier: Modifier = Modifier) {
    StatusPill(stringResource(orderStatusLabel(status)), orderStatusTone(status), modifier, icon = orderStatusIcon(status))
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
    val (label, icon, tone) = when {
        failed -> Triple(R.string.rentals_sync_failed_badge, Icons.Default.Warning, StatusTone.Danger)
        localState == RentalRecordEntity.STATE_DRAFT -> Triple(R.string.rentals_sync_draft, Icons.Default.CloudOff, StatusTone.Warning)
        localState == RentalRecordEntity.STATE_CONFLICT -> Triple(R.string.rentals_sync_conflict, Icons.Default.Warning, StatusTone.Danger)
        else -> Triple(R.string.rentals_sync_pending, Icons.Default.Sync, StatusTone.Info)
    }
    StatusPill(stringResource(label), tone, modifier, icon = icon)
}
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
