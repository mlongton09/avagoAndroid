package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.FeatureFlags
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.sync.SyncEngine
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Ordered status columns shown on the dispatch board (excludes CANCELLED). */
val DISPATCH_COLUMNS = listOf(
    WoStatus.DRAFT,
    WoStatus.OPEN,
    WoStatus.ASSIGNED,
    WoStatus.IN_PROGRESS,
    WoStatus.ON_HOLD,
    WoStatus.COMPLETE,
)

@HiltViewModel
class DispatchBoardViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
    private val featureFlags: FeatureFlags,
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

    /** Live search query for the dispatch board — matches iOS UISearchController inline search. */
    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allWos: StateFlow<List<WorkOrderEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeAll(accountId)
                    .catch { e -> Timber.e(e, "[DispatchBoardVM] flow error"); emit(emptyList()) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val columns: StateFlow<Map<WoStatus, List<WorkOrderEntity>>> =
        combine(allWos, searchQuery) { wos, query ->
            // Only active WOs on the board — exclude cancelled, then apply search filter
            val active = wos
                .filter { it.status != WoStatus.CANCELLED.key }
                .let { list ->
                    if (query.isBlank()) list
                    else list.filter { it.title.contains(query, ignoreCase = true) }
                }
            DISPATCH_COLUMNS.associateWith { status ->
                active.filter { it.status == status.key }
                    .sortedBy { it.dueDate ?: Long.MAX_VALUE }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DISPATCH_COLUMNS.associateWith { emptyList() },
        )

    /**
     * True when work is unevenly distributed: the technician with the most assigned
     * open WOs has more than 2x the average load, and the banner has not been dismissed.
     */
    val showRebalanceBanner: StateFlow<Boolean> =
        combine(allWos, _bannerDismissed) { wos, dismissed ->
            if (dismissed) return@combine false
            val activeWos = wos.filter {
                it.status != WoStatus.CANCELLED.key &&
                    it.status != WoStatus.COMPLETE.key &&
                    !it.assignedTo.isNullOrBlank()
            }
            if (activeWos.size < 2) return@combine false
            val countsByTech = activeWos.groupBy { it.assignedTo }.mapValues { it.value.size }
            if (countsByTech.size < 2) return@combine false
            val maxCount = countsByTech.values.max()
            val avgCount = countsByTech.values.average()
            maxCount > avgCount * 2.0
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    /**
     * Stub: auto-rebalance is coming in a future release.
     * Currently just dismisses the banner; a snackbar is shown from the UI.
     */
    fun rebalance() {
        _bannerDismissed.value = true
    }

    /**
     * Called when a card is dragged to a new column.
     * RBAC is checked in the UI before calling this.
     */
    fun moveToStatus(wo: WorkOrderEntity, targetStatus: WoStatus) {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val updated = wo.copy(
                    status = targetStatus.key,
                    updatedAt = now,
                    startedAt = if (targetStatus == WoStatus.IN_PROGRESS && wo.startedAt == null)
                        now else wo.startedAt,
                    completedAt = if (targetStatus == WoStatus.COMPLETE) now else wo.completedAt,
                )
                repository.upsert(accountId, updated)
            } catch (e: Exception) {
                Timber.e(e, "[DispatchBoardVM] moveToStatus failed for ${wo.woId}")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { syncEngine.sync() } catch (e: Exception) {
                Timber.e(e, "[DispatchBoardVM] sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
