package com.avago.feature.crew.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.EmptyStateView
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.crew.R
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.Machine
import com.avago.feature.crew.data.MeterInput

@Composable
fun CrewHomeScreen(
    onBack: () -> Unit,
    onMachine: (String) -> Unit,
    onScan: () -> Unit,
    onOperatorJobs: () -> Unit,
    onLoans: () -> Unit,
    onCoOwnership: () -> Unit,
    onRecord: () -> Unit,
    viewModel: CrewHomeViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val machines by viewModel.machines.collectAsStateWithLifecycle()
    val clock by viewModel.clock.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices, snackbar)

    CrewScaffold(
        title = stringResource(R.string.crew_title),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, stringResource(R.string.crew_refresh)) }
        },
    ) { inner ->
        LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
            clock?.takeIf { it.open }?.let { s ->
                item("clock") {
                    Panel(title = stringResource(R.string.crew_clocked_in_title)) {
                        Text(stringResource(R.string.crew_clocked_in_since, s.assetName ?: "—", Fmt.time(s.clockInAt)))
                        OutlinedButton(
                            onClick = viewModel::clockOut,
                            enabled = !busy,
                            modifier = Modifier.padding(top = AvagoSpacing.md).heightIn(min = MinTouch),
                        ) { Text(stringResource(R.string.crew_clock_out)) }
                    }
                }
            }
            item("shortcuts") {
                SectionCard {
                    Shortcut(Icons.Default.QrCodeScanner, stringResource(R.string.crew_scan_passport), onScan)
                    Shortcut(Icons.Default.Engineering, stringResource(R.string.crew_operator_jobs), onOperatorJobs)
                    Shortcut(Icons.Default.Handshake, stringResource(R.string.crew_loans), onLoans)
                    Shortcut(Icons.Default.Groups, stringResource(R.string.crew_coownership), onCoOwnership)
                    Shortcut(Icons.Default.Badge, stringResource(R.string.crew_operator_record), onRecord)
                }
            }
            item("machines_h") { Heading(stringResource(R.string.crew_machines_today)) }
            when (val m = machines) {
                is Load.Loading -> item("loading") { ListCaption(stringResource(R.string.crew_loading)) }
                is Load.Failed -> item("err") {
                    LoadBox(m, viewModel::refresh, PaddingValues()) {}
                }
                is Load.Ready -> {
                    if (m.value.items.isEmpty()) {
                        item("empty") {
                            EmptyStateView(
                                icon = Icons.Outlined.Agriculture,
                                title = stringResource(R.string.crew_no_machines),
                                message = stringResource(R.string.crew_no_machines_hint),
                            )
                        }
                    } else {
                        if (m.value.inspectionsPending > 0) {
                            item("pending") {
                                ListCaption(pluralStringResource(R.plurals.crew_inspections_pending, m.value.inspectionsPending, m.value.inspectionsPending))
                            }
                        }
                        items(m.value.items, key = { it.assetId }) { MachineRow(it, onClick = { onMachine(it.assetId) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(onClick = onClick),
    )
}

@Composable
private fun MachineRow(m: Machine, onClick: () -> Unit) {
    Panel(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleSmall)
                m.makeModel?.let { Caption(it) }
                m.lastMeter?.let { Caption(stringResource(R.string.crew_last_meter, it)) }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InspectionPill(m.inspectedTodayResult)
                if (m.openDefects > 0) {
                    StatusPill(pluralStringResource(R.plurals.crew_open_defects, m.openDefects, m.openDefects), StatusTone.Danger)
                }
                if (m.openClockSessionId != null) StatusPill(stringResource(R.string.crew_on_clock), StatusTone.Info)
            }
        }
    }
}

@Composable
internal fun InspectionPill(result: String?) {
    when (result) {
        null -> StatusPill(stringResource(R.string.crew_not_inspected), StatusTone.Warning)
        "pass" -> StatusPill(stringResource(R.string.crew_inspected_pass), StatusTone.Success)
        else -> StatusPill(stringResource(R.string.crew_inspected_fail), StatusTone.Danger)
    }
}

@Composable
fun MachineScreen(
    onBack: () -> Unit,
    onInspect: (String) -> Unit,
    onFault: (String) -> Unit,
    onPassport: () -> Unit,
    viewModel: MachineViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val machine by viewModel.machine.collectAsStateWithLifecycle()
    val clock by viewModel.clock.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var meterOpen by rememberSaveable { mutableStateOf(false) }
    NoticeEffect(viewModel.notices, snackbar)

    CrewScaffold(
        title = machine.valueOrNull()?.name ?: stringResource(R.string.crew_machine),
        onBack = onBack,
        snackbar = snackbar,
    ) { inner ->
        LoadBox(machine, viewModel::refresh, inner) { m ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("status") {
                    Panel(title = stringResource(R.string.crew_today)) {
                        if (m == null) {
                            Caption(stringResource(R.string.crew_machine_not_assigned))
                        } else {
                            InspectionPill(m.inspectedTodayResult)
                            m.lastMeter?.let { Caption(stringResource(R.string.crew_last_meter, it), Modifier.padding(top = 8.dp)) }
                        }
                    }
                }
                item("clock") {
                    Panel(title = stringResource(R.string.crew_clock)) {
                        val here = clock?.let { it.open && it.assetId == viewModel.assetId } == true
                        val elsewhere = clock?.open == true && !here
                        when {
                            here -> Caption(stringResource(R.string.crew_on_clock_since, Fmt.time(clock?.clockInAt)))
                            elsewhere -> Caption(stringResource(R.string.crew_on_clock_elsewhere, clock?.assetName ?: "—"))
                            else -> Caption(stringResource(R.string.crew_off_clock))
                        }
                        Row(Modifier.padding(top = AvagoSpacing.md), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (clock?.open == true) {
                                OutlinedButton(onClick = viewModel::clockOut, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                    Icon(Icons.Default.Timer, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.crew_clock_out))
                                }
                            } else {
                                Button(onClick = viewModel::clockIn, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                    Icon(Icons.Default.Timer, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.crew_clock_in))
                                }
                            }
                        }
                    }
                }
                item("actions") {
                    Panel(title = stringResource(R.string.crew_actions)) {
                        Shortcut(Icons.Default.CheckCircle, stringResource(R.string.crew_daily_inspection)) { onInspect(viewModel.assetId) }
                        Shortcut(Icons.Default.Timer, stringResource(R.string.crew_record_meter)) { meterOpen = true }
                        Shortcut(Icons.Default.Build, stringResource(R.string.crew_report_fault)) { onFault(viewModel.assetId) }
                        Shortcut(Icons.Default.QrCodeScanner, stringResource(R.string.crew_scan_passport), onPassport)
                    }
                }
            }
        }
    }
    if (meterOpen) {
        MeterDialog(
            onDismiss = { meterOpen = false },
            onSave = {
                viewModel.recordMeter(it)
                meterOpen = false
            },
        )
    }
}

