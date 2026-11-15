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
import com.avago.feature.rentals.marketplace.domain.MktContact
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktMeetupStatus
import com.avago.feature.rentals.marketplace.domain.MktMyRequests
import com.avago.feature.rentals.marketplace.domain.MktOwnerRef
import com.avago.feature.rentals.marketplace.domain.MktRenterBooking
import com.avago.feature.rentals.marketplace.domain.MktRenterMeetup
import com.avago.feature.rentals.marketplace.domain.MktSlot
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
import java.time.ZoneId
import javax.inject.Inject

/** One-shot outcome shown as a snackbar. */
enum class MktRequestsNotice { CounterAccepted, Cancelled }

data class MktRequestsState(
    val loading: Boolean = true,
    val data: MktMyRequests? = null,
    val error: MktError? = null,
    /** A pull-to-refresh failed while data is shown. */
    val refreshFailed: Boolean = false,
    val busyId: String? = null,
    val actionError: MktError? = null,
    val notice: MktRequestsNotice? = null,
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
    MktRequestsContent(state, session != null, onBack, onSignIn, vm::refresh, vm::acceptCounter, vm::cancel, onOpenListing, vm::noticeShown)
}

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
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmCancel by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val accepted = stringResource(R.string.mkt_counter_accepted)
    val cancelled = stringResource(R.string.mkt_request_cancelled)
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(if (it == MktRequestsNotice.CounterAccepted) accepted else cancelled)
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
                                MeetupCard(m, busy = state.busyId == m.id, onAccept = { onAccept(m.id) }, onCancel = { confirmCancel = m.id }, onOpenListing = onOpenListing)
                            }
                        } else {
                            if (data.bookings.isEmpty()) item(key = "no-bookings") { EmptyState(stringResource(R.string.mkt_no_bookings), icon = Icons.Outlined.EventNote) }
                            items(data.bookings, key = { "b:" + it.orderId }) { b -> BookingCard(b, onOpenListing) }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetupCard(m: MktRenterMeetup, busy: Boolean, onAccept: () -> Unit, onCancel: () -> Unit, onOpenListing: (String) -> Unit) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                if (m.status == MktMeetupStatus.COUNTERED && m.counter != null) {
                    Button(onClick = onAccept, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mkt_accept_counter)) }
                }
                if (MktMeetupStatus.renterCanCancel(m.status)) {
                    TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.mkt_cancel_request), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
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
private fun BookingCard(b: MktRenterBooking, onOpenListing: (String) -> Unit) {
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
