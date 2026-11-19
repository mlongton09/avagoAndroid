package com.avago.feature.inventory.purchaseorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.PurchaseOrder
import com.avago.core.data.domain.PurchaseOrderRepository
import com.avago.core.data.domain.PurchaseOrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PurchaseOrderListUiState(
    val items: List<PurchaseOrder> = emptyList(),
    val filtered: List<PurchaseOrder> = emptyList(),
    val selectedStatus: PurchaseOrderStatus? = null,
    val isLoading: Boolean = true,
)

val PO_STATUSES: List<PurchaseOrderStatus> = PurchaseOrderStatus.entries

@HiltViewModel
class PurchaseOrderListViewModel @Inject constructor(
    orders: PurchaseOrderRepository,
) : ViewModel() {

    private val selectedStatus = MutableStateFlow<PurchaseOrderStatus?>(null)

    val uiState: StateFlow<PurchaseOrderListUiState> =
        combine(orders.observePurchaseOrders(), selectedStatus) { pos, status ->
            PurchaseOrderListUiState(
                items = pos,
                filtered = if (status == null) pos else pos.filter { it.status == status },
                selectedStatus = status,
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseOrderListUiState())

    fun setStatus(s: PurchaseOrderStatus?) {
        selectedStatus.value = s
    }
}
