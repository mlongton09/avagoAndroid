package com.avago.feature.inventory.cyclecounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.CountScope
import com.avago.core.data.domain.CycleCountDraft
import com.avago.core.data.domain.CycleCountState
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.StockMovement
import com.avago.core.data.domain.StockMovementLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

data class CycleCountListItem(
    val count: StockMovement,
    val locationName: String?,
) {
    val counted: Int get() = count.lines.count { it.countedQty != null }
}

data class CycleCountListUiState(
    val counts: List<CycleCountListItem> = emptyList(),
    val filtered: List<CycleCountListItem> = emptyList(),
    val selectedStatus: CycleCountState? = null,
    val isLoading: Boolean = true,
    val showCreateSheet: Boolean = false,
)

val CYCLE_COUNT_STATUSES: List<CycleCountState> = CycleCountState.entries

@HiltViewModel
class CycleCountListViewModel @Inject constructor(
    inventory: InventoryRepository,
    locations: LocationRepository,
) : ViewModel() {

    private val selectedStatus = MutableStateFlow<CycleCountState?>(null)
    private val showCreate = MutableStateFlow(false)

    val uiState: StateFlow<CycleCountListUiState> = combine(
        inventory.observeMovements(setOf(MovementKind.COUNT)),
        locations.observeLocations(),
        selectedStatus,
        showCreate,
    ) { counts, locs, status, create ->
        val names = locs.associate { it.id to it.name }
        val items = counts
            .sortedByDescending { it.createdAt }
            .map { CycleCountListItem(it, names[it.locationId]) }
        CycleCountListUiState(
            counts = items,
            filtered = if (status == null) items else items.filter { it.count.countState == status },
            selectedStatus = status,
            isLoading = false,
            showCreateSheet = create,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleCountListUiState())

    fun setStatus(s: CycleCountState?) {
        selectedStatus.value = s
    }

    fun openCreate() {
        showCreate.value = true
    }

    fun dismissCreate() {
        showCreate.value = false
    }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

data class CycleCountLineItem(
    val line: StockMovementLine,
    /** What the counter typed but has not saved yet. */
    val input: String,
) {
    val isDirty: Boolean
        get() = input.trim().toBigDecimalOrNull()?.let { it.compareTo(line.countedQty ?: return@let true) != 0 } ?: false

    val variance: BigDecimal?
        get() = line.countedQty?.let { counted -> line.expectedQty?.let { counted - it } }
}

data class CycleCountDetailUiState(
    val count: StockMovement? = null,
    val location: Location? = null,
    val lines: List<CycleCountLineItem> = emptyList(),
    val isLoading: Boolean = true,
    val actionError: String? = null,
) {
    val state: CycleCountState? get() = count?.countState

    /** Blind counts hide expected quantities while counting. */
    val showExpected: Boolean get() = count != null && (!count.blindCount || state != CycleCountState.COUNTING)
}

@HiltViewModel
class CycleCountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inventory: InventoryRepository,
    locations: LocationRepository,
) : ViewModel() {

    private val countId: String = checkNotNull(savedStateHandle["countId"])
    private val edits = MutableStateFlow<Map<String, String>>(emptyMap())
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CycleCountDetailUiState> = combine(
        inventory.observeMovement(countId),
        locations.observeLocations(),
        edits,
        actionError,
    ) { count, locs, edits, error ->
        CycleCountDetailUiState(
            count = count,
            location = count?.let { c -> locs.firstOrNull { it.id == c.locationId } },
            lines = count?.lines.orEmpty().sortedBy { it.lineNo }.map { line ->
                CycleCountLineItem(line, edits[line.id] ?: line.countedQty?.stripTrailingZeros()?.toPlainString().orEmpty())
            },
            isLoading = false,
            actionError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleCountDetailUiState())

    fun setLineQty(lineId: String, qty: String) = edits.update { it + (lineId to qty) }

    /** Records the typed quantity of a line. */
    fun saveLine(lineId: String) {
        val text = edits.value[lineId]?.trim() ?: return
        val qty = text.toBigDecimalOrNull()
        if (qty == null || qty.signum() < 0) {
            actionError.value = "Enter a valid counted quantity"
            return
        }
        act {
            inventory.recordCount(lineId, qty)
            edits.update { it - lineId }
        }
    }

    fun start() = act { inventory.startCycleCount(countId) }

    /** Saves pending line entries, then completes the count. */
    fun complete() = act {
        edits.value.forEach { (lineId, text) ->
            val qty = text.trim().toBigDecimalOrNull() ?: return@forEach
            inventory.recordCount(lineId, qty)
        }
        edits.value = emptyMap()
        inventory.completeCycleCount(countId)
    }

    fun post() = act { inventory.postCycleCount(countId) }
    fun cancel() = act { inventory.cancelCycleCount(countId) }
    fun clearError() {
        actionError.value = null
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            actionError.value = null
            runCatching { block() }.onFailure { e ->
                Timber.e(e, "CycleCountDetailViewModel: action failed")
                actionError.value = e.message
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------------

/** Scopes that need no further selection; category/bin/part-list scopes have no selector in the catalog. */
val CREATABLE_COUNT_SCOPES = listOf(CountScope.FULL, CountScope.ABC_A, CountScope.ABC_B, CountScope.ABC_C)

data class CreateCycleCountUiState(
    val locationId: String? = null,
    val locationName: String? = null,
    val scope: CountScope = CountScope.FULL,
    val blindCount: Boolean = false,
    /** UTC midnight of the scheduled date; null = unscheduled. */
    val scheduledOnMs: Long? = null,
    val notes: String = "",
    val startNow: Boolean = true,
    val showLocationPicker: Boolean = false,
    val isSubmitting: Boolean = false,
    val createdCountId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CreateCycleCountViewModel @Inject constructor(
    private val inventory: InventoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCycleCountUiState())
    val state: StateFlow<CreateCycleCountUiState> = _state.asStateFlow()

    fun showLocationPicker(show: Boolean) = _state.update { it.copy(showLocationPicker = show) }

    fun setLocation(location: Location?) =
        _state.update { it.copy(locationId = location?.id, locationName = location?.name, showLocationPicker = false) }

    fun setScope(v: CountScope) = _state.update { it.copy(scope = v) }
    fun setBlindCount(v: Boolean) = _state.update { it.copy(blindCount = v) }
    fun setScheduledOn(ms: Long?) = _state.update { it.copy(scheduledOnMs = ms) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setStartNow(v: Boolean) = _state.update { it.copy(startNow = v) }

    fun submit() {
        val s = _state.value
        val locationId = s.locationId
        if (locationId == null) {
            _state.update { it.copy(error = "Choose the location to count") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                val id = inventory.planCycleCount(
                    CycleCountDraft(
                        locationId = locationId,
                        scope = s.scope,
                        blindCount = s.blindCount,
                        scheduledOn = s.scheduledOnMs?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() },
                        notes = s.notes.takeIf { it.isNotBlank() },
                    ),
                )
                // Same entity key: the outbox holds the start until the create is resolved.
                if (s.startNow) inventory.startCycleCount(id)
                _state.update { it.copy(isSubmitting = false, createdCountId = id) }
            } catch (e: Exception) {
                Timber.e(e, "CreateCycleCountViewModel: submit failed")
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }
}
