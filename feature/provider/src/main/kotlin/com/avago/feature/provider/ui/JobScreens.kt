package com.avago.feature.provider.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.EmptyStateView
import com.avago.feature.provider.R
import com.avago.feature.provider.data.JobStatus
import com.avago.feature.provider.data.StatusForm

@Composable
fun ProviderJobsScreen(
    onBack: () -> Unit,
    onJob: (String) -> Unit,
    vm: JobsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    NoticeEffect(vm.notices, snackbar)
    ProviderScaffold(title = stringResource(R.string.provider_jobs), onBack = onBack, snackbar = snackbar) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            StatusFilterRow(JobStatus.ALL, filter, vm::setFilter)
            LoadBox(state, vm::refresh, PaddingValues()) { jobs ->
                if (jobs.isEmpty()) {
                    EmptyStateView(Icons.Filled.Build, stringResource(R.string.provider_jobs_empty))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Panel {
                                jobs.forEachIndexed { i, j ->
                                    if (i > 0) CardDivider()
                                    DocRow(
                                        title = j.title?.let { "${j.number} · $it" } ?: j.number,
                                        subtitle = listOfNotNull(j.customerName, j.scheduledFor?.let(Fmt::dateTime)).joinToString(" · ").ifBlank { null },
                                        status = j.status,
                                        trailing = null,
                                    ) { onJob(j.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderJobScreen(
    onBack: () -> Unit,
    onCustomer: (String) -> Unit,
    onInvoice: (String) -> Unit,
    vm: JobDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var statusTo by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = rememberPhotoCapture(vm::addPhoto)
    NoticeEffect(vm.notices, snackbar)
    EventEffect(vm.events, snackbar, onInvoice = onInvoice)
    ProviderScaffold(
        title = state.valueOrNull()?.job?.number ?: stringResource(R.string.provider_job),
        onBack = onBack,
        snackbar = snackbar,
    ) { inner ->
        LoadBox(state, vm::refresh, inner) { d ->
            val job = d.job
            LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("head") {
                    Panel(title = job.title ?: stringResource(R.string.provider_job), trailing = { StatusChip(job.status) }) {
                        job.customerName?.let { name ->
                            DocRow(name, d.customer?.phone ?: d.customer?.email, null, null, job.customerId?.let { id -> { onCustomer(id) } })
                        }
                        d.asset?.let { a ->
                            KeyValue(stringResource(R.string.provider_asset), listOfNotNull(a.name, a.summary).joinToString(" · "))
                            a.vinSerial?.let { KeyValue(stringResource(R.string.provider_vin_serial), it) }
                        }
                        job.scheduledFor?.let { KeyValue(stringResource(R.string.provider_scheduled), Fmt.dateTime(it)) }
                        job.startedAt?.let { KeyValue(stringResource(R.string.provider_started), Fmt.dateTime(it)) }
                        job.completedAt?.let { KeyValue(stringResource(R.string.provider_completed), Fmt.dateTime(it)) }
                        job.labourHours?.let { KeyValue(stringResource(R.string.provider_labour_hours), Fmt.number(it)) }
                        job.meterReading?.let { KeyValue(stringResource(R.string.provider_meter_reading), "${Fmt.number(it)} ${meterUnitLabel(job.meterUnit ?: "hours")}") }
                        job.description?.let { Caption(it, Modifier.padding(top = 4.dp)) }
                    }
                }
                if (job.nextStatuses.isNotEmpty()) {
                    item("status") {
                        Panel(title = stringResource(R.string.provider_update_status)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                job.nextStatuses.forEach { s ->
                                    if (s == JobStatus.CANCELLED) {
                                        OutlinedButton(onClick = { statusTo = s }, enabled = !busy) { Text(statusActionLabel(s)) }
                                    } else {
                                        Button(onClick = { statusTo = s }, enabled = !busy) { Text(statusActionLabel(s)) }
                                    }
                                }
                            }
                        }
                    }
                }
                item("notes") { NotesPanel(job.notes.orEmpty(), busy, vm::saveNotes) }
                item("photos") {
                    Panel(
                        title = stringResource(R.string.provider_photos_n, d.photos.size),
                        trailing = {
                            TextButton(onClick = capture, enabled = job.status != JobStatus.CANCELLED) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                                Text(stringResource(R.string.provider_add_photo), Modifier.padding(start = 6.dp))
                            }
                        },
                    ) {
                        if (uploading > 0) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        if (d.photos.isEmpty()) {
                            Caption(stringResource(R.string.provider_photos_empty))
                        } else {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                d.photos.forEach { p ->
                                    AsyncImage(
                                        model = p.url,
                                        contentDescription = stringResource(R.string.provider_photo_cd),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
                item("lines") { LinesPanel(job.lines, d.lineTotals, d.currency) }
                item("actions") {
                    Panel {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = vm::send, enabled = !busy) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Text(stringResource(R.string.provider_send_customer), Modifier.padding(start = 6.dp))
                            }
                            when {
                                job.invoiceId != null -> OutlinedButton(onClick = { onInvoice(job.invoiceId) }) {
                                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                                    Text(stringResource(R.string.provider_view_invoice), Modifier.padding(start = 6.dp))
                                }
                                job.canInvoice -> Button(onClick = vm::createInvoice, enabled = !busy) {
                                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                                    Text(stringResource(R.string.provider_create_invoice), Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        Caption(stringResource(R.string.provider_send_hint), Modifier.padding(top = 4.dp))
                    }
                }
            }
            statusTo?.let { to ->
                StatusDialog(StatusForm.forJob(job, to), busy, onSubmit = vm::changeStatus, onDismiss = { statusTo = null })
            }
        }
    }
}

@Composable
private fun statusActionLabel(s: String): String = when (s) {
    JobStatus.IN_PROGRESS -> stringResource(R.string.provider_act_start)
    JobStatus.ON_HOLD -> stringResource(R.string.provider_act_hold)
    JobStatus.COMPLETED -> stringResource(R.string.provider_act_complete)
    JobStatus.CANCELLED -> stringResource(R.string.provider_act_cancel)
    else -> statusLabel(s)
}

@Composable
private fun NotesPanel(saved: String, busy: Boolean, onSave: (String) -> Unit) {
    var draft by rememberSaveable(saved) { mutableStateOf(saved) }
    LaunchedEffect(saved) { draft = saved }
    Panel(
        title = stringResource(R.string.provider_notes),
        trailing = {
            TextButton(onClick = { onSave(draft) }, enabled = !busy && draft.trim() != saved.trim()) { Text(stringResource(R.string.provider_save)) }
        },
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(stringResource(R.string.provider_notes_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
