package com.avago.feature.rentals.marketplace.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktOwnerMeetup
import com.avago.feature.rentals.marketplace.renter.periodText
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.meetupIcon
import com.avago.feature.rentals.marketplace.ui.meetupTone
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.marketplace.ui.openUri
import com.avago.feature.rentals.marketplace.ui.ownerStatusRes
import com.avago.feature.rentals.marketplace.ui.slotLabel
import com.avago.feature.rentals.ui.DateTimeField
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.readableWidth
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Which action dialog is open. */
private enum class MeetDialog { Accept, Counter, Decline, Block }

@Composable
fun MktMeetupDetailScreen(
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    vm: MktMeetupDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    MktMeetupDetailContent(
        state, onBack, onOpenOrder, vm::load,
        MktMeetupActions(vm::slotOpen, vm::accept, vm::counter, vm::decline, vm::block, vm::clearError, vm::noticeShown),
    )
}

class MktMeetupActions(
    val slotOpen: (Int) -> Boolean,
    val accept: (Int, String, Boolean) -> Unit,
    /** Returns false when local validation failed (the dialog stays open). */
    val counter: (Instant?, Instant?, String) -> Boolean,
    val decline: (String) -> Unit,
    val block: (String) -> Unit,
    val clearError: () -> Unit,
    val noticeShown: () -> Unit,
)

