package com.avago.feature.assets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun catDisplayName(categoryId: String): String =
    categoryId.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private fun relativeDate(epochMs: Long): String {
    val days = ((System.currentTimeMillis() - epochMs) / 86_400_000L).toInt().coerceAtLeast(0)
    return when {
        days == 0  -> "Today"
        days == 1  -> "Yesterday"
        days < 7   -> "$days days ago"
        days < 30  -> { val w = days / 7;   "$w wk${if (w  == 1) "" else "s"} ago" }
        days < 365 -> { val m = days / 30;  "$m mo${if (m  == 1) "" else "s"} ago" }
        else       -> { val y = days / 365; "$y yr${if (y  == 1) "" else "s"} ago" }
    }
}

// ── Data ──────────────────────────────────────────────────────────────────────

data class GlobalCatRow(
    val assetId: String,
    val assetName: String,
    val avatarColor: String,
    val entryTitle: String,
    val entryDate: Long,
    val entryCost: Double,
    val entryOdometer: Double,
)

data class GlobalCatInfo(
    val id: String,
    val displayName: String,
    val count: Int,
)

enum class GlobalCatFilterMode { BY_DATE, BY_METER }
enum class GlobalCatSortOrder  { NEWEST, OLDEST }

data class GlobalCategoryReportState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedCategory: String = "",
    val filterMode: GlobalCatFilterMode = GlobalCatFilterMode.BY_DATE,
    val sortOrder: GlobalCatSortOrder  = GlobalCatSortOrder.NEWEST,
    val rows: List<GlobalCatRow> = emptyList(),
    val availableCategories: List<GlobalCatInfo> = emptyList(),
    val isPickerOpen: Boolean = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class GlobalCategoryReportViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalCategoryReportState())
    val state: StateFlow<GlobalCategoryReportState> = _state.asStateFlow()

    init { loadInitial() }

    private fun loadInitial(isRefresh: Boolean = false) {
        val accountId = identity.getActiveAccountId() ?: return
        _state.update {
            it.copy(isLoading = !isRefresh, isRefreshing = isRefresh, error = null)
        }
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val allLogs = db.logDao().observeAll(accountId).first()

                val defaultCat = allLogs
                    .filter { !it.category.isNullOrBlank() }
                    .groupBy { it.category!! }
                    .maxByOrNull { it.value.size }?.key ?: "service"

                val cats = allLogs
                    .filter { !it.category.isNullOrBlank() }
                    .groupBy { it.category!! }
                    .map { (id, entries) -> GlobalCatInfo(id, catDisplayName(id), entries.size) }
                    .sortedByDescending { it.count }

                val mode = _state.value.filterMode
                val sort = GlobalCatSortOrder.NEWEST
                _state.update {
                    it.copy(
                        selectedCategory = if (it.selectedCategory.isBlank()) defaultCat else it.selectedCategory,
                        availableCategories = cats,
                        sortOrder = sort,
                    )
                }
                buildRows(accountId, _state.value.selectedCategory, mode, sort)
            } catch (e: Exception) {
                Timber.e(e, "[GlobalCategoryReport] loadInitial failed")
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Load failed") }
            }
        }
    }

    fun refresh() = loadInitial(isRefresh = true)

    private suspend fun buildRows(
        accountId: String,
        category: String,
        mode: GlobalCatFilterMode,
        sort: GlobalCatSortOrder,
    ) {
        val db = dbFactory.get(accountId)
        val allLogs = db.logDao().observeAll(accountId).first()
        val catLogs = allLogs.filter { it.category == category }

        // Latest entry per asset: by date for BY_DATE, by meter for BY_METER
        val latestPerAsset = if (mode == GlobalCatFilterMode.BY_METER) {
            catLogs.filter { (it.odometerValue ?: 0.0) > 0.0 }
                .groupBy { it.assetId }
                .mapValues { (_, entries) -> entries.maxByOrNull { it.odometerValue ?: 0.0 }!! }
        } else {
            catLogs.groupBy { it.assetId }
                .mapValues { (_, entries) -> entries.maxByOrNull { it.entryDate }!! }
        }

        val rows = latestPerAsset.values.mapNotNull { log ->
            val assetId = log.assetId ?: return@mapNotNull null
            val asset = db.assetDao().getById(assetId) ?: return@mapNotNull null
            GlobalCatRow(
                assetId = asset.assetId,
                assetName = asset.name,
                avatarColor = asset.avatarColor ?: "#2563EB",
                entryTitle = log.title,
                entryDate = log.entryDate,
                entryCost = log.cost ?: 0.0,
                entryOdometer = log.odometerValue ?: 0.0,
            )
        }

        _state.update {
            it.copy(isLoading = false, isRefreshing = false, rows = applySort(rows, mode, sort))
        }
    }

    fun setCategory(categoryId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        val mode = _state.value.filterMode
        val sort = GlobalCatSortOrder.NEWEST
        _state.update { it.copy(selectedCategory = categoryId, sortOrder = sort, isPickerOpen = false, isLoading = true) }
        viewModelScope.launch {
            try {
                buildRows(accountId, categoryId, mode, sort)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilterMode(mode: GlobalCatFilterMode) {
        val accountId = identity.getActiveAccountId() ?: return
        val category = _state.value.selectedCategory
        val sort = GlobalCatSortOrder.NEWEST
        _state.update { it.copy(filterMode = mode, sortOrder = sort, isLoading = true) }
        viewModelScope.launch {
            try {
                buildRows(accountId, category, mode, sort)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setSortOrder(order: GlobalCatSortOrder) {
        _state.update { state ->
            state.copy(sortOrder = order, rows = applySort(state.rows, state.filterMode, order))
        }
    }

    fun openPicker()  { _state.update { it.copy(isPickerOpen = true) } }
    fun closePicker() { _state.update { it.copy(isPickerOpen = false) } }

    private fun applySort(
        rows: List<GlobalCatRow>,
        mode: GlobalCatFilterMode,
        sort: GlobalCatSortOrder,
    ): List<GlobalCatRow> = when {
        mode == GlobalCatFilterMode.BY_DATE && sort == GlobalCatSortOrder.NEWEST ->
            rows.sortedByDescending { it.entryDate }
        mode == GlobalCatFilterMode.BY_DATE ->
            rows.sortedBy { it.entryDate }
        sort == GlobalCatSortOrder.NEWEST ->
            rows.sortedByDescending { it.entryOdometer }  // Highest
        else ->
            rows.sortedBy { it.entryOdometer }  // Lowest
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalCategoryReportScreen(
    onBack: () -> Unit,
    viewModel: GlobalCategoryReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filterMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("By Category") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Filter mode button — mirrors iOS nav bar right button
                    Box {
                        IconButton(onClick = { filterMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter mode",
                                tint = if (state.filterMode == GlobalCatFilterMode.BY_METER)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("By Date") },
                                onClick = {
                                    viewModel.setFilterMode(GlobalCatFilterMode.BY_DATE)
                                    filterMenuExpanded = false
                                },
                                trailingIcon = if (state.filterMode == GlobalCatFilterMode.BY_DATE) {
                                    { Text("✓", color = MaterialTheme.colorScheme.primary) }
                                } else null,
                            )
                            DropdownMenuItem(
                                text = { Text("By Meter") },
                                onClick = {
                                    viewModel.setFilterMode(GlobalCatFilterMode.BY_METER)
                                    filterMenuExpanded = false
                                },
                                trailingIcon = if (state.filterMode == GlobalCatFilterMode.BY_METER) {
                                    { Text("✓", color = MaterialTheme.colorScheme.primary) }
                                } else null,
                            )
                        }
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
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        // ── Header ────────────────────────────────────────────
                        item {
                            CategoryReportHeader(
                                selectedCategory = state.selectedCategory,
                                filterMode = state.filterMode,
                                sortOrder = state.sortOrder,
                                onChangeCategoryClick = { viewModel.openPicker() },
                                onSortOrderChanged = { viewModel.setSortOrder(it) },
                            )
                        }

                        // ── Empty state ───────────────────────────────────────
                        if (state.rows.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 64.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No entries found for this category",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            items(state.rows, key = { it.assetId }) { row ->
                                CategoryReportRowItem(row = row, filterMode = state.filterMode)
                                HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Category picker bottom sheet
    if (state.isPickerOpen) {
        CategoryPickerSheet(
            categories = state.availableCategories,
            selectedId = state.selectedCategory,
            onSelect = { viewModel.setCategory(it) },
            onDismiss = { viewModel.closePicker() },
        )
    }
}

// ── Header composable ─────────────────────────────────────────────────────────

@Composable
private fun CategoryReportHeader(
    selectedCategory: String,
    filterMode: GlobalCatFilterMode,
    sortOrder: GlobalCatSortOrder,
    onChangeCategoryClick: () -> Unit,
    onSortOrderChanged: (GlobalCatSortOrder) -> Unit,
) {
    val emoji = CATEGORY_ICONS[selectedCategory.lowercase()] ?: "📦"
    val displayName = catDisplayName(selectedCategory)
    val pill0Label = if (filterMode == GlobalCatFilterMode.BY_DATE) "Newest" else "Highest"
    val pill1Label = if (filterMode == GlobalCatFilterMode.BY_DATE) "Oldest" else "Lowest"

    Column(modifier = Modifier.fillMaxWidth()) {
        // Description
        Text(
            text = "Most recent entry per asset for this category.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        )

        Spacer(Modifier.height(10.dp))

        // Category row: icon + name + change button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category badge
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            TextButton(onClick = onChangeCategoryClick) {
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Sort pills
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortPill(
                label = pill0Label,
                selected = sortOrder == GlobalCatSortOrder.NEWEST,
                onClick = { onSortOrderChanged(GlobalCatSortOrder.NEWEST) },
            )
            SortPill(
                label = pill1Label,
                selected = sortOrder == GlobalCatSortOrder.OLDEST,
                onClick = { onSortOrderChanged(GlobalCatSortOrder.OLDEST) },
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// ── Row composable ────────────────────────────────────────────────────────────

@Composable
private fun CategoryReportRowItem(
    row: GlobalCatRow,
    filterMode: GlobalCatFilterMode,
) {
    val days = ((System.currentTimeMillis() - row.entryDate) / 86_400_000L).toInt().coerceAtLeast(0)
    val dateColor = when {
        days > 365 -> MaterialTheme.colorScheme.error
        days > 180 -> Color(0xFFFF9500)
        else       -> MaterialTheme.colorScheme.onSurface
    }
    val dateText = relativeDate(row.entryDate)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Asset avatar — colored circle with initial
        AssetInitialAvatar(name = row.assetName, colorHex = row.avatarColor)

        Spacer(Modifier.width(12.dp))

        // Left column: asset name + entry title
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.assetName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = row.entryTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Right column: date (primary) + meter + cost
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (filterMode == GlobalCatFilterMode.BY_DATE) dateColor
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.entryOdometer > 0) {
                val fmt = if (row.entryOdometer >= 1000) "%.0f".format(row.entryOdometer)
                          else "%.1f".format(row.entryOdometer)
                Text(
                    text = "$fmt mi",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (filterMode == GlobalCatFilterMode.BY_METER) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (filterMode == GlobalCatFilterMode.BY_METER) FontWeight.SemiBold
                                 else FontWeight.Normal,
                )
            }
            if (row.entryCost > 0) {
                Text(
                    text = "${"$"}${"%.0f".format(row.entryCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AssetInitialAvatar(name: String, colorHex: String, modifier: Modifier = Modifier) {
    val color = remember(colorHex) {
        try { Color(android.graphics.Color.parseColor(colorHex)) }
        catch (_: Exception) { Color(0xFF2563EB) }
    }
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Category picker bottom sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    categories: List<GlobalCatInfo>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Select Category",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                val emoji = CATEGORY_ICONS[cat.id.lowercase()] ?: "📦"
                ListItem(
                    headlineContent = {
                        Text(
                            text = cat.displayName,
                            fontWeight = if (cat.id == selectedId) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    supportingContent = {
                        Text("${cat.count} entries", style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = {
                        Text(emoji, style = MaterialTheme.typography.headlineSmall)
                    },
                    trailingContent = if (cat.id == selectedId) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                    } else null,
                    modifier = Modifier.clickable { onSelect(cat.id) },
                )
            }
        }
    }
}
