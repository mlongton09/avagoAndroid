package com.avago.feature.inventory.parts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
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

data class ReceiveUseUiState(
    val quantity: String = "",
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val isDone: Boolean = false,
)

@HiltViewModel
class ReceiveUseViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiveUseUiState())
    val state: StateFlow<ReceiveUseUiState> = _state.asStateFlow()

    fun setQuantity(q: String) { _state.value = _state.value.copy(quantity = q, error = null) }
    fun setNotes(n: String) { _state.value = _state.value.copy(notes = n) }

    fun submit(inventoryId: String, mode: ReceiveUseMode) {
        val qty = _state.value.quantity.toDoubleOrNull() ?: run {
            _state.value = _state.value.copy(error = "Invalid quantity")
            return
        }
        if (qty <= 0) {
            _state.value = _state.value.copy(error = "Quantity must be positive")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                when (mode) {
                    ReceiveUseMode.RECEIVE -> serviceClient.receiveInventory(
                        inventoryId = inventoryId,
                        request = InventoryReceiveRequest(
                            quantity = qty,
                            notes = _state.value.notes.takeIf { it.isNotBlank() },
                        ),
                    )
                    ReceiveUseMode.USE -> serviceClient.useInventory(
                        inventoryId = inventoryId,
                        request = InventoryUseRequest(
                            quantity = qty,
                            notes = _state.value.notes.takeIf { it.isNotBlank() },
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
