package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.sync.SyncEngine
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class WoListFilter { ALL, OPEN, MINE, OVERDUE }

enum class WoHorizon { ALL_TIME, THIS_WEEK, THIS_MONTH }

data class WoBucket(val label: String, val items: List<WorkOrderEntity>)

@HiltViewModel
class WorkOrderListViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _scopeFilter = MutableStateFlow(WoListFilter.ALL)
    val filter: StateFlow<WoListFilter> = _scopeFilter.asStateFlow()

    private val _horizon = MutableStateFlow(WoHorizon.ALL_TIME)
    val horizon: StateFlow<WoHorizon> = _horizon.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allWos: StateFlow<List<WorkOrderEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) {
                    flowOf(emptyList())
                } else {
                    try {
                        repository.observeAll(accountId)
                    } catch (e: Exception) {
                        Timber.e(e, "[WoListVM] Failed to observe WOs for $accountId")
                        flowOf(emptyList())
                    }
                }
            }
            .catch { e ->
                Timber.e(e, "[WoListVM] WO flow error")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val buckets: StateFlow<List<WoBucket>> = combine(
        _allWos,
        _searchQuery,
        _scopeFilter,
        _horizon,
    ) { all, query, scope, horizon ->
        val myUserId = identityManager.getActiveUserId() ?: identityManager.getActiveAccountId()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowMs = System.currentTimeMillis()

        // 1. Horizon filter
        val horizonFiltered = when (horizon) {
            WoHorizon.ALL_TIME -> all
            WoHorizon.THIS_WEEK -> all.filter { wo ->
                val due = wo.dueDate
                if (due == null) {
                    // no-due-date items shown in all horizons
                    true
                } else {
                    val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    // overdue OR within next 7 days
                    dueDate.isBefore(today) || !dueDate.isAfter(today.plusDays(7))
                }
            }
            WoHorizon.THIS_MONTH -> all.filter { wo ->
                val due = wo.dueDate
                if (due == null) {
                    true
                } else {
                    val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    // overdue OR within next 30 days
                    dueDate.isBefore(today) || !dueDate.isAfter(today.plusDays(30))
                }
            }
        }

        // 2. Scope filter
        val scopeFiltered = horizonFiltered.filter { wo ->
            when (scope) {
                WoListFilter.ALL -> true
                WoListFilter.OPEN -> wo.status !in listOf("complete", "cancelled")
                WoListFilter.MINE -> wo.assignedTo == myUserId || wo.createdBy == myUserId
                WoListFilter.OVERDUE -> {
                    val due = wo.dueDate ?: return@filter false
                    due < nowMs && wo.status !in listOf("complete", "cancelled")
                }
            }
        }

        // 3. Search filter
        val searched = scopeFiltered.filter { wo ->
            if (query.isBlank()) true
            else wo.title.contains(query, ignoreCase = true) ||
                wo.description?.contains(query, ignoreCase = true) == true
        }

        // 4. Group into buckets
        val completedStatuses = listOf("complete", "cancelled")

        val overdue = mutableListOf<WorkOrderEntity>()
        val dueToday = mutableListOf<WorkOrderEntity>()
        val thisWeek = mutableListOf<WorkOrderEntity>()
        val thisMonth = mutableListOf<WorkOrderEntity>()
        val later = mutableListOf<WorkOrderEntity>()
        val noDueDate = mutableListOf<WorkOrderEntity>()
        val completed = mutableListOf<WorkOrderEntity>()

        for (wo in searched) {
            if (wo.status in completedStatuses) {
                completed.add(wo)
                continue
            }
            val due = wo.dueDate
            if (due == null) {
                noDueDate.add(wo)
                continue
            }
            val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            when {
                dueDate.isBefore(today) -> overdue.add(wo)
                dueDate.isEqual(today) -> dueToday.add(wo)
                !dueDate.isAfter(today.plusDays(7)) -> thisWeek.add(wo)
                !dueDate.isAfter(today.plusDays(30)) -> thisMonth.add(wo)
                else -> later.add(wo)
            }
        }

        // Sort each bucket
        fun List<WorkOrderEntity>.byDueAsc() = sortedBy { it.dueDate }
        fun List<WorkOrderEntity>.byDueDesc() = sortedByDescending { it.dueDate }

        val result = mutableListOf<WoBucket>()
        if (overdue.isNotEmpty()) result.add(WoBucket("Overdue", overdue.byDueAsc()))
        if (dueToday.isNotEmpty()) result.add(WoBucket("Due Today", dueToday.byDueAsc()))
        if (thisWeek.isNotEmpty()) result.add(WoBucket("This Week", thisWeek.byDueAsc()))
        if (thisMonth.isNotEmpty()) result.add(WoBucket("This Month", thisMonth.byDueAsc()))
        if (later.isNotEmpty()) result.add(WoBucket("Later", later.byDueAsc()))
        if (noDueDate.isNotEmpty()) result.add(WoBucket("No Due Date", noDueDate.sortedByDescending { it.updatedAt }))
        val completedSorted = completed.sortedByDescending { it.updatedAt }.take(20)
        if (completedSorted.isNotEmpty()) result.add(WoBucket("Completed", completedSorted))

        result
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }

    fun onFilterChanged(f: WoListFilter) { _scopeFilter.value = f }

    fun onScopeChanged(f: WoListFilter) { _scopeFilter.value = f }

    fun onHorizonChanged(h: WoHorizon) { _horizon.value = h }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncError.value = null
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[WoListVM] Sync failed")
                _syncError.value = "Couldn't sync. Tap to retry."
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
