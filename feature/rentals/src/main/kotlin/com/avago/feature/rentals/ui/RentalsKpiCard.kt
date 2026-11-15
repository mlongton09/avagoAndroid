package com.avago.feature.rentals.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.rental.Money
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.SectionCard
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalsOnline
import com.avago.feature.rentals.domain.KpiSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * G4 KPI card on the rentals home (`GET /analytics/kpi-summary?days=30`), only for staff with
 * `rental.reports`. The last answer is cached, so the card shows offline; it is hidden while
 * there is nothing to show (no permission, never loaded, or every value still null).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RentalsKpiViewModel @Inject constructor(
    private val online: RentalsOnline,
    private val permissions: PermissionsManager,
) : ViewModel() {

    val kpi: StateFlow<KpiSummary?> = permissions.observeCan(PERMISSION).flatMapLatest { ok ->
        if (!ok) flowOf(null) else online.observe(online.kpiKey).map { KpiSummary.parse(it?.data)?.takeIf { k -> !k.isEmpty } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh()
    }

    /** Pull-to-refresh on the home; a failure keeps the cached card. */
    fun refresh() {
        if (permissions.can(PERMISSION)) viewModelScope.launch { online.refreshKpi() }
    }

    companion object {
        const val PERMISSION = "rental.reports"
    }
}

@Composable
internal fun RentalsKpiCard(viewModel: RentalsKpiViewModel = hiltViewModel()) {
    val kpi by viewModel.kpi.collectAsStateWithLifecycle()
    kpi?.let { KpiCard(it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KpiCard(k: KpiSummary) {
    SectionCard(title = pluralStringResource(R.plurals.rentals_kpi_title, k.days, k.days), contentPadding = true) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
        ) {
            KpiSummary.percent(k.utilizationBp)?.let {
                Kpi(stringResource(R.string.rentals_kpi_utilization), it, KpiSummary.trendPercent(k.utilizationBp, k.prevUtilizationBp))
            }
            k.revenueMinor?.let { r ->
                Kpi(
                    stringResource(R.string.rentals_kpi_revenue),
                    k.currency?.let { formatMoney(Money.of(r, it)) } ?: r.toString(),
                    KpiSummary.trendPercent(k.revenueMinor, k.prevRevenueMinor),
                )
            }
            k.checkouts?.let { Kpi(stringResource(R.string.rentals_kpi_checkouts), it.toString(), KpiSummary.trendPercent(k.checkouts, k.prevCheckouts)) }
            if (k.unitsOnRent != null && k.units != null) {
                Kpi(stringResource(R.string.rentals_kpi_on_rent), stringResource(R.string.rentals_kpi_of, k.unitsOnRent, k.units), null)
            }
            k.openQuotes?.let { q ->
                val sub = k.quotesExpiring?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.rentals_kpi_quotes_expiring, it.toInt(), it) }
                Kpi(stringResource(R.string.rentals_kpi_open_quotes), q.toString(), null, sub)
            }
            KpiSummary.percent(k.winRateBp)?.let { Kpi(stringResource(R.string.rentals_kpi_win_rate), it, null) }
            k.arOverdueMinor?.takeIf { it > 0 }?.let { a ->
                Kpi(stringResource(R.string.rentals_kpi_ar_overdue), k.currency?.let { formatMoney(Money.of(a, it)) } ?: a.toString(), null, danger = true)
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, trend: Int?, sub: String? = null, danger: Boolean = false) {
    val trendText = trend?.let { stringResource(R.string.rentals_kpi_trend, (if (it > 0) "+" else "") + it) }
    Column(
        Modifier.widthIn(min = 120.dp).clearAndSetSemantics {
            contentDescription = listOfNotNull(label, value, trendText, sub).joinToString(", ")
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        listOfNotNull(trendText, sub).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(name = "KPI – light", showBackground = true, widthDp = 380)
@Preview(name = "KPI – dark", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "KPI – 200% font", showBackground = true, widthDp = 380, fontScale = 2f)
@Preview(name = "KPI – German", showBackground = true, widthDp = 380, locale = "de")
@Composable
private fun KpiCardPreview() {
    AvagoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(vertical = AvagoSpacing.sm)) {
                KpiCard(
                    KpiSummary(
                        days = 30, currency = "EUR", utilizationBp = 6_420, prevUtilizationBp = 6_000, revenueMinor = 1_234_500,
                        prevRevenueMinor = 1_100_000, checkouts = 42, prevCheckouts = 50, winRateBp = 3_500, unitsOnRent = 18, units = 40,
                        openQuotes = 7, quotesExpiring = 2, arOverdueMinor = 98_000, refreshedThrough = null,
                    ),
                )
            }
        }
    }
}
