package com.avago.feature.inventory.purchaseorders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.PartDao
import com.avago.core.data.db.dao.PoLineDao
import com.avago.core.data.db.dao.PurchaseOrderDao
import com.avago.core.data.db.dao.VendorDao
import com.avago.core.data.db.entity.PoLineEntity
import com.avago.core.data.db.entity.PurchaseOrderEntity
import com.avago.core.data.db.entity.VendorEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.model.CreatePurchaseOrderRequest
import com.avago.core.network.model.PoLineRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

data class PoDetailUiState(
    val po: PurchaseOrderEntity? = null,
    val vendor: VendorEntity? = null,
    val lines: List<PoLineEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isActioning: Boolean = false,
    val actionError: String? = null,
    val showGrnSheet: Boolean = false,
)

@HiltViewModel
class PurchaseOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val poDao: PurchaseOrderDao,
    private val poLineDao: PoLineDao,
    private val vendorDao: VendorDao,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val poId: String = checkNotNull(savedStateHandle["poId"])
    private val _showGrnSheet = MutableStateFlow(false)
    private val _isActioning = MutableStateFlow(false)
    private val _actionError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PoDetailUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(PoDetailUiState(isLoading = false))
        else combine(
            poDao.observeAll(accountId),
            poLineDao.observeAll(accountId),
            vendorDao.observeAll(accountId),
            _showGrnSheet,
            _isActioning,
            _actionError,
        ) { pos, lines, vendors, showGrn, actioning, actionErr ->
            val po = pos.find { it.poId == poId }
            val vendor = vendors.find { it.vendorId == po?.vendorId }
            val poLines = lines.filter { it.poId == poId }.sortedBy { it.displayOrder }
            PoDetailUiState(
                po = po,
                vendor = vendor,
                lines = poLines,
                isLoading = false,
                showGrnSheet = showGrn,
                isActioning = actioning,
                actionError = actionErr,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PoDetailUiState(),
    )

    fun approve() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.approvePurchaseOrder(accountId, poId)
            } catch (e: Exception) {
                Timber.e(e, "PurchaseOrderViewModel: approve failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun markOrdered() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.markPurchaseOrderOrdered(accountId, poId)
            } catch (e: Exception) {
                Timber.e(e, "PurchaseOrderViewModel: markOrdered failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun openGrnSheet() { _showGrnSheet.value = true }
    fun dismissGrnSheet() { _showGrnSheet.value = false }
    fun clearError() { _actionError.value = null }
}

// ---------------------------------------------------------------------------
// Create / Edit
// ---------------------------------------------------------------------------

data class PoLineForm(
    val id: String = java.util.UUID.randomUUID().toString(),
    val partId: String = "",
    val partName: String = "",
    val description: String = "",
    val quantity: String = "1",
    val unitCost: String = "",
    val currency: String = "USD",
)

data class CreatePoUiState(
    val vendorId: String = "",
    val vendorName: String = "",
    val lines: List<PoLineForm> = emptyList(),
    val expectedDelivery: String = "",
    val shipToLocationId: String = "",
    val notes: String = "",
    val costApproval: String = "not_required",
    val showVendorPicker: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class PurchaseOrderCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val poDao: PurchaseOrderDao,
    private val poLineDao: PoLineDao,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val poId: String? = savedStateHandle["poId"]

    private val _state = MutableStateFlow(CreatePoUiState())
    val state: StateFlow<CreatePoUiState> = _state.asStateFlow()

    init {
        poId?.let { loadPo(it) }
    }

    private fun loadPo(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val po = poDao.getById(id)
            if (po != null) {
                _state.value = _state.value.copy(
                    vendorId = po.vendorId ?: "",
                    expectedDelivery = po.expectedDelivery ?: "",
                    shipToLocationId = po.shipToLocationId ?: "",
                    notes = po.notes ?: "",
                    isLoading = false,
                )
            } else {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun setVendor(vendorId: String, vendorName: String) {
        _state.value = _state.value.copy(vendorId = vendorId, vendorName = vendorName, showVendorPicker = false)
    }

    fun showVendorPicker() { _state.value = _state.value.copy(showVendorPicker = true) }
    fun dismissVendorPicker() { _state.value = _state.value.copy(showVendorPicker = false) }

    fun addLine() {
        _state.value = _state.value.copy(lines = _state.value.lines + PoLineForm())
    }

    fun removeLine(id: String) {
        _state.value = _state.value.copy(lines = _state.value.lines.filter { it.id != id })
    }

    fun updateLine(updated: PoLineForm) {
        _state.value = _state.value.copy(
            lines = _state.value.lines.map { if (it.id == updated.id) updated else it },
        )
    }

    fun setExpectedDelivery(v: String) { _state.value = _state.value.copy(expectedDelivery = v) }
    fun setShipToLocation(v: String) { _state.value = _state.value.copy(shipToLocationId = v) }
    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }
    fun setCostApproval(v: String) { _state.value = _state.value.copy(costApproval = v) }

    fun save() {
        val s = _state.value
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val request = CreatePurchaseOrderRequest(
                    vendor_id = s.vendorId.takeIf { it.isNotBlank() },
                    expected_delivery = s.expectedDelivery.takeIf { it.isNotBlank() },
                    ship_to_location_id = s.shipToLocationId.takeIf { it.isNotBlank() },
                    notes = s.notes.takeIf { it.isNotBlank() },
                    cost_approval = s.costApproval,
                    lines = s.lines.map { line ->
                        PoLineRequest(
                            part_id = line.partId.takeIf { it.isNotBlank() },
                            description = line.description.takeIf { it.isNotBlank() },
                            quantity = line.quantity.toDoubleOrNull() ?: 1.0,
                            unit_cost = line.unitCost.toDoubleOrNull(),
                            currency = line.currency.takeIf { it.isNotBlank() },
                        )
                    },
                )
                if (poId == null) {
                    serviceClient.createPurchaseOrder(accountId, request)
                } else {
                    serviceClient.updatePurchaseOrder(accountId, poId, request)
                }
                _state.value = _state.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                Timber.e(e, "PurchaseOrderCreateViewModel: save failed")
                _state.value = _state.value.copy(isSaving = false, error = e.message)
            }
        }
    }
}
