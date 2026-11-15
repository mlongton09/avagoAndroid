package com.avago.feature.crew.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.ui.components.EmptyStateView
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.crew.R
import com.avago.feature.crew.data.CoBalances
import com.avago.feature.crew.data.CoBookings
import com.avago.feature.crew.data.CoGroup
import com.avago.feature.crew.data.CoOwnership
import com.avago.feature.crew.data.CrewService
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.toLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CoOwnershipsViewModel @Inject constructor(private val crew: CrewService) : NoticeViewModel() {
    private val _groups = MutableStateFlow<Load<List<CoOwnership>>>(Load.Loading)
    val groups: StateFlow<Load<List<CoOwnership>>> = _groups.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _groups.value = Load.Loading
            _groups.value = crew.coOwnerships().toLoad()
        }
    }
}

@HiltViewModel
class CoGroupViewModel @Inject constructor(
    private val crew: CrewService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val groupId: String = checkNotNull(savedState["groupId"])
    private val _group = MutableStateFlow<Load<CoGroup>>(Load.Loading)
    val group: StateFlow<Load<CoGroup>> = _group.asStateFlow()
    private val _bookings = MutableStateFlow<Load<CoBookings>>(Load.Loading)
    val bookings: StateFlow<Load<CoBookings>> = _bookings.asStateFlow()
    private val _balances = MutableStateFlow<Load<CoBalances>>(Load.Loading)
    val balances: StateFlow<Load<CoBalances>> = _balances.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _group.value = crew.coGroup(groupId).toLoad()
            loadBookings()
            _balances.value = crew.coBalances(groupId).toLoad()
        }
    }

    private suspend fun loadBookings() {
        val today = LocalDate.now()
        _bookings.value = crew.coBookings(groupId, today.minusDays(7).toString(), today.plusDays(BOOKING_WINDOW_DAYS).toString()).toLoad()
    }

    fun book(startsAt: String, endsAt: String, notes: String?) {
        act(R.string.crew_booking_created, { crew.createCoBooking(groupId, startsAt, endsAt, notes) }) { loadBookings() }
    }

    fun cancel(bookingId: String) {
        act(R.string.crew_booking_cancelled, { crew.cancelCoBooking(groupId, bookingId) }) { loadBookings() }
    }

    companion object {
        const val BOOKING_WINDOW_DAYS = 90L
    }
}

/**
 * Booking window from a picked date range (UTC-midnight millis, as Material date pickers return)
 * and local start/end hours; ISO-8601 with offset. Null when the window is empty or reversed.
 */
