package com.avago.feature.assets.ui

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
import androidx.compose.ui.platform.LocalContext
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
import com.avago.feature.assets.viewmodel.ExtrasLoad
import com.avago.feature.assets.viewmodel.ExtrasNotice
import com.avago.feature.assets.viewmodel.RecallsViewModel
import com.avago.feature.assets.viewmodel.ToolDetailsViewModel
import com.avago.feature.assets.viewmodel.VehicleDetailsViewModel
import kotlinx.coroutines.flow.Flow

// Feature I screens: vehicle details (v2 store) and safety recalls (server query) for one asset.

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
    val ctx = LocalContext.current
    LaunchedEffect(notices) {
        notices.collect { n ->
            snackbar.showSnackbar(
                when (n) {
                    is ExtrasNotice.Failed -> scopeErrorText(ctx, n.error)
                },
            )
        }
    }
}

internal fun scopeErrorText(ctx: android.content.Context, e: ApiError): String = when {
    e.offline -> ctx.getString(R.string.asset_scope_err_offline)
    e.code == "rate_limited" || e.status == 429 -> ctx.getString(R.string.asset_scope_err_rate_limited)
    e.code == "vin_decode_unavailable" -> ctx.getString(R.string.asset_scope_err_vin_unavailable)
    e.status == 403 -> ctx.getString(R.string.asset_scope_err_forbidden)
    e.status == 404 -> ctx.getString(R.string.asset_scope_err_not_found)
    e.message.isNotBlank() -> e.message
    else -> ctx.getString(R.string.rental_error_unknown)
}

@Composable
private fun <T> ExtrasLoadBox(state: ExtrasLoad<T>, onRetry: () -> Unit, padding: PaddingValues, content: @Composable (T) -> Unit) {
    when (state) {
        is ExtrasLoad.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ExtrasLoad.Failed -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ErrorBanner(
                message = scopeErrorText(LocalContext.current, state.error),
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
private fun ReadOnlyField(label: String, value: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf { it.isNotBlank() } ?: "\u2014", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyDetails(padding: PaddingValues, message: String) {
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        SectionCard(contentPadding = true) { Text(message, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Read-only: catalog v1 has no tool-details command (docs "Catalog requests"). */
@Composable
fun ToolDetailsScreen(onBack: () -> Unit, viewModel: ToolDetailsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ExtrasScaffold(
        title = stringResource(R.string.asset_scope_tool_title),
        onBack = onBack,
        snackbar = snackbar,
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ExtrasScaffold
        }
        val tool = current.details
        if (tool == null) {
            EmptyDetails(padding, stringResource(R.string.asset_scope_tool_none))
            return@ExtrasScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.asset_scope_calibration), contentPadding = true) {
                    ExpiryPill(stringResource(R.string.asset_scope_calibration), ExpiryStatus.of(tool.calibrationDueOn), tool.calibrationDueOn)
                    ReadOnlyField(stringResource(R.string.asset_scope_calibrated_on), tool.calibratedOn)
                    ReadOnlyField(stringResource(R.string.asset_scope_interval_days), tool.calibrationIntervalDays?.toString())
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_usage), contentPadding = true) {
                    val unit = when (tool.usageUnit) {
                        "days" -> stringResource(R.string.asset_scope_usage_days)
                        "cycles" -> stringResource(R.string.asset_scope_usage_cycles)
                        else -> tool.usageUnit
                    }
                    ReadOnlyField(stringResource(R.string.asset_scope_usage_value), listOfNotNull(tool.usageValue?.toPlainString(), unit).joinToString(" "))
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_value), contentPadding = true) {
                    ReadOnlyField(
                        stringResource(R.string.asset_scope_replacement_value),
                        tool.replacementValue?.let { com.avago.core.data.Formatters.formatCurrency(it.toDouble(), tool.currency ?: "USD") },
                    )
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_tracker), contentPadding = true) {
                    ReadOnlyField(stringResource(R.string.asset_scope_tracker_tag), tool.trackerTagId)
                    ReadOnlyField(stringResource(R.string.asset_scope_tracker), tool.trackerKind?.let { trackerKindLabel(it) })
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

/** Read-only: catalog v1 has no vehicle-details command (docs "Catalog requests"). */
@Composable
fun VehicleDetailsScreen(onBack: () -> Unit, viewModel: VehicleDetailsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ExtrasScaffold(
        title = stringResource(R.string.asset_scope_vehicle_title),
        onBack = onBack,
        snackbar = snackbar,
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ExtrasScaffold
        }
        val v = current.details
        if (v == null) {
            EmptyDetails(padding, stringResource(R.string.asset_scope_vehicle_none))
            return@ExtrasScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.asset_scope_vehicle_specs), contentPadding = true) {
                    ReadOnlyField(stringResource(R.string.asset_scope_body_class), v.bodyClass)
                    ReadOnlyField(stringResource(R.string.asset_scope_fuel_type), v.fuelType)
                    ReadOnlyField(stringResource(R.string.asset_scope_gvwr), v.gvwr)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_registration), contentPadding = true) {
                    ExpiryPill(stringResource(R.string.asset_scope_registration), ExpiryStatus.of(v.registrationExpiresOn), v.registrationExpiresOn)
                    ReadOnlyField(stringResource(R.string.asset_scope_registration_number), v.registrationNumber)
                }
            }
            item {
                SectionCard(title = stringResource(R.string.asset_scope_insurance), contentPadding = true) {
                    ExpiryPill(stringResource(R.string.asset_scope_insurance), ExpiryStatus.of(v.insuranceExpiresOn), v.insuranceExpiresOn)
                    ReadOnlyField(stringResource(R.string.asset_scope_policy_number), v.insurancePolicyNumber)
                }
            }
        }
    }
}

@Composable
fun RecallsScreen(onBack: () -> Unit, viewModel: RecallsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
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
                    RecallCard(r)
                }
            }
        }
    }
}

@Composable
private fun RecallCard(r: Recall) {
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
        if (!r.open) {
            Text(stringResource(R.string.asset_scope_remedied_on, r.remediedAt.orEmpty().take(10)),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            r.remedyNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
