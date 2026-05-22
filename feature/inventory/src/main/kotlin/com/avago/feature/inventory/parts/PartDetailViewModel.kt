package com.avago.feature.inventory.parts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.InventoryDao
import com.avago.core.data.db.dao.InventoryTransactionDao
import com.avago.core.data.db.dao.PartDao
import com.avago.core.data.db.dao.StockingLevelDao
import com.avago.core.data.db.entity.InventoryEntity
import com.avago.core.data.db.entity.InventoryTransactionEntity
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

data class PartDetailUiState(
    val part: PartEntity? = null,
    val inventory: InventoryEntity? = null,
    val stockingLevel: StockingLevelEntity? = null,
    val transactions: List<InventoryTransactionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val showReceiveSheet: Boolean = false,
    val showUseSheet: Boolean = false,
)

@HiltViewModel
class PartDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val partDao: PartDao,
    private val inventoryDao: InventoryDao,
    private val stockingLevelDao: StockingLevelDao,
    private val inventoryTransactionDao: InventoryTransactionDao,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val partId: String = checkNotNull(savedStateHandle["partId"])

    private val _showReceive = MutableStateFlow(false)
    private val _showUse = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PartDetailUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(PartDetailUiState(isLoading = false))
        else {
            @Suppress("UNCHECKED_CAST")
            combine(
                partDao.observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                inventoryDao.observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                stockingLevelDao.observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                inventoryTransactionDao.observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                _showReceive as kotlinx.coroutines.flow.Flow<Any?>,
                _showUse as kotlinx.coroutines.flow.Flow<Any?>,
            ) { v ->
                val parts = v[0] as List<PartEntity>
                val inventories = v[1] as List<InventoryEntity>
                val levels = v[2] as List<StockingLevelEntity>
                val transactions = v[3] as List<InventoryTransactionEntity>
                val showReceive = v[4] as Boolean
                val showUse = v[5] as Boolean
                val part = parts.find { it.partId == partId }
                val inv = inventories.find { it.partId == partId }
                val sl = levels.find { it.partId == partId }
                val txns = transactions
                    .filter { it.partId == partId }
                    .sortedByDescending { it.createdAt }
                PartDetailUiState(
                    part = part,
                    inventory = inv,
                    stockingLevel = sl,
                    transactions = txns,
                    isLoading = false,
                    showReceiveSheet = showReceive,
                    showUseSheet = showUse,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PartDetailUiState(),
    )

    fun openReceive() { _showReceive.value = true }
    fun openUse() { _showUse.value = true }
    fun dismissSheet() {
        _showReceive.value = false
        _showUse.value = false
    }
}