internal fun bookingWindow(startUtcMillis: Long, endUtcMillis: Long, startHour: Int, endHour: Int, zone: ZoneId): Pair<String, String>? {
    val d1 = Instant.ofEpochMilli(startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val d2 = Instant.ofEpochMilli(endUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    if (startHour !in 0..23 || endHour !in 0..24) return null
    val s = ZonedDateTime.of(d1, LocalTime.of(startHour, 0), zone)
    val e = if (endHour == 24) ZonedDateTime.of(d2.plusDays(1), LocalTime.MIDNIGHT, zone) else ZonedDateTime.of(d2, LocalTime.of(endHour, 0), zone)
    if (!e.isAfter(s)) return null
    val f = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    return s.format(f) to e.format(f)
}

@Composable
fun CoOwnershipsScreen(onBack: () -> Unit, onGroup: (String) -> Unit, viewModel: CoOwnershipsViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    CrewScaffold(stringResource(R.string.crew_coownership), onBack, snackbar) { inner ->
        LoadBox(groups, viewModel::refresh, inner) { list ->
            if (list.isEmpty()) {
                Column(Modifier.padding(inner)) {
                    EmptyStateView(icon = Icons.Outlined.Groups, title = stringResource(R.string.crew_no_coownership), message = stringResource(R.string.crew_no_coownership_hint))
                }
            } else {
                LazyColumn(contentPadding = screenPadding(inner)) {
                    items(list, key = { it.groupId }) { g ->
                        Panel(Modifier.clickable { onGroup(g.groupId) }) {
                            Text(g.assetName, style = MaterialTheme.typography.titleSmall)
                            Caption(stringResource(R.string.crew_share_members, sharePct(g.shareBp), g.memberCount))
                        }
                    }
                }
            }
        }
    }
}

internal fun sharePct(bp: Int): String = if (bp % 100 == 0) "${bp / 100}%" else "%.2f%%".format(bp / 100.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoGroupScreen(onBack: () -> Unit, viewModel: CoGroupViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val group by viewModel.group.collectAsStateWithLifecycle()
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var bookOpen by rememberSaveable { mutableStateOf(false) }
    var cancelId by rememberSaveable { mutableStateOf<String?>(null) }
    NoticeEffect(viewModel.notices, snackbar)

    CrewScaffold(group.valueOrNull()?.assetName ?: stringResource(R.string.crew_coownership), onBack, snackbar,
        fab = {
            if (tab == 0) {
                FloatingActionButton(onClick = { bookOpen = true }) { Icon(Icons.Default.Add, stringResource(R.string.crew_book)) }
            }
        },
    ) { inner ->
        LoadBox(group, viewModel::refresh, inner) { g ->
            Column(Modifier.padding(top = inner.calculateTopPadding())) {
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.crew_bookings)) })
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.crew_balances)) })
                }
                if (tab == 0) {
                    LoadBox(bookings, viewModel::refresh, androidx.compose.foundation.layout.PaddingValues()) { b ->
                        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                            if (b.blocked > 0) item("blocked") { ListCaption(stringResource(R.string.crew_blocked_by_loans, b.blocked)) }
                            if (b.items.isEmpty()) item("none") { ListCaption(stringResource(R.string.crew_no_bookings)) }
                            items(b.items, key = { it.id }) { bk ->
                                Panel {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(bk.memberName ?: "—", style = MaterialTheme.typography.titleSmall)
                                            Caption("${Fmt.dateTime(bk.startsAt)} – ${Fmt.dateTime(bk.endsAt)}")
                                            bk.notes?.let { Caption(it) }
                                        }
                                        val mine = bk.memberId != null && bk.memberId == g.myMemberId
                                        if (bk.status == "cancelled") {
                                            StatusPill(stringResource(R.string.crew_loan_status_cancelled), StatusTone.Neutral)
                                        } else if ((mine || g.isPrimary) && bk.status != "completed") {
                                            TextButton(onClick = { cancelId = bk.id }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                                Text(stringResource(R.string.crew_cancel))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LoadBox(balances, viewModel::refresh, androidx.compose.foundation.layout.PaddingValues()) { bal ->
                        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                            item("members") {
                                Panel(title = stringResource(R.string.crew_total_costs, Fmt.money(bal.totalMinor, bal.currency))) {
                                    bal.members.forEach { m ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(m.name)
                                                Caption(stringResource(R.string.crew_paid_owed, Fmt.money(m.paidMinor, bal.currency), Fmt.money(m.owedMinor, bal.currency)))
                                            }
                                            val tone = when {
                                                m.netMinor > 0 -> StatusTone.Success
                                                m.netMinor < 0 -> StatusTone.Warning
                                                else -> StatusTone.Neutral
                                            }
                                            StatusPill(Fmt.money(m.netMinor, bal.currency), tone)
                                        }
                                    }
                                }
                            }
                            item("settle") {
                                Panel(title = stringResource(R.string.crew_settle_up)) {
                                    if (bal.settleUp.isEmpty()) Caption(stringResource(R.string.crew_all_settled))
                                    bal.settleUp.forEach { s ->
                                        Text(stringResource(R.string.crew_pays, s.fromName, s.toName, Fmt.money(s.amountMinor, bal.currency)), Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (bookOpen) {
        BookDialog(onDismiss = { bookOpen = false }, onBook = { s, e, n ->
            viewModel.book(s, e, n)
            bookOpen = false
        })
    }
    cancelId?.let { id ->
        AlertDialog(
            onDismissRequest = { cancelId = null },
            title = { Text(stringResource(R.string.crew_cancel_booking_q)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancel(id)
                    cancelId = null
                }) { Text(stringResource(R.string.crew_confirm)) }
            },
            dismissButton = { TextButton(onClick = { cancelId = null }) { Text(stringResource(R.string.crew_keep)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDialog(onDismiss: () -> Unit, onBook: (String, String, String?) -> Unit) {
    val range = rememberDateRangePickerState()
    var pickOpen by remember { mutableStateOf(true) }
    var startHour by rememberSaveable { mutableStateOf("7") }
    var endHour by rememberSaveable { mutableStateOf("18") }
    var notes by rememberSaveable { mutableStateOf("") }
    if (pickOpen) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickOpen = false }, enabled = range.selectedStartDateMillis != null) { Text(stringResource(R.string.crew_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crew_cancel)) } },
        ) {
            DateRangePicker(range, Modifier.weight(1f))
        }
        return
    }
    val s = range.selectedStartDateMillis ?: return
    val e = range.selectedEndDateMillis ?: s
    val window = bookingWindow(s, e, startHour.toIntOrNull() ?: -1, endHour.toIntOrNull() ?: -1, ZoneId.systemDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crew_book)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("${Fmt.date(Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate().toString())} – ${Fmt.date(Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate().toString())}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startHour, { startHour = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.crew_from_hour)) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(endHour, { endHour = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.crew_to_hour)) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (window == null) Caption(stringResource(R.string.crew_invalid_window))
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.crew_notes)) })
            }
        },
        confirmButton = {
            TextButton(onClick = { window?.let { onBook(it.first, it.second, notes.ifBlank { null }) } }, enabled = window != null) {
                Text(stringResource(R.string.crew_book))
            }
        },
        dismissButton = { TextButton(onClick = { pickOpen = true }) { Text(stringResource(R.string.crew_back)) } },
    )
}
