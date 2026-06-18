package com.avago.feature.inventory.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LocationEntity
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.PartTransferRequestEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.CreatePartTransferRequestBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Transfer Request List
// ---------------------------------------------------------------------------

data class PartTransferListUiState(
    val requests: List<PartTransferRequestEntity> = emptyList(),
    val filterStatus: String? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PartTransferListViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PartTransferListUiState())
    val state: StateFlow<PartTransferListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).partTransferRequestDao().observeAll(accountId) }
                .collect { requests ->
                    _state.value = _state.value.copy(requests = requests, isLoading = false)
                }
        }
    }

    fun setFilter(status: String?) { _state.value = _state.value.copy(filterStatus = status) }

    fun filteredRequests(): List<PartTransferRequestEntity> {
        val s = _state.value
        return if (s.filterStatus == null) s.requests
        else s.requests.filter { it.status == s.filterStatus }
    }
}

// ---------------------------------------------------------------------------
// Transfer Request Create
// ---------------------------------------------------------------------------

data class CreateTransferRequestUiState(
    val partSearch: String = "",
    val selectedPart: PartEntity? = null,
    val parts: List<PartEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val quantity: String = "",
    val fromLocationId: String = "",
    val toLocationId: String = "",
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreateTransferRequestViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateTransferRequestUiState())
    val state: StateFlow<CreateTransferRequestUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).partDao().observeAll(accountId) }
                .collect { parts -> _state.value = _state.value.copy(parts = parts) }
        }
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).locationDao().observeAll(accountId) }
                .collect { locs -> _state.value = _state.value.copy(locations = locs) }
        }
    }

    fun setPartSearch(q: String) { _state.value = _state.value.copy(partSearch = q, selectedPart = null) }
    fun selectPart(part: PartEntity) { _state.value = _state.value.copy(selectedPart = part, partSearch = part.name) }
    fun setQuantity(v: String) { _state.value = _state.value.copy(quantity = v) }
    fun setFromLocationId(v: String) { _state.value = _state.value.copy(fromLocationId = v) }
    fun setToLocationId(v: String) { _state.value = _state.value.copy(toLocationId = v) }
    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }

    fun reset() { _state.value = CreateTransferRequestUiState() }

    fun filteredParts(): List<PartEntity> {
        val q = _state.value.partSearch
        return if (q.isBlank()) _state.value.parts
        else _state.value.parts.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun submit() {
        val s = _state.value
        val qty = s.quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) { _state.value = s.copy(error = "Invalid quantity"); return }
        val part = s.selectedPart ?: run { _state.value = s.copy(error = "Select a part"); return }
        val accountId = identityManager.getActiveAccountId() ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            when (val result = serviceClient.createPartTransferRequest(
                accountId = accountId,
                request = CreatePartTransferRequestBody(
                    part_id = part.partId,
                    quantity = qty,
                    from_location_id = s.fromLocationId.takeIf { it.isNotBlank() },
                    to_location_id = s.toLocationId.takeIf { it.isNotBlank() },
                    notes = s.notes.takeIf { it.isNotBlank() },
                ),
            )) {
                is NetworkResult.Success -> _state.value = _state.value.copy(isSubmitting = false, isDone = true)
                is NetworkResult.Error -> {
                    Timber.e("CreateTransferRequestViewModel: submit failed ${result.code} ${result.message}")
                    _state.value = _state.value.copy(isSubmitting = false, error = result.message)
                }
                is NetworkResult.Unauthorized ->
                    _state.value = _state.value.copy(isSubmitting = false, error = "Session expired. Please sign in again.")
            }
        }
    }
}
