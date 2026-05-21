package com.avago.feature.inventory.cyclecounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.CycleCountDao
import com.avago.core.data.db.dao.CycleCountLineDao
import com.avago.core.data.db.dao.LocationDao
import com.avago.core.data.db.dao.PartDao
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
    private val cycleCountDao: CycleCountDao,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _selectedStatus = MutableStateFlow<String?>(null)
    private val _showCreate = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CycleCountListUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(CycleCountListUiState(isLoading = false))
        else combine(
            cycleCountDao.observeAll(accountId),
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
    private val cycleCountDao: CycleCountDao,
    private val cycleCountLineDao: CycleCountLineDao,
    private val locationDao: LocationDao,
    private val partDao: PartDao,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val countId: String = checkNotNull(savedStateHandle["countId"])
    private val _isActioning = MutableStateFlow(false)
    private val _actionError = MutableStateFlow<String?>(null)
    // Local qty edits: lineId -> value
    private val _localQtyEdits = MutableStateFlow<Map<String, String>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CycleCountDetailUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(CycleCountDetailUiState(isLoading = false))
        else combine(
            cycleCountDao.observeAll(accountId),
            cycleCountLineDao.observeAll(accountId),
            locationDao.observeAll(accountId),
            partDao.observeAll(accountId),
            _isActioning,
            _actionError,
            _localQtyEdits,
        ) { counts, lines, locations, parts, actioning, actionErr, edits ->
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CycleCountDetailUiState(),
    )

    fun setLineQty(lineId: String, qty: String) {
        _localQtyEdits.value = _localQtyEdits.value + (lineId to qty)
    }

    fun lock() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.lockCycleCount(accountId, countId)
            } catch (e: Exception) {
                Timber.e(e, "CycleCountDetailViewModel: lock failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }

    fun reconcile() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isActioning.value = true
            _actionError.value = null
            try {
                serviceClient.reconcileCycleCount(accountId, countId)
            } catch (e: Exception) {
                Timber.e(e, "CycleCountDetailViewModel: reconcile failed")
                _actionError.value = e.message
            } finally {
                _isActioning.value = false
            }
        }
    }
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
