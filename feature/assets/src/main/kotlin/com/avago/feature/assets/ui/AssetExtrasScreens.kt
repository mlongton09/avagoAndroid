package com.avago.feature.assets.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.network.json.ApiError
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.feature.assets.R
import com.avago.feature.assets.model.ExpiryStatus
import com.avago.feature.assets.model.Recall
import com.avago.feature.assets.model.ToolForm
import com.avago.feature.assets.viewmodel.ExtrasLoad
import com.avago.feature.assets.viewmodel.ExtrasNotice
import com.avago.feature.assets.viewmodel.RecallsViewModel
import com.avago.feature.assets.viewmodel.ToolDetailsViewModel
import com.avago.feature.assets.viewmodel.VehicleDetailsViewModel
import kotlinx.coroutines.flow.Flow

// Feature I screens: tool and vehicle details (v2 store, edited with catalog commands) and safety recalls
// (server query with REST remedy actions) for one asset.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtrasScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.asset_detail_back))
                    }
                },
                actions = { actions() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
private fun ExtrasNoticeEffect(notices: Flow<ExtrasNotice>, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(notices) {
        notices.collect { n ->
            snackbar.showSnackbar(
                when (n) {
                    is ExtrasNotice.Failed -> scopeErrorText(resources, n.error)
                    ExtrasNotice.Saved -> resources.getString(R.string.asset_scope_saved)
                    ExtrasNotice.NotSaved -> resources.getString(R.string.asset_scope_not_saved)
                },
            )
        }
    }
}

internal fun scopeErrorText(res: Resources, e: ApiError): String = when {
    e.offline -> res.getString(R.string.asset_scope_err_offline)
    e.code == "rate_limited" || e.status == 429 -> res.getString(R.string.asset_scope_err_rate_limited)
    e.code == "vin_decode_unavailable" -> res.getString(R.string.asset_scope_err_vin_unavailable)
    e.status == 403 -> res.getString(R.string.asset_scope_err_forbidden)
    e.status == 404 -> res.getString(R.string.asset_scope_err_not_found)
    e.message.isNotBlank() -> e.message
    else -> res.getString(R.string.rental_error_unknown)
}

@Composable
private fun <T> ExtrasLoadBox(state: ExtrasLoad<T>, onRetry: () -> Unit, padding: PaddingValues, content: @Composable (T) -> Unit) {
    when (state) {
        is ExtrasLoad.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ExtrasLoad.Failed -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ErrorBanner(
                message = scopeErrorText(LocalResources.current, state.error),
                onRetry = onRetry,
                retryLabel = stringResource(R.string.asset_scope_retry),
            )
        }
        is ExtrasLoad.Ready -> content(state.value)
    }
}

