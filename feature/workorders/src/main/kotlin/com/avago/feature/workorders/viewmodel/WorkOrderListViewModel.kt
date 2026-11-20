package com.avago.feature.workorders.viewmodel

import android.content.Context
import com.avago.core.data.domain.AdminRepository
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.SavedView
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.domain.SyncRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoSavedViewSpec
import com.avago.feature.workorders.model.WoSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

/** Due-date bucket ? the UI maps each key to a localized `wo_bucket_*` string. */
enum class WoBucketKey { OVERDUE, DUE_TODAY, THIS_WEEK, THIS_MONTH, LATER, COMPLETED }

data class WoBucket(val key: WoBucketKey, val items: List<WorkOrder>)

@HiltViewModel
class WorkOrderListViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    workers: WorkerRepository,
    private val sync: SyncRepository,
    private val permissionsManager: PermissionsManager,
    private val admin: AdminRepository,
    @ApplicationContext private val appContext: Context,
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

    private val _allWos: StateFlow<List<WorkOrder>> = repository.observeWorkOrders()
        .catch { e ->
            Timber.e(e, "[WoListVM] WO flow error")
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val myWorkerId: StateFlow<String?> = workers.observeMe()
        .map { it?.id }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Asset subtitle labels keyed by asset id. */
    val assetLabels: StateFlow<Map<String, String?>> = _allWos
        .map { wos -> wos.mapNotNull { wo -> wo.primaryAssetId?.let { it to wo.primaryAssetName } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val buckets: StateFlow<List<WoBucket>> = combine(
        combine(_allWos, _searchQuery, _scopeFilter, _horizon) { a, b, c, d -> Quad(a, b, c, d) },
        _statusFilter,
        myWorkerId,
    ) { (all, query, scope, horizon), statusFilter, myWorker ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // 1. Horizon filter (matches iOS Now / Next / Later semantics)
        val horizonFiltered = when (horizon) {
            WoHorizon.NOW -> all.filter { wo ->
                val due = wo.dueAt
                if (due == null) true
                else {
                    val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    // Overdue OR due within the next 7 days — actionable now
                    dueDate.isBefore(today) || !dueDate.isAfter(today.plusDays(7))
                }
            }
            WoHorizon.NEXT -> all.filter { wo ->
                val due = wo.dueAt
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
                WoListFilter.MINE -> myWorker != null && wo.assignedWorkerId == myWorker
                WoListFilter.ALL -> true
            }
        }

        // 3. Status filter — mirrors iOS selectedStatuses multi-select. Empty means "all".
        val statusFiltered = if (statusFilter.isEmpty()) scopeFiltered
        else scopeFiltered.filter { wo -> wo.status.wire in statusFilter }

        // 4. Search filter ? web woSearch parity: every term must match title, id,
        //    description, asset label, assignee or category (case/diacritic-insensitive).
        val searched = WoSearch.filter(statusFiltered, query) { wo ->
            listOf(wo.title, wo.id, wo.description, wo.primaryAssetName, wo.assignedWorkerName, wo.categoryName, wo.locationName)
        }

        // 5. Group into buckets
        val overdue = mutableListOf<WorkOrder>()
        val dueToday = mutableListOf<WorkOrder>()
        val thisWeek = mutableListOf<WorkOrder>()
        val thisMonth = mutableListOf<WorkOrder>()
        val later = mutableListOf<WorkOrder>()
        val noDueDate = mutableListOf<WorkOrder>()
        val completed = mutableListOf<WorkOrder>()

        for (wo in searched) {
            if (wo.status.isTerminal) {
                completed.add(wo)
                continue
            }
            val due = wo.dueAt
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
        fun List<WorkOrder>.byDueAsc() = sortedBy { it.dueAt }
        fun List<WorkOrder>.byDueDesc() = sortedByDescending { it.dueAt }

        val result = mutableListOf<WoBucket>()
        if (overdue.isNotEmpty()) result.add(WoBucket(WoBucketKey.OVERDUE, overdue.byDueAsc()))
        if (dueToday.isNotEmpty()) result.add(WoBucket(WoBucketKey.DUE_TODAY, dueToday.byDueAsc()))
        if (thisWeek.isNotEmpty()) result.add(WoBucket(WoBucketKey.THIS_WEEK, thisWeek.byDueAsc()))
        if (thisMonth.isNotEmpty()) result.add(WoBucket(WoBucketKey.THIS_MONTH, thisMonth.byDueAsc()))
        val laterWithNoDueDate = later.byDueAsc() + noDueDate.sortedByDescending { it.updatedAt }
        if (laterWithNoDueDate.isNotEmpty()) result.add(WoBucket(WoBucketKey.LATER, laterWithNoDueDate))
        val completedSorted = completed.sortedByDescending { it.updatedAt }.take(20)
        if (completedSorted.isNotEmpty()) result.add(WoBucket(WoBucketKey.COMPLETED, completedSorted))

        result
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** True when the scoped list has rows but the active search / status filter hides them all. */
    val isFilteredEmpty: StateFlow<Boolean> = combine(buckets, _searchQuery, _statusFilter) { b, q, s ->
        b.isEmpty() && (WoSearch.terms(q).isNotEmpty() || s.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }

    /** Clears search text and status filters (empty-state "Clear filters" action). */
    fun clearSearchAndFilters() {
        _searchQuery.value = ""
        _statusFilter.value = emptySet()
    }

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
                if (!sync.syncNow()) _syncError.value = appContext.getString(R.string.wo_list_sync_failed)
            } catch (e: Exception) {
                Timber.e(e, "[WoListVM] Sync failed")
                _syncError.value = appContext.getString(R.string.wo_list_sync_failed)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ------------------------------------------------------------------ saved views

    /** The member's own and the account's saved work-order views. */
    val savedViews: StateFlow<List<SavedView>> = admin.observeSavedViews(WoSavedViewSpec.DATASET)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Managers may share views with the whole account and change shared ones. */
    val isManager: StateFlow<Boolean> = admin.observeIsManager()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _activeViewId = MutableStateFlow<String?>(null)

    /** The applied view while the filters still match it; changing a filter leaves the view. */
    val activeView: StateFlow<SavedView?> = combine(
        savedViews, _activeViewId, combine(_searchQuery, _statusFilter, _scopeFilter, _horizon, WoSavedViewSpec::of),
    ) { views, id, current ->
        views.firstOrNull { it.id == id }?.takeIf { WoSavedViewSpec.parse(it.spec) == current }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _viewMessage = MutableStateFlow<Int?>(null)

    /** A string resource about the last saved-view action, shown once. */
    val viewMessage: StateFlow<Int?> = _viewMessage.asStateFlow()

    fun viewMessageShown() {
        _viewMessage.value = null
    }

    fun applyView(view: SavedView) {
        val spec = WoSavedViewSpec.parse(view.spec)
        if (spec == null) {
            _viewMessage.value = R.string.wo_view_unreadable
            return
        }
        _searchQuery.value = spec.query
        _statusFilter.value = spec.statuses.toSet()
        _scopeFilter.value = spec.scopeFilter
        _horizon.value = spec.horizonFilter
        _activeViewId.value = view.id
    }

    private fun currentSpec() = WoSavedViewSpec.of(_searchQuery.value, _statusFilter.value, _scopeFilter.value, _horizon.value)

    /** Saves the current filters as a new view. */
    fun saveView(name: String, shared: Boolean) = viewAction {
        _activeViewId.value = admin.createSavedView(WoSavedViewSpec.DATASET, name, currentSpec().toJson(), shared)
    }

    /** Renames [view], changes its sharing, and when [withCurrentFilters] replaces its filters with the current ones. */
    fun updateView(view: SavedView, name: String, shared: Boolean, withCurrentFilters: Boolean) = viewAction {
        admin.updateSavedView(view.id, name, if (withCurrentFilters) currentSpec().toJson() else view.spec, shared)
        if (withCurrentFilters) _activeViewId.value = view.id
    }

    fun deleteView(view: SavedView) = viewAction {
        admin.deleteSavedView(view.id)
        if (_activeViewId.value == view.id) _activeViewId.value = null
    }

    private fun viewAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: StoreNotReadyException) {
                Timber.w(e, "[WoListVM] saved view change before the store opened")
                _viewMessage.value = R.string.wo_view_not_ready
            } catch (e: RecordNotFoundException) {
                Timber.w(e, "[WoListVM] saved view is gone")
                _viewMessage.value = R.string.wo_view_gone
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "[WoListVM] saved view rejected locally")
                _viewMessage.value = R.string.wo_view_invalid
            } catch (e: IllegalStateException) {
                Timber.e(e, "[WoListVM] saved view change could not be queued")
                _viewMessage.value = R.string.wo_view_save_failed
            }
        }
    }

    fun rescheduleWo(woId: String, newDueDateMs: Long) {
        viewModelScope.launch {
            try {
                repository.edit(woId) { it.copy(dueAt = newDueDateMs) }
            } catch (e: Exception) {
                Timber.e(e, "[WoListVM] reschedule failed")
                _syncError.value = appContext.getString(R.string.wo_error_save_failed)
            }
        }
    }
}

/** Tuple helper for combining 4 flows — avoids nesting a second combine(). */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
