package com.avago.feature.inventory.cyclecounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.CycleCountEntity
import com.avago.core.data.db.entity.CycleCountLineEntity
import com.avago.core.data.db.entity.LocationEntity
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.model.CreateCycleCountRequest
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
// List
// ---------------------------------------------------------------------------

data class CycleCountListUiState(
    val counts: List<CycleCountEntity> = emptyList(),
    val filtered: List<CycleCountEntity> = emptyList(),
    val selectedStatus: String? = null,
    val isLoading: Boolean = true,
    val showCreateSheet: Boolean = false,
)

val CYCLE_COUNT_STATUSES = listOf("in_progress", "locked", "reconciled")

@HiltViewModel
class CycleCountListViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _selectedStatus = MutableStateFlow<String?>(null)
    private val _showCreate = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CycleCountListUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(CycleCountListUiState(isLoading = false))
        else combine(
            dbFactory.get(accountId).cycleCountDao().observeAll(accountId),
            _selectedStatus,
            _showCreate,
        ) { counts, status, showCreate ->
            val filtered = if (status == null) counts else counts.filter { it.status == status }
            CycleCountListUiState(
                counts = counts,
                filtered = filtered,
                selectedStatus = status,
                isLoading = false,
                showCreateSheet = showCreate,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CycleCountListUiState(),
    )

    fun setStatus(s: String?) { _selectedStatus.value = s }
    fun openCreate() { _showCreate.value = true }
    fun dismissCreate() { _showCreate.value = false }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

data class CycleCountDetailUiState(
    val count: CycleCountEntity? = null,
    val location: LocationEntity? = null,
    val lines: List<CycleCountLineWithPart> = emptyList(),
    val isLoading: Boolean = true,
    val isActioning: Boolean = false,
    val actionError: String? = null,
)

data class CycleCountLineWithPart(
    val line: CycleCountLineEntity,
    val part: PartEntity?,
    val countedQtyInput: String,
)

@HiltViewModel
class CycleCountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val countId: String = checkNotNull(savedStateHandle["countId"])
    private val _isActioning = MutableStateFlow(false)
    private val _actionError = MutableStateFlow<String?>(null)
    private val _localQtyEdits = MutableStateFlow<Map<String, String>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CycleCountDetailUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(CycleCountDetailUiState(isLoading = false))
        else {
            val db = dbFactory.get(accountId)
            @Suppress("UNCHECKED_CAST")
            combine(
                db.cycleCountDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.cycleCountLineDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.locationDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.partDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                _isActioning as kotlinx.coroutines.flow.Flow<Any?>,
                _actionError as kotlinx.coroutines.flow.Flow<Any?>,
                _localQtyEdits as kotlinx.coroutines.flow.Flow<Any?>,
            ) { v ->
                val counts = v[0] as List<CycleCountEntity>
                val lines = v[1] as List<CycleCountLineEntity>
                val locations = v[2] as List<LocationEntity>
                val parts = v[3] as List<PartEntity>
                val actioning = v[4] as Boolean
                val actionErr = v[5] as String?
                @Suppress("UNCHECKED_CAST")
                val edits = v[6] as Map<String, String>
                val count = counts.find { it.cycleCountId == countId }
                val location = locations.find { it.locationId == count?.locationId }
                val partMap = parts.associateBy { it.partId }
                val countLines = lines
                    .filter { it.cycleCountId == countId }
                    .map { line ->
                        CycleCountLineWithPart(
                            line = line,
                            part = partMap[line.partId],
                            countedQtyInput = edits[line.lineId] ?: line.countedQty?.toString() ?: "",
                        )
                    }
                CycleCountDetailUiState(
                    count = count,
                    location = location,
                    lines = countLines,
                    isLoading = false,
                    isActioning = actioning,
                    actionError = actionErr,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CycleCountDetailUiState(),
    )

    fun setLineQty(lineId: String, qty: String) {
        _localQtyEdits.value = _localQtyEdits.value + (lineId to qty)
    }

    fun start() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.startCycleCount(accountId, countId)
            } catch (e: Exception) {
                Timber.e(e, "CycleCountDetailViewModel: start failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun complete() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.completeCycleCount(accountId, countId)
            } catch (e: Exception) {
                Timber.e(e, "CycleCountDetailViewModel: complete failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun post() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.postCycleCount(accountId, countId)
            } catch (e: Exception) {
                Timber.e(e, "CycleCountDetailViewModel: post failed")
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
                serviceClient.cancelCycleCount(accountId, countId)
            } catch (e: Exception) {
                Timber.e(e, "CycleCountDetailViewModel: cancel failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun clearError() { _actionError.value = null }
}

// ---------------------------------------------------------------------------
// Create sheet state
// ---------------------------------------------------------------------------

data class CreateCycleCountUiState(
    val locationId: String = "",
    val scopeType: String = "full",
    val scopeValue: String = "",
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CreateCycleCountViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCycleCountUiState())
    val state: StateFlow<CreateCycleCountUiState> = _state.asStateFlow()

    fun setLocationId(v: String) { _state.value = _state.value.copy(locationId = v) }
    fun setScopeType(v: String) { _state.value = _state.value.copy(scopeType = v) }
    fun setScopeValue(v: String) { _state.value = _state.value.copy(scopeValue = v) }

    fun submit() {
        val s = _state.value
        if (s.locationId.isBlank()) {
            _state.value = s.copy(error = "Location is required")
            return
        }
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                serviceClient.createCycleCount(
                    accountId = accountId,
                    request = CreateCycleCountRequest(
                        location_id = s.locationId,
                        scope_type = s.scopeType.takeIf { it.isNotBlank() && it != "full" },
                        scope_value = s.scopeValue.takeIf { it.isNotBlank() },
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false, isDone = true)
            } catch (e: Exception) {
                Timber.e(e, "CreateCycleCountViewModel: submit failed")
                _state.value = _state.value.copy(isSubmitting = false, error = e.message)
            }
        }
    }
}
