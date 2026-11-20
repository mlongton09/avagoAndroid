package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.GlAccount
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class CostLinesEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    workers: WorkerRepository,
    activities: ActivityRepository,
) : ViewModel() {

    private val woId: String = requireNotNull(savedStateHandle["woId"]) {
        "CostLinesEditorViewModel requires woId in SavedStateHandle"
    }

    val costLines: StateFlow<List<CostLine>> = repository.observeDetail(woId)
        .map { it?.costLines.orEmpty() }
        .catch { e -> Timber.e(e, "[CostLinesVM] flow error"); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<BigDecimal> = costLines
        .map { lines -> lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BigDecimal.ZERO)

    /** Worker display names by worker id (labor performers). */
    val workerNames: StateFlow<Map<String, String>> = workers.observeWorkers()
        .map { list -> list.associate { it.id to it.displayName } }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Worker user ids by worker id, to preselect the performer when editing. */
    val workerUserIds: StateFlow<Map<String, String>> = workers.observeWorkers()
        .map { list -> list.mapNotNull { w -> w.userId?.let { w.id to it } }.toMap() }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Active general-ledger accounts a line can be booked to. */
    val glAccounts: StateFlow<List<GlAccount>> = activities.observeGlAccounts()
        .catch { e -> Timber.e(e, "[CostLinesVM] gl accounts flow error"); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun addLine(draft: CostLineDraft) = save { repository.addCostLine(woId, draft) }

    fun updateLine(lineId: String, draft: CostLineDraft) = save { repository.updateCostLine(lineId, draft) }

    fun removeLine(lineId: String) = save { repository.deleteCostLine(lineId) }

    fun clearError() { _error.value = null }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "[CostLinesVM] save failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }
}
