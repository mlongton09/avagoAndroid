package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.network.AvagoServiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

// ── Data models ───────────────────────────────────────────────────────────────

data class CostEntry(
    val label: String,
    val totalCost: Double,
    val periodLabel: String,
    val breakdown: Map<String, Double> = emptyMap(),
)

enum class CostPeriod(val label: String, val months: Int) {
    THREE_MONTHS("3 Months", 3),
    SIX_MONTHS("6 Months", 6),
    ONE_YEAR("1 Year", 12),
}

data class CostReportState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedPeriod: CostPeriod = CostPeriod.THREE_MONTHS,
    val entries: List<CostEntry> = emptyList(),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CostReportViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    @Suppress("UnusedPrivateMember")
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val _state = MutableStateFlow(CostReportState())
    val state: StateFlow<CostReportState> = _state.asStateFlow()

    fun loadReport(period: CostPeriod = _state.value.selectedPeriod, isRefresh: Boolean = false) {
        val accountId = identity.getActiveAccountId() ?: return
        _state.update {
            it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                selectedPeriod = period,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                // No dedicated cost-report endpoint exists in AvagoServiceClient yet.
                // We derive cost totals from local log entries grouped by asset.
                // TODO: replace with a real API call (e.g. serviceClient.getCostReport(accountId, period.months))
                //       once the endpoint is available on the back-end.
                val now = System.currentTimeMillis()
                val cutoff = now - period.months.toLong() * 30L * 24L * 60L * 60L * 1000L

                val logs = db.logDao().observeAll(accountId)
                    .first()
                    .filter { it.entryDate >= cutoff }

                // Group by asset and sum costs
                val assetIds = logs.map { it.assetId }.distinct()
                val entries = assetIds.mapNotNull { assetId ->
                    val asset = db.assetDao().getById(assetId) ?: return@mapNotNull null
                    val totalCost = logs
                        .filter { it.assetId == assetId }
                        .sumOf { it.cost ?: 0.0 }
                    if (totalCost == 0.0) return@mapNotNull null
                    CostEntry(
                        label = asset.name,
                        totalCost = totalCost,
                        periodLabel = period.label,
                    )
                }.sortedByDescending { it.totalCost }

                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        entries = entries,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[CostReportViewModel] loadReport failed")
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Failed to load cost report",
                    )
                }
            }
        }
    }

    fun selectPeriod(period: CostPeriod) {
        if (period != _state.value.selectedPeriod) {
            loadReport(period)
        }
    }

    fun refresh() = loadReport(isRefresh = true)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostReportScreen(
    onBack: () -> Unit,
    viewModel: CostReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadReport()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cost Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    CostReportContent(
                        state = state,
                        onPeriodSelected = viewModel::selectPeriod,
                    )
                }
            }
        }
    }
}

@Composable
private fun CostReportContent(
    state: CostReportState,
    onPeriodSelected: (CostPeriod) -> Unit,
) {
    val maxCost = state.entries.maxOfOrNull { it.totalCost } ?: 1.0

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Period selector
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(CostPeriod.entries) { period ->
                    FilterChip(
                        selected = state.selectedPeriod == period,
                        onClick = { onPeriodSelected(period) },
                        label = { Text(period.label) },
                    )
                }
            }
        }

        if (state.entries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No cost data for ${state.selectedPeriod.label}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            item {
                Text(
                    text = "By Asset  ·  ${state.selectedPeriod.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(state.entries) { entry ->
                CostEntryRow(entry = entry, maxCost = maxCost)
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$${"%,.2f".format(state.entries.sumOf { it.totalCost })}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CostEntryRow(entry: CostEntry, maxCost: Double) {
    val fillFraction = (entry.totalCost / maxCost).coerceIn(0.0, 1.0).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$${"%,.2f".format(entry.totalCost)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            // Bar indicator proportional to max cost
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            if (fillFraction > 0f) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${(fillFraction * 100).roundToInt()}% of highest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
