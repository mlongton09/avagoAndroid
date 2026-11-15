package com.avago.feature.crew.ui

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.ui.components.KpiItem
import com.avago.core.ui.components.KpiTile
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.crew.R
import com.avago.feature.crew.data.CrewService
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.OperatorRecord
import com.avago.feature.crew.data.RecordEvent
import com.avago.feature.crew.data.RecordShare
import com.avago.feature.crew.data.toLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OperatorRecordViewModel @Inject constructor(private val crew: CrewService) : NoticeViewModel() {
    private val _record = MutableStateFlow<Load<OperatorRecord>>(Load.Loading)
    val record: StateFlow<Load<OperatorRecord>> = _record.asStateFlow()
    private val _events = MutableStateFlow<List<RecordEvent>>(emptyList())
    val events: StateFlow<List<RecordEvent>> = _events.asStateFlow()
    private val _nextBefore = MutableStateFlow<String?>(null)
    val hasMore: StateFlow<String?> = _nextBefore.asStateFlow()
    private val _shares = MutableStateFlow<List<RecordShare>>(emptyList())
    val shares: StateFlow<List<RecordShare>> = _shares.asStateFlow()
    private val _created = MutableStateFlow<RecordShare?>(null)
    val created: StateFlow<RecordShare?> = _created.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _record.value = Load.Loading
            _record.value = crew.operatorRecord().toLoad()
            crew.recordEvents().valueOrNull()?.let {
                _events.value = it.items
                _nextBefore.value = it.nextBefore
            }
            _shares.value = crew.shares().valueOrNull().orEmpty()
        }
    }

    fun loadMore() {
        val before = _nextBefore.value ?: return
        _nextBefore.value = null
        viewModelScope.launch {
            crew.recordEvents(before).valueOrNull()?.let { p ->
                _events.update { it + p.items }
                _nextBefore.value = p.nextBefore
            }
        }
    }

    fun createShare(label: String?, days: Int, showEmployers: Boolean) {
        act(R.string.crew_share_created, { crew.createShare(label, days, showEmployers) }) { s ->
            _shares.update { listOf(s) + it }
            _created.value = s
        }
    }

    fun consumeCreated() { _created.value = null }

    fun revoke(id: String) {
        act(R.string.crew_share_revoked, { crew.revokeShare(id) }) {
            _shares.update { l -> l.map { if (it.id == id) it.copy(revoked = true) else it } }
        }
    }
}

