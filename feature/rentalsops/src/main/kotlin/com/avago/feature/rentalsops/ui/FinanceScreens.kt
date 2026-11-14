package com.avago.feature.rentalsops.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.chart.ChartColors
import com.avago.core.design.chart.ChartFormat
import com.avago.core.design.chart.ChartLayout
import com.avago.core.design.chart.ChartTheme
import com.avago.core.design.chart.chartTextPaint
import com.avago.core.design.chart.drawChartEmpty
import com.avago.core.design.chart.drawChartSkeleton
import com.avago.core.design.chart.drawChartZeroLine
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.data.RentalOpsRepository.Fetch
import com.avago.feature.rentalsops.domain.AgingBucket
import com.avago.feature.rentalsops.domain.AgingReport
import com.avago.feature.rentalsops.domain.AgingRow
import com.avago.feature.rentalsops.domain.MinorMoney
import com.avago.feature.rentalsops.domain.Statement
import com.avago.feature.rentalsops.sync.RentalOpsOutbox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared load state for the two read-only finance views. */
data class FinanceState<T>(
    val loading: Boolean = false,
    val data: T? = null,
    /** Epoch millis of the shown copy; non-null with [stale] when it is a cached fallback. */
    val fetchedAt: Long? = null,
    val stale: Boolean = false,
    val failure: Int? = null,
    val failed: Boolean = false,
)

private fun <T> Fetch.toState(parse: (kotlinx.serialization.json.JsonObject) -> T): FinanceState<T> = when (this) {
    is Fetch.Ok -> FinanceState(data = parse(data.body), fetchedAt = data.fetchedAt)
    is Fetch.Failed -> FinanceState(
        data = cached?.let { parse(it.body) },
        fetchedAt = cached?.fetchedAt,
        stale = cached != null,
        failure = status,
        failed = true,
    )
}

@Composable
private fun FailureBanner(s: FinanceState<*>) {
    if (!s.failed) return
    val msg = when {
        s.failure == 403 -> stringResource(R.string.rentalops_err_forbidden)
        s.failure in RentalOpsOutbox.NOT_DEPLOYED && s.data == null -> stringResource(R.string.rentalops_err_unavailable)
        s.stale -> stringResource(R.string.rentalops_offline_cached, Fmt.millis(s.fetchedAt ?: 0))
        else -> stringResource(R.string.rentalops_err_offline)
    }
    OfflineBanner(msg)
}

// ---- Statement ---------------------------------------------------------------------------

@HiltViewModel
class StatementViewModel @Inject constructor(private val repo: RentalOpsRepository) : ViewModel() {
    val customers = repo.observeCustomers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _state = MutableStateFlow(FinanceState<Statement>())
    val state: StateFlow<FinanceState<Statement>> = _state.asStateFlow()
    private val _selected = MutableStateFlow<RentalOpsRepository.Customer?>(null)
    val selected: StateFlow<RentalOpsRepository.Customer?> = _selected.asStateFlow()

    fun select(c: RentalOpsRepository.Customer) {
        _selected.value = c
        load()
    }