@Composable
internal fun MktMeetupDetailContent(
    state: MktMeetupDetailState,
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onRetry: () -> Unit,
    actions: MktMeetupActions,
) {
    var dialog by rememberSaveable { mutableStateOf<MeetDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val texts = mapOf(
        MktMeetupNotice.Accepted to stringResource(R.string.mkt_inbox_accepted_toast),
        MktMeetupNotice.Countered to stringResource(R.string.mkt_inbox_countered_toast),
        MktMeetupNotice.Declined to stringResource(R.string.mkt_inbox_declined_toast),
        MktMeetupNotice.Blocked to stringResource(R.string.mkt_inbox_blocked_toast),
        MktMeetupNotice.Reloaded to stringResource(R.string.mkt_admin_err_version_conflict),
    )
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(texts.getValue(it))
            actions.noticeShown()
        }
    }
    val m = state.meetup
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.mkt_meetup_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (m != null && state.allowed && state.canAnswer) {
                BottomActionBar {
                    OutlinedButton(
                        onClick = { actions.clearError(); dialog = MeetDialog.Counter }, enabled = !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.mkt_inbox_suggest), maxLines = 2) }
                    Button(
                        onClick = { actions.clearError(); dialog = MeetDialog.Accept }, enabled = !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.mkt_inbox_accept)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.allowed -> NoPermission(stringResource(R.string.mkt_inbox_no_permission))
                m == null && state.loading -> SkeletonList(rows = 4)
                m == null -> EmptyState(
                    mktErrorText(state.loadError ?: MktError.Generic, owner = true), icon = Icons.AutoMirrored.Outlined.EventNote,
                    actionLabel = stringResource(R.string.mkt_retry), onAction = onRetry,
                )
                else -> MeetupBody(
                    m, state, onOpenOrder,
                    onDecline = { actions.clearError(); dialog = MeetDialog.Decline },
                    onBlock = { actions.clearError(); dialog = MeetDialog.Block },
                )
            }
        }
    }
    if (m != null) {
        when (dialog) {
            MeetDialog.Accept -> AcceptDialog(m, state.error, actions.slotOpen, onDismiss = { dialog = null }) { i, msg, res ->
                actions.accept(i, msg, res); dialog = null
            }
            MeetDialog.Counter -> CounterDialog(m, state.error, onDismiss = { dialog = null }) { a, b, msg ->
                if (actions.counter(a, b, msg)) dialog = null
            }
            MeetDialog.Decline -> MessageDialog(
                stringResource(R.string.mkt_inbox_decline_title), stringResource(R.string.mkt_inbox_message_optional),
                stringResource(R.string.mkt_inbox_decline_confirm), destructive = true, body = null,
                onDismiss = { dialog = null },
            ) { actions.decline(it); dialog = null }
            MeetDialog.Block -> MessageDialog(
                stringResource(R.string.mkt_inbox_block_title, m.renter.name.ifBlank { stringResource(R.string.mkt_inbox_renter) }),
                stringResource(R.string.mkt_reason_optional), stringResource(R.string.mkt_inbox_block_confirm), destructive = true,
                body = stringResource(R.string.mkt_inbox_block_body), onDismiss = { dialog = null },
            ) { actions.block(it); dialog = null }
            null -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetupBody(m: MktOwnerMeetup, state: MktMeetupDetailState, onOpenOrder: (String) -> Unit, onDecline: () -> Unit, onBlock: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).readableWidth().padding(AvagoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
    ) {
        state.error?.let { ErrorBanner(mktErrorText(it, owner = true)) }
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                Text(m.renter.name.ifBlank { stringResource(R.string.mkt_inbox_renter) }, style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.xs), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    StatusPill(stringResource(ownerStatusRes(m.status)), meetupTone(m.status), icon = meetupIcon(m.status))
                    if (m.renter.emailVerified) StatusPill(stringResource(R.string.mkt_inbox_verified), StatusTone.Success, icon = Icons.Outlined.Verified)
                    if (m.renter.blocked) StatusPill(stringResource(R.string.mkt_inbox_blocked), StatusTone.Danger)
                }
                Text(
                    listOfNotNull(m.className.ifBlank { stringResource(R.string.mkt_inbox_unknown_item) }, m.qty.takeIf { it > 1 }?.let { "× $it" }, m.locationName)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                )
                m.createdAt?.let { Text(stringResource(R.string.mkt_inbox_received, dateTime(it, zone)), style = MaterialTheme.typography.bodySmall) }
                if (state.canAnswer || m.status == com.avago.feature.rentals.marketplace.domain.MktMeetupStatus.COUNTERED) {
                    m.expiresAt?.let { Text(stringResource(R.string.mkt_inbox_expires, dateTime(it, zone)), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        SectionCard(title = stringResource(R.string.mkt_inbox_proposed_times)) {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                m.accepted?.let { Text(stringResource(R.string.mkt_inbox_meeting_at, slotLabel(it, zone, locale)), style = MaterialTheme.typography.titleMedium) }
                m.counter?.let { Text(stringResource(R.string.mkt_inbox_you_suggested, slotLabel(it, zone, locale)), style = MaterialTheme.typography.bodyMedium) }
                m.proposed.forEach { Text("• " + slotLabel(it, zone, locale), style = MaterialTheme.typography.bodyMedium) }
                if (m.rentalStart != null && m.rentalEnd != null) {
                    Text(stringResource(R.string.mkt_inbox_rental_period, periodText(m.rentalStart, m.rentalEnd, zone)), style = MaterialTheme.typography.bodyMedium)
                }
                m.message?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodyLarge) }
                m.ownerMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.mkt_inbox_your_message, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (m.renter.email != null || m.renter.phone != null) {
            SectionCard(title = stringResource(R.string.mkt_renter_contact)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    m.renter.phone?.let { p ->
                        OutlinedButton(onClick = { context.openUri("tel:" + p.filter { it.isDigit() || it == '+' }) }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Icon(Icons.Outlined.Call, contentDescription = null); Text(" " + stringResource(R.string.mkt_call) + " " + p)
                        }
                    }
                    m.renter.email?.let { e ->
                        OutlinedButton(onClick = { context.openUri("mailto:$e") }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Icon(Icons.Outlined.Email, contentDescription = null); Text(" " + e)
                        }
                    }
                }
            }
        }
        m.orderId?.let { id ->
            OutlinedButton(onClick = { onOpenOrder(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                Text(m.orderNumber?.let { stringResource(R.string.mkt_inbox_view_order, it) } ?: stringResource(R.string.mkt_open_order))
            }
        }
        if (state.canDecline || state.canBlock) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                if (state.canDecline) TextButton(onClick = onDecline, enabled = !state.busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.mkt_inbox_decline), color = MaterialTheme.colorScheme.error)
                }
                if (state.canBlock) TextButton(onClick = onBlock, enabled = !state.busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.mkt_inbox_block), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun dateTime(i: Instant, zone: ZoneId): String =
    java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT).withZone(zone).format(i)

@Composable
private fun AcceptDialog(
    m: MktOwnerMeetup,
    error: MktError?,
    slotOpen: (Int) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Boolean) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    var index by rememberSaveable { mutableIntStateOf(m.proposed.indices.firstOrNull { slotOpen(it) } ?: -1) }
    var message by rememberSaveable { mutableStateOf("") }
    var reserve by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mkt_inbox_accept_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                error?.let { ErrorBanner(mktErrorText(it, owner = true)) }
                Text(stringResource(R.string.mkt_inbox_pick_time), style = MaterialTheme.typography.labelLarge)
                m.proposed.forEachIndexed { i, s ->
                    val open = slotOpen(i)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MinTouch)
                            .selectable(selected = index == i, enabled = open, role = Role.RadioButton, onClick = { index = i }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == i, onClick = null, enabled = open)
                        Text(
                            slotLabel(s, zone, locale) + if (open) "" else " (" + stringResource(R.string.mkt_inbox_past) + ")",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = AvagoSpacing.sm),
                            color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    message, { message = it.take(2000) }, label = { Text(stringResource(R.string.mkt_inbox_message_optional)) },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                if (m.hasRentalPeriod) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MinTouch).toggleable(reserve, role = Role.Checkbox, onValueChange = { reserve = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = reserve, onCheckedChange = null)
                        Text(stringResource(R.string.mkt_inbox_create_reservation), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = AvagoSpacing.sm))
                    }
                }
                Text(stringResource(R.string.mkt_inbox_accept_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(index, message, reserve) }, enabled = index >= 0, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.mkt_inbox_accept_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_inbox_cancel)) } },
    )
}