@Composable
internal fun ExpiryPill(label: String, status: ExpiryStatus?, due: String?) {
    val (text, color) = when (status) {
        ExpiryStatus.Overdue -> stringResource(R.string.asset_scope_overdue) to MaterialTheme.colorScheme.error
        ExpiryStatus.DueSoon -> stringResource(R.string.asset_scope_due_soon) to MaterialTheme.colorScheme.tertiary
        ExpiryStatus.Ok -> stringResource(R.string.asset_scope_valid) to MaterialTheme.colorScheme.primary
        null -> stringResource(R.string.asset_scope_not_set) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!due.isNullOrBlank()) {
                Text(stringResource(R.string.asset_scope_due_on, due), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AssistChip(onClick = {}, label = { Text(text, color = color) })
    }
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String? = null,
    hint: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun fieldError(code: String?): String? = when (code) {
    null -> null
    "date" -> stringResource(R.string.asset_scope_err_date)
    "range" -> stringResource(R.string.asset_scope_err_interval)
    "number" -> stringResource(R.string.asset_scope_err_number)
    "currency" -> stringResource(R.string.asset_scope_err_currency)
    "length" -> stringResource(R.string.asset_scope_err_length)
    else -> stringResource(R.string.rental_error_unknown)
}

@Composable
private fun SaveAction(busy: Boolean, onSave: () -> Unit) {
    TextButton(onClick = onSave, enabled = !busy) { Text(stringResource(R.string.asset_scope_save)) }
}

private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)

@Composable
private fun LoadingBox(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ToolDetailsScreen(onBack: () -> Unit, viewModel: ToolDetailsViewModel = hiltViewModel()) {
    val tool by viewModel.state.collectAsStateWithLifecycle()
    val loaded by viewModel.form.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ExtrasNoticeEffect(viewModel.notices, snackbar)
    ExtrasScaffold(
        title = stringResource(R.string.asset_scope_tool_title),
        onBack = onBack,
        snackbar = snackbar,
        actions = { if (loaded != null) SaveAction(busy) { viewModel.submit() } },
    ) { padding ->
        val form = loaded
        if (form == null) {
            LoadingBox(padding)
            return@ExtrasScaffold
        }
        val dateHint = stringResource(R.string.asset_scope_date_hint)
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.asset_scope_calibration), contentPadding = true) {
                    val due = tool?.calibrationDueOn
                    ExpiryPill(stringResource(R.string.asset_scope_calibration), ExpiryStatus.of(due), due)
                    FormField(form.calibratedOn, { viewModel.update(form.copy(calibratedOn = it)) },
                        stringResource(R.string.asset_scope_calibrated_on), fieldError(errors["calibrated_on"]), dateHint)
                    FormField(form.intervalDays, { viewModel.update(form.copy(intervalDays = it.filter(Char::isDigit).take(4))) },
                        stringResource(R.string.asset_scope_interval_days), fieldError(errors["calibration_interval_days"]),
                        keyboard = numberKeys)
                    val derived = form.derivedDueOn
                    FormField(form.dueOn, { viewModel.update(form.copy(dueOn = it)) },
                        stringResource(R.string.asset_scope_due_date), fieldError(errors["calibration_due_on"]),
                        if (derived != null) stringResource(R.string.asset_scope_due_derived, derived.toString())
                        else stringResource(R.string.asset_scope_due_hint))
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_usage), contentPadding = true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "" to R.string.asset_scope_usage_default,
                            "days" to R.string.asset_scope_usage_days,
                            "cycles" to R.string.asset_scope_usage_cycles,
                        ).forEach { (unit, res) ->
                            FilterChip(
                                selected = form.usageUnit == unit,
                                onClick = { viewModel.update(form.copy(usageUnit = unit)) },
                                label = { Text(stringResource(res)) },
                            )
                        }
                    }
                    FormField(form.usageValue, { viewModel.update(form.copy(usageValue = it)) },
                        stringResource(R.string.asset_scope_usage_value), fieldError(errors["usage_value"]),
                        stringResource(R.string.asset_scope_usage_value_hint), keyboard = decimalKeys)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_value), contentPadding = true) {
                    FormField(form.replacementValue, { viewModel.update(form.copy(replacementValue = it)) },
                        stringResource(R.string.asset_scope_replacement_value), fieldError(errors["replacement_value"]),
                        keyboard = decimalKeys)
                    FormField(form.currency, { viewModel.update(form.copy(currency = it.take(3).uppercase())) },
                        stringResource(R.string.asset_scope_currency), fieldError(errors["currency"]),
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_tracker), contentPadding = true) {
                    FormField(form.trackerTagId, { viewModel.update(form.copy(trackerTagId = it)) },
                        stringResource(R.string.asset_scope_tracker_tag), fieldError(errors["tracker_tag_id"]))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolForm.TRACKER_KINDS.forEach { kind ->
                            FilterChip(
                                selected = form.trackerKind == kind,
                                onClick = { viewModel.update(form.copy(trackerKind = if (form.trackerKind == kind) "" else kind)) },
                                label = { Text(trackerKindLabel(kind)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun trackerKindLabel(kind: String): String = stringResource(
    when (kind) {
        "gps" -> R.string.asset_scope_tracker_gps
        "bluetooth" -> R.string.asset_scope_tracker_bluetooth
        "uwb" -> R.string.asset_scope_tracker_uwb
        else -> R.string.asset_scope_tracker_other
    },
)

@Composable
fun VehicleDetailsScreen(onBack: () -> Unit, viewModel: VehicleDetailsViewModel = hiltViewModel()) {
    val v by viewModel.state.collectAsStateWithLifecycle()
    val loaded by viewModel.form.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ExtrasNoticeEffect(viewModel.notices, snackbar)
    ExtrasScaffold(
        title = stringResource(R.string.asset_scope_vehicle_title),
        onBack = onBack,
        snackbar = snackbar,
        actions = { if (loaded != null) SaveAction(busy) { viewModel.submit() } },
    ) { padding ->
        val form = loaded
        if (form == null) {
            LoadingBox(padding)
            return@ExtrasScaffold
        }
        val dateHint = stringResource(R.string.asset_scope_date_hint)
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.asset_scope_vehicle_specs), contentPadding = true) {
                    FormField(form.bodyClass, { viewModel.update(form.copy(bodyClass = it)) }, stringResource(R.string.asset_scope_body_class))
                    FormField(form.fuelType, { viewModel.update(form.copy(fuelType = it)) }, stringResource(R.string.asset_scope_fuel_type))
                    FormField(form.gvwr, { viewModel.update(form.copy(gvwr = it)) }, stringResource(R.string.asset_scope_gvwr))
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_registration), contentPadding = true) {
                    val expires = v?.registrationExpiresOn
                    ExpiryPill(stringResource(R.string.asset_scope_registration), ExpiryStatus.of(expires), expires)
                    FormField(form.registrationNumber, { viewModel.update(form.copy(registrationNumber = it)) },
                        stringResource(R.string.asset_scope_registration_number),
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                    FormField(form.registrationExpiresOn, { viewModel.update(form.copy(registrationExpiresOn = it)) },
                        stringResource(R.string.asset_scope_expires_on), fieldError(errors["registration_expires_on"]), dateHint)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_insurance), contentPadding = true) {
                    val expires = v?.insuranceExpiresOn
                    ExpiryPill(stringResource(R.string.asset_scope_insurance), ExpiryStatus.of(expires), expires)
                    FormField(form.insurancePolicyNumber, { viewModel.update(form.copy(insurancePolicyNumber = it)) },
                        stringResource(R.string.asset_scope_policy_number))
                    FormField(form.insuranceExpiresOn, { viewModel.update(form.copy(insuranceExpiresOn = it)) },
                        stringResource(R.string.asset_scope_expires_on), fieldError(errors["insurance_expires_on"]), dateHint)
                }
            }
        }
    }
}