/** Parses a user-typed decimal ("1.234,5" or "1234.5"); null when blank or invalid. */
internal fun parseDecimal(s: String): Double? {
    val t = s.trim().replace(" ", "")
    if (t.isEmpty()) return null
    val normalized = when {
        !t.contains(',') -> t
        !t.contains('.') -> t.replace(',', '.')
        t.lastIndexOf(',') > t.lastIndexOf('.') -> t.replace(".", "").replace(',', '.')
        else -> t.replace(",", "")
    }
    return normalized.toDoubleOrNull()?.takeIf { it >= 0 }
}

@Composable
private fun MeterDialog(onDismiss: () -> Unit, onSave: (MeterInput) -> Unit) {
    var meter by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("h") }
    var fuel by rememberSaveable { mutableStateOf("") }
    var charge by rememberSaveable { mutableStateOf("") }
    val input = MeterInput(
        meterValue = parseDecimal(meter),
        meterUnit = unit.takeIf { meter.isNotBlank() },
        fuelQty = parseDecimal(fuel),
        fuelUnit = "l".takeIf { fuel.isNotBlank() },
        chargePct = charge.trim().toIntOrNull()?.coerceIn(0, 100),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crew_record_meter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    meter, { meter = it },
                    label = { Text(stringResource(R.string.crew_meter_value)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("h" to R.string.crew_unit_hours, "km" to R.string.crew_unit_km, "mi" to R.string.crew_unit_mi).forEach { (u, label) ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(stringResource(label)) })
                    }
                }
                OutlinedTextField(
                    fuel, { fuel = it },
                    label = { Text(stringResource(R.string.crew_fuel_litres)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    charge, { charge = it },
                    label = { Text(stringResource(R.string.crew_charge_pct)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(input) }, enabled = input.hasValue) { Text(stringResource(R.string.crew_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crew_cancel)) } },
    )
}

