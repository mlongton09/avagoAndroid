package com.avago.feature.inventory.purchaseorders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val dbFactory: DatabaseFactory,
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
        else {
            val db = dbFactory.get(accountId)
            @Suppress("UNCHECKED_CAST")
            combine(
                db.purchaseOrderDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.poLineDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.vendorDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                _showGrnSheet as kotlinx.coroutines.flow.Flow<Any?>,
                _isActioning as kotlinx.coroutines.flow.Flow<Any?>,
                _actionError as kotlinx.coroutines.flow.Flow<Any?>,
            ) { v ->
                val pos = v[0] as List<PurchaseOrderEntity>
                val lines = v[1] as List<PoLineEntity>
                val vendors = v[2] as List<VendorEntity>
                val showGrn = v[3] as Boolean
                val actioning = v[4] as Boolean
                val actionErr = v[5] as String?
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

    fun submit() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.submitPurchaseOrder(accountId, poId)
            } catch (e: Exception) {
                Timber.e(e, "PurchaseOrderViewModel: submit failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun reject() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.rejectPurchaseOrder(accountId, poId)
            } catch (e: Exception) {
                Timber.e(e, "PurchaseOrderViewModel: reject failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun close() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.closePurchaseOrder(accountId, poId)
            } catch (e: Exception) {
                Timber.e(e, "PurchaseOrderViewModel: close failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun cancel() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.cancelPurchaseOrder(accountId, poId)
            } catch (e: Exception) {
                Timber.e(e, "PurchaseOrderViewModel: cancel failed")
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
    val partId: String? = null,
    val partName: String? = null,
    val description: String = "",
    val quantity: String = "1",
    val unitCost: String = "",
    val currency: String = "USD",
)

val CURRENCIES = listOf("USD", "EUR", "GBP", "CAD", "AUD", "MXN", "JPY", "CHF")

data class CreatePoUiState(
    val vendorId: String = "",
    val vendorName: String = "",
    val lines: List<PoLineForm> = emptyList(),
    // Date picker: stored as epoch millis; null = not set
    val expectedDeliveryMs: Long? = null,
    val shipToLocationId: String? = null,
    val shipToLocationName: String? = null,
    val notes: String = "",
    val costApproval: String = "not_required",
    // Cost fields
    val shippingCost: String = "",
    val discountAmount: String = "",
    val currency: String = "USD",
    // UI state
    val showVendorPicker: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class PurchaseOrderCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
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
            val accountId = identityManager.getActiveAccountId() ?: run {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }
            val po = dbFactory.get(accountId).purchaseOrderDao().getById(id)
            if (po != null) {
                // Parse ISO date string back to millis if present
                val deliveryMs = po.expectedDelivery?.let { dateStr ->
                    runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time
                    }.getOrNull()
                }
                _state.value = _state.value.copy(
                    vendorId = po.vendorId ?: "",
                    expectedDeliveryMs = deliveryMs,
                    shipToLocationId = po.shipToLocationId,
                    currency = po.currency ?: "USD",
                    shippingCost = po.shippingCost?.toString() ?: "",
                    discountAmount = po.discount?.toString() ?: "",
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

    fun onDeliveryDateChanged(ms: Long) {
        _state.value = _state.value.copy(expectedDeliveryMs = ms)
    }

    fun onLocationSelected(id: String, name: String) {
        _state.value = _state.value.copy(shipToLocationId = id, shipToLocationName = name)
    }

    fun onPartSelectedForLine(lineIndex: Int, partId: String, partName: String, unitCost: Double) {
        val lines = _state.value.lines.toMutableList()
        if (lineIndex !in lines.indices) return
        val line = lines[lineIndex]
        lines[lineIndex] = line.copy(
            partId = partId,
            partName = partName,
            description = if (line.description.isBlank()) partName else line.description,
            unitCost = if (line.unitCost.isBlank()) unitCost.toString() else line.unitCost,
        )
        _state.value = _state.value.copy(lines = lines)
    }

    fun onShippingCostChanged(v: String) { _state.value = _state.value.copy(shippingCost = v) }
    fun onDiscountAmountChanged(v: String) { _state.value = _state.value.copy(discountAmount = v) }
    fun onCurrencyChanged(v: String) { _state.value = _state.value.copy(currency = v) }

    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }
    fun setCostApproval(v: String) { _state.value = _state.value.copy(costApproval = v) }

    fun save() {
        val s = _state.value
        val accountId = identityManager.getActiveAccountId() ?: return
        // Format delivery date as ISO string for the API
        val deliveryIso = s.expectedDeliveryMs?.let { ms ->
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val request = CreatePurchaseOrderRequest(
                    vendor_id = s.vendorId.takeIf { it.isNotBlank() },
                    expected_delivery = deliveryIso,
                    ship_to_location_id = s.shipToLocationId?.takeIf { it.isNotBlank() },
                    notes = s.notes.takeIf { it.isNotBlank() },
                    cost_approval = s.costApproval,
                    lines = s.lines.map { line ->
                        PoLineRequest(
                            part_id = line.partId?.takeIf { it.isNotBlank() },
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
