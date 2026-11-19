package com.avago.feature.inventory.parts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.ProductSupplier
import com.avago.core.data.domain.StockLedgerEntry
import com.avago.core.data.domain.StockPosition
import com.avago.core.data.domain.StockingPolicy
import com.avago.core.data.domain.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import javax.inject.Inject

data class PartDetailUiState(
    val product: Product? = null,
    val positions: List<StockPosition> = emptyList(),
    val policies: List<StockingPolicy> = emptyList(),
    val history: List<StockLedgerEntry> = emptyList(),
    val suppliers: List<ProductSupplier> = emptyList(),
    val isLoading: Boolean = true,
    val sheet: ReceiveUseMode? = null,
) {
    val onHand: BigDecimal get() = positions.fold(BigDecimal.ZERO) { acc, p -> acc + p.onHand }
}

@HiltViewModel
class PartDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    inventory: InventoryRepository,
    suppliers: SupplierRepository,
) : ViewModel() {

    private val productId: String = checkNotNull(savedStateHandle["partId"])
    private val sheet = MutableStateFlow<ReceiveUseMode?>(null)

    val uiState: StateFlow<PartDetailUiState> = combine(
        inventory.observeProduct(productId),
        inventory.observePositions(productId = productId),
        inventory.observePolicies(productId),
        inventory.observeLedger(productId),
        combine(suppliers.observeProductSuppliers(productId = productId), sheet, ::Pair),
    ) { product, positions, policies, history, (productSuppliers, openSheet) ->
        PartDetailUiState(
            product = product,
            positions = positions.filter { it.onHand.signum() != 0 },
            policies = policies,
            history = history,
            suppliers = productSuppliers,
            isLoading = false,
            sheet = openSheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PartDetailUiState())

    fun openReceive() { sheet.value = ReceiveUseMode.RECEIVE }

    fun openUse() { sheet.value = ReceiveUseMode.USE }

    fun dismissSheet() { sheet.value = null }
}
