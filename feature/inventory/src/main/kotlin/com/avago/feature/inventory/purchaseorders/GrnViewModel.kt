package com.avago.feature.inventory.purchaseorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.GoodsReceiptDraft
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.PurchaseOrder
import com.avago.core.data.domain.PurchaseOrderRepository
import com.avago.core.data.domain.ReceiptLineDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

data class GrnLineEntry(
    val poLineId: String,
    val label: String,
    val outstandingQty: BigDecimal,
    val receivedQty: String = "",
    val discrepancyReason: String = "",
)

data class GrnUiState(
    val receivingLocationId: String? = null,
    val receivingLocationName: String? = null,
    val carrier: String = "",
    val trackingNumber: String = "",
    val packingSlipNo: String = "",
    val notes: String = "",
    val lines: List<GrnLineEntry> = emptyList(),
    val showLocationPicker: Boolean = false,
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GrnViewModel @Inject constructor(
    private val orders: PurchaseOrderRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GrnUiState())
    val state: StateFlow<GrnUiState> = _state.asStateFlow()
    private var initialisedFor: String? = null

    /** Prefills one entry per open line with its outstanding quantity. */
    fun init(po: PurchaseOrder) {
        if (initialisedFor == po.id) return
        initialisedFor = po.id
        po.shipToLocationId?.let { id ->
            viewModelScope.launch {
                val name = locations.location(id)?.name
                _state.update { if (it.receivingLocationId == id) it.copy(receivingLocationName = name) else it }
            }
        }
        _state.update {
            it.copy(
                receivingLocationId = po.shipToLocationId,
                lines = po.lines.filter { l -> l.outstandingQty.signum() > 0 }.map { line ->
                    GrnLineEntry(
                        poLineId = line.id,
                        label = line.description ?: line.productName ?: line.supplierSku ?: "#${line.lineNo}",
                        outstandingQty = line.outstandingQty,
                        receivedQty = line.outstandingQty.stripTrailingZeros().toPlainString(),
                    )
                },
            )
        }
    }

    fun setCarrier(v: String) = _state.update { it.copy(carrier = v) }
    fun setTrackingNumber(v: String) = _state.update { it.copy(trackingNumber = v) }
    fun setPackingSlipNo(v: String) = _state.update { it.copy(packingSlipNo = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun showLocationPicker(show: Boolean) = _state.update { it.copy(showLocationPicker = show) }

    fun setLocation(id: String?, name: String?) =
        _state.update { it.copy(receivingLocationId = id, receivingLocationName = name, showLocationPicker = false) }

    fun setLineQty(poLineId: String, qty: String) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.poLineId == poLineId) it.copy(receivedQty = qty) else it })
    }

    fun setLineDiscrepancy(poLineId: String, reason: String) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.poLineId == poLineId) it.copy(discrepancyReason = reason) else it })
    }

    fun submit(poId: String) {
        val s = _state.value
        val lines = mutableListOf<ReceiptLineDraft>()
        for (line in s.lines) {
            val text = line.receivedQty.trim()
            if (text.isEmpty()) continue
            val qty = text.toBigDecimalOrNull()
            if (qty == null || qty.signum() < 0) {
                _state.update { it.copy(error = "Enter a valid quantity for \"${line.label}\"") }
                return
            }
            if (qty.signum() == 0) continue
            val short = line.outstandingQty - qty
            lines += ReceiptLineDraft(
                purchaseOrderLineId = line.poLineId,
                quantity = qty,
                discrepancyQty = short.takeIf { line.discrepancyReason.isNotBlank() && it.signum() != 0 },
                discrepancyReason = line.discrepancyReason.takeIf { it.isNotBlank() },
            )
        }
        if (lines.isEmpty()) {
            _state.update { it.copy(error = "Enter a received quantity for at least one line") }
            return
        }
        if (s.receivingLocationId == null) {
            _state.update { it.copy(error = "Choose the location the goods are received into") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                orders.receive(
                    GoodsReceiptDraft(
                        purchaseOrderId = poId,
                        lines = lines,
                        locationId = s.receivingLocationId,
                        carrier = s.carrier,
                        trackingNumber = s.trackingNumber,
                        packingSlipNo = s.packingSlipNo,
                        notes = s.notes,
                    ),
                )
                _state.update { it.copy(isSubmitting = false, isDone = true) }
            } catch (e: Exception) {
                Timber.e(e, "GrnViewModel: submit failed")
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }
}
