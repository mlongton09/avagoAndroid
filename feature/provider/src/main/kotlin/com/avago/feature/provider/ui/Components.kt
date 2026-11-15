package com.avago.feature.provider.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.cardListItemColors
import com.avago.feature.provider.R
import com.avago.feature.provider.data.AssetForm
import com.avago.feature.provider.data.CustomerForm
import com.avago.feature.provider.data.DocLine
import com.avago.feature.provider.data.METER_UNITS
import com.avago.feature.provider.data.SERVICE_TYPES
import com.avago.feature.provider.data.StatusForm
import com.avago.feature.provider.data.Totals
import kotlinx.coroutines.flow.Flow

/** Handles share requests in place and forwards navigation events. */
@Composable
internal fun EventEffect(
    events: Flow<ProviderEvent>,
    snackbar: SnackbarHostState,
    onCustomer: (String) -> Unit = {},
    onInvoice: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    LaunchedEffect(events) {
        events.collect { e ->
            when (e) {
                is ProviderEvent.Share -> runCatching { shareText(ctx, e.url) }.onFailure {
                    snackbar.showSnackbar(e.url)
                }
                is ProviderEvent.OpenCustomer -> onCustomer(e.id)
                is ProviderEvent.OpenInvoice -> onInvoice(e.id)
            }
        }
    }
}

/** Horizontal chip row: "All" plus each status. */
@Composable
internal fun StatusFilterRow(statuses: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AvagoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.provider_filter_all)) })
        statuses.forEach { s ->
            FilterChip(selected = selected == s, onClick = { onSelect(s) }, label = { Text(statusLabel(s)) })
        }
    }
}

/** Tappable card row with an optional status chip and trailing amount. */
@Composable
internal fun DocRow(title: String, subtitle: String?, status: String?, trailing: String?, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = subtitle?.let { { Text(it) } },
        overlineContent = status?.let { { StatusChip(it) } },
        trailingContent = trailing?.let { { Text(it, style = MaterialTheme.typography.titleSmall) } },
        colors = cardListItemColors(),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
internal fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun LinesPanel(lines: List<DocLine>, totals: Totals?, currency: String) {
    Panel(title = stringResource(R.string.provider_lines)) {
        if (lines.isEmpty()) Caption(stringResource(R.string.provider_lines_empty))
        lines.forEachIndexed { i, l ->
            if (i > 0) CardDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(l.description, style = MaterialTheme.typography.bodyMedium)
                    Caption(
                        stringResource(
                            R.string.provider_line_detail,
                            lineKindLabel(l.kind),
                            Fmt.number(l.quantity),
                            Fmt.money(l.unitPriceMinor, currency),
                        ),
                    )
                }
                Text(Fmt.money(l.amountMinor, currency), style = MaterialTheme.typography.bodyMedium)
            }
        }
        totals?.let { t ->
            CardDivider()
            if (t.labourMinor > 0) KeyValue(stringResource(R.string.provider_labour), Fmt.money(t.labourMinor, currency))
            if (t.partsMinor > 0) KeyValue(stringResource(R.string.provider_parts), Fmt.money(t.partsMinor, currency))
            KeyValue(stringResource(R.string.provider_subtotal), Fmt.money(t.subtotalMinor, currency))
            if (t.taxMinor > 0) KeyValue(stringResource(R.string.provider_tax), Fmt.money(t.taxMinor, currency))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(stringResource(R.string.provider_total), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(Fmt.money(t.totalMinor, currency), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun lineKindLabel(kind: String): String = when (kind) {
    "labour" -> stringResource(R.string.provider_labour)
    "parts" -> stringResource(R.string.provider_parts)
    else -> stringResource(R.string.provider_fee)
}

@Composable
private fun fieldError(code: String?): String? = when (code) {
    null -> null
    "required" -> stringResource(R.string.provider_err_required)
    "email" -> stringResource(R.string.provider_err_email)
    "year" -> stringResource(R.string.provider_err_year)
    else -> stringResource(R.string.provider_err_number)
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FormDialog(
    title: String,
    confirm: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.provider_cancel)) } },
    )
}