@Composable
fun RecallsScreen(onBack: () -> Unit, viewModel: RecallsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var remedying by remember { mutableStateOf<Recall?>(null) }
    ExtrasNoticeEffect(viewModel.notices, snackbar)
    ExtrasScaffold(
        title = stringResource(R.string.asset_scope_recalls_title),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.asset_scope_refresh))
            }
        },
    ) { padding ->
        ExtrasLoadBox(state, { viewModel.refresh(force = false) }, padding) { list ->
            LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionCard(contentPadding = true) {
                        val summary = when {
                            !list.supported -> stringResource(R.string.asset_scope_recalls_unsupported)
                            list.openCount == 0 -> stringResource(R.string.asset_scope_recalls_none_open)
                            else -> stringResource(R.string.asset_scope_recalls_open, list.openCount)
                        }
                        Text(summary, style = MaterialTheme.typography.titleMedium)
                        if (list.unavailable) {
                            Text(stringResource(R.string.asset_scope_recalls_unavailable), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        } else if (list.stale) {
                            Text(stringResource(R.string.asset_scope_recalls_stale), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (list.blocksRentals && list.openCount > 0) {
                            Text(stringResource(R.string.asset_scope_recalls_blocking), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(list.recalls, key = { it.campaignNumber }) { r ->
                    RecallCard(r, busy, onRemedy = { remedying = r }, onReopen = { viewModel.setRemedied(r.campaignNumber, false) })
                }
            }
        }
    }
    remedying?.let { r ->
        var note by remember(r.campaignNumber) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { remedying = null },
            title = { Text(stringResource(R.string.asset_scope_mark_remedied)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.campaignNumber, style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(note, { note = it.take(500) }, label = { Text(stringResource(R.string.asset_scope_remedy_note)) },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRemedied(r.campaignNumber, true, note)
                    remedying = null
                }) { Text(stringResource(R.string.asset_scope_mark_remedied)) }
            },
            dismissButton = { TextButton(onClick = { remedying = null }) { Text(stringResource(R.string.asset_cancel)) } },
        )
    }
}

@Composable
private fun RecallCard(r: Recall, busy: Boolean, onRemedy: () -> Unit, onReopen: () -> Unit) {
    SectionCard(title = r.component ?: r.campaignNumber, contentPadding = true) {
        Text(
            stringResource(R.string.asset_scope_campaign, r.campaignNumber, r.reportReceivedDate ?: "—"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (r.parkIt || r.parkOutside) {
            Text(
                stringResource(if (r.parkIt) R.string.asset_scope_park_it else R.string.asset_scope_park_outside),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        r.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        r.consequence?.let { Text(stringResource(R.string.asset_scope_consequence, it), style = MaterialTheme.typography.bodySmall) }
        r.remedy?.let { Text(stringResource(R.string.asset_scope_remedy, it), style = MaterialTheme.typography.bodySmall) }
        if (r.open) {
            OutlinedButton(onClick = onRemedy, enabled = !busy, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.asset_scope_mark_remedied))
            }
        } else {
            Text(stringResource(R.string.asset_scope_remedied_on, r.remediedAt.orEmpty().take(10)),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            r.remedyNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onReopen, enabled = !busy) { Text(stringResource(R.string.asset_scope_reopen)) }
        }
    }
}