    fun load() = viewModelScope.launch {
        val c = _selected.value ?: return@launch
        _state.value = FinanceState(loading = true)
        _state.value = repo.statement(c.id).toState(Statement::parse)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementScreen(onBack: () -> Unit, vm: StatementViewModel = hiltViewModel()) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { OpsTopBar(stringResource(R.string.rentalops_statement_title), onBack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(AvagoSpacing.lg)) {
                    OutlinedTextField(
                        value = if (expanded) query else selected?.name.orEmpty(),
                        onValueChange = { query = it; expanded = true },
                        label = { Text(stringResource(R.string.rentalops_customer)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    )
                    val matches = customers.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }.take(50)
                    ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
                        matches.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { vm.select(c); query = ""; expanded = false },
                                modifier = Modifier.heightIn(min = MinTouch),
                            )
                        }
                    }
                }
                if (customers.isEmpty()) {
                    Text(
                        stringResource(R.string.rentalops_no_customers),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = AvagoSpacing.lg),
                    )
                }
                FailureBanner(s)
                if (s.loading) CircularProgressIndicator(Modifier.padding(AvagoSpacing.xxl))
            }
            val st = s.data ?: return@LazyColumn
            item {
                Card(Modifier.fillMaxWidth().padding(AvagoSpacing.lg)) {
                    Column(Modifier.padding(AvagoSpacing.lg)) {
                        Text(stringResource(R.string.rentalops_balance), style = MaterialTheme.typography.titleSmall)
                        if (st.balanceByCurrency.isEmpty()) Text(ChartFormat.money(0.0, "USD"))
                        st.balanceByCurrency.toSortedMap().forEach { (cur, minor) ->
                            Text(ChartFormat.money(MinorMoney.toMajor(minor, cur), cur), style = MaterialTheme.typography.headlineSmall)
                        }
                        st.creditMinor?.takeIf { it != 0L }?.let { cr ->
                            val cur = st.balanceByCurrency.keys.singleOrNull() ?: st.invoices.firstOrNull()?.currency ?: "USD"
                            Text(stringResource(R.string.rentalops_credit, ChartFormat.money(MinorMoney.toMajor(cr, cur), cur)))
                        }
                    }
                }
                SectionHeader(stringResource(R.string.rentalops_invoices))
            }
            if (st.invoices.isEmpty()) item { EmptyState(stringResource(R.string.rentalops_no_invoices)) }
            items(st.invoices, key = { it.id }) { inv ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MinTouch).padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(inv.number ?: inv.id.take(8), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.rentalops_invoice_dates, Fmt.date(inv.issuedAt), Fmt.date(inv.dueAt)) + (inv.status?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(ChartFormat.money(MinorMoney.toMajor(inv.balanceMinor, inv.currency), inv.currency), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.rentalops_of_total, ChartFormat.money(MinorMoney.toMajor(inv.totalMinor, inv.currency), inv.currency)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

// ---- AR aging ----------------------------------------------------------------------------

@HiltViewModel
class AgingViewModel @Inject constructor(private val repo: RentalOpsRepository) : ViewModel() {
    private val _state = MutableStateFlow(FinanceState<AgingReport>())
    val state: StateFlow<FinanceState<AgingReport>> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        _state.value = repo.arAging().toState(AgingReport::parse)
    }
}

@Composable
internal fun bucketLabel(b: AgingBucket): String = stringResource(
    when (b) {
        AgingBucket.CURRENT -> R.string.rentalops_bucket_current
        AgingBucket.D1_30 -> R.string.rentalops_bucket_1_30
        AgingBucket.D31_60 -> R.string.rentalops_bucket_31_60
        AgingBucket.D61_90 -> R.string.rentalops_bucket_61_90
        AgingBucket.OVER_90 -> R.string.rentalops_bucket_over_90
    },
)

@Composable
fun AgingScreen(onBack: () -> Unit, vm: AgingViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showTable by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            OpsTopBar(stringResource(R.string.rentalops_aging_title), onBack) {
                TextButton(onClick = { vm.load() }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentalops_refresh)) }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { FailureBanner(s) }
            val report = s.data
            if (report == null) {
                item {
                    if (s.loading) {
                        AgingChart(null, loading = true)
                    } else if (!s.failed) {
                        EmptyState(stringResource(R.string.rentalops_aging_empty))
                    }
                }
                return@LazyColumn
            }
            report.asOf?.let { item { Text(stringResource(R.string.rentalops_as_of, it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = AvagoSpacing.lg)) } }
            if (report.totals.isEmpty()) item { EmptyState(stringResource(R.string.rentalops_aging_empty)) }
            report.totals.forEach { row ->
                item(key = "chart-${row.currency}") {
                    Card(Modifier.fillMaxWidth().padding(AvagoSpacing.lg)) {
                        Column(Modifier.padding(AvagoSpacing.lg)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.rentalops_aging_total, row.currency), style = MaterialTheme.typography.labelLarge)
                                    Text(money(row.total, row.currency), style = MaterialTheme.typography.headlineSmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(R.string.rentalops_over_60), style = MaterialTheme.typography.labelLarge)
                                    Text(ChartFormat.percent(report.over60Share(row.currency)), style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                            AgingChart(row, loading = false, modifier = Modifier.padding(top = AvagoSpacing.md))
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = MinTouch).toggleable(value = showTable, role = Role.Switch, onValueChange = { showTable = it }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.rentalops_show_table), Modifier.weight(1f))
                                Switch(checked = showTable, onCheckedChange = null)
                            }
                            if (showTable) BucketTable(row)
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.rentalops_customers)) }
            items(report.customers, key = { it.customerId + it.currency }) { c ->
                val key = c.customerId + c.currency
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(role = Role.Button) { expanded = if (expanded == key) null else key }
                            .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm)
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            val over = c.row[AgingBucket.D61_90] + c.row[AgingBucket.OVER_90]
                            if (over > 0) Text(stringResource(R.string.rentalops_over_60_amount, money(over, c.currency)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(money(c.row.total, c.currency), fontWeight = FontWeight.SemiBold)
                    }
                    if (expanded == key) {
                        BucketTable(c.row, Modifier.padding(horizontal = AvagoSpacing.lg))
                        c.invoices.forEach { inv ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.xxl, vertical = AvagoSpacing.xs).semantics(mergeDescendants = true) {}) {
                                Text(inv.number ?: inv.invoiceId.take(8), Modifier.weight(1f))
                                Text(bucketLabel(inv.bucket), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(money(inv.balanceMinor, c.currency), textAlign = TextAlign.End)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun money(minor: Long, currency: String) = ChartFormat.money(MinorMoney.toMajor(minor, currency), currency)

@Composable
private fun BucketTable(row: AgingRow, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        AgingBucket.entries.forEach { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {}) {
                Text(bucketLabel(b), Modifier.weight(1f))
                Text(money(row[b], row.currency), textAlign = TextAlign.End)
            }
        }
    }
}

/**
 * Column chart per chart-style-guide §AR aging: fixed bucket order, aging ramp colours,
 * zero baseline, single series → no legend, data labels only when they fit.
 */
@Composable
private fun AgingChart(row: AgingRow?, loading: Boolean, modifier: Modifier = Modifier) {
    val colors = ChartTheme.colors()
    val labels = AgingBucket.entries.map { bucketLabel(it) }
    val empty = stringResource(R.string.rentalops_aging_empty)
    val a11y = row?.let { r ->
        AgingBucket.entries.indices.joinToString("; ") { i ->
            val b = AgingBucket.entries[i]
            "${labels[i]}: ${money(r[b], r.currency)}"
        }
    } ?: empty
    Box(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = a11y },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when {
                loading -> drawChartSkeleton(colors, AgingBucket.entries.size)
                row == null || row.total == 0L -> drawChartEmpty(empty, colors)
                else -> drawAgingColumns(row, labels, colors)
            }
        }
    }
}

