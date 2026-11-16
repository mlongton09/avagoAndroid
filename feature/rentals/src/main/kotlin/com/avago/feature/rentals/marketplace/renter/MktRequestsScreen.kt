package com.avago.feature.rentals.marketplace.renter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.data.MarketplaceRepository
import com.avago.feature.rentals.marketplace.data.MktResult
import com.avago.feature.rentals.marketplace.data.RenterSession
import com.avago.feature.rentals.marketplace.domain.MktAddress
import com.avago.feature.rentals.marketplace.domain.MktCancelMode
import com.avago.feature.rentals.marketplace.domain.MktChangeDates
import com.avago.feature.rentals.marketplace.domain.MktChangeKind
import com.avago.feature.rentals.marketplace.domain.MktContact
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktHistoryEvent
import com.avago.feature.rentals.marketplace.domain.MktLastChange
import com.avago.feature.rentals.marketplace.domain.MktMeetSlots
import com.avago.feature.rentals.marketplace.domain.MktMeetupStatus
import com.avago.feature.rentals.marketplace.domain.MktMyRequests
import com.avago.feature.rentals.marketplace.domain.MktOwnerRef
import com.avago.feature.rentals.marketplace.domain.MktPendingChange
import com.avago.feature.rentals.marketplace.domain.MktRenterBooking
import com.avago.feature.rentals.marketplace.domain.MktRenterMeetup
import com.avago.feature.rentals.marketplace.domain.MktSlot
import com.avago.feature.rentals.marketplace.domain.MktSlots
import com.avago.feature.rentals.marketplace.ui.MktPreviews
import com.avago.feature.rentals.marketplace.ui.dateRange
import com.avago.feature.rentals.marketplace.ui.meetupIcon
import com.avago.feature.rentals.marketplace.ui.meetupTone
import com.avago.feature.rentals.marketplace.ui.mktErrorText
import com.avago.feature.rentals.marketplace.ui.money
import com.avago.feature.rentals.marketplace.ui.openUri
import com.avago.feature.rentals.marketplace.ui.orderStatusRes
import com.avago.feature.rentals.marketplace.ui.renterStatusRes
import com.avago.feature.rentals.marketplace.ui.slotLabel
import com.avago.feature.rentals.marketplace.ui.timeRange
import com.avago.feature.rentals.ui.EmptyState
import com.avago.feature.rentals.ui.LabelValue
import com.avago.feature.rentals.ui.MinTouch
import com.avago.feature.rentals.ui.RentalsTopBar
import com.avago.feature.rentals.ui.readableWidth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** One-shot outcome shown as a snackbar. */
enum class MktRequestsNotice { CounterAccepted, Cancelled, ChangeSent, BookingCancelled, CancelRequested, Withdrawn }

/** "Change time" sheet of an accepted / pending meet-up (§6.5): the listing's open slots. */
data class MktRescheduleState(
    val meetupId: String,
    val loading: Boolean = true,
    val slots: MktMeetSlots? = null,
    val selected: List<MktSlot> = emptyList(),
    val error: MktError? = null,
) {
    val zone: ZoneId get() = slots?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
}

data class MktRequestsState(
    val loading: Boolean = true,
    val data: MktMyRequests? = null,
    val error: MktError? = null,
    /** A pull-to-refresh failed while data is shown. */
    val refreshFailed: Boolean = false,
    val busyId: String? = null,
    val actionError: MktError? = null,
    val notice: MktRequestsNotice? = null,
    val reschedule: MktRescheduleState? = null,
)

/** "My requests" (§6, §10.5): meet-ups (accept a counter, cancel) and marketplace bookings. */
@HiltViewModel
class MktRequestsViewModel @Inject constructor(private val repo: MarketplaceRepository) : ViewModel() {
    private val _state = MutableStateFlow(MktRequestsState())
    val state: StateFlow<MktRequestsState> = _state.asStateFlow()
    val session: StateFlow<RenterSession?> = repo.session

    init { if (repo.session.value != null) refresh() else _state.update { it.copy(loading = false) } }

