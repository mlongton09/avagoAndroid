package com.avago.feature.inventory.purchaseorders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.PurchaseOrder
import com.avago.core.data.domain.PurchaseOrderDraft
import com.avago.core.data.domain.PurchaseOrderLineDraft
import com.avago.core.data.domain.PurchaseOrderRepository
import com.avago.core.data.domain.StockMovement
import com.avago.core.data.domain.Supplier
import com.avago.core.data.domain.toEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

/** Transitions that take an optional reason, confirmed in a dialog. */
enum class PoReasonAction { REJECT, CANCEL }

data class PoDetailUiState(
    val po: PurchaseOrder? = null,
    val receipts: List<StockMovement> = emptyList(),
    val isLoading: Boolean = true,
    val actionError: String? = null,
    val showGrnSheet: Boolean = false,
    val reasonAction: PoReasonAction? = null,
)

@HiltViewModel
class PurchaseOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orders: PurchaseOrderRepository,
    private val inventory: InventoryRepository,
) : ViewModel() {

    private val poId: String = checkNotNull(savedStateHandle["poId"])
    private val ui = MutableStateFlow(PoDetailUiState())

    val uiState: StateFlow<PoDetailUiState> = combine(
        orders.observePurchaseOrder(poId),
        inventory.observeMovements(setOf(MovementKind.RECEIPT)).map { all -> all.filter { it.purchaseOrderId == poId } },
        ui,
    ) { po, receipts, ui ->
        ui.copy(po = po, receipts = receipts.sortedByDescending { it.effectiveAt ?: it.createdAt }, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PoDetailUiState())

    fun submit() = act { orders.submit(poId) }
    fun approve() = act { orders.approve(poId) }
    fun markOrdered() = act { orders.markOrdered(poId) }
    fun close() = act { orders.close(poId) }

    fun requestReason(action: PoReasonAction) = ui.update { it.copy(reasonAction = action) }
    fun dismissReason() = ui.update { it.copy(reasonAction = null) }

    fun confirmReason(reason: String) {
        val action = ui.value.reasonAction ?: return
        ui.update { it.copy(reasonAction = null) }
        act {
            when (action) {
                PoReasonAction.REJECT -> orders.reject(poId, reason)
                PoReasonAction.CANCEL -> orders.cancel(poId, reason)
            }
        }
    }

    /** Reverses a posted receipt (the server re-opens the order lines). */
    fun reverseReceipt(receiptId: String) = act { inventory.reverseDocument(receiptId) }

    fun openGrnSheet() = ui.update { it.copy(showGrnSheet = true) }
    fun dismissGrnSheet() = ui.update { it.copy(showGrnSheet = false) }
    fun clearError() = ui.update { it.copy(actionError = null) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            ui.update { it.copy(actionError = null) }
            runCatching { block() }.onFailure { e ->
                Timber.e(e, "PurchaseOrderViewModel: action failed")
                ui.update { it.copy(actionError = e.message) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Create / Edit
// ---------------------------------------------------------------------------

data class PoLineForm(
    val key: String = UUID.randomUUID().toString(),
    /** The stored line this row edits; null for a new line. */
    val lineId: String? = null,
    val productId: String? = null,
    val productName: String? = null,
    val description: String = "",
    val quantity: String = "1",
    val unitCost: String = "",
    val taxRatePct: String = "",
)

val CURRENCIES = listOf("USD", "EUR", "GBP", "CAD", "AUD", "MXN", "JPY", "CHF")

data class CreatePoUiState(
    val vendorId: String = "",
    val vendorName: String = "",
    val lines: List<PoLineForm> = listOf(PoLineForm()),
    /** UTC midnight of the expected date (as the date picker reports it); null = not set. */
    val expectedDeliveryMs: Long? = null,
    val shipToLocationId: String? = null,
    val shipToLocationName: String? = null,
    val notes: String = "",
    val internalNotes: String = "",
    val supplierInvoiceRef: String = "",
    val currency: String = "USD",
    /** Vendor, currency and lines can only change while the order is a draft. */
    val isDraft: Boolean = true,
    val showVendorPicker: Boolean = false,
    val showLocationPicker: Boolean = false,
    /** Index of the line whose product is being picked. */
    val productPickerLine: Int? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class PurchaseOrderCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orders: PurchaseOrderRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val poId: String? = savedStateHandle["poId"]
    private var stored: PurchaseOrder? = null

    private val _state = MutableStateFlow(CreatePoUiState())
    val state: StateFlow<CreatePoUiState> = _state.asStateFlow()

    init {
        poId?.let { loadPo(it) }
    }

    private fun loadPo(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val po = orders.purchaseOrder(id)
            stored = po
            if (po == null) {
                _state.update { it.copy(isLoading = false, error = "Purchase order not found") }
                return@launch
            }
            val shipTo = po.shipToLocationId?.let { locations.location(it) }
            _state.update {
                it.copy(
                    vendorId = po.supplierPartyId,
                    vendorName = po.supplierName.orEmpty(),
                    currency = po.currency,
                    expectedDeliveryMs = po.expectedOn?.let { d ->
                        runCatching { LocalDate.parse(d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
                    },
                    shipToLocationId = po.shipToLocationId,
                    shipToLocationName = shipTo?.name,
                    notes = po.notes.orEmpty(),
                    internalNotes = po.internalNotes.orEmpty(),
                    supplierInvoiceRef = po.supplierInvoiceRef.orEmpty(),
                    isDraft = po.isEditable,
                    lines = po.lines.map { line ->
                        PoLineForm(
                            key = line.id,
                            lineId = line.id,
                            productId = line.productId,
                            productName = line.productName,
                            description = line.description.orEmpty(),
                            quantity = line.orderedQty.stripTrailingZeros().toPlainString(),
                            unitCost = line.unitCost.toPlainString(),
                            taxRatePct = line.taxRatePct?.stripTrailingZeros()?.toPlainString().orEmpty(),
                        )
                    },
                    isLoading = false,
                )
            }
        }
    }

    fun setVendor(vendor: Supplier) {
        _state.update {
            it.copy(
                vendorId = vendor.partyId,
                vendorName = vendor.name,
                currency = if (stored == null) vendor.currency ?: it.currency else it.currency,
                showVendorPicker = false,
            )
        }
    }

    fun showVendorPicker() = _state.update { it.copy(showVendorPicker = true) }
    fun dismissVendorPicker() = _state.update { it.copy(showVendorPicker = false) }
    fun showLocationPicker() = _state.update { it.copy(showLocationPicker = true) }
    fun dismissLocationPicker() = _state.update { it.copy(showLocationPicker = false) }

    fun onLocationSelected(id: String?, name: String?) =
        _state.update { it.copy(shipToLocationId = id, shipToLocationName = name, showLocationPicker = false) }

    fun addLine() = _state.update { it.copy(lines = it.lines + PoLineForm()) }

    fun removeLine(key: String) = _state.update { s -> s.copy(lines = s.lines.filter { it.key != key }) }

    fun updateLine(updated: PoLineForm) =
        _state.update { s -> s.copy(lines = s.lines.map { if (it.key == updated.key) updated else it }) }

    fun pickProductFor(lineIndex: Int?) = _state.update { it.copy(productPickerLine = lineIndex) }

    fun onProductSelected(product: Product) {
        _state.update { s ->
            val index = s.productPickerLine ?: return@update s
            val lines = s.lines.toMutableList()
            if (index !in lines.indices) return@update s.copy(productPickerLine = null)
            val line = lines[index]
            lines[index] = line.copy(
                productId = product.id,
                productName = product.name,
                description = line.description.ifBlank { product.name },
                unitCost = line.unitCost.ifBlank { product.standardUnitCost?.toPlainString().orEmpty() },
            )
            s.copy(lines = lines, productPickerLine = null)
        }
    }

    fun onDeliveryDateChanged(ms: Long?) = _state.update { it.copy(expectedDeliveryMs = ms) }
    fun onCurrencyChanged(v: String) = _state.update { it.copy(currency = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setInternalNotes(v: String) = _state.update { it.copy(internalNotes = v) }
    fun setSupplierInvoiceRef(v: String) = _state.update { it.copy(supplierInvoiceRef = v) }

    fun save() {
        val s = _state.value
        if (s.vendorId.isBlank()) {
            _state.update { it.copy(error = "Choose a vendor") }
            return
        }
        val lines = try {
            s.lines.map { it.toDraft() }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(error = e.message) }
            return
        }
        val expectedOn = s.expectedDeliveryMs?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val current = stored
                if (current == null) {
                    if (lines.isEmpty()) throw IllegalArgumentException("Add at least one line")
                    orders.createPurchaseOrder(
                        PurchaseOrderDraft(
                            supplierPartyId = s.vendorId,
                            currency = s.currency,
                            lines = lines,
                            shipToLocationId = s.shipToLocationId,
                            expectedOn = expectedOn,
                            notes = s.notes,
                        ),
                    )
                } else {
                    orders.updatePurchaseOrder(
                        current.id,
                        current.toEdit().copy(
                            supplierPartyId = s.vendorId,
                            currency = s.currency,
                            shipToLocationId = s.shipToLocationId,
                            expectedOn = expectedOn,
                            notes = s.notes,
                            internalNotes = s.internalNotes,
                            supplierInvoiceRef = s.supplierInvoiceRef,
                        ),
                    )
                    if (current.isEditable) saveLines(current, s.lines.zip(lines))
                }
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                Timber.e(e, "PurchaseOrderCreateViewModel: save failed")
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    private suspend fun saveLines(current: PurchaseOrder, lines: List<Pair<PoLineForm, PurchaseOrderLineDraft>>) {
        val kept = lines.mapNotNull { it.first.lineId }.toSet()
        current.lines.filter { it.id !in kept }.forEach { orders.deleteLine(it.id) }
        val storedLines = current.lines.associateBy { it.id }
        lines.forEach { (form, draft) ->
            val existing = form.lineId?.let(storedLines::get)
            if (existing == null) {
                orders.addLine(current.id, draft)
            } else {
                // Keep the fields this form does not edit.
                orders.updateLine(
                    existing.id,
                    draft.copy(
                        supplierSku = existing.supplierSku,
                        deliverToLocationId = existing.deliverToLocationId,
                        deliverToBinId = existing.deliverToBinId,
                        workOrderId = existing.workOrderId,
                        notes = existing.notes,
                    ),
                )
            }
        }
    }

    private fun PoLineForm.toDraft(): PurchaseOrderLineDraft {
        val label = description.ifBlank { productName.orEmpty() }
        require(label.isNotBlank()) { "Each line needs a description or part" }
        val qty = quantity.trim().toBigDecimalOrNull()
        require(qty != null && qty.signum() > 0) { "Enter a positive quantity for \"$label\"" }
        val cost = unitCost.trim().ifEmpty { "0" }.toBigDecimalOrNull()
        require(cost != null && cost.signum() >= 0) { "Enter a valid unit cost for \"$label\"" }
        val tax = taxRatePct.trim().takeIf { it.isNotEmpty() }?.let {
            requireNotNull(it.toBigDecimalOrNull()?.takeIf { pct -> pct >= BigDecimal.ZERO }) { "Enter a valid tax % for \"$label\"" }
        }
        return PurchaseOrderLineDraft(
            description = label,
            orderedQty = qty,
            unitCost = cost,
            productId = productId,
            taxRatePct = tax,
        )
    }
}
