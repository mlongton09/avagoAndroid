package com.avago.feature.workorders.viewmodel

import com.avago.core.data.domain.SyncRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.FeatureFlags
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.feature.workorders.model.WoSearch
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.model.availableTransitions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Ordered status columns shown on the dispatch board (terminal statuses other than completed are hidden). */
val DISPATCH_COLUMNS = listOf(
    WoStatus.DRAFT,
    WoStatus.OPEN,
    WoStatus.ASSIGNED,
    WoStatus.IN_PROGRESS,
    WoStatus.ON_HOLD,
    WoStatus.COMPLETE,
)

/** Statuses a card may be moved to from the board; assignment needs a technician and happens on the detail screen. */
fun boardMoveTargets(current: WoStatus): List<WoStatus> =
    current.availableTransitions().map { it.targetStatus }
        .filter { it != WoStatus.ASSIGNED && it in DISPATCH_COLUMNS }
        .distinct()

/** True when a board move from [from] to [to] can be undone by the reverse transition. */
fun canUndoMove(from: WoStatus, to: WoStatus): Boolean = from in to.availableTransitions().map { it.targetStatus }

@HiltViewModel
class DispatchBoardViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val sync: SyncRepository,
    featureFlags: FeatureFlags,
) : ViewModel() {

    /** Reflects [FeatureFlags.dispatchEnabled]; observed reactively via the config DB. */
    val dispatchEnabled: StateFlow<Boolean> =
        featureFlags.observeFlag("feature.dispatch_enabled", default = true)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = featureFlags.dispatchEnabled,
            )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Live search query for the dispatch board. */
    val searchQuery = MutableStateFlow("")

    private val allWos: StateFlow<List<WorkOrder>> = repository.observeWorkOrders()
        .catch { e -> Timber.e(e, "[DispatchBoardVM] flow error"); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val columns: StateFlow<Map<WoStatus, List<WorkOrder>>> =
        combine(allWos, searchQuery) { wos, query ->
            val visible = WoSearch.filter(wos, query) {
                listOf(it.title, it.description, it.assignedWorkerName, it.categoryName)
            }
            val byStatus = visible.groupBy { WoStatus.fromDomain(it.status) }
            DISPATCH_COLUMNS.associateWith { status ->
                byStatus[status].orEmpty().sortedBy { it.dueAt ?: Long.MAX_VALUE }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DISPATCH_COLUMNS.associateWith { emptyList() },
        )

    /**
     * True when work is unevenly distributed: the technician with the most assigned
     * active WOs has more than 2x the average load, and the banner has not been dismissed.
     */
    val showRebalanceBanner: StateFlow<Boolean> =
        combine(allWos, _bannerDismissed) { wos, dismissed ->
            if (dismissed) return@combine false
            val countsByTech = wos
                .filter { !it.status.isTerminal && it.assignedWorkerId != null }
                .groupingBy { it.assignedWorkerId }
                .eachCount()
            if (countsByTech.size < 2 || countsByTech.values.sum() < 2) return@combine false
            countsByTech.values.max() > countsByTech.values.average() * 2.0
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    /** Automatic rebalancing is not offered on mobile yet; acknowledging the banner dismisses it. */
    fun rebalance() {
        _bannerDismissed.value = true
    }

    /** Called when a card is moved to a new column; the server enforces the status machine. */
    fun moveToStatus(wo: WorkOrder, targetStatus: WoStatus) {
        if (wo.status == targetStatus.domain) return
        viewModelScope.launch {
            try {
                repository.transition(wo.id, targetStatus.domain)
            } catch (e: Exception) {
                Timber.e(e, "[DispatchBoardVM] moveToStatus failed for ${wo.id}")
                _error.value = e.message
            }
        }
    }

    /** Undo for a board move: transitions back to the status before [moveToStatus]. */
    fun restore(original: WorkOrder) {
        viewModelScope.launch {
            try {
                repository.transition(original.id, original.status)
            } catch (e: Exception) {
                Timber.e(e, "[DispatchBoardVM] restore failed for ${original.id}")
                _error.value = e.message
            }
        }
    }

    fun clearError() { _error.value = null }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { sync.syncNow() } catch (e: Exception) {
                Timber.e(e, "[DispatchBoardVM] sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