    fun refresh() {
        if (repo.session.value == null) return
        _state.update { it.copy(loading = true, error = null, refreshFailed = false) }
        viewModelScope.launch {
            when (val r = repo.myRequests()) {
                is MktResult.Ok -> _state.update { it.copy(loading = false, data = r.value) }
                is MktResult.Err -> _state.update {
                    if (it.data != null && !r.error.needsSignIn) it.copy(loading = false, refreshFailed = true)
                    else it.copy(loading = false, error = r.error, data = if (r.error.needsSignIn) null else it.data)
                }
            }
        }
    }

    fun acceptCounter(id: String) = act(id, MktRequestsNotice.CounterAccepted) { repo.acceptCounter(id) }
    fun cancel(id: String) = act(id, MktRequestsNotice.Cancelled) { repo.cancelMeetup(id) }
    fun noticeShown() = _state.update { it.copy(notice = null) }

    fun openReschedule(m: MktRenterMeetup) {
        val listingId = m.listingId ?: return
        _state.update { it.copy(reschedule = MktRescheduleState(m.id), actionError = null) }
        viewModelScope.launch {
            val r = repo.meetSlots(listingId, RESCHEDULE_DAYS, null)
            _state.update { s ->
                val rs = s.reschedule?.takeIf { it.meetupId == m.id } ?: return@update s
                s.copy(
                    reschedule = when (r) {
                        is MktResult.Ok -> rs.copy(loading = false, slots = r.value)
                        is MktResult.Err -> rs.copy(loading = false, error = r.error)
                    },
                )
            }
        }
    }

    fun toggleRescheduleSlot(slot: MktSlot) = _state.update { s ->
        s.copy(reschedule = s.reschedule?.let { it.copy(selected = MktSlots.toggle(it.selected, slot), error = null) })
    }

    fun closeReschedule() = _state.update { it.copy(reschedule = null) }

    fun submitReschedule(message: String) {
        val rs = _state.value.reschedule ?: return
        MktSlots.validate(rs.selected, 0, Instant.now())?.let { code ->
            _state.update { it.copy(reschedule = rs.copy(error = MktError.ofCode(code))) }
            return
        }
        perform(rs.meetupId, MktRequestsNotice.ChangeSent, closeSheet = true) {
            repo.rescheduleMeetup(rs.meetupId, rs.selected, message.trim().ifBlank { null })
        }
    }

    /** "Change dates" of a pending booking: keeps the booking's times of day. */
    fun changeDates(b: MktRenterBooking, first: LocalDate, last: LocalDate, message: String, zone: ZoneId = ZoneId.systemDefault()) {
        val (start, end) = MktChangeDates.window(first, last, b.start, b.end, zone)
        MktChangeDates.validate(start, end, b.start, b.end, null, null, Instant.now())?.let { code ->
            _state.update { it.copy(actionError = MktError.ofCode(code)) }
            return
        }
        perform(b.orderId, MktRequestsNotice.ChangeSent) { repo.changeBookingDates(b.orderId, start, end, message.trim().ifBlank { null }) }
    }

    fun cancelBooking(b: MktRenterBooking, reason: String) {
        val notice = if (b.cancelMode == MktCancelMode.FREE) MktRequestsNotice.BookingCancelled else MktRequestsNotice.CancelRequested
        perform(b.orderId, notice) { repo.cancelBooking(b.orderId, reason.trim().ifBlank { null }) }
    }

    fun withdraw(changeId: String) = perform(changeId, MktRequestsNotice.Withdrawn) { repo.withdrawChange(changeId) }

