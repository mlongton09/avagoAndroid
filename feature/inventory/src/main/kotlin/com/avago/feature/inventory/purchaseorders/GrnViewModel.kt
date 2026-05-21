package com.avago.feature.inventory.purchaseorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.PoLineEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.model.CreateGrnRequest
import com.avago.core.network.model.GrnLineRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class GrnLineEntry(
    val poLineId: String,
    val partId: String?,
    val description: String?,
    val expectedQty: Double,
    val receivedQty: String = "",
)

data class GrnUiState(
    val grnNumber: String = "",
    val receivedDate: String = "",
    val carrier: String = "",
    val trackingNumber: String = "",
    val packingSlipNo: String = "",
    val notes: String = "",
    val hasDiscrepancy: Boolean = false,
    val lines: List<GrnLineEntry> = emptyList(),
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GrnViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GrnUiState())
    val state: StateFlow<GrnUiState> = _state.asStateFlow()

    fun initLines(poLines: List<PoLineEntity>) {
        if (_state.value.lines.isNotEmpty()) return
        _state.value = _state.value.copy(
            lines = poLines.map { line ->
                GrnLineEntry(
                    poLineId = line.poLineId,
                    partId = line.partId,
                    description = line.description,
                    expectedQty = line.quantity,
                    receivedQty = line.quantity.toString(),
                )
            },
        )
    }

    fun setGrnNumber(v: String) { _state.value = _state.value.copy(grnNumber = v) }
    fun setReceivedDate(v: String) { _state.value = _state.value.copy(receivedDate = v) }
    fun setCarrier(v: String) { _state.value = _state.value.copy(carrier = v) }
    fun setTrackingNumber(v: String) { _state.value = _state.value.copy(trackingNumber = v) }
    fun setPackingSlipNo(v: String) { _state.value = _state.value.copy(packingSlipNo = v) }
    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }
    fun setHasDiscrepancy(v: Boolean) { _state.value = _state.value.copy(hasDiscrepancy = v) }
    fun setLineQty(poLineId: String, qty: String) {
        _state.value = _state.value.copy(
            lines = _state.value.lines.map {
                if (it.poLineId == poLineId) it.copy(receivedQty = qty) else it
            },
        )
    }

    fun submit(poId: String) {
        val accountId = identityManager.getActiveAccountId() ?: return
        val s = _state.value
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                val request = CreateGrnRequest(
                    grn_number = s.grnNumber.takeIf { it.isNotBlank() },
                    received_at = if (s.receivedDate.isNotBlank()) {
                        runCatching {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                .parse(s.receivedDate)?.time
                        }.getOrNull()
                    } else null,
                    carrier = s.carrier.takeIf { it.isNotBlank() },
                    tracking_number = s.trackingNumber.takeIf { it.isNotBlank() },
                    packing_slip_no = s.packingSlipNo.takeIf { it.isNotBlank() },
                    notes = s.notes.takeIf { it.isNotBlank() },
                    has_discrepancy = s.hasDiscrepancy,
                    lines = s.lines.map { line ->
                        GrnLineRequest(
                            po_line_id = line.poLineId,
                            part_id = line.partId,
                            quantity_received = line.receivedQty.toDoubleOrNull() ?: line.expectedQty,
                        )
                    },
                )
                serviceClient.createGrn(accountId, poId, request)
                _state.value = _state.value.copy(isSubmitting = false, isDone = true)
            } catch (e: Exception) {
                Timber.e(e, "GrnViewModel: submit failed")
                _state.value = _state.value.copy(isSubmitting = false, error = e.message)
            }
        }
    }
}
