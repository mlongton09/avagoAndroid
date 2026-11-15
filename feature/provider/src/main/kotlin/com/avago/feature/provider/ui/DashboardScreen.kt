package com.avago.feature.provider.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.cardListItemColors
import com.avago.feature.provider.R
import com.avago.feature.provider.data.Dashboard

@Composable
fun ProviderDashboardScreen(
    onBack: (() -> Unit)?,
    onCustomers: () -> Unit,
    onJobs: () -> Unit,
    onQuotes: () -> Unit,
    onInvoices: () -> Unit,
    onCustomer: (String) -> Unit,
    onJob: (String) -> Unit,
    onQuote: (String) -> Unit,
    onInvoice: (String) -> Unit,
    vm: ProviderDashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val name by vm.businessName.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    NoticeEffect(vm.notices, snackbar)
    ProviderScaffold(
        title = name ?: stringResource(R.string.provider_title),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.provider_refresh)) }
        },
    ) { inner ->
        LoadBox(state, vm::refresh, inner) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(inner), verticalArrangement = ListGap) {
                item("stats") { StatsPanel(d) }
                item("nav") {
                    Panel {
                        NavRow(Icons.Filled.Build, StatusTone.Accent, stringResource(R.string.provider_jobs), stringResource(R.string.provider_jobs_sub), onJobs)
                        CardDivider()
                        NavRow(Icons.Filled.People, StatusTone.Info, stringResource(R.string.provider_customers), stringResource(R.string.provider_customers_sub), onCustomers)
                        CardDivider()
                        NavRow(Icons.Filled.Description, StatusTone.Neutral, stringResource(R.string.provider_quotes), stringResource(R.string.provider_quotes_sub), onQuotes)
                        CardDivider()
                        NavRow(Icons.AutoMirrored.Filled.ReceiptLong, StatusTone.Success, stringResource(R.string.provider_invoices), stringResource(R.string.provider_invoices_sub), onInvoices)
                    }
                }
                item("today") {
                    Panel(title = stringResource(R.string.provider_today_jobs)) {
                        if (d.todayJobs.isEmpty()) Caption(stringResource(R.string.provider_today_empty))
                        d.todayJobs.forEachIndexed { i, j ->
                            if (i > 0) CardDivider()
                            DocRow(
                                title = j.title?.let { "${j.number} · $it" } ?: j.number,
                                subtitle = listOfNotNull(Fmt.time(j.scheduledFor), j.customerName, j.assetName).joinToString(" · ").ifBlank { null },
                                status = j.status,
                                trailing = null,
                            ) { onJob(j.id) }
                        }
                    }
                }
                if (d.openQuotes.isNotEmpty()) {
                    item("quotes") {
                        Panel(title = stringResource(R.string.provider_open_quotes)) {
                            d.openQuotes.forEachIndexed { i, q ->
                                if (i > 0) CardDivider()
                                DocRow(q.number, q.customerName, q.status, Fmt.money(q.totalMinor, q.currency)) { onQuote(q.id) }
                            }
                        }
                    }
                }
                if (d.unpaidInvoices.isNotEmpty()) {
                    item("invoices") {
                        Panel(title = stringResource(R.string.provider_unpaid_invoices)) {
                            d.unpaidInvoices.forEachIndexed { i, inv ->
                                if (i > 0) CardDivider()
                                val due = inv.date?.let { stringResource(R.string.provider_due, Fmt.date(it)) }
                                DocRow(inv.number, listOfNotNull(inv.customerName, due).joinToString(" · ").ifBlank { null }, null, Fmt.money(inv.totalMinor, inv.currency)) { onInvoice(inv.id) }
                            }
                        }
                    }
                }
                if (d.recentCustomers.isNotEmpty()) {
                    item("recent") {
                        Panel(title = stringResource(R.string.provider_recent_customers)) {
                            d.recentCustomers.forEachIndexed { i, c ->
                                if (i > 0) CardDivider()
                                DocRow(c.name, if (c.linked) stringResource(R.string.provider_linked) else null, null, null) { onCustomer(c.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(d: Dashboard) {
    Panel(title = stringResource(R.string.provider_overview)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(Fmt.money(d.unpaidTotalMinor, d.currency), stringResource(R.string.provider_stat_unpaid, d.unpaidCount))
            Stat(d.customerCount.toString(), stringResource(R.string.provider_stat_customers, d.linkedCustomerCount))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(d.assetsServiced.toString(), stringResource(R.string.provider_stat_assets))
            Stat(d.remindersDue.toString(), stringResource(R.string.provider_stat_reminders))
        }
    }
}

@Composable
private fun RowScope.Stat(value: String, label: String) {
    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Caption(label)
    }
}

@Composable
private fun NavRow(icon: ImageVector, tone: StatusTone, title: String, sub: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        leadingContent = { LeadingBadge(icon, tone) },
        colors = cardListItemColors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