    private fun perform(id: String, notice: MktRequestsNotice, closeSheet: Boolean = false, call: suspend () -> MktResult<*>) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, actionError = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is MktResult.Ok -> {
                    _state.update { it.copy(busyId = null, notice = notice, reschedule = if (closeSheet) null else it.reschedule) }
                    refresh()
                }
                is MktResult.Err -> {
                    _state.update { s ->
                        val rs = s.reschedule
                        if (closeSheet && rs != null) s.copy(busyId = null, reschedule = rs.copy(error = r.error))
                        else s.copy(busyId = null, actionError = r.error)
                    }
                    if (r.error == MktError.InvalidState || r.error == MktError.ChangePending) refresh()
                }
            }
        }
    }

    private companion object {
        const val RESCHEDULE_DAYS = 14
    }

    private fun act(id: String, notice: MktRequestsNotice, call: suspend () -> MktResult<MktRenterMeetup?>) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, actionError = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is MktResult.Ok -> {
                    _state.update { s ->
                        val updated = r.value
                        val data = s.data?.let { d -> if (updated == null) d else d.copy(meetups = d.meetups.map { if (it.id == id) updated else it }) }
                        s.copy(busyId = null, data = data, notice = notice)
                    }
                    refresh()
                }
                is MktResult.Err -> {
                    _state.update { it.copy(busyId = null, actionError = r.error) }
                    // The request changed on the owner side (409 invalid_state): show the current status.
                    if (r.error == MktError.InvalidState || r.error == MktError.SlotPassed) refresh()
                }
            }
        }
    }
}

@Composable
fun MktRequestsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenListing: (String) -> Unit,
    vm: MktRequestsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    LaunchedEffect(session != null) { if (session != null && state.data == null && !state.loading) vm.refresh() }
    MktRequestsContent(
        state, session != null, onBack, onSignIn, vm::refresh, vm::acceptCounter, vm::cancel, onOpenListing, vm::noticeShown,
        MktChangeActions(
            onReschedule = vm::openReschedule, onToggleSlot = vm::toggleRescheduleSlot, onCloseReschedule = vm::closeReschedule,
            onSubmitReschedule = vm::submitReschedule, onChangeDates = { b, a, z, msg -> vm.changeDates(b, a, z, msg) },
            onCancelBooking = vm::cancelBooking, onWithdraw = vm::withdraw,
        ),
    )
}

