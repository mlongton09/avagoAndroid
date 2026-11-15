package com.avago.feature.provider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.EmptyStateView
import com.avago.feature.provider.R
import com.avago.feature.provider.data.CustomerAsset

@Composable
fun ProviderCustomersScreen(
    onBack: () -> Unit,
    onCustomer: (String) -> Unit,
    vm: CustomersViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by rememberSaveable { mutableStateOf(false) }
    NoticeEffect(vm.notices, snackbar)
    EventEffect(vm.events, snackbar, onCustomer = onCustomer)
    ProviderScaffold(
        title = stringResource(R.string.provider_customers),
        onBack = onBack,
        snackbar = snackbar,
        floating = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.provider_add_customer)) },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text(stringResource(R.string.provider_search_customers)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.md, vertical = 8.dp),
            )
            LoadBox(state, vm::refresh, PaddingValues()) { list ->
                if (list.isEmpty()) {
                    EmptyStateView(Icons.Filled.Search, stringResource(R.string.provider_customers_empty))
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = inner.calculateBottomPadding() + 88.dp),
                    ) {
                        item {
                            Panel {
                                list.forEachIndexed { i, c ->
                                    if (i > 0) CardDivider()
                                    val sub = listOfNotNull(
                                        c.assetCount?.let { stringResource(R.string.provider_asset_count, it) },
                                        stringResource(R.string.provider_linked).takeIf { c.linked },
                                        c.phone ?: c.email,
                                    ).joinToString(" · ").ifBlank { null }
                                    DocRow(c.name, sub, null, null) { onCustomer(c.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) CustomerDialog(busy, onSubmit = vm::add, onDismiss = { adding = false })
}

@Composable
fun ProviderCustomerScreen(
    onBack: () -> Unit,
    onJob: (String) -> Unit,
    onQuote: (String) -> Unit,
    onInvoice: (String) -> Unit,
    vm: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }
    NoticeEffect(vm.notices, snackbar)
    ProviderScaffold(
        title = state.valueOrNull()?.customer?.name ?: stringResource(R.string.provider_customer),
        onBack = onBack,
        snackbar = snackbar,
    ) { inner ->
        LoadBox(state, vm::refresh, inner) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("contact") {
                    Panel(title = stringResource(R.string.provider_contact)) {
                        d.customer.email?.let { KeyValue(stringResource(R.string.provider_email), it) }
                        d.customer.phone?.let { KeyValue(stringResource(R.string.provider_phone), it) }
                        val link = when {
                            d.customer.linked -> stringResource(R.string.provider_linked)
                            d.connectionStatus != null -> statusLabel(d.connectionStatus)
                            else -> stringResource(R.string.provider_not_linked)
                        }
                        KeyValue(stringResource(R.string.provider_avago_link), link)
                        d.customer.notes?.let { Caption(it, Modifier.padding(top = 4.dp)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            d.customer.phone?.let { p ->
                                OutlinedButton(onClick = { openUri(ctx, "tel:${p.filter { c -> c.isDigit() || c == '+' }}") }) {
                                    Icon(Icons.Filled.Call, contentDescription = null)
                                    Text(stringResource(R.string.provider_call), Modifier.padding(start = 6.dp))
                                }
                            }
                            d.customer.email?.let { e ->
                                OutlinedButton(onClick = { openUri(ctx, "mailto:$e") }) {
                                    Icon(Icons.Filled.Email, contentDescription = null)
                                    Text(stringResource(R.string.provider_email_action), Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }
                item("assets") {
                    Panel(
                        title = stringResource(R.string.provider_assets),
                        trailing = { TextButton(onClick = { adding = true }) { Text(stringResource(R.string.provider_add_asset)) } },
                    ) {
                        if (d.assets.isEmpty()) Caption(stringResource(R.string.provider_assets_empty))
                        d.assets.forEachIndexed { i, a ->
                            if (i > 0) CardDivider()
                            AssetRow(a)
                        }
                    }
                }
                if (d.jobs.isNotEmpty()) {
                    item("jobs") {
                        Panel(title = stringResource(R.string.provider_jobs)) {
                            d.jobs.forEachIndexed { i, j ->
                                if (i > 0) CardDivider()
                                DocRow(j.title?.let { "${j.number} · $it" } ?: j.number, j.scheduledFor?.let(Fmt::dateTime), j.status, null) { onJob(j.id) }
                            }
                        }
                    }
                }
                if (d.quotes.isNotEmpty()) {
                    item("quotes") {
                        Panel(title = stringResource(R.string.provider_quotes)) {
                            d.quotes.forEachIndexed { i, q ->
                                if (i > 0) CardDivider()
                                DocRow(q.number, q.title, q.status, Fmt.money(q.totalMinor, q.currency)) { onQuote(q.id) }
                            }
                        }
                    }
                }
                if (d.invoices.isNotEmpty()) {
                    item("invoices") {
                        Panel(title = stringResource(R.string.provider_invoices)) {
                            d.invoices.forEachIndexed { i, inv ->
                                if (i > 0) CardDivider()
                                DocRow(inv.number, inv.dueDate?.let { stringResource(R.string.provider_due, Fmt.date(it)) }, inv.status, Fmt.money(inv.totalMinor, inv.currency)) { onInvoice(inv.id) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) AssetDialog(busy, onSubmit = vm::addAsset, onDismiss = { adding = false })
}

@Composable
private fun AssetRow(a: CustomerAsset) {
    val parts = listOfNotNull(
        serviceTypeLabel(a.serviceType),
        a.summary,
        a.vinSerial?.let { stringResource(R.string.provider_vin_short, it) },
        a.lastMeter?.let { "${Fmt.number(it)} ${meterUnitLabel(a.meterUnit ?: "hours")}" },
    )
    DocRow(a.name, parts.joinToString(" · "), null, if (a.ownerAssetId != null) stringResource(R.string.provider_linked) else null, null)
}
