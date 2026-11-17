package com.avago.feature.rentals.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.assets.model.text
import com.avago.feature.assets.ui.AssetClassificationPicker
import com.avago.feature.rentals.R
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun OnboardingEntry(onOpen: () -> Unit, vm: OnboardingEntryViewModel = hiltViewModel()) {
    val allowed by vm.allowed.collectAsStateWithLifecycle()
    if (allowed) FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.onb_entry))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // Declarative four-step layout; validation is pure domain code.
fun OnboardingScreen(onBack: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val session = ui.session
    val editor = session.editor
    val draft = editor.draft
    var paste by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFile)
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.onb_entry)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.onb_back)) } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.onb_intro))
                if (!ui.permitted) {
                    Text(stringResource(R.string.onb_permission), color = MaterialTheme.colorScheme.error)
                }
                session.error?.let {
                    Text(stringResource(R.string.onb_error, it), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.reload() }, enabled = !ui.busy) {
                        Text(stringResource(R.string.onb_retry_keep))
                    }
                }
                if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (session.publishedSuccessfully) {
                item {
                    val receipt = session.receipt!!
                    Text(stringResource(R.string.onb_published), style = MaterialTheme.typography.titleLarge)
                    Text(receipt.text("public_url"))
                    Text(stringResource(R.string.onb_requests, receipt.text("requests_ready")))
                    Text(stringResource(R.string.onb_payments, receipt.text("payments_configured")))
                }
                return@LazyColumn
            }
            item {
                Column {
                    ONBOARDING_STEPS.forEach { step ->
                        FilterChip(selected = draft.step == step, onClick = { vm.editStore { it.copy(step = step) } },
                            enabled = !ui.busy, label = { Text(stepLabel(step)) })
                    }
                }
            }
            when (draft.step) {
                "store" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(draft.title, R.string.onb_store_title, enabled = !ui.busy) { title ->
                            vm.editStore {
                                val derived = it.slug.isBlank() || it.slug == slugFromTitle(it.title)
                                it.copy(title = title, slug = if (derived) slugFromTitle(title) else it.slug)
                            }
                        }
                        Field(draft.slug, R.string.onb_slug, enabled = !ui.busy) { value ->
                            vm.editStore { it.copy(slug = value) }
                        }
                        Field(draft.currency, R.string.onb_currency, enabled = !ui.busy) { value ->
                            vm.editStore { it.copy(currency = value.trim().uppercase(java.util.Locale.ROOT)) }
                        }
                    }
                }
                "inventory" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice(draft.mode, listOf("same", "different"), !ui.busy,
                                {
                                    if (it == "same") stringResource(R.string.onb_same)
                                    else stringResource(R.string.onb_different)
                                }) { mode ->
                                vm.editStore { it.copy(mode = mode) }
                            }
                            Text(stringResource(R.string.onb_shared), style = MaterialTheme.typography.titleMedium)
                            ItemFields(ui.shared, ui, shared = true) { updated -> vm.editShared { updated } }
                            OutlinedButton(onClick = vm::applyShared, enabled = !ui.busy) {
                                Text(stringResource(R.string.onb_apply_all))
                            }
                            Text(
                                stringResource(R.string.onb_manual_preserved),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (draft.mode == "different") {
                                Field(paste, R.string.onb_paste, singleLine = false) { paste = it }
                                Button(
                                    onClick = { if (vm.paste(paste)) paste = "" },
                                    enabled = !ui.busy && paste.isNotBlank(),
                                ) {
                                    Text(stringResource(R.string.onb_add_names))
                                }
                                OutlinedButton(onClick = {
                                    picker.launch(arrayOf(
                                        "text/csv", "text/comma-separated-values",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/octet-stream",
                                    ))
                                }, enabled = !ui.busy) { Text(stringResource(R.string.onb_import)) }
                            }
                            val canAdd = editor.rows.size < MAX_INVENTORY &&
                                (draft.mode == "different" || editor.rows.isEmpty())
                            OutlinedButton(onClick = vm::addRow, enabled = !ui.busy && canAdd) {
                                Text(stringResource(R.string.onb_add_row))
                            }
                            if (ui.suggesting) TextButton(onClick = vm::cancelSuggestions) {
                                Text(stringResource(R.string.onb_cancel_ai))
                            }
                            else TextButton(onClick = { vm.suggest() }, enabled = !ui.busy && ui.taxonomy != null) {
                                Text(stringResource(R.string.onb_suggest))
                            }
                            ui.notice?.let { Text(stringResource(R.string.onb_review_category)) }
                            Text(stringResource(R.string.onb_counts, editor.rows.size,
                                editor.rows.sumOf { it.quantity.toLongOrNull() ?: 0L }))
                        }
                    }
                    ui.parsed?.let { parsed ->
                        item {
                            Column {
                                Text(stringResource(R.string.onb_parse_only))
                                if (parsed["truncated"]?.jsonPrimitive?.booleanOrNull != false) Text(
                                    stringResource(R.string.onb_truncated), color = MaterialTheme.colorScheme.error)
                                parsed["sheets"]?.jsonArray?.forEach { sheet ->
                                    TextButton(
                                        onClick = { vm.selectSheet(sheet.jsonPrimitive.content) }, enabled = !ui.busy,
                                    ) {
                                        Text(sheet.jsonPrimitive.content)
                                    }
                                }
                                Button(onClick = vm::acceptImport, enabled = !ui.busy) {
                                    Text(stringResource(R.string.onb_add_import))
                                }
                            }
                        }
                    }
                    items(editor.rows, key = { it.id }) { row ->
                        var expanded by rememberSaveable(row.id) { mutableStateOf(editor.rows.size == 1) }
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(row.item.name.ifBlank { stringResource(R.string.onb_missing_name) })
                                }
                                if (row.incomplete(ui)) {
                                    Text(
                                        stringResource(R.string.onb_row_incomplete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                if (expanded) {
                                    ItemFields(row, ui, shared = false) { updated -> vm.editRow(row.id) { updated } }
                                    TextButton(onClick = { vm.removeRow(row.id) }, enabled = !ui.busy) {
                                        Text(stringResource(R.string.onb_remove))
                                    }
                                }
                            }
                        }
                    }
                }
                "rental" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Choice(draft.booking_mode, listOf("quote", "hold"), !ui.busy,
                            {
                                if (it == "quote") stringResource(R.string.onb_quote)
                                else stringResource(R.string.onb_hold)
                            }) { value ->
                            vm.editStore { it.copy(booking_mode = value) }
                        }
                        Field(draft.contact_email, R.string.onb_email, enabled = !ui.busy) { value ->
                            vm.editStore { it.copy(contact_email = value) }
                        }
                        Field(draft.contact_phone, R.string.onb_phone, enabled = !ui.busy) { value ->
                            vm.editStore { it.copy(contact_phone = value) }
                        }
                        var locationOpen by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { locationOpen = true }, enabled = !ui.busy) {
                            Text(ui.locations.find { it.locationId == draft.location_id }?.name
                                ?: draft.location_id ?: stringResource(R.string.onb_location))
                        }
                        if (locationOpen) AlertDialog(
                            onDismissRequest = { locationOpen = false },
                            title = { Text(stringResource(R.string.onb_location)) },
                            text = {
                                LazyColumn {
                                    item {
                                        TextButton(onClick = {
                                            vm.editStore { it.copy(location_id = null) }; locationOpen = false
                                        }) { Text(stringResource(R.string.onb_no_location)) }
                                    }
                                    items(ui.locations, key = { it.locationId }) { location ->
                                        TextButton(onClick = {
                                            vm.editStore { it.copy(location_id = location.locationId) }
                                            locationOpen = false
                                        }) { Text(location.name) }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { locationOpen = false }) {
                                    Text(stringResource(R.string.onb_close))
                                }
                            },
                        )
                        Text(stringResource(R.string.onb_operator))
                        OperatorChoice(ui.shared.item.operator_mode, !ui.busy) { mode ->
                            vm.editShared { it.copy(item = it.item.copy(operator_mode = mode)) }
                        }
                        OutlinedButton(onClick = vm::applyOperator, enabled = !ui.busy) {
                            Text(stringResource(R.string.onb_apply_operator))
                        }
                        Text(stringResource(R.string.onb_optional))
                    }
                }
                "review" -> {
                  item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(draft.title, style = MaterialTheme.typography.titleLarge)
                        Text("${draft.slug} · ${draft.currency} · ${draft.booking_mode}")
                        Text(stringResource(
                            R.string.onb_counts, editor.rows.size,
                            editor.rows.sumOf { it.quantity.toLongOrNull() ?: 0 },
                        ))
                        Text(stringResource(R.string.onb_no_marketplace))
                        Text(stringResource(R.string.onb_optional))
                        Button(onClick = { vm.save(publish = true) }, enabled = !ui.busy && ui.permitted) {
                            Text(stringResource(R.string.onb_publish))
                        }
                    }
                  }
                  items(editor.rows, key = { "review-${it.id}" }) { row ->
                      Text("${row.item.name} × ${row.quantity} · ${row.rate} ${draft.currency}")
                  }
                }
            }
            item {
                Column {
                    Button(onClick = { vm.save() }, enabled = !ui.busy && ui.permitted) {
                        Text(stringResource(R.string.onb_save))
                    }
                    val next = ONBOARDING_STEPS.indexOf(draft.step) + 1
                    if (next in ONBOARDING_STEPS.indices) OutlinedButton(onClick = {
                        vm.editStore { it.copy(step = ONBOARDING_STEPS[next]) }; vm.save()
                    }, enabled = !ui.busy && ui.permitted) { Text(stringResource(R.string.onb_next)) }
                }
            }
        }
    }
}