@Composable
fun InspectionScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: InspectionViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val checklist by viewModel.checklist.collectAsStateWithLifecycle()
    val answers by viewModel.answers.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    var notes by rememberSaveable { mutableStateOf("") }
    var meter by rememberSaveable { mutableStateOf("") }
    var photoKey by remember { mutableStateOf<String?>(null) }
    val capture = rememberPhotoCapture { jpeg -> photoKey?.let { viewModel.addPhoto(it, jpeg) } }
    NoticeEffect(viewModel.notices, snackbar)

    result?.let { r ->
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(if (r.result == "pass") R.string.crew_inspection_passed else R.string.crew_inspection_failed)) },
            text = {
                Text(
                    if (r.defects > 0) pluralStringResource(R.plurals.crew_defects_created, r.defects, r.defects)
                    else stringResource(R.string.crew_inspection_saved),
                )
            },
            confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.crew_done)) } },
        )
    }

    CrewScaffold(
        title = checklist.valueOrNull()?.assetName ?: stringResource(R.string.crew_daily_inspection),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            TextButton(onClick = viewModel::markAllPass, enabled = checklist is Load.Ready) {
                Text(stringResource(R.string.crew_all_pass))
            }
        },
    ) { inner ->
        LoadBox(checklist, viewModel::load, inner) { c ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                if (missing.isNotEmpty()) {
                    item("missing") { StatusPill(pluralStringResource(R.plurals.crew_items_unanswered, missing.size, missing.size), StatusTone.Warning) }
                }
                c.items.groupBy { it.section }.forEach { (section, group) ->
                    section?.let { s -> item("s_$s") { Heading(s) } }
                    items(group, key = { it.key }) { item ->
                        val a = answers[item.key] ?: AnswerDraft()
                        Panel(title = item.label) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(a.answered && a.pass == true, { viewModel.answer(item.key, true) }, { Text(stringResource(R.string.crew_pass)) })
                                FilterChip(a.answered && a.pass == false, { viewModel.answer(item.key, false) }, { Text(stringResource(R.string.crew_fail)) })
                                FilterChip(a.answered && a.pass == null, { viewModel.answer(item.key, null) }, { Text(stringResource(R.string.crew_na)) })
                            }
                            if (item.key in missing) Caption(stringResource(R.string.crew_answer_required))
                            if (a.pass == false) {
                                OutlinedTextField(
                                    a.note, { viewModel.note(item.key, it) },
                                    label = { Text(stringResource(R.string.crew_what_is_wrong)) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    c.severities.forEach { s ->
                                        FilterChip(a.severity == s, { viewModel.severity(item.key, s) }, { Text(severityLabel(s)) })
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            photoKey = item.key
                                            capture()
                                        },
                                        modifier = Modifier.heightIn(min = MinTouch),
                                    ) { Text(stringResource(R.string.crew_add_photo)) }
                                    if (a.photoIds.isNotEmpty()) Caption(pluralStringResource(R.plurals.crew_photos, a.photoIds.size, a.photoIds.size))
                                }
                            }
                        }
                    }
                }
                item("notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.crew_notes)) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            meter, { meter = it },
                            label = { Text(stringResource(R.string.crew_meter_value_optional)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.submit(notes.ifBlank { null }, parseDecimal(meter)) },
                            enabled = !busy && uploading == 0,
                            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                        ) { Text(stringResource(if (uploading > 0) R.string.crew_uploading else R.string.crew_submit_inspection)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun severityLabel(s: String): String = when (s) {
    "minor" -> stringResource(R.string.crew_sev_minor)
    "major" -> stringResource(R.string.crew_sev_major)
    "critical" -> stringResource(R.string.crew_sev_critical)
    "low" -> stringResource(R.string.crew_sev_low)
    "medium" -> stringResource(R.string.crew_sev_medium)
    "high" -> stringResource(R.string.crew_sev_high)
    else -> s
}

@Composable
fun FaultScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: FaultViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val photos by viewModel.photoIds.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val sent by viewModel.sent.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var severity by rememberSaveable { mutableStateOf("major") }
    val capture = rememberPhotoCapture(viewModel::addPhoto)
    NoticeEffect(viewModel.notices, snackbar)
    LaunchedEffect(sent) { if (sent) onDone() }

    CrewScaffold(stringResource(R.string.crew_report_fault), onBack, snackbar) { inner ->
        Column(Modifier.padding(screenPadding(inner)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                title, { title = it },
                label = { Text(stringResource(R.string.crew_fault_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                note, { note = it },
                label = { Text(stringResource(R.string.crew_fault_details)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("minor", "major", "critical").forEach { s ->
                    FilterChip(severity == s, { severity = s }, { Text(severityLabel(s)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = capture, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.crew_add_photo))
                }
                Spacer(Modifier.width(12.dp))
                if (photos.isNotEmpty()) Caption(pluralStringResource(R.plurals.crew_photos, photos.size, photos.size))
            }
            Button(
                onClick = { viewModel.submit(title, note.ifBlank { null }, severity) },
                enabled = !busy && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.crew_send_fault)) }
        }
    }
}