@Composable
private fun CounterDialog(m: MktOwnerMeetup, error: MktError?, onDismiss: () -> Unit, onConfirm: (Instant, Instant, String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val length = m.proposed.firstOrNull()?.let { Duration.between(it.start, it.end) }?.takeIf { !it.isNegative && !it.isZero } ?: Duration.ofMinutes(30)
    val initial = remember { Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.HOURS) }
    var start by rememberSaveable { mutableStateOf(m.proposed.firstOrNull()?.start?.takeIf { it.isAfter(Instant.now()) } ?: initial) }
    var end by rememberSaveable { mutableStateOf(start.plus(length)) }
    var message by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mkt_inbox_suggest_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                error?.let { ErrorBanner(mktErrorText(it, owner = true)) }
                DateTimeField(stringResource(R.string.mkt_inbox_start), start, zone, onChange = { s ->
                    // Keep the meeting length when the start moves.
                    val len = Duration.between(start, end).takeIf { !it.isNegative && !it.isZero } ?: length
                    start = s; end = s.plus(len)
                })
                DateTimeField(stringResource(R.string.mkt_inbox_end), end, zone, onChange = { end = it })
                OutlinedTextField(
                    message, { message = it.take(2000) }, label = { Text(stringResource(R.string.mkt_inbox_message_optional)) },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.mkt_inbox_suggest_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(start, end, message) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.mkt_inbox_suggest_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_inbox_cancel)) } },
    )
}

@Composable
private fun MessageDialog(
    title: String,
    label: String,
    confirm: String,
    destructive: Boolean,
    body: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(text, { text = it.take(2000) }, label = { Text(label) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_inbox_cancel)) } },
    )
}

@MktPreviews
@Composable
private fun MktMeetupDetailPreview() {
    AvagoTheme {
        MktMeetupDetailContent(
            MktMeetupDetailState(loading = false, meetup = previewMeetup()), {}, {}, {},
            MktMeetupActions({ true }, { _, _, _ -> }, { _, _, _ -> true }, {}, {}, {}, {}),
        )
    }
}
