package com.avago.feature.inventory.purchaseorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.PurchaseOrderDao
import com.avago.core.data.db.dao.VendorDao
import com.avago.core.data.db.entity.PurchaseOrderEntity
import com.avago.core.data.db.entity.VendorEntity
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

data class PoListItem(
    val po: PurchaseOrderEntity,
    val vendor: VendorEntity?,
)

data class PurchaseOrderListUiState(
    val items: List<PoListItem> = emptyList(),
    val filtered: List<PoListItem> = emptyList(),
    val selectedStatus: String? = null,
    val isLoading: Boolean = true,
)

val PO_STATUSES = listOf(
    "draft",
    "pending_approval",
    "approved",
    "ordered",
    "partially_received",
    "received",
    "closed",
    "cancelled",
)

@HiltViewModel
class PurchaseOrderListViewModel @Inject constructor(
    private val poDao: PurchaseOrderDao,
    private val vendorDao: VendorDao,
    private val identityManager: IdentityManager,
) : ViewModel() {

    val selectedStatus = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PurchaseOrderListUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(PurchaseOrderListUiState(isLoading = false))
        else combine(
            poDao.observeAll(accountId),
            vendorDao.observeAll(accountId),
            selectedStatus,
        ) { pos, vendors, status ->
            val vendorMap = vendors.associateBy { it.vendorId }
            val items = pos.map { po -> PoListItem(po, vendorMap[po.vendorId]) }
            val filtered = if (status == null) items else items.filter { it.po.status == status }
            PurchaseOrderListUiState(
                items = items,
                filtered = filtered,
                selectedStatus = status,
                isLoading = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PurchaseOrderListUiState(),
    )

    fun setStatus(s: String?) { selectedStatus.value = s }
}