/** Renter change / cancellation callbacks (§6.5). */
internal class MktChangeActions(
    val onReschedule: (MktRenterMeetup) -> Unit = {},
    val onToggleSlot: (MktSlot) -> Unit = {},
    val onCloseReschedule: () -> Unit = {},
    val onSubmitReschedule: (String) -> Unit = {},
    val onChangeDates: (MktRenterBooking, LocalDate, LocalDate, String) -> Unit = { _, _, _, _ -> },
    val onCancelBooking: (MktRenterBooking, String) -> Unit = { _, _ -> },
    val onWithdraw: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MktRequestsContent(
    state: MktRequestsState,
    signedIn: Boolean,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onAccept: (String) -> Unit,
    onCancel: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    onNoticeShown: () -> Unit,
    changes: MktChangeActions = MktChangeActions(),
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmCancel by rememberSaveable { mutableStateOf<String?>(null) }
    var changeDatesOf by rememberSaveable { mutableStateOf<String?>(null) }
    var cancelBookingOf by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val notices = mapOf(
        MktRequestsNotice.CounterAccepted to stringResource(R.string.mkt_counter_accepted),
        MktRequestsNotice.Cancelled to stringResource(R.string.mkt_request_cancelled),
        MktRequestsNotice.ChangeSent to stringResource(R.string.mkt_change_sent),
        MktRequestsNotice.BookingCancelled to stringResource(R.string.mkt_booking_cancelled),
        MktRequestsNotice.CancelRequested to stringResource(R.string.mkt_cancel_requested),
        MktRequestsNotice.Withdrawn to stringResource(R.string.mkt_change_withdrawn),
    )
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(notices.getValue(it))
            onNoticeShown()
        }
    }
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.mkt_my_requests), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val data = state.data
            when {
                !signedIn -> SignInPrompt(stringResource(R.string.mkt_sign_in_requests), onSignIn)
                data == null && state.loading -> SkeletonList(rows = 4)
                data == null -> EmptyState(
                    mktErrorText(state.error ?: MktError.Generic), icon = Icons.Outlined.EventNote,
                    actionLabel = stringResource(R.string.mkt_retry), onAction = onRefresh,
                )
                else -> PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "tabs") {
                            SingleChoiceSegmentedButtonRow(Modifier.readableWidth().padding(AvagoSpacing.lg)) {
                                listOf(R.string.mkt_meetups_heading, R.string.mkt_bookings_heading).forEachIndexed { i, label ->
                                    SegmentedButton(
                                        selected = tab == i, onClick = { tab = i },
                                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                                        modifier = Modifier.heightIn(min = MinTouch),
                                    ) { Text(stringResource(label)) }
                                }
                            }
                        }
                        if (state.refreshFailed) {
                            item(key = "refresh-failed") {
                                ErrorBanner(
                                    stringResource(R.string.rentals_refresh_failed), modifier = Modifier.padding(horizontal = AvagoSpacing.lg),
                                    onRetry = onRefresh, retryLabel = stringResource(R.string.rentals_retry), tone = StatusTone.Warning,
                                )
                            }
                        }
                        state.actionError?.let { e ->
                            item(key = "action-error") { ErrorBanner(mktErrorText(e), modifier = Modifier.padding(horizontal = AvagoSpacing.lg)) }
                        }
                        if (tab == 0) {
                            if (data.meetups.isEmpty()) item(key = "no-meetups") { EmptyState(stringResource(R.string.mkt_no_meetups), icon = Icons.Outlined.EventNote) }
                            items(data.meetups, key = { "m:" + it.id }) { m ->
                                MeetupCard(
                                    m, busy = state.busyId != null, onAccept = { onAccept(m.id) }, onCancel = { confirmCancel = m.id }, onOpenListing = onOpenListing,
                                    onReschedule = { changes.onReschedule(m) }, onWithdraw = changes.onWithdraw,
                                )
                            }
                        } else {
                            if (data.bookings.isEmpty()) item(key = "no-bookings") { EmptyState(stringResource(R.string.mkt_no_bookings), icon = Icons.Outlined.EventNote) }
                            items(data.bookings, key = { "b:" + it.orderId }) { b ->
                                BookingCard(
                                    b, onOpenListing, busy = state.busyId != null,
                                    onChangeDates = { changeDatesOf = b.orderId }, onCancel = { cancelBookingOf = b.orderId }, onWithdraw = changes.onWithdraw,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    confirmCancel?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text(stringResource(R.string.mkt_cancel_request_title)) },
            text = { Text(stringResource(R.string.mkt_cancel_request_body)) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = null; onCancel(id) }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.mkt_cancel_request), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = null }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_keep)) } },
        )
    }
    state.reschedule?.let { rs -> RescheduleDialog(rs, busy = state.busyId != null, changes) }
    val bookings = state.data?.bookings.orEmpty()
    changeDatesOf?.let { id -> bookings.firstOrNull { it.orderId == id } }?.let { b ->
        ChangeDatesDialog(b, onDismiss = { changeDatesOf = null }, onSubmit = { a, z, msg -> changeDatesOf = null; changes.onChangeDates(b, a, z, msg) })
    }
    cancelBookingOf?.let { id -> bookings.firstOrNull { it.orderId == id } }?.let { b ->
        CancelBookingDialog(b, onDismiss = { cancelBookingOf = null }, onConfirm = { reason -> cancelBookingOf = null; changes.onCancelBooking(b, reason) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RescheduleDialog(rs: MktRescheduleState, busy: Boolean, changes: MktChangeActions) {
    var message by rememberSaveable(rs.meetupId) { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val zone = rs.zone
    AlertDialog(
        onDismissRequest = changes.onCloseReschedule,
        title = { Text(stringResource(R.string.mkt_change_time)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                item(key = "intro") {
                    Text(stringResource(R.string.mkt_change_time_body, MktSlots.MAX, zone.id), style = MaterialTheme.typography.bodyMedium)
                }
                rs.error?.let { e -> item(key = "err") { ErrorBanner(mktErrorText(e)) } }
                val slots = rs.slots
                when {
                    rs.loading -> item(key = "loading") { CircularProgressIndicator(Modifier.size(24.dp)) }
                    slots == null -> Unit
                    slots.slots.isEmpty() -> item(key = "none") { Text(stringResource(R.string.mkt_no_slots)) }
                    else -> MktSlots.groupByDay(slots.slots, zone).forEach { (day, list) ->
                        item(key = "d:$day") {
                            Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(day), style = MaterialTheme.typography.labelLarge)
                        }
                        item(key = "s:$day") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                                list.forEach { s ->
                                    val on = rs.selected.any { it.start == s.start }
                                    FilterChip(
                                        selected = on, onClick = { changes.onToggleSlot(s) },
                                        enabled = on || rs.selected.size < MktSlots.MAX,
                                        label = { Text(timeRange(s, zone, locale)) },
                                        modifier = Modifier.heightIn(min = MinTouch),
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "msg") {
                    OutlinedTextField(
                        value = message, onValueChange = { message = it.take(MktSlots.MESSAGE_LIMIT) },
                        label = { Text(stringResource(R.string.mkt_change_message)) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { changes.onSubmitReschedule(message) }, enabled = !busy && rs.selected.isNotEmpty(),
                modifier = Modifier.heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.mkt_send_change)) }
        },
        dismissButton = { TextButton(onClick = changes.onCloseReschedule, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_cancel)) } },
    )
}

@Composable
private fun ChangeDatesDialog(b: MktRenterBooking, onDismiss: () -> Unit, onSubmit: (LocalDate, LocalDate, String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val current = if (b.start != null && b.end != null) MktChangeDates.days(b.start, b.end, zone) else null
    var first by rememberSaveable(b.orderId) { mutableStateOf(current?.first) }
    var last by rememberSaveable(b.orderId) { mutableStateOf(current?.second) }
    var message by rememberSaveable(b.orderId) { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mkt_change_dates)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                Text(stringResource(R.string.mkt_change_dates_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                    Text(first?.let { dayRange(it, last) } ?: stringResource(R.string.mkt_pick_dates))
                }
                OutlinedTextField(
                    value = message, onValueChange = { message = it.take(MktSlots.MESSAGE_LIMIT) },
                    label = { Text(stringResource(R.string.mkt_change_message)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val a = first
            TextButton(
                onClick = { if (a != null) onSubmit(a, last ?: a, message) }, enabled = a != null,
                modifier = Modifier.heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.mkt_send_change)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_cancel)) } },
    )
    if (picking) DateRangeDialog(first, last, onDismiss = { picking = false }, onPick = { a, z -> first = a; last = z ?: a; picking = false })
}

@Composable
private fun CancelBookingDialog(b: MktRenterBooking, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable(b.orderId) { mutableStateOf("") }
    val free = b.cancelMode == MktCancelMode.FREE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (free) R.string.mkt_cancel_booking_title else R.string.mkt_request_cancel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                Text(stringResource(if (free) R.string.mkt_cancel_booking_free else R.string.mkt_cancel_booking_request), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it.take(MktSlots.MESSAGE_LIMIT) },
                    label = { Text(stringResource(R.string.mkt_cancel_reason)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(if (free) R.string.mkt_cancel_booking else R.string.mkt_request_cancel), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_keep)) } },
    )
}

/** Open change request + the owner's last answer (§6.5). */
@Composable
private fun ChangeNotice(pending: MktPendingChange?, last: MktLastChange?, busy: Boolean, onWithdraw: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    if (pending != null) {
        val what = when {
            pending.kind == MktChangeKind.BOOKING_CANCEL -> stringResource(R.string.mkt_change_pending_cancel)
            pending.start != null && pending.end != null -> stringResource(R.string.mkt_change_pending_dates, dateRange(pending.start, pending.end, zone))
            else -> stringResource(R.string.mkt_change_pending_time, pending.proposed.joinToString("; ") { slotLabel(it, zone, locale) })
        }
        StatusPill(stringResource(R.string.mkt_change_waiting), StatusTone.Warning)
        Text(what, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onWithdraw(pending.id) }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
            Text(stringResource(R.string.mkt_withdraw_change))
        }
    } else if (last != null && last.status == "declined") {
        Text(stringResource(R.string.mkt_change_declined), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        last.ownerMessage?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
    }
}

/** Collapsible request history (§6.5). */
@Composable
private fun HistoryList(history: List<MktHistoryEvent>) {
    if (history.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(zone)
    TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = MinTouch)) {
        Text(stringResource(if (open) R.string.mkt_hide_history else R.string.mkt_show_history, history.size))
    }
    if (open) {
        history.forEach { e ->
            val label = historyRes(e.action)?.let { stringResource(it) }
                ?: e.status?.let { s -> orderStatusRes(s)?.let { stringResource(it) } ?: s }
                ?: e.action.replace('_', ' ')
            Text(listOfNotNull(e.at?.let(fmt::format), label).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun historyRes(action: String): Int? = when (action) {
    "requested", "created" -> R.string.mkt_hist_requested
    "countered" -> R.string.mkt_hist_countered
    "accepted", "counter_accepted" -> R.string.mkt_hist_accepted
    "declined" -> R.string.mkt_hist_declined
    "cancelled" -> R.string.mkt_hist_cancelled
    "reschedule_requested", "dates_requested" -> R.string.mkt_hist_change_requested
    "cancel_requested" -> R.string.mkt_hist_cancel_requested
    "rescheduled", "reschedule_accepted", "dates_accepted", "change_accepted", "cancel_accepted" -> R.string.mkt_hist_change_accepted
    "reschedule_declined", "dates_declined", "change_declined", "cancel_declined" -> R.string.mkt_hist_change_declined
    "change_withdrawn" -> R.string.mkt_hist_change_withdrawn
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetupCard(
    m: MktRenterMeetup,
    busy: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onOpenListing: (String) -> Unit,
    onReschedule: () -> Unit = {},
    onWithdraw: (String) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    Card(
        onClick = { m.listingId?.let(onOpenListing) },
        enabled = m.listingId != null,
        modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
    ) {
        Column(Modifier.padding(AvagoSpacing.md), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
            Text(m.title(locale), style = MaterialTheme.typography.titleMedium)
            Text(m.owner.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(stringResource(renterStatusRes(m.status)), meetupTone(m.status), icon = meetupIcon(m.status))
            when {
                m.accepted != null -> Text(stringResource(R.string.mkt_meet_at) + " " + slotLabel(m.accepted, zone, locale), style = MaterialTheme.typography.bodyMedium)
                m.counter != null -> Text(stringResource(R.string.mkt_owner_suggests) + " " + slotLabel(m.counter, zone, locale), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Text(stringResource(R.string.mkt_you_proposed), style = MaterialTheme.typography.labelLarge)
                    m.proposed.forEach { Text("• " + slotLabel(it, zone, locale), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (m.rentalStart != null && m.rentalEnd != null) {
                Text(stringResource(R.string.mkt_inbox_rental_period, periodText(m.rentalStart, m.rentalEnd, zone)), style = MaterialTheme.typography.bodySmall)
            }
            m.ownerMessage?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
            if (m.orderId != null) Text(stringResource(R.string.mkt_reservation_linked), style = MaterialTheme.typography.bodySmall)
            if (m.status == MktMeetupStatus.ACCEPTED) {
                Text(stringResource(R.string.mkt_exact_address), style = MaterialTheme.typography.labelLarge)
                Text(m.address?.oneLine?.takeIf { it.isNotBlank() } ?: m.area?.label.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                ContactActions(m.contact, m.address)
            } else if (m.address == null && m.area != null) {
                Text(m.area.label + " · " + stringResource(R.string.mkt_address_after_confirm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ChangeNotice(m.pendingChange, m.lastChange, busy, onWithdraw)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                if (m.status == MktMeetupStatus.COUNTERED && m.counter != null) {
                    Button(onClick = onAccept, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_accept_counter)) }
                }
                if (m.canReschedule && m.pendingChange == null && m.listingId != null) {
                    OutlinedButton(onClick = onReschedule, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_change_time)) }
                }
                if (m.canCancel) {
                    TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.mkt_cancel_request), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HistoryList(m.history)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContactActions(contact: MktContact?, address: MktAddress?) {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
        contact?.phone?.let { p ->
            OutlinedButton(onClick = { context.openUri("tel:" + p.filter { it.isDigit() || it == '+' }) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Icon(Icons.Outlined.Call, contentDescription = null); Text(" " + stringResource(R.string.mkt_call))
            }
        }
        contact?.email?.let { e ->
            OutlinedButton(onClick = { context.openUri("mailto:$e") }, modifier = Modifier.heightIn(min = MinTouch)) {
                Icon(Icons.Outlined.Email, contentDescription = null); Text(" " + stringResource(R.string.mkt_email_owner))
            }
        }
        address?.let { a ->
            OutlinedButton(onClick = { context.openUri(a.geoUri()) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Icon(Icons.Outlined.Directions, contentDescription = null); Text(" " + stringResource(R.string.mkt_directions))
            }
        }
    }
}

@Composable
private fun BookingCard(
    b: MktRenterBooking,
    onOpenListing: (String) -> Unit,
    busy: Boolean = false,
    onChangeDates: () -> Unit = {},
    onCancel: () -> Unit = {},
    onWithdraw: (String) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    Card(
        onClick = { b.listingId?.let(onOpenListing) },
        enabled = b.listingId != null,
        modifier = Modifier.readableWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
    ) {
        Column(Modifier.padding(AvagoSpacing.md), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
            Text(b.title(locale), style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(b.owner.name, b.reference).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(orderStatusRes(b.status)?.let { stringResource(it) } ?: b.status, StatusTone.Info)
            if (b.start != null && b.end != null) Text(dateRange(b.start, b.end, zone), style = MaterialTheme.typography.bodyMedium)
            b.totalMinor?.let { LabelValue(stringResource(R.string.mkt_total), money(it, b.currency), Modifier.padding(horizontal = AvagoSpacing.xs)) }
            if (b.address != null || b.contact != null) ContactActions(b.contact, b.address)
            ChangeNotice(b.pendingChange, b.lastChange, busy, onWithdraw)
            if (b.pendingChange == null && (b.canChangeDates || b.cancelMode != null)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    if (b.canChangeDates) {
                        OutlinedButton(onClick = onChangeDates, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_change_dates)) }
                    }
                    if (b.cancelMode != null) {
                        TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                            Text(
                                stringResource(if (b.cancelMode == MktCancelMode.FREE) R.string.mkt_cancel_booking else R.string.mkt_request_cancel),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            HistoryList(b.history)
        }
    }
}

/** Date-only "1 Dec 2026 – 4 Dec 2026" for an [a, b) rental period. */
internal fun periodText(a: Instant, b: Instant, zone: ZoneId): String {
    val first = a.atZone(zone).toLocalDate()
    val lastExclusive = b.atZone(zone).toLocalDate()
    val last = if (b.atZone(zone).toLocalTime() == java.time.LocalTime.MIDNIGHT && lastExclusive.isAfter(first)) lastExclusive.minusDays(1) else lastExclusive
    return dayRange(first, last)
}

@MktPreviews
@Composable
private fun MktRequestsPreview() {
    AvagoTheme {
        val now = Instant.now().plus(Duration.ofDays(2))
        val slot = MktSlot(now, now.plus(Duration.ofMinutes(30)))
        MktRequestsContent(
            MktRequestsState(
                loading = false,
                data = MktMyRequests(
                    listOf(
                        MktRenterMeetup(
                            "m1", MktMeetupStatus.COUNTERED, "l1", "Utility trailer", emptyMap(), MktOwnerRef("acme", "Cariboo Rentals"), 1,
                            listOf(slot), slot.copy(start = slot.start.plusSeconds(3600), end = slot.end.plusSeconds(3600)), null, null, null,
                            "Is it free Saturday?", "Can we do an hour later?", null, null, null, null, null, null,
                        ),
                    ),
                    emptyList(),
                ),
            ),
            signedIn = true, onBack = {}, onSignIn = {}, onRefresh = {}, onAccept = {}, onCancel = {}, onOpenListing = {}, onNoticeShown = {},
        )
    }
}