private fun DrawScope.drawAgingColumns(row: AgingRow, labels: List<String>, colors: ChartColors) {
    val n = AgingBucket.entries.size
    val tick = chartTextPaint(colors.textMuted)
    val labelH = tick.textSize * 1.6f
    val dataPaint = chartTextPaint(colors.text, ChartTheme.DataLabelTextSize)
    val top = dataPaint.textSize * 1.6f
    val baseY = size.height - labelH
    val plotH = (baseY - top).coerceAtLeast(1f)
    val slot = size.width / n
    val barW = (slot * 0.6f).coerceAtMost(ChartTheme.BarMaxWidth.toPx())
    val max = AgingBucket.entries.maxOf { row[it] }.coerceAtLeast(1L).toDouble()
    val step = ChartLayout.labelStep(labels.maxOf { tick.measureText(it) } + 8.dp.toPx(), slot)
    AgingBucket.entries.forEachIndexed { i, b ->
        val v = row[b].coerceAtLeast(0L)
        val h = (v / max * plotH).toFloat()
        val x = slot * i + (slot - barW) / 2f
        if (h > 0f) {
            drawRoundRect(
                color = colors.aging[i.coerceAtMost(colors.aging.lastIndex)],
                topLeft = Offset(x, baseY - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(ChartTheme.BarCornerRadius.toPx()),
            )
        }
        val label = ChartFormat.moneyCompact(MinorMoney.toMajor(v, row.currency), row.currency)
        if (ChartLayout.dataLabelFits(dataPaint.measureText(label), slot, n)) {
            drawContext.canvas.nativeCanvas.drawText(label, slot * i + slot / 2f, baseY - h - dataPaint.textSize * 0.4f, dataPaint)
        }
        if (i % step == 0) {
            drawContext.canvas.nativeCanvas.drawText(labels[i], slot * i + slot / 2f, size.height - tick.textSize * 0.3f, tick)
        }
    }
    drawChartZeroLine(baseY, 0f, size.width, colors)
}
