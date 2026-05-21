package com.avago.feature.inventory.parts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.InventoryDao
import com.avago.core.data.db.dao.PartDao
import com.avago.core.data.db.dao.StockingLevelDao
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

data class InventoryListUiState(
    val items: List<PartListItem> = emptyList(),
    val filteredItems: List<PartListItem> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val partDao: PartDao,
    private val inventoryDao: InventoryDao,
    private val stockingLevelDao: StockingLevelDao,
    private val identityManager: IdentityManager,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountFlow = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(Triple(emptyList(), emptyList(), emptyList()))
        else combine(
            partDao.observeAll(accountId),
            inventoryDao.observeAll(accountId),
            stockingLevelDao.observeAll(accountId),
        ) { parts, inventories, stockingLevels -> Triple(parts, inventories, stockingLevels) }
    }

    val uiState: StateFlow<InventoryListUiState> = combine(
        accountFlow,
        searchQuery,
        selectedCategory,
    ) { (parts, inventories, stockingLevels), query, category ->
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

        val filtered = allItems.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.part.name.contains(query, ignoreCase = true) ||
                item.part.sku?.contains(query, ignoreCase = true) == true
            val matchesCategory = category == null || item.part.category == category
            matchesQuery && matchesCategory
        }

        InventoryListUiState(
            items = allItems,
            filteredItems = filtered,
            searchQuery = query,
            selectedCategory = category,
            categories = categories,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryListUiState(),
    )

    fun setSearchQuery(q: String) { searchQuery.value = q }
    fun setCategory(cat: String?) { selectedCategory.value = cat }
}
