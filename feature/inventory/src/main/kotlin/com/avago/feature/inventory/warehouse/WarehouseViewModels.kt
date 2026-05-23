@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.avago.feature.inventory.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.BinEntity
import com.avago.core.data.db.entity.LocationEntity
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.model.CreatePartIssueRequest
import com.avago.core.network.model.InventoryReceiveRequest
import com.avago.core.network.model.PartIssueLineRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Shared search state for part picking
// ---------------------------------------------------------------------------

data class WarehousePartSearchState(
    val query: String = "",
    val parts: List<PartEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val bins: List<BinEntity> = emptyList(),
    val isLoading: Boolean = true,
)

// ---------------------------------------------------------------------------
// Warehouse Receive ViewModel
// ---------------------------------------------------------------------------

data class WarehouseReceiveUiState(
    val partSearch: String = "",
    val selectedPart: PartEntity? = null,
    val parts: List<PartEntity> = emptyList(),
    val quantity: String = "",
    val locationId: String = "",
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WarehouseReceiveViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(WarehouseReceiveUiState())
    val state: StateFlow<WarehouseReceiveUiState> = _state.asStateFlow()

    init {
        loadParts()
    }

    private fun loadParts() {
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).partDao().observeAll(accountId) }
                .collect { parts -> _state.value = _state.value.copy(parts = parts) }
        }
    }

    fun setPartSearch(q: String) { _state.value = _state.value.copy(partSearch = q) }
    fun selectPart(part: PartEntity) { _state.value = _state.value.copy(selectedPart = part, partSearch = part.name) }
    fun setQuantity(q: String) { _state.value = _state.value.copy(quantity = q) }
    fun setLocationId(v: String) { _state.value = _state.value.copy(locationId = v) }
    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }

    fun filteredParts(): List<PartEntity> {
        val q = _state.value.partSearch
        return if (q.isBlank()) _state.value.parts
        else _state.value.parts.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun submit() {
        val s = _state.value
        val qty = s.quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _state.value = s.copy(error = "Invalid quantity")
            return
        }
        val part = s.selectedPart ?: run {
            _state.value = s.copy(error = "Select a part")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                serviceClient.receiveInventory(
                    inventoryId = part.partId,
                    request = InventoryReceiveRequest(
                        quantity = qty,
                        location_id = s.locationId.takeIf { it.isNotBlank() },
                        notes = s.notes.takeIf { it.isNotBlank() },
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false, isDone = true)
            } catch (e: Exception) {
                Timber.e(e, "WarehouseReceiveViewModel: submit failed")
                _state.value = _state.value.copy(isSubmitting = false, error = e.message)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Warehouse Issue ViewModel
// ---------------------------------------------------------------------------

data class WarehouseIssueUiState(
    val partSearch: String = "",
    val selectedPart: PartEntity? = null,
    val parts: List<PartEntity> = emptyList(),
    val quantity: String = "",
    val workOrderId: String = "",
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WarehouseIssueViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(WarehouseIssueUiState())
    val state: StateFlow<WarehouseIssueUiState> = _state.asStateFlow()

    init {
        loadParts()
    }

    private fun loadParts() {
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).partDao().observeAll(accountId) }
                .collect { parts -> _state.value = _state.value.copy(parts = parts) }
        }
    }

    fun setPartSearch(q: String) { _state.value = _state.value.copy(partSearch = q) }
    fun selectPart(part: PartEntity) { _state.value = _state.value.copy(selectedPart = part, partSearch = part.name) }
    fun setQuantity(q: String) { _state.value = _state.value.copy(quantity = q) }
    fun setWorkOrderId(v: String) { _state.value = _state.value.copy(workOrderId = v) }
    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }

    fun filteredParts(): List<PartEntity> {
        val q = _state.value.partSearch
        return if (q.isBlank()) _state.value.parts
        else _state.value.parts.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun submit() {
        val s = _state.value
        val qty = s.quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _state.value = s.copy(error = "Invalid quantity")
            return
        }
        val part = s.selectedPart ?: run {
            _state.value = s.copy(error = "Select a part")
            return
        }
        val accountId = identityManager.getActiveAccountId() ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                serviceClient.createPartIssue(
                    accountId = accountId,
                    request = CreatePartIssueRequest(
                        issue_type = "issue",
                        reference_id = s.workOrderId.takeIf { it.isNotBlank() },
                        reference_type = if (s.workOrderId.isNotBlank()) "work_order" else null,
                        notes = s.notes.takeIf { it.isNotBlank() },
                        lines = listOf(
                            PartIssueLineRequest(
                                part_id = part.partId,
                                quantity = qty,
                            ),
                        ),
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false, isDone = true)
            } catch (e: Exception) {
                Timber.e(e, "WarehouseIssueViewModel: submit failed")
                _state.value = _state.value.copy(isSubmitting = false, error = e.message)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Warehouse Move ViewModel
// ---------------------------------------------------------------------------

data class WarehouseMoveUiState(
    val partSearch: String = "",
    val selectedPart: PartEntity? = null,
    val parts: List<PartEntity> = emptyList(),
    val quantity: String = "",
    val fromLocationId: String = "",
    val fromBinId: String = "",
    val toLocationId: String = "",
    val toBinId: String = "",
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WarehouseMoveViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(WarehouseMoveUiState())
    val state: StateFlow<WarehouseMoveUiState> = _state.asStateFlow()

    init {
        loadParts()
    }

    private fun loadParts() {
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).partDao().observeAll(accountId) }
                .collect { parts -> _state.value = _state.value.copy(parts = parts) }
        }
    }

    fun setPartSearch(q: String) { _state.value = _state.value.copy(partSearch = q) }
    fun selectPart(part: PartEntity) { _state.value = _state.value.copy(selectedPart = part, partSearch = part.name) }
    fun setQuantity(q: String) { _state.value = _state.value.copy(quantity = q) }
    fun setFromLocationId(v: String) { _state.value = _state.value.copy(fromLocationId = v) }
    fun setFromBinId(v: String) { _state.value = _state.value.copy(fromBinId = v) }
    fun setToLocationId(v: String) { _state.value = _state.value.copy(toLocationId = v) }
    fun setToBinId(v: String) { _state.value = _state.value.copy(toBinId = v) }
    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }

    fun filteredParts(): List<PartEntity> {
        val q = _state.value.partSearch
        return if (q.isBlank()) _state.value.parts
        else _state.value.parts.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun submit() {
        val s = _state.value
        val qty = s.quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _state.value = s.copy(error = "Invalid quantity")
            return
        }
        val part = s.selectedPart ?: run {
            _state.value = s.copy(error = "Select a part")
            return
        }
        if (s.fromLocationId.isBlank() || s.toLocationId.isBlank()) {
            _state.value = s.copy(error = "Select from and to locations")
            return
        }
        val accountId = identityManager.getActiveAccountId() ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                serviceClient.createPartIssue(
                    accountId = accountId,
                    request = CreatePartIssueRequest(
                        issue_type = "transfer",
                        from_location_id = s.fromLocationId,
                        to_location_id = s.toLocationId,
                        notes = s.notes.takeIf { it.isNotBlank() },
                        lines = listOf(
                            PartIssueLineRequest(
                                part_id = part.partId,
                                quantity = qty,
                            ),
                        ),
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false, isDone = true)
            } catch (e: Exception) {
                Timber.e(e, "WarehouseMoveViewModel: submit failed")
                _state.value = _state.value.copy(isSubmitting = false, error = e.message)
            }
        }
    }
}
