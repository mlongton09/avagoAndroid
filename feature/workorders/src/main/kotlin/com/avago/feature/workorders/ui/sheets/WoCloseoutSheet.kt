package com.avago.feature.workorders.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.ChecklistRowState
import com.avago.feature.workorders.model.WoCloseout
import com.avago.feature.workorders.model.WoCloseoutOptions
import com.avago.feature.workorders.model.WoCode
import com.avago.feature.workorders.model.WoCodeKind
import com.avago.feature.workorders.model.WoSignoff
import com.avago.feature.workorders.model.parseIsoMs
import com.avago.feature.workorders.model.validateCloseout
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LOCAL_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun isoToLocal(iso: String?): String =
    parseIsoMs(iso)?.let { LOCAL_FMT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }.orEmpty()

private fun localToIso(text: String): String? = runCatching {
    LocalDateTime.parse(text.trim(), LOCAL_FMT).atZone(ZoneId.systemDefault()).toInstant().toString()
}.getOrNull()

/**
 * Close-out sheet + completion gate (contract §2). Submit is blocked until [validateCloseout]
 * passes; missing items are listed. Codes come from the service or [com.avago.feature.workorders.model.DEFAULT_WO_CODES].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WoCloseoutSheet(
    initial: WoCloseout,
    codes: Map<WoCodeKind, List<WoCode>>,
    hasAsset: Boolean,
    checklist: List<ChecklistRowState>,
    options: WoCloseoutOptions,
    defaultMeter: String?,
    completedBy: String?,
    isSaving: Boolean,
    onSubmit: (WoCloseout) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var failure by rememberSaveable { mutableStateOf(initial.failureCode.orEmpty()) }
    var cause by rememberSaveable { mutableStateOf(initial.causeCode.orEmpty()) }
    var remedy by rememberSaveable { mutableStateOf(initial.remedyCode.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial.resolutionNotes.orEmpty()) }
    var meter by rememberSaveable {
        mutableStateOf(initial.meterReading?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: defaultMeter.orEmpty())
    }
    var downStart by rememberSaveable { mutableStateOf(isoToLocal(initial.downtimeStart)) }
    var downEnd by rememberSaveable { mutableStateOf(isoToLocal(initial.downtimeEnd)) }
    var signer by rememberSaveable { mutableStateOf(initial.signoff?.name.orEmpty()) }
    var attempted by remember { mutableStateOf(false) }

    fun build(): WoCloseout = initial.copy(
        failureCode = failure.ifBlank { null },
        causeCode = cause.ifBlank { null },
        remedyCode = remedy.ifBlank { null },
        resolutionNotes = notes.trim().ifBlank { null },
        meterReading = meter.trim().replace(',', '.').toDoubleOrNull(),
        downtimeStart = localToIso(downStart),
        downtimeEnd = localToIso(downEnd),
        signoff = signer.trim().takeIf { it.isNotBlank() }?.let {
            WoSignoff(name = it, signedAt = initial.signoff?.takeIf { s -> s.name == it }?.signedAt ?: Instant.now().toString())
        },
        completedBy = completedBy ?: initial.completedBy,
    )

    val validation = validateCloseout(build(), hasAsset, checklist, options)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.wo_closeout_title), style = MaterialTheme.typography.titleLarge)
            CodeDropdown(stringResource(R.string.wo_closeout_failure_code), failure, codes[WoCodeKind.FAILURE].orEmpty()) { failure = it }
            CodeDropdown(stringResource(R.string.wo_closeout_cause_code), cause, codes[WoCodeKind.CAUSE].orEmpty()) { cause = it }
            CodeDropdown(stringResource(R.string.wo_closeout_remedy_code), remedy, codes[WoCodeKind.REMEDY].orEmpty()) { remedy = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.wo_closeout_resolution_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasAsset) {
                OutlinedTextField(
                    value = meter,
                    onValueChange = { meter = it },
                    label = { Text(stringResource(R.string.wo_closeout_meter_reading)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = downStart,
                    onValueChange = { downStart = it },
                    label = { Text(stringResource(R.string.wo_closeout_downtime_start)) },
                    placeholder = { Text("yyyy-MM-dd HH:mm") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = downEnd,
                    onValueChange = { downEnd = it },
                    label = { Text(stringResource(R.string.wo_closeout_downtime_end)) },
                    placeholder = { Text("yyyy-MM-dd HH:mm") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { downEnd = LOCAL_FMT.format(LocalDateTime.now()) }) {
                Text(stringResource(R.string.wo_closeout_downtime_end_now))
            }
            OutlinedTextField(
                value = signer,
                onValueChange = { signer = it },
                label = {
                    Text(
                        if (options.requireSignature) stringResource(R.string.wo_closeout_signoff_required)
                        else stringResource(R.string.wo_closeout_signoff)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (attempted && !validation.ok) {
                Text(
                    stringResource(R.string.wo_closeout_missing, validation.missing.map { closeoutMissingLabel(it) }.joinToString(", ")),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.wo_cancel)) }
                Button(
                    onClick = {
                        attempted = true
                        if (validation.ok) onSubmit(build())
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.wo_closeout_complete))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun closeoutMissingLabel(key: String): String = when (key) {
    "failure_code" -> stringResource(R.string.wo_closeout_failure_code)
    "cause_code" -> stringResource(R.string.wo_closeout_cause_code)
    "remedy_code" -> stringResource(R.string.wo_closeout_remedy_code)
    "resolution_notes" -> stringResource(R.string.wo_closeout_resolution_notes)
    "meter_reading" -> stringResource(R.string.wo_closeout_meter_reading)
    "checklist" -> stringResource(R.string.wo_closeout_missing_checklist)
    "downtime" -> stringResource(R.string.wo_closeout_downtime)
    "signoff" -> stringResource(R.string.wo_closeout_signoff)
    else -> key
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeDropdown(label: String, value: String, codes: List<WoCode>, onSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = codes.firstOrNull { it.code == value }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = current?.let { "${it.code} · ${it.label}" } ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            codes.forEach { c ->
                DropdownMenuItem(text = { Text("${c.code} · ${c.label}") }, onClick = { onSelected(c.code); open = false })
            }
        }
    }
}
