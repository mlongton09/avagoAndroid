package com.avago.feature.inventory.parts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.InventoryEntity
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.StockingLevelEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PartListItem(
    val part: PartEntity,
    val inventory: InventoryEntity?,
    val stockingLevel: StockingLevelEntity?,
    val needsReorder: Boolean,
)

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
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)
    val stockFilter = MutableStateFlow("all")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountFlow = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(Triple(emptyList<PartEntity>(), emptyList<InventoryEntity>(), emptyList<StockingLevelEntity>()))
        else {
            val db = dbFactory.get(accountId)
            combine(
                db.partDao().observeAll(accountId),
                db.inventoryDao().observeAll(accountId),
                db.stockingLevelDao().observeAll(accountId),
            ) { parts, inventories, stockingLevels -> Triple(parts, inventories, stockingLevels) }
        }
    }

    val uiState: StateFlow<InventoryListUiState> = combine(
        accountFlow,
        searchQuery,
        selectedCategory,
        stockFilter,
    ) { (parts, inventories, stockingLevels), query, category, stockFilterVal ->
        val invByPart = inventories.associateBy { it.partId }
        val slByPart = stockingLevels.associateBy { it.partId }

        val allItems = parts.map { part ->
            val inv = invByPart[part.partId]
            val sl = slByPart[part.partId]
            val needsReorder = if (inv != null && sl != null) {
                StockingLevelPolicy.needsReorder(
                    onHand = inv.quantityOnHand,
                    minQty = sl.minQty ?: 0.0,
                    safetyStock = sl.safetyStock ?: 0.0,
                )
            } else false
            PartListItem(part, inv, sl, needsReorder)
        }

        val categories = parts.mapNotNull { it.category }.distinct().sorted()

        // Summary stats from full unfiltered list
        val totalCount = allItems.size
        val inStockCount = allItems.count { item ->
            val onHand = item.inventory?.quantityOnHand ?: 0.0
            val threshold = (item.stockingLevel?.minQty ?: 0.0) + (item.stockingLevel?.safetyStock ?: 0.0)
            onHand > threshold
        }
        val outOfStockCount = allItems.count { item ->
            val onHand = item.inventory?.quantityOnHand ?: 0.0
            onHand <= 0.0
        }
        val lowStockCount = allItems.count { item ->
            val onHand = item.inventory?.quantityOnHand ?: 0.0
            val threshold = (item.stockingLevel?.minQty ?: 0.0) + (item.stockingLevel?.safetyStock ?: 0.0)
            onHand > 0.0 && onHand <= threshold
        }

        val filtered = allItems.filter { item ->
            val onHand = item.inventory?.quantityOnHand ?: 0.0
            val threshold = (item.stockingLevel?.minQty ?: 0.0) + (item.stockingLevel?.safetyStock ?: 0.0)

            val matchesQuery = query.isBlank() ||
                item.part.name.contains(query, ignoreCase = true) ||
                item.part.sku?.contains(query, ignoreCase = true) == true ||
                item.part.unitOfMeasure?.contains(query, ignoreCase = true) == true
            val matchesCategory = category == null || item.part.category == category
            val matchesStock = when (stockFilterVal) {
                "in_stock" -> onHand > threshold
                "low_stock" -> onHand > 0.0 && onHand <= threshold
                "out_of_stock" -> onHand <= 0.0
                else -> true
            }
            matchesQuery && matchesCategory && matchesStock
        }

        // Build display list: group by category with section headers, sorted alphabetically within each group
        val displayList = buildList {
            val grouped = filtered
                .groupBy { it.part.category?.ifBlank { null } ?: "Uncategorized" }
                .entries
                .sortedBy { it.key }
            for ((groupKey, groupItems) in grouped) {
                add(InventoryListEntry.Header(groupKey))
                groupItems.sortedBy { it.part.name }.forEach { add(InventoryListEntry.PartRow(it)) }
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
            totalCount = totalCount,
            inStockCount = inStockCount,
            lowStockCount = lowStockCount,
            outOfStockCount = outOfStockCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryListUiState(),
    )

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setCategory(cat: String?) { selectedCategory.value = cat }
    fun setStockFilter(f: String) { stockFilter.value = f }
}