@Composable
fun OperatorRecordScreen(onBack: () -> Unit, viewModel: OperatorRecordViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val record by viewModel.record.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val more by viewModel.hasMore.collectAsStateWithLifecycle()
    val shares by viewModel.shares.collectAsStateWithLifecycle()
    val created by viewModel.created.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var createOpen by rememberSaveable { mutableStateOf(false) }
    NoticeEffect(viewModel.notices, snackbar)
    val shareSubject = stringResource(R.string.crew_operator_record)

    created?.let { s ->
        s.url?.let { shareText(ctx, it, shareSubject) }
        viewModel.consumeCreated()
    }

    CrewScaffold(
        stringResource(R.string.crew_operator_record), onBack, snackbar,
        actions = {
            IconButton(onClick = { createOpen = true }, enabled = !busy) { Icon(Icons.Default.Share, stringResource(R.string.crew_share_record)) }
        },
    ) { inner ->
        LoadBox(record, viewModel::refresh, inner) { r ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("kpi") {
                    Panel(title = r.displayName ?: stringResource(R.string.crew_operator_record)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KpiTile(KpiItem(stringResource(R.string.crew_kpi_inspections), r.inspections.toString(), Icons.Default.CheckCircle, StatusTone.Success), Modifier.weight(1f))
                            KpiTile(KpiItem(stringResource(R.string.crew_kpi_hours), Fmt.hours(r.hoursLogged), Icons.Default.Timer, StatusTone.Info), Modifier.weight(1f))
                            KpiTile(KpiItem(stringResource(R.string.crew_kpi_incidents), r.incidents.toString(), Icons.Default.Warning, if (r.incidents > 0) StatusTone.Warning else StatusTone.Neutral), Modifier.weight(1f))
                        }
                        r.firstActivityAt?.let { Caption(stringResource(R.string.crew_active_since, Fmt.date(it)), Modifier.padding(top = 8.dp)) }
                    }
                }
                if (r.employers.isNotEmpty()) {
                    item("emp") {
                        Panel(title = stringResource(R.string.crew_employers)) {
                            r.employers.forEach { e ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.name, Modifier.weight(1f))
                                    if (e.current) StatusPill(stringResource(R.string.crew_current), StatusTone.Info)
                                }
                            }
                        }
                    }
                }
                if (r.certifications.isNotEmpty()) {
                    item("certs") {
                        Panel(title = stringResource(R.string.crew_certifications)) {
                            r.certifications.forEach { c ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(c.certType)
                                        Caption(listOfNotNull(c.issuer, c.expiresOn?.let { Fmt.date(it) }).joinToString(" · "))
                                    }
                                    when (c.expiryStatus) {
                                        "expired" -> StatusPill(stringResource(R.string.crew_expired), StatusTone.Danger)
                                        "expiring" -> StatusPill(stringResource(R.string.crew_expiring), StatusTone.Warning)
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
                item("shares") {
                    Panel(
                        title = stringResource(R.string.crew_share_links),
                        trailing = {
                            IconButton(onClick = { createOpen = true }, enabled = !busy) { Icon(Icons.Default.Add, stringResource(R.string.crew_share_create)) }
                        },
                    ) {
                        if (shares.isEmpty()) Caption(stringResource(R.string.crew_no_shares))
                        shares.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.label ?: stringResource(R.string.crew_share_link))
                                    Caption(
                                        if (s.revoked) stringResource(R.string.crew_revoked)
                                        else stringResource(R.string.crew_expires, Fmt.date(s.expiresAt)),
                                    )
                                }
                                if (!s.revoked) {
                                    s.url?.let { url ->
                                        IconButton(onClick = { shareText(ctx, url, shareSubject) }) { Icon(Icons.Default.Share, stringResource(R.string.crew_share)) }
                                    }
                                    TextButton(onClick = { viewModel.revoke(s.id) }, enabled = !busy) { Text(stringResource(R.string.crew_revoke)) }
                                }
                            }
                        }
                    }
                }
                item("events_h") { Heading(stringResource(R.string.crew_activity)) }
                items(events, key = { it.id }) { e ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(eventLabel(e), style = MaterialTheme.typography.bodyMedium)
                        Caption(listOfNotNull(Fmt.dateTime(e.occurredAt), e.accountName).joinToString(" · "))
                    }
                }
                if (more != null) {
                    item("more") {
                        TextButton(onClick = viewModel::loadMore, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.crew_load_more)) }
                    }
                }
            }
        }
    }
    if (createOpen) {
        CreateShareDialog(
            onDismiss = { createOpen = false },
            onCreate = { label, days, emp ->
                viewModel.createShare(label, days, emp)
                createOpen = false
            },
        )
    }
}

@Composable
private fun eventLabel(e: RecordEvent): String {
    val asset = e.assetName ?: "—"
    return when (e.kind) {
        "inspection" -> stringResource(R.string.crew_ev_inspection, asset, e.result ?: "—")
        "clock" -> stringResource(R.string.crew_ev_clock, asset, Fmt.hours(e.hours))
        "meter" -> stringResource(R.string.crew_ev_meter, asset, listOfNotNull(e.meterValue, e.meterUnit).joinToString(" "))
        "incident", "fault", "defect" -> stringResource(R.string.crew_ev_incident, asset)
        else -> "${e.kind} · $asset"
    }
}

@Composable
private fun CreateShareDialog(onDismiss: () -> Unit, onCreate: (String?, Int, Boolean) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableIntStateOf(30) }
    var employers by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crew_share_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.crew_share_label)) }, singleLine = true)
                Caption(stringResource(R.string.crew_share_valid_for))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { d -> FilterChip(days == d, { days = d }, { Text(stringResource(R.string.crew_days, d)) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(employers, { employers = it })
                    Text(stringResource(R.string.crew_share_show_employers))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(label.ifBlank { null }, days, employers) }) { Text(stringResource(R.string.crew_create_and_share)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crew_cancel)) } },
    )
}
