package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Scope toggle — matches iOS "Mine / All" selector. */
enum class WoListFilter { MINE, ALL }

/** Horizon selector — matches iOS "Now / Next / Later" segmented control. */
enum class WoHorizon { NOW, NEXT, LATER }

data class WoBucket(val label: String, val items: List<WorkOrderEntity>)

@HiltViewModel
class WorkOrderListViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _scopeFilter = MutableStateFlow(WoListFilter.MINE)
    val filter: StateFlow<WoListFilter> = _scopeFilter.asStateFlow()

    private val _horizon = MutableStateFlow(WoHorizon.NOW)
    val horizon: StateFlow<WoHorizon> = _horizon.asStateFlow()

    /**
     * Multi-select status filter — mirrors iOS UnifiedWorkOrdersViewController's
     * `selectedStatuses` set. Empty set means "show all" (no filter applied).
     * Each entry is a raw status key, e.g. "pending_review", "assigned", etc.
     */
    private val _statusFilter = MutableStateFlow<Set<String>>(emptySet())
    val statusFilter: StateFlow<Set<String>> = _statusFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    val canCreateWo: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_CREATE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_CREATE))

    /** True for dispatcher-tier roles (root/admin/manager/dispatcher) — mirrors iOS canSeeAllScope. */
    val canSeeAllScope: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_ASSIGN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_ASSIGN))

    val canOpenDispatch: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.DISPATCH_MANAGE_ASSIGNMENTS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.DISPATCH_MANAGE_ASSIGNMENTS))

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
                // Lazily keeps the Room Flow alive as long as the ViewModel lives — ensures
                // edits made while the list screen is off-screen (navigated away) are always
                // reflected the moment the user returns, with no stale-data flash.
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )

    val buckets: StateFlow<List<WoBucket>> = combine(
        combine(_allWos, _searchQuery, _scopeFilter, _horizon) { a, b, c, d -> Quad(a, b, c, d) },
        _statusFilter,
    ) { (all, query, scope, horizon), statusFilter ->
        val myUserId = identityManager.getActiveUserId() ?: identityManager.getActiveAccountId()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // 1. Horizon filter (matches iOS Now / Next / Later semantics)
        val horizonFiltered = when (horizon) {
            WoHorizon.NOW -> all.filter { wo ->
                val due = wo.dueDate
                if (due == null) true
                else {
                    val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    // Overdue OR due within the next 7 days — actionable now
                    dueDate.isBefore(today) || !dueDate.isAfter(today.plusDays(7))
                }
            }
            WoHorizon.NEXT -> all.filter { wo ->
                val due = wo.dueDate
                if (due == null) true
                else {
                    val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    // Overdue OR due within the next 30 days — coming up soon
                    dueDate.isBefore(today) || !dueDate.isAfter(today.plusDays(30))
                }
            }
            WoHorizon.LATER -> all  // All work orders — full planning view
        }

        // 2. Scope filter (matches iOS Mine / All toggle)
        val scopeFiltered = horizonFiltered.filter { wo ->
            when (scope) {
                WoListFilter.MINE -> myUserId != null && wo.assignedTo == myUserId
                WoListFilter.ALL -> true
            }
        }

        // 3. Status filter — mirrors iOS selectedStatuses multi-select. Empty means "all".
        val statusFiltered = if (statusFilter.isEmpty()) scopeFiltered
        else scopeFiltered.filter { wo -> wo.status in statusFilter }

        // 4. Search filter
        val searched = statusFiltered.filter { wo ->
            if (query.isBlank()) true
            else wo.title.contains(query, ignoreCase = true) ||
                wo.description?.contains(query, ignoreCase = true) == true
        }

        // 5. Group into buckets
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
        val laterWithNoDueDate = later.byDueAsc() + noDueDate.sortedByDescending { it.updatedAt }
        if (laterWithNoDueDate.isNotEmpty()) result.add(WoBucket("Later", laterWithNoDueDate))
        val completedSorted = completed.sortedByDescending { it.updatedAt }.take(20)
        if (completedSorted.isNotEmpty()) result.add(WoBucket("Completed", completedSorted))

        result
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // Asset subtitle labels keyed by assetId — populated from local DB whenever
    // the WO list changes. Mirrors iOS UnifiedWorkOrderCell.assetSubtitle().
    @OptIn(ExperimentalCoroutinesApi::class)
    val assetLabels: StateFlow<Map<String, String?>> = _allWos
        .flatMapLatest { wos ->
            val accountId = identityManager.getActiveAccountId()
                ?: return@flatMapLatest flowOf(emptyMap())
            flow {
                val uniqueIds = wos.mapNotNull { it.assetId }.distinct()
                emit(uniqueIds.associateWith { id -> repository.assetLabelFor(id, accountId) })
            }
        }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }

    fun onFilterChanged(f: WoListFilter) { _scopeFilter.value = f }

    fun onScopeChanged(f: WoListFilter) { _scopeFilter.value = f }

    fun onHorizonChanged(h: WoHorizon) { _horizon.value = h }

    /** Toggle one status key in the multi-select filter (mirrors iOS makeStatusFilterMenu). */
    fun toggleStatusFilter(statusKey: String) {
        _statusFilter.value = if (statusKey in _statusFilter.value)
            _statusFilter.value - statusKey
        else
            _statusFilter.value + statusKey
    }

    /** Clear all active status filters. */
    fun clearStatusFilter() { _statusFilter.value = emptySet() }

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

    fun rescheduleWo(woId: String, newDueDateMs: Long) {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val existing = repository.getById(accountId, woId) ?: run {
                Timber.w("[WoListVM] rescheduleWo: WO $woId not found in local DB")
                return@launch
            }
            val updated = existing.copy(
                dueDate = newDueDateMs,
                updatedAt = System.currentTimeMillis(),
            )
            repository.upsert(accountId, updated)
            Timber.d("[WoListVM] rescheduled $woId to $newDueDateMs")
        }
    }
}

/** Tuple helper for combining 4 flows — avoids nesting a second combine(). */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
