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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.network.json.ApiResult
import com.avago.core.ui.components.EmptyStateView
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.crew.R
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.Notice
import com.avago.feature.crew.data.OperatorJob
import com.avago.feature.crew.data.OperatorJobCard
import com.avago.feature.crew.data.OperatorService
import com.avago.feature.crew.data.SiteAnswer
import com.avago.feature.crew.data.SiteChecklist
import com.avago.feature.crew.data.Timesheet
import com.avago.feature.crew.data.toLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OperatorJobsViewModel @Inject constructor(private val ops: OperatorService) : NoticeViewModel() {
    private val _jobs = MutableStateFlow<Load<List<OperatorJob>>>(Load.Loading)
    val jobs: StateFlow<Load<List<OperatorJob>>> = _jobs.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _jobs.value = Load.Loading
            _jobs.value = ops.myJobs().toLoad()
        }
    }
}

@HiltViewModel
class OperatorJobViewModel @Inject constructor(
    private val ops: OperatorService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val accountId: String = checkNotNull(savedState["accountId"])
    val assignmentId: String = checkNotNull(savedState["assignmentId"])
    private val _job = MutableStateFlow<Load<OperatorJobCard>>(Load.Loading)
    val job: StateFlow<Load<OperatorJobCard>> = _job.asStateFlow()
    private val _checklist = MutableStateFlow<SiteChecklist?>(null)
    val checklist: StateFlow<SiteChecklist?> = _checklist.asStateFlow()
    private val _timesheets = MutableStateFlow<List<Timesheet>>(emptyList())
    val timesheets: StateFlow<List<Timesheet>> = _timesheets.asStateFlow()
    private val _serverProblems = MutableStateFlow<List<String>>(emptyList())
    val serverProblems: StateFlow<List<String>> = _serverProblems.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _job.value = Load.Loading
            _job.value = ops.job(accountId, assignmentId).toLoad()
            _checklist.value = ops.checklist(accountId, assignmentId).valueOrNull()
            loadTimesheets()
        }
    }

    private suspend fun loadTimesheets() {
        ops.timesheets(accountId, assignmentId).valueOrNull()?.let { _timesheets.value = it }
    }

    fun setAnswer(key: String, ok: Boolean) = _checklist.update { c -> c?.copy(answers = c.answers + (key to (c.answers[key] ?: SiteAnswer(null)).copy(ok = ok))) }

    fun setNote(key: String, note: String) = _checklist.update { c -> c?.copy(answers = c.answers + (key to (c.answers[key] ?: SiteAnswer(null)).copy(note = note))) }

    fun setLiftPlan(attached: Boolean) = _checklist.update { it?.copy(liftPlanAttached = attached) }

    fun saveChecklist() {
        val c = _checklist.value ?: return
        act(R.string.crew_checklist_saved, { ops.saveChecklist(accountId, assignmentId, c) })
    }

    /** Saves, then completes; the server re-validates and returns `problems` on 422. */
    fun completeChecklist() {
        val c = _checklist.value ?: return
        if (c.localProblems().isNotEmpty()) {
            _serverProblems.value = c.localProblems()
            return
        }
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val saved = ops.saveChecklist(accountId, assignmentId, c)
                val r = if (saved is ApiResult.Err) saved else ops.completeChecklist(accountId, assignmentId)
                when (r) {
                    is ApiResult.Ok -> {
                        _serverProblems.value = emptyList()
                        _checklist.value = ops.checklist(accountId, assignmentId).valueOrNull() ?: c
                        _notices.emit(Notice.Done(R.string.crew_checklist_completed))
                    }
                    is ApiResult.Err -> {
                        val problems = r.error.details?.get("problems")?.let { el ->
                            (el as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        }.orEmpty()
                        _serverProblems.value = problems
                        _notices.emit(Notice.Failed(r.error))
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun startShift() = act(R.string.crew_shift_started, { ops.startTimesheet(accountId, assignmentId) }) { loadTimesheets() }

    fun stopShift(id: String, breakMinutes: Int, notes: String?) =
        act(R.string.crew_shift_stopped, { ops.stopTimesheet(accountId, id, breakMinutes, notes) }) { loadTimesheets() }

    fun submit(id: String) = act(R.string.crew_timesheet_submitted, { ops.submitTimesheet(accountId, id) }) { loadTimesheets() }

    fun sign(id: String, signer: String) {
        if (signer.isBlank()) return
        act(R.string.crew_timesheet_signed, { ops.signTimesheet(accountId, id, signer.trim()) }) { loadTimesheets() }
    }
}

@Composable
fun OperatorJobsScreen(onBack: () -> Unit, onJob: (accountId: String, assignmentId: String) -> Unit, viewModel: OperatorJobsViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    CrewScaffold(stringResource(R.string.crew_operator_jobs), onBack, snackbar) { inner ->
        LoadBox(jobs, viewModel::refresh, inner) { list ->
            if (list.isEmpty()) {
                Column(Modifier.padding(inner)) {
                    EmptyStateView(icon = Icons.Outlined.Engineering, title = stringResource(R.string.crew_no_jobs), message = stringResource(R.string.crew_no_jobs_hint))
                }
            } else {
                LazyColumn(contentPadding = screenPadding(inner)) {
                    items(list, key = { it.id }) { j ->
                        Panel(Modifier.clickable { onJob(j.accountId, j.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(j.orderNumber?.let { "#$it" } ?: stringResource(R.string.crew_job), style = MaterialTheme.typography.titleSmall)
                                    Caption(listOfNotNull(j.accountName, j.locationName).joinToString(" · "))
                                    Caption("${Fmt.dateTime(j.startAt)} – ${Fmt.dateTime(j.endAt)}")
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (j.checklistCompletedAt == null) StatusPill(stringResource(R.string.crew_checklist_due), StatusTone.Warning)
                                    if (j.openTimesheets > 0) StatusPill(stringResource(R.string.crew_on_shift), StatusTone.Info)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun siteKeyLabel(key: String): String = when (key) {
    "site_access" -> stringResource(R.string.crew_site_access)
    "ground_conditions" -> stringResource(R.string.crew_ground_conditions)
    "overhead_hazards" -> stringResource(R.string.crew_overhead_hazards)
    "ppe" -> stringResource(R.string.crew_ppe)
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun problemLabel(code: String): String = when {
    code == "lift_plan_missing" -> stringResource(R.string.crew_problem_lift_plan)
    code.endsWith("_unanswered") -> stringResource(R.string.crew_problem_unanswered, siteKeyLabel(code.removeSuffix("_unanswered")))
    code.endsWith("_failed_without_note") -> stringResource(R.string.crew_problem_note, siteKeyLabel(code.removeSuffix("_failed_without_note")))
    else -> code
}

@Composable
fun OperatorJobScreen(onBack: () -> Unit, viewModel: OperatorJobViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val job by viewModel.job.collectAsStateWithLifecycle()
    val checklist by viewModel.checklist.collectAsStateWithLifecycle()
    val sheets by viewModel.timesheets.collectAsStateWithLifecycle()
    val problems by viewModel.serverProblems.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var stopId by rememberSaveable { mutableStateOf<String?>(null) }
    var signId by rememberSaveable { mutableStateOf<String?>(null) }
    NoticeEffect(viewModel.notices, snackbar)

    CrewScaffold(job.valueOrNull()?.orderNumber?.let { "#$it" } ?: stringResource(R.string.crew_job), onBack, snackbar) { inner ->
        LoadBox(job, viewModel::refresh, inner) { j ->
            LazyColumn(contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("card") {
                    Panel {
                        j.customerName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        j.locationName?.let { Caption(it) }
                        Caption("${Fmt.dateTime(j.startAt)} – ${Fmt.dateTime(j.endAt)}")
                        if (j.requiredCertTypes.isNotEmpty()) Caption(stringResource(R.string.crew_required_certs, j.requiredCertTypes.joinToString()))
                    }
                }
                checklist?.let { c ->
                    item("checklist") {
                        val done = c.completedAt != null
                        Panel(
                            title = stringResource(R.string.crew_site_checklist),
                            trailing = { if (done) StatusPill(stringResource(R.string.crew_completed), StatusTone.Success) },
                        ) {
                            c.keys.forEach { k ->
                                val a = c.answers[k] ?: SiteAnswer(null)
                                Text(siteKeyLabel(k), Modifier.padding(top = 8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(a.ok == true, { viewModel.setAnswer(k, true) }, { Text(stringResource(R.string.crew_ok)) }, enabled = !done)
                                    FilterChip(a.ok == false, { viewModel.setAnswer(k, false) }, { Text(stringResource(R.string.crew_not_ok)) }, enabled = !done)
                                }
                                if (a.ok == false) {
                                    OutlinedTextField(
                                        a.note, { viewModel.setNote(k, it) },
                                        label = { Text(stringResource(R.string.crew_note_required)) },
                                        enabled = !done,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (c.liftPlanRequired) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(c.liftPlanAttached, viewModel::setLiftPlan, enabled = !done)
                                    Text(stringResource(R.string.crew_lift_plan_attached))
                                }
                            }
                            problems.forEach { p -> Caption("• ${problemLabel(p)}") }
                            if (!done) {
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = viewModel::saveChecklist, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                        Text(stringResource(R.string.crew_save))
                                    }
                                    Button(onClick = viewModel::completeChecklist, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                                        Text(stringResource(R.string.crew_complete))
                                    }
                                }
                            }
                        }
                    }
                }
                item("ts_h") {
                    Panel(
                        title = stringResource(R.string.crew_timesheets),
                        trailing = {
                            if (sheets.none { it.canStop } && j.isOperator) {
                                TextButton(onClick = viewModel::startShift, enabled = !busy) { Text(stringResource(R.string.crew_start_shift)) }
                            }
                        },
                    ) {
                        if (sheets.isEmpty()) Caption(stringResource(R.string.crew_no_timesheets))
                        sheets.forEach { t ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.date(t.workDate ?: t.startedAt))
                                    Caption("${Fmt.time(t.startedAt)} – ${t.endedAt?.let { Fmt.time(it) } ?: "…"} · ${Fmt.hours(t.workedHours)}")
                                    t.rejectReason?.let { Caption(stringResource(R.string.crew_rejected_reason, it)) }
                                }
                                TimesheetPill(t.status)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (t.canStop) TextButton(onClick = { stopId = t.id }, enabled = !busy) { Text(stringResource(R.string.crew_stop_shift)) }
                                if (t.canSubmit) TextButton(onClick = { viewModel.submit(t.id) }, enabled = !busy) { Text(stringResource(R.string.crew_submit)) }
                                if (t.canSign) TextButton(onClick = { signId = t.id }, enabled = !busy) { Text(stringResource(R.string.crew_customer_sign)) }
                            }
                        }
                    }
                }
            }
        }
    }

    stopId?.let { id ->
        var breakMin by rememberSaveable { mutableStateOf("0") }
        var notes by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { stopId = null },
            title = { Text(stringResource(R.string.crew_stop_shift)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        breakMin, { breakMin = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.crew_break_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.crew_notes)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.stopShift(id, breakMin.toIntOrNull() ?: 0, notes.ifBlank { null })
                    stopId = null
                }) { Text(stringResource(R.string.crew_stop_shift)) }
            },
            dismissButton = { TextButton(onClick = { stopId = null }) { Text(stringResource(R.string.crew_cancel)) } },
        )
    }
    signId?.let { id ->
        var signer by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { signId = null },
            title = { Text(stringResource(R.string.crew_customer_sign)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption(stringResource(R.string.crew_customer_sign_hint))
                    OutlinedTextField(signer, { signer = it }, label = { Text(stringResource(R.string.crew_signer_name)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sign(id, signer)
                    signId = null
                }, enabled = signer.isNotBlank()) { Text(stringResource(R.string.crew_sign)) }
            },
            dismissButton = { TextButton(onClick = { signId = null }) { Text(stringResource(R.string.crew_cancel)) } },
        )
    }
}

@Composable
private fun TimesheetPill(status: String) {
    val (label, tone) = when (status) {
        "open" -> R.string.crew_ts_open to StatusTone.Info
        "submitted" -> R.string.crew_ts_submitted to StatusTone.Warning
        "signed" -> R.string.crew_ts_signed to StatusTone.Success
        "approved" -> R.string.crew_ts_approved to StatusTone.Success
        "rejected" -> R.string.crew_ts_rejected to StatusTone.Danger
        else -> null to StatusTone.Neutral
    }
    StatusPill(label?.let { stringResource(it) } ?: status, tone)
}