@Composable
internal fun CustomerDialog(busy: Boolean, onSubmit: (CustomerForm) -> Boolean, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val form = CustomerForm(name, email, phone, notes)
    val errors = if (tried) form.errors() else emptyMap()
    FormDialog(
        title = stringResource(R.string.provider_add_customer),
        confirm = stringResource(R.string.provider_save),
        busy = busy,
        onConfirm = {
            tried = true
            if (onSubmit(form)) onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        FormField(name, { name = it }, stringResource(R.string.provider_name), fieldError(errors["name"]))
        FormField(email, { email = it }, stringResource(R.string.provider_email), fieldError(errors["email"]), KeyboardType.Email)
        FormField(phone, { phone = it }, stringResource(R.string.provider_phone), null, KeyboardType.Phone)
        FormField(notes, { notes = it }, stringResource(R.string.provider_notes), null, singleLine = false)
    }
}

@Composable
internal fun AssetDialog(busy: Boolean, onSubmit: (AssetForm) -> Boolean, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("equipment") }
    var make by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var vin by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val form = AssetForm(name, type, make, model, year, vin)
    val errors = if (tried) form.errors() else emptyMap()
    FormDialog(
        title = stringResource(R.string.provider_add_asset),
        confirm = stringResource(R.string.provider_save),
        busy = busy,
        onConfirm = {
            tried = true
            if (onSubmit(form)) onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        FormField(name, { name = it }, stringResource(R.string.provider_name), fieldError(errors["name"]))
        Text(stringResource(R.string.provider_service_type), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SERVICE_TYPES.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(serviceTypeLabel(t)) }) }
        }
        FormField(make, { make = it }, stringResource(R.string.provider_make), null)
        FormField(model, { model = it }, stringResource(R.string.provider_model), null)
        FormField(year, { year = it }, stringResource(R.string.provider_year), fieldError(errors["year"]), KeyboardType.Number)
        FormField(vin, { vin = it }, stringResource(R.string.provider_vin_serial), null)
    }
}

@Composable
internal fun StatusDialog(initial: StatusForm, busy: Boolean, onSubmit: (StatusForm) -> Boolean, onDismiss: () -> Unit) {
    var hours by rememberSaveable { mutableStateOf(initial.labourHours) }
    var meter by rememberSaveable { mutableStateOf(initial.meterReading) }
    var unit by rememberSaveable { mutableStateOf(initial.meterUnit) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    val form = initial.copy(labourHours = hours, meterReading = meter, meterUnit = unit, notes = notes)
    val errors = form.errors()
    FormDialog(
        title = stringResource(R.string.provider_status_to, statusLabel(initial.status)),
        confirm = stringResource(R.string.provider_confirm),
        busy = busy,
        onConfirm = { if (onSubmit(form)) onDismiss() },
        onDismiss = onDismiss,
    ) {
        FormField(hours, { hours = it }, stringResource(R.string.provider_labour_hours), fieldError(errors["labour_hours"]), KeyboardType.Decimal)
        FormField(meter, { meter = it }, stringResource(R.string.provider_meter_reading), fieldError(errors["meter_reading"]), KeyboardType.Decimal)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            METER_UNITS.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(meterUnitLabel(u)) }) }
        }
        FormField(notes, { notes = it }, stringResource(R.string.provider_status_note), null, singleLine = false)
        if (initial.status == "completed") Caption(stringResource(R.string.provider_complete_hint))
    }
}

@Composable
internal fun MarkPaidDialog(busy: Boolean, onSubmit: (String?) -> Unit, onDismiss: () -> Unit) {
    var ref by rememberSaveable { mutableStateOf("") }
    FormDialog(
        title = stringResource(R.string.provider_mark_paid),
        confirm = stringResource(R.string.provider_mark_paid),
        busy = busy,
        onConfirm = {
            onSubmit(ref.ifBlank { null })
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Caption(stringResource(R.string.provider_mark_paid_hint))
        FormField(ref, { ref = it }, stringResource(R.string.provider_paid_reference), null)
    }
}
