package com.avago.feature.inventory.parts

import com.avago.core.data.domain.SyncRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.ProductStock
import com.avago.core.data.domain.StockStatus
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PartListItem(val stock: ProductStock) {
    val category: String? get() = stock.product.categoryName?.ifBlank { null }
}

/** One entry in the displayed list — either a section header or a part row. */
sealed interface InventoryListEntry {
    data class Header(val title: String) : InventoryListEntry
    data class PartRow(val item: PartListItem) : InventoryListEntry
}

data class InventoryListUiState(
    val items: List<PartListItem> = emptyList(),
    val displayList: List<InventoryListEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val stockFilter: String = "all",
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true,
    // Summary stats (computed from full unfiltered list)
    val totalCount: Int = 0,
    val inStockCount: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
)

@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    private val sync: SyncRepository,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val canCreatePart: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.INVENTORY_CREATE_PART)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.INVENTORY_CREATE_PART))

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)
    val stockFilter = MutableStateFlow("all")

    val uiState: StateFlow<InventoryListUiState> = combine(
        inventory.observeProductStock(),
        searchQuery,
        selectedCategory,
        stockFilter,
    ) { stock, query, category, stockFilterVal ->
        val allItems = stock.map(::PartListItem)
        val categories = allItems.mapNotNull { it.category }.distinct().sorted()

        val filtered = allItems.filter { item ->
            val product = item.stock.product
            val matchesQuery = query.isBlank() ||
                product.name.contains(query, ignoreCase = true) ||
                product.sku.contains(query, ignoreCase = true) ||
                product.manufacturer?.contains(query, ignoreCase = true) == true ||
                product.gtin?.contains(query, ignoreCase = true) == true
            val matchesCategory = category == null || item.category == category
            val matchesStock = when (stockFilterVal) {
                "in_stock" -> item.stock.status == StockStatus.IN_STOCK
                "low_stock" -> item.stock.status == StockStatus.LOW
                "out_of_stock" -> item.stock.status == StockStatus.OUT
                else -> true
            }
            matchesQuery && matchesCategory && matchesStock
        }

        // Build display list: group by category with section headers, sorted alphabetically within each group
        val displayList = buildList {
            val grouped = filtered
                .groupBy { it.category ?: "Uncategorized" }
                .entries
                .sortedBy { it.key }
            for ((groupKey, groupItems) in grouped) {
                add(InventoryListEntry.Header(groupKey))
                groupItems.sortedBy { it.stock.product.name.lowercase() }.forEach { add(InventoryListEntry.PartRow(it)) }
            }
        }

        InventoryListUiState(
            items = allItems,
            displayList = displayList,
            searchQuery = query,
            selectedCategory = category,
            stockFilter = stockFilterVal,
            categories = categories,
            isLoading = false,
            totalCount = allItems.size,
            inStockCount = allItems.count { it.stock.status == StockStatus.IN_STOCK },
            lowStockCount = allItems.count { it.stock.status == StockStatus.LOW },
            outOfStockCount = allItems.count { it.stock.status == StockStatus.OUT },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryListUiState(),
    )

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setCategory(cat: String?) { selectedCategory.value = cat }
    fun setStockFilter(f: String) { stockFilter.value = f }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                sync.syncNow()
            } catch (e: Exception) {
                Timber.e(e, "[InventoryListViewModel] Sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
