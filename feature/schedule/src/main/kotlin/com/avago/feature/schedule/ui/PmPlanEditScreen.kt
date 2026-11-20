package com.avago.feature.schedule.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetMeter
import com.avago.core.data.domain.PmEndType
import com.avago.core.data.domain.PmIntervalUnit
import com.avago.core.data.domain.PmMeterCondition
import com.avago.core.data.domain.PmTriggerKind
import com.avago.core.data.domain.WorkOrderTemplate
import com.avago.feature.schedule.R
import com.avago.feature.schedule.ui.components.intervalUnitLabel
import com.avago.feature.schedule.ui.components.meterConditionLabel
import com.avago.feature.schedule.ui.components.triggerKindLabel
import com.avago.feature.schedule.util.CalendarFreq
import com.avago.feature.schedule.util.MeterMode
import com.avago.feature.schedule.util.PlanForm
import com.avago.feature.schedule.util.PlanFormError
import com.avago.feature.schedule.util.TriggerForm
import com.avago.feature.schedule.viewmodel.PlanEditResult
import com.avago.feature.schedule.viewmodel.PmPlanEditViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Creates or edits a PM plan and its calendar, interval and meter triggers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmPlanEditScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PmPlanEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val canManage by viewModel.canManage.collectAsStateWithLifecycle()
    val assets by viewModel.assetChoices.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val meters by viewModel.meters.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val failedText = stringResource(R.string.schedule_action_failed)

    LaunchedEffect(result) {
        when (result) {
            PlanEditResult.SAVED, PlanEditResult.DELETED -> {
                viewModel.onResultShown()
                onDone()
            }
            PlanEditResult.FAILED -> {
                viewModel.onResultShown()
                snackbar.showSnackbar(failedText)
            }
            null -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.schedule_detail_back))
                    }
                },
                title = { Text(stringResource(if (viewModel.planId == null) R.string.pm_plan_new else R.string.pm_plan_edit)) },
                actions = {
                    if (viewModel.planId != null && canManage && form != null) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !isSaving) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.pm_plan_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val f = form
        when {
            f != null -> PlanEditor(
                form = f,
                isNew = viewModel.planId == null,
                enabled = canManage && !isSaving,
                error = error,
                assets = assets,
                templates = templates,
                meters = meters,
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
            notFound -> Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pm_plan_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.pm_plan_delete_title)) },
            text = { Text(stringResource(R.string.pm_plan_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.pm_plan_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.schedule_detail_cancel_btn)) } },
        )
    }
}

@Composable
private fun PlanEditor(
    form: PlanForm,
    isNew: Boolean,
    enabled: Boolean,
    error: Pair<PlanFormError, Long?>?,
    assets: List<Asset>,
    templates: List<WorkOrderTemplate>,
    meters: List<AssetMeter>,
    viewModel: PmPlanEditViewModel,
    modifier: Modifier = Modifier,
) {
    var pickingAsset by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text(stringResource(R.string.pm_plan_title_label)) },
            isError = error?.first == PlanFormError.TITLE,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v) } },
            label = { Text(stringResource(R.string.pm_plan_description_label)) },
            enabled = enabled,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        ListItem(
            overlineContent = { Text(stringResource(R.string.pm_plan_asset_label)) },
            headlineContent = { Text(form.assetName ?: stringResource(R.string.schedule_no_asset)) },
            supportingContent = if (isNew) null else ({ Text(stringResource(R.string.pm_plan_asset_fixed)) }),
            modifier = Modifier.clickable(enabled = isNew && enabled) { pickingAsset = true },
        )
        ChoiceField(
            label = stringResource(R.string.pm_plan_template_label),
            selected = templates.firstOrNull { it.id == form.templateId }?.title
                ?: if (form.templateId == null) stringResource(R.string.pm_plan_template_none) else form.templateId,
            options = listOf<WorkOrderTemplate?>(null) + templates,
            optionLabel = { it?.title ?: stringResource(R.string.pm_plan_template_none) },
            enabled = enabled,
            onSelect = { t -> viewModel.update { it.copy(templateId = t?.id) } },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pm_plan_active_label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.pm_plan_active_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = form.active, onCheckedChange = { v -> viewModel.update { it.copy(active = v) } }, enabled = enabled)
        }
        DateField(
            label = stringResource(R.string.pm_plan_last_done_label),
            value = form.lastDoneAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() },
            emptyText = stringResource(R.string.schedule_detail_never),
            enabled = enabled,
            onChange = { d -> viewModel.update { it.copy(lastDoneAt = d?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()) } },
        )

        HorizontalDivider()
        Text(stringResource(R.string.pm_plan_ends_label), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PmEndType.NEVER to R.string.pm_plan_ends_never,
                PmEndType.COUNT to R.string.pm_plan_ends_count,
                PmEndType.DATE to R.string.pm_plan_ends_date,
            ).forEach { (type, label) ->
                FilterChip(
                    selected = form.endType == type,
                    onClick = { viewModel.update { it.copy(endType = type) } },
                    label = { Text(stringResource(label)) },
                    enabled = enabled,
                )
            }
        }
        when (form.endType) {
            PmEndType.COUNT -> OutlinedTextField(
                value = form.endCount,
                onValueChange = { v -> viewModel.update { it.copy(endCount = v.filter(Char::isDigit)) } },
                label = { Text(stringResource(R.string.pm_plan_end_count_label)) },
                isError = error?.first == PlanFormError.END_COUNT,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            PmEndType.DATE -> DateField(
                label = stringResource(R.string.pm_plan_end_date_label),
                value = form.endOn,
                emptyText = stringResource(R.string.schedule_detail_not_set),
                enabled = enabled,
                isError = error?.first == PlanFormError.END_DATE,
                onChange = { d -> viewModel.update { it.copy(endOn = d) } },
            )
        }

        HorizontalDivider()
        Text(stringResource(R.string.pm_plan_triggers_label), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.pm_plan_triggers_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        form.triggers.forEach { t ->
            TriggerCard(
                trigger = t,
                hasAsset = form.assetId != null,
                meters = meters,
                enabled = enabled,
                error = error?.takeIf { it.second == t.key }?.first,
                onChange = { transform -> viewModel.updateTrigger(t.key, transform) },
                onRemove = { viewModel.removeTrigger(t.key) },
            )
        }
        OutlinedButton(onClick = viewModel::addTrigger, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.pm_plan_add_trigger))
        }

        error?.let { (e, _) ->
            Text(errorText(e), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = viewModel::save, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pm_plan_save))
        }
        Spacer(Modifier.height(32.dp))
    }

    if (pickingAsset) {
        AssetPickerDialog(
            assets = assets,
            onDismiss = { pickingAsset = false },
            onPick = { a ->
                pickingAsset = false
                viewModel.selectAsset(a)
            },
        )
    }
}

