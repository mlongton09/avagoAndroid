package com.avago.feature.provider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.EmptyStateView
import com.avago.feature.provider.R
import com.avago.feature.provider.data.Invoice
import com.avago.feature.provider.data.Load
import com.avago.feature.provider.data.Quote

@Composable
private fun <T> DocListScreen(
    title: String,
    statuses: List<String>,
    state: Load<List<T>>,
    filter: String?,
    onFilter: (String?) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    emptyIcon: ImageVector,
    emptyText: String,
    row: @Composable (T) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    ProviderScaffold(title = title, onBack = onBack, snackbar = snackbar) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            StatusFilterRow(statuses, filter, onFilter)
            LoadBox(state, onRetry, PaddingValues()) { list ->
                if (list.isEmpty()) {
                    EmptyStateView(emptyIcon, emptyText)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Panel {
                                list.forEachIndexed { i, it ->
                                    if (i > 0) CardDivider()
                                    row(it)
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
fun ProviderQuotesScreen(onBack: () -> Unit, onQuote: (String) -> Unit, vm: QuotesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    DocListScreen(
        title = stringResource(R.string.provider_quotes),
        statuses = Quote.STATUSES,
        state = state,
        filter = filter,
        onFilter = vm::setFilter,
        onRetry = vm::refresh,
        onBack = onBack,
        emptyIcon = Icons.Filled.Description,
        emptyText = stringResource(R.string.provider_quotes_empty),
    ) { q ->
        DocRow(
            title = q.title?.let { "${q.number} · $it" } ?: q.number,
            subtitle = listOfNotNull(q.customerName, q.validUntil?.let { stringResource(R.string.provider_valid_until, Fmt.date(it)) }).joinToString(" · ").ifBlank { null },
            status = q.status,
            trailing = Fmt.money(q.totalMinor, q.currency),
        ) { onQuote(q.id) }
    }
}

@Composable
fun ProviderInvoicesScreen(onBack: () -> Unit, onInvoice: (String) -> Unit, vm: InvoicesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    DocListScreen(
        title = stringResource(R.string.provider_invoices),
        statuses = Invoice.STATUSES,
        state = state,
        filter = filter,
        onFilter = vm::setFilter,
        onRetry = vm::refresh,
        onBack = onBack,
        emptyIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        emptyText = stringResource(R.string.provider_invoices_empty),
    ) { inv ->
        DocRow(
            title = inv.number,
            subtitle = listOfNotNull(inv.customerName, inv.dueDate?.let { stringResource(R.string.provider_due, Fmt.date(it)) }).joinToString(" · ").ifBlank { null },
            status = inv.status,
            trailing = Fmt.money(inv.totalMinor, inv.currency),
        ) { onInvoice(inv.id) }
    }
}

@Composable
fun ProviderQuoteScreen(onBack: () -> Unit, onCustomer: (String) -> Unit, onJob: (String) -> Unit, vm: QuoteDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    NoticeEffect(vm.notices, snackbar)
    EventEffect(vm.events, snackbar)
    ProviderScaffold(title = state.valueOrNull()?.quote?.number ?: stringResource(R.string.provider_quote), onBack = onBack, snackbar = snackbar) { inner ->
        LoadBox(state, vm::refresh, inner) { d ->
            val q = d.quote
            LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("head") {
                    Panel(title = q.title ?: stringResource(R.string.provider_quote), trailing = { StatusChip(q.status) }) {
                        q.customerName?.let { DocRow(it, d.customer?.email ?: d.customer?.phone, null, null, q.customerId?.let { id -> { onCustomer(id) } }) }
                        q.validUntil?.let { KeyValue(stringResource(R.string.provider_valid_until_label), Fmt.date(it)) }
                        q.sentAt?.let { KeyValue(stringResource(R.string.provider_sent), Fmt.dateTime(it)) }
                        q.declineReason?.let { KeyValue(stringResource(R.string.provider_decline_reason), it) }
                        q.notes?.let { Caption(it, Modifier.padding(top = 4.dp)) }
                    }
                }
                item("lines") { LinesPanel(q.lines, d.totals, q.currency) }
                item("actions") {
                    Panel {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (q.canSend) {
                                Button(onClick = vm::send, enabled = !busy && q.lines.isNotEmpty()) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Text(stringResource(if (q.status == "sent") R.string.provider_resend else R.string.provider_send_quote), Modifier.padding(start = 6.dp))
                                }
                            }
                            q.jobId?.let { id -> OutlinedButton(onClick = { onJob(id) }) { Text(stringResource(R.string.provider_view_job)) } }
                        }
                        if (q.canSend && q.lines.isEmpty()) Caption(stringResource(R.string.provider_err_quote_empty))
                        if (q.canSend) Caption(stringResource(R.string.provider_send_hint), Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderInvoiceScreen(onBack: () -> Unit, onCustomer: (String) -> Unit, onJob: (String) -> Unit, vm: InvoiceDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var paying by rememberSaveable { mutableStateOf(false) }
    NoticeEffect(vm.notices, snackbar)
    EventEffect(vm.events, snackbar)
    ProviderScaffold(title = state.valueOrNull()?.invoice?.number ?: stringResource(R.string.provider_invoice), onBack = onBack, snackbar = snackbar) { inner ->
        LoadBox(state, vm::refresh, inner) { d ->
            val inv = d.invoice
            LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("head") {
                    Panel(title = stringResource(R.string.provider_invoice), trailing = { StatusChip(inv.status) }) {
                        inv.customerName?.let { DocRow(it, d.customer?.email ?: d.customer?.phone, null, null, inv.customerId?.let { id -> { onCustomer(id) } }) }
                        inv.dueDate?.let { KeyValue(stringResource(R.string.provider_due_label), Fmt.date(it)) }
                        inv.sentAt?.let { KeyValue(stringResource(R.string.provider_sent), Fmt.dateTime(it)) }
                        inv.paidAt?.let { KeyValue(stringResource(R.string.provider_paid_at), Fmt.dateTime(it)) }
                        inv.paidMethod?.let { KeyValue(stringResource(R.string.provider_paid_method), it.replace('_', ' ')) }
                        inv.paidReference?.let { KeyValue(stringResource(R.string.provider_paid_reference), it) }
                        if (inv.canMarkPaid) {
                            Caption(
                                stringResource(if (d.onlinePaymentAvailable) R.string.provider_online_pay_on else R.string.provider_online_pay_off),
                                Modifier.padding(top = 4.dp),
                            )
                        }
                        inv.notes?.let { Caption(it, Modifier.padding(top = 4.dp)) }
                    }
                }
                item("lines") { LinesPanel(inv.lines, d.totals, inv.currency) }
                item("actions") {
                    Panel {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (inv.canSend) {
                                OutlinedButton(onClick = vm::send, enabled = !busy) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Text(stringResource(if (inv.status == "sent") R.string.provider_resend else R.string.provider_send_invoice), Modifier.padding(start = 6.dp))
                                }
                            }
                            if (inv.canMarkPaid) {
                                Button(onClick = { paying = true }, enabled = !busy) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                    Text(stringResource(R.string.provider_mark_paid), Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        inv.jobId?.let { id -> OutlinedButton(onClick = { onJob(id) }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.provider_view_job)) } }
                        if (inv.canMarkPaid && inv.totalMinor <= 0) Caption(stringResource(R.string.provider_err_invoice_zero))
                    }
                }
            }
        }
    }
    if (paying) MarkPaidDialog(busy, onSubmit = vm::markPaid, onDismiss = { paying = false })
}
