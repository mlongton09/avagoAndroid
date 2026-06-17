package com.avago.feature.inventory.parts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.FormFillRouter
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.model.InventoryReceiveRequest
import com.avago.core.network.model.InventoryUseRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class ReceiveUseMode { RECEIVE, USE }

// Change 50: reason codes for inventory adjustments
val INVENTORY_REASON_CODES = listOf(
    "PURCHASE", "USE", "ADJUSTMENT", "TRANSFER", "RETURN", "WASTE", "INITIAL_COUNT",
)

data class ReceiveUseUiState(
    val quantity: String = "",
    val notes: String = "",
    // Change 50: selected reason code; null = not yet chosen
    val reasonCode: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val isDone: Boolean = false,
)

@HiltViewModel
class ReceiveUseViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
    private val formFillRouter: FormFillRouter,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiveUseUiState())
    val state: StateFlow<ReceiveUseUiState> = _state.asStateFlow()

    fun setQuantity(q: String) { _state.value = _state.value.copy(quantity = q, error = null) }
    fun setNotes(n: String) { _state.value = _state.value.copy(notes = n) }
    // Change 50: reason code setter
    fun setReasonCode(code: String) { _state.value = _state.value.copy(reasonCode = code, error = null) }

    fun registerFormFill(screenId: String) {
        formFillRouter.register(screenId) { fields -> applyScoutFields(fields) }
    }

    fun unregisterFormFill(screenId: String) {
        formFillRouter.unregister(screenId)
    }

    private fun applyScoutFields(fields: Map<String, String?>): List<String> {
        val touched = mutableListOf<String>()
        _state.value = _state.value.let { s ->
            var updated = s
            (fields["quantity"] ?: fields["qty"])?.toDoubleOrNull()?.let {
                updated = updated.copy(quantity = it.toString()); touched.add("quantity")
            }
            fields["notes"]?.trim()?.let {
                updated = updated.copy(notes = it); touched.add("notes")
            }
            updated
        }
        return touched
    }

    fun submit(inventoryId: String, mode: ReceiveUseMode) {
        val qty = _state.value.quantity.toDoubleOrNull() ?: run {
            _state.value = _state.value.copy(error = "Invalid quantity")
            return
        }
        if (qty <= 0) {
            _state.value = _state.value.copy(error = "Quantity must be positive")
            return
        }
        // Change 50: reason code — the dropdown is optional for RECEIVE/USE but when the
        // caller explicitly selects "ADJUSTMENT" as the reason code, the field is already set.
        val reasonCode = _state.value.reasonCode
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                when (mode) {
                    ReceiveUseMode.RECEIVE -> serviceClient.receiveInventory(
                        inventoryId = inventoryId,
                        request = InventoryReceiveRequest(
                            quantity = qty,
                            notes = _state.value.notes.takeIf { it.isNotBlank() },
                            reason_code = reasonCode,
                        ),
                    )
                    ReceiveUseMode.USE -> serviceClient.useInventory(
                        inventoryId = inventoryId,
                        request = InventoryUseRequest(
                            quantity = qty,
                            notes = _state.value.notes.takeIf { it.isNotBlank() },
                            reason_code = reasonCode,
                        ),
                    )
                }
                _state.value = _state.value.copy(isSubmitting = false, isDone = true)
            } catch (e: Exception) {
                Timber.e(e, "ReceiveUseViewModel: submit failed")
                _state.value = _state.value.copy(isSubmitting = false, error = e.message)
            }
        }
    }
}