@Composable
private fun TriggerCard(
    trigger: TriggerForm,
    hasAsset: Boolean,
    meters: List<AssetMeter>,
    enabled: Boolean,
    error: PlanFormError?,
    onChange: ((TriggerForm) -> TriggerForm) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    PmTriggerKind.entries.forEach { kind ->
                        FilterChip(
                            selected = trigger.kind == kind,
                            onClick = { onChange { it.copy(kind = kind) } },
                            label = { Text(triggerKindLabel(kind)) },
                            enabled = enabled && (kind != PmTriggerKind.METER || hasAsset),
                        )
                    }
                }
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.pm_plan_remove_trigger))
                }
            }
            when (trigger.kind) {
                PmTriggerKind.CALENDAR -> CalendarFields(trigger, enabled, error, onChange)
                PmTriggerKind.INTERVAL -> IntervalFields(trigger, enabled, error, onChange)
                PmTriggerKind.METER -> MeterFields(trigger, meters, enabled, error, onChange)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pm_plan_generate_once), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = trigger.generateOnce, onCheckedChange = { v -> onChange { it.copy(generateOnce = v) } }, enabled = enabled)
            }
        }
    }
}

@Composable
private fun CalendarFields(t: TriggerForm, enabled: Boolean, error: PlanFormError?, onChange: ((TriggerForm) -> TriggerForm) -> Unit) {
    val freq = t.freq
    if (freq == null) {
        // A stored rule the editor cannot express is kept unless a frequency is picked.
        Text(
            stringResource(R.string.pm_plan_rrule_custom, t.storedRrule.orEmpty()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EveryField(t, enabled, error, onChange, Modifier.weight(1f))
        ChoiceField(
            label = stringResource(R.string.pm_plan_frequency_label),
            selected = freq?.let { freqLabel(it) } ?: stringResource(R.string.pm_plan_rrule_custom_short),
            options = CalendarFreq.entries,
            optionLabel = { freqLabel(it) },
            enabled = enabled,
            onSelect = { f -> onChange { it.copy(freq = f) } },
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun IntervalFields(t: TriggerForm, enabled: Boolean, error: PlanFormError?, onChange: ((TriggerForm) -> TriggerForm) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EveryField(t, enabled, error, onChange, Modifier.weight(1f))
        ChoiceField(
            label = stringResource(R.string.pm_plan_unit_label),
            selected = intervalUnitLabel(t.unit),
            options = PmIntervalUnit.entries,
            optionLabel = { intervalUnitLabel(it) },
            enabled = enabled,
            onSelect = { u -> onChange { it.copy(unit = u) } },
            modifier = Modifier.weight(2f),
        )
    }
    Text(
        stringResource(R.string.pm_plan_interval_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EveryField(
    t: TriggerForm,
    enabled: Boolean,
    error: PlanFormError?,
    onChange: ((TriggerForm) -> TriggerForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = t.every,
        onValueChange = { v -> onChange { it.copy(every = v.filter(Char::isDigit)) } },
        label = { Text(stringResource(R.string.pm_plan_every_label)) },
        isError = error == PlanFormError.TRIGGER_EVERY,
        enabled = enabled && (t.kind != PmTriggerKind.CALENDAR || t.freq != null),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun MeterFields(
    t: TriggerForm,
    meters: List<AssetMeter>,
    enabled: Boolean,
    error: PlanFormError?,
    onChange: ((TriggerForm) -> TriggerForm) -> Unit,
) {
    val meter = meters.firstOrNull { it.id == t.meterId }
    ChoiceField(
        label = stringResource(R.string.pm_plan_meter_label),
        selected = meter?.let(::meterLabel) ?: stringResource(R.string.pm_plan_meter_pick),
        options = meters,
        optionLabel = { meterLabel(it) },
        enabled = enabled,
        isError = error == PlanFormError.METER_MISSING,
        onSelect = { m -> onChange { it.copy(meterId = m.id) } },
    )
    if (meters.isEmpty()) {
        Text(
            stringResource(R.string.pm_plan_meter_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = t.meterMode == MeterMode.EVERY,
            onClick = { onChange { it.copy(meterMode = MeterMode.EVERY) } },
            label = { Text(stringResource(R.string.pm_plan_meter_every)) },
            enabled = enabled,
        )
        FilterChip(
            selected = t.meterMode == MeterMode.THRESHOLD,
            onClick = { onChange { it.copy(meterMode = MeterMode.THRESHOLD) } },
            label = { Text(stringResource(R.string.pm_plan_meter_threshold)) },
            enabled = enabled,
        )
    }
    val unit = meter?.unit.orEmpty()
    when (t.meterMode) {
        MeterMode.EVERY -> OutlinedTextField(
            value = t.meterInterval,
            onValueChange = { v -> onChange { it.copy(meterInterval = v) } },
            label = { Text(stringResource(R.string.pm_plan_meter_interval_label, unit)) },
            isError = error == PlanFormError.METER_VALUE,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        MeterMode.THRESHOLD -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoiceField(
                label = stringResource(R.string.pm_plan_condition_label),
                selected = meterConditionLabel(t.condition),
                options = PmMeterCondition.entries,
                optionLabel = { meterConditionLabel(it) },
                enabled = enabled,
                onSelect = { c -> onChange { it.copy(condition = c) } },
                modifier = Modifier.weight(3f),
            )
            OutlinedTextField(
                value = t.threshold,
                onValueChange = { v -> onChange { it.copy(threshold = v) } },
                label = { Text(stringResource(R.string.pm_plan_threshold_label, unit)) },
                isError = error == PlanFormError.METER_VALUE,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(2f),
            )
        }
    }
}

private fun meterLabel(m: AssetMeter): String = if (m.unit.isBlank()) m.kind else "${m.kind} (${m.unit})"

@Composable
private fun freqLabel(f: CalendarFreq): String = stringResource(
    when (f) {
        CalendarFreq.DAILY -> R.string.schedule_unit_days
        CalendarFreq.WEEKLY -> R.string.schedule_unit_weeks
        CalendarFreq.MONTHLY -> R.string.schedule_unit_months
        CalendarFreq.YEARLY -> R.string.schedule_unit_years
    },
)

@Composable
private fun errorText(e: PlanFormError): String = stringResource(
    when (e) {
        PlanFormError.TITLE -> R.string.pm_plan_error_title
        PlanFormError.NO_TRIGGERS -> R.string.pm_plan_error_no_triggers
        PlanFormError.END_COUNT -> R.string.pm_plan_error_end_count
        PlanFormError.END_DATE -> R.string.pm_plan_error_end_date
        PlanFormError.TRIGGER_EVERY -> R.string.pm_plan_error_every
        PlanFormError.METER_NEEDS_ASSET -> R.string.pm_plan_error_meter_asset
        PlanFormError.METER_MISSING -> R.string.pm_plan_error_meter_missing
        PlanFormError.METER_VALUE -> R.string.pm_plan_error_meter_value
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceField(
    label: String,
    selected: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            isError = isError,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    emptyText: String,
    enabled: Boolean,
    onChange: (LocalDate?) -> Unit,
    isError: Boolean = false,
) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: emptyText, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = { picking = true }, enabled = enabled) { Text(stringResource(R.string.pm_plan_pick_date)) }
        if (value != null) {
            TextButton(onClick = { onChange(null) }, enabled = enabled) { Text(stringResource(R.string.pm_plan_clear_date)) }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text(stringResource(R.string.pm_plan_pick_date)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.schedule_detail_cancel_btn)) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun AssetPickerDialog(assets: List<Asset>, onDismiss: () -> Unit, onPick: (Asset?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(assets, query) {
        val q = query.trim()
        if (q.isEmpty()) assets else assets.filter { a -> a.name.contains(q, ignoreCase = true) || a.serialNumber?.contains(q, ignoreCase = true) == true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pm_plan_asset_label)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.pm_plan_asset_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.schedule_no_asset)) },
                            modifier = Modifier.clickable { onPick(null) },
                        )
                    }
                    items(shown, key = { it.id }) { a ->
                        ListItem(
                            headlineContent = { Text(a.name) },
                            supportingContent = a.serialNumber?.let { sn -> { Text(sn) } },
                            modifier = Modifier.clickable { onPick(a) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_detail_cancel_btn)) } },
    )
}