private fun ItemEditor.incomplete(ui: OnboardingUi) = listOf(
    item.name.isBlank(),
    quantity.toIntOrNull() !in 1..MAX_INVENTORY,
    parseMinor(rate, ui.session.editor.draft.currency) == null,
    ui.taxonomy?.valid(item.classification) != true,
).any { it }

@Composable
private fun ItemFields(row: ItemEditor, ui: OnboardingUi, shared: Boolean, onChange: (ItemEditor) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!shared) {
            Field(row.item.name, R.string.onb_name, enabled = !ui.busy) {
                onChange(row.copy(item = row.item.copy(name = it)))
            }
            Field(row.quantity, R.string.onb_quantity, enabled = !ui.busy) { onChange(row.copy(quantity = it)) }
        }
        var typeOpen by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { typeOpen = true }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
            Text(row.item.asset_type.ifBlank { stringResource(R.string.onb_type) })
        }
        if (typeOpen) AlertDialog(
            onDismissRequest = { typeOpen = false }, title = { Text(stringResource(R.string.onb_type)) },
            text = {
                LazyColumn {
                    items(ui.catalog?.entries.orEmpty().filter { ui.catalog?.isVisible(it.id) == true }) { type ->
                        TextButton(onClick = {
                            onChange(row.copy(item = row.item.copy(asset_type = type.id)))
                            typeOpen = false
                        }) {
                            Text(type.id.replace('_', ' '))
                        }
                    }
                }
            }, confirmButton = {
                TextButton(onClick = { typeOpen = false }) { Text(stringResource(R.string.onb_close)) }
            },
        )
        AssetClassificationPicker(ui.taxonomy, row.item.classification) {
            onChange(row.copy(item = row.item.copy(classification = it)))
        }
        Field(row.rate, R.string.onb_rate, enabled = !ui.busy) { onChange(row.copy(rate = it)) }
        Text(stringResource(R.string.onb_precision, currencyDigits(ui.session.editor.draft.currency) ?: 0))
        OperatorChoice(row.item.operator_mode, !ui.busy) {
            onChange(row.copy(item = row.item.copy(operator_mode = it)))
        }
        if (!shared) {
            val label = if (ui.catalog?.info(row.item.asset_type)?.serialRequired == true) R.string.onb_serial_required
                else R.string.onb_serial
            Field(row.serials, label,
                singleLine = false, enabled = !ui.busy) { onChange(row.copy(serials = it)) }
            Field(
                row.item.unit_names.orEmpty().joinToString("\n"), R.string.onb_unit_names,
                singleLine = false, enabled = !ui.busy,
            ) { value ->
                val names = value.takeIf(String::isNotBlank)?.lines()?.map(String::trim)
                onChange(row.copy(item = row.item.copy(unit_names = names)))
            }
        }
    }
}

@Composable
private fun OperatorChoice(value: String, enabled: Boolean, onSelect: (String) -> Unit) =
    Choice(value, OPERATOR_MODES, enabled, { mode ->
        stringResource(when (mode) {
            "operator_required" -> R.string.onb_operated
            "either" -> R.string.onb_either
            else -> R.string.onb_self
        })
    }, onSelect)

@Composable
private fun Choice(value: String, choices: List<String>, enabled: Boolean,
    label: @Composable (String) -> String, onSelect: (String) -> Unit) {
    Column { choices.forEach { option ->
        FilterChip(
            selected = value == option, onClick = { onSelect(option) },
            enabled = enabled, label = { Text(label(option)) },
        )
    } }
}

@Composable
private fun Field(
    value: String,
    @StringRes label: Int,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(value, onChange, label = { Text(stringResource(label)) }, singleLine = singleLine,
        enabled = enabled, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun stepLabel(step: String) = stringResource(when (step) {
    "inventory" -> R.string.onb_inventory
    "rental" -> R.string.onb_rental
    "review" -> R.string.onb_review
    else -> R.string.onb_store
})
