package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── Category icon map ─────────────────────────────────────────────────────────

val CATEGORY_ICONS = mapOf(
    "parts" to "🔩",
    "labor" to "👷",
    "fuel" to "⛽",
    "oil" to "🛢️",
    "tires" to "🔘",
    "inspection" to "🔍",
    "cleaning" to "🧹",
    "other" to "📦",
)

// ── Data models ───────────────────────────────────────────────────────────────

enum class CategoryFilterMode(val label: String) {
    BY_DATE("By Date"),
    BY_METER("By Meter"),
}

enum class CategorySortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    HIGHEST("Highest Cost"),
    LOWEST("Lowest Cost"),
}

data class CategoryCostEntry(
    val entryId: String,
    val category: String,
    val label: String,
    val cost: Double,
    val date: Long,
    val meterReading: Double?,
)

data class CategoryReportState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val filterMode: CategoryFilterMode = CategoryFilterMode.BY_DATE,
    val sortOrder: CategorySortOrder = CategorySortOrder.NEWEST,
    val entries: List<CategoryCostEntry> = emptyList(),
    val assetName: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CategoryReportViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    @Suppress("UnusedPrivateMember")
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryReportState())
    val state: StateFlow<CategoryReportState> = _state.asStateFlow()

    private var currentAssetId: String? = null

    fun loadForAsset(assetId: String, isRefresh: Boolean = false) {
        val accountId = identity.getActiveAccountId() ?: return
        currentAssetId = assetId
        _state.update {
            it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)

                // Load asset name
                val asset = db.assetDao().getById(assetId)

                // Load all log entries for this asset that have a cost
                // No dedicated category-report API endpoint exists yet in AvagoServiceClient.
                // TODO: replace with serviceClient.getCategoryReport(accountId, assetId)
                //       once the endpoint is available.
                val logs = db.logDao().observeAll(accountId)
                    .first()
                    .filter { it.assetId == assetId && (it.cost ?: 0.0) > 0.0 }

                val entries = logs.map { log ->
                    CategoryCostEntry(
                        entryId = log.entryId,
                        category = log.category ?: "other",
                        label = log.title,
                        cost = log.cost ?: 0.0,
                        date = log.entryDate,
                        meterReading = log.odometerValue,
                    )
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        entries = applySorting(entries, it.sortOrder, it.filterMode),
                        assetName = asset?.name,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[CategoryReportViewModel] loadForAsset failed")
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Failed to load report",
                    )
                }
            }
        }
    }

    fun setFilterMode(mode: CategoryFilterMode) {
        _state.update {
            // When switching to BY_METER, switch sort to HIGHEST; BY_DATE → NEWEST
            val newSort = when (mode) {
                CategoryFilterMode.BY_DATE -> CategorySortOrder.NEWEST
                CategoryFilterMode.BY_METER -> CategorySortOrder.HIGHEST
            }
            it.copy(
                filterMode = mode,
                sortOrder = newSort,
                entries = applySorting(it.entries, newSort, mode),
            )
        }
    }

    fun setSortOrder(order: CategorySortOrder) {
        _state.update {
            it.copy(
                sortOrder = order,
                entries = applySorting(it.entries, order, it.filterMode),
            )
        }
    }

    fun refresh() {
        currentAssetId?.let { loadForAsset(it, isRefresh = true) }
    }

    private fun applySorting(
        entries: List<CategoryCostEntry>,
        sort: CategorySortOrder,
        mode: CategoryFilterMode,
    ): List<CategoryCostEntry> = when {
        mode == CategoryFilterMode.BY_DATE && sort == CategorySortOrder.NEWEST ->
            entries.sortedByDescending { it.date }
        mode == CategoryFilterMode.BY_DATE && sort == CategorySortOrder.OLDEST ->
            entries.sortedBy { it.date }
        sort == CategorySortOrder.HIGHEST ->
            entries.sortedByDescending { it.cost }
        sort == CategorySortOrder.LOWEST ->
            entries.sortedBy { it.cost }
        else -> entries.sortedByDescending { it.date }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryReportScreen(
    assetId: String,
    onBack: () -> Unit,
    viewModel: CategoryReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(assetId) {
        viewModel.loadForAsset(assetId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Category Report")
                        if (state.assetName != null) {
                            Text(
                                text = state.assetName!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
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
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    CategoryReportContent(
                        state = state,
                        onFilterModeChanged = viewModel::setFilterMode,
                        onSortOrderChanged = viewModel::setSortOrder,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryReportContent(
    state: CategoryReportState,
    onFilterModeChanged: (CategoryFilterMode) -> Unit,
    onSortOrderChanged: (CategorySortOrder) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Filter mode chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(CategoryFilterMode.entries) { mode ->
                    FilterChip(
                        selected = state.filterMode == mode,
                        onClick = { onFilterModeChanged(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }

        // Sort order chips — relevant options depend on filter mode
        item {
            val sortOptions = if (state.filterMode == CategoryFilterMode.BY_DATE) {
                listOf(CategorySortOrder.NEWEST, CategorySortOrder.OLDEST)
            } else {
                listOf(CategorySortOrder.HIGHEST, CategorySortOrder.LOWEST)
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(sortOptions) { sort ->
                    FilterChip(
                        selected = state.sortOrder == sort,
                        onClick = { onSortOrderChanged(sort) },
                        label = { Text(sort.label) },
                    )
                }
            }
        }

        if (state.entries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No cost entries found for this asset",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(state.entries, key = { it.entryId }) { entry ->
                CategoryEntryRow(
                    entry = entry,
                    filterMode = state.filterMode,
                    dateFormat = dateFormat,
                )
            }
        }
    }
}

@Composable
private fun CategoryEntryRow(
    entry: CategoryCostEntry,
    filterMode: CategoryFilterMode,
    dateFormat: SimpleDateFormat,
) {
    val icon = CATEGORY_ICONS[entry.category.lowercase()] ?: CATEGORY_ICONS["other"] ?: "📦"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.category.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(2.dp))
                if (filterMode == CategoryFilterMode.BY_DATE) {
                    Text(
                        text = dateFormat.format(Date(entry.date)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val meter = entry.meterReading
                    Text(
                        text = if (meter != null) "${"%.0f".format(meter)} hrs/mi" else "No meter reading",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "$${"%,.2f".format(entry.cost)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
