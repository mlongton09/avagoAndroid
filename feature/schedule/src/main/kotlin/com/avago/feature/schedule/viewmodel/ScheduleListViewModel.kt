package com.avago.feature.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ScheduleEntity
import com.avago.core.sync.SyncEngine
import com.avago.feature.schedule.repository.ScheduleRepository
import com.avago.feature.schedule.util.RruleHelper
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
import javax.inject.Inject

/** Status filter applied to the schedule list. */
enum class ScheduleStatusFilter { ALL, DUE_SOON, UPCOMING, OVERDUE }

/** Schedule-type filter applied to the schedule list. */
enum class ScheduleTypeFilter { ALL, BY_DATE, BY_METER }

@HiltViewModel
class ScheduleListViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    /** When non-null, shows only schedules belonging to this asset. */
    val assetId: MutableStateFlow<String?> = MutableStateFlow(null)

    private val _statusFilter = MutableStateFlow(ScheduleStatusFilter.ALL)
    val statusFilter: StateFlow<ScheduleStatusFilter> = _statusFilter.asStateFlow()

    private val _typeFilter = MutableStateFlow(ScheduleTypeFilter.ALL)
    val typeFilter: StateFlow<ScheduleTypeFilter> = _typeFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allSchedules: StateFlow<List<ScheduleEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) {
                    flowOf(emptyList())
                } else {
                    try {
                        repository.observeAll(accountId)
                    } catch (e: Exception) {
                        Timber.e(e, "[ScheduleListVM] observe failed for $accountId")
                        flowOf(emptyList())
                    }
                }
            }
            .catch { e ->
                Timber.e(e, "[ScheduleListVM] flow error")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val schedules: StateFlow<List<ScheduleEntity>> = combine(
        _allSchedules,
        assetId,
        _statusFilter,
        _typeFilter,
    ) { all, filterAssetId, status, type ->
        all
            .filter { it.isActive }
            .filter { s -> filterAssetId == null || s.assetId == filterAssetId }
            .filter { s ->
                when (type) {
                    ScheduleTypeFilter.ALL -> true
                    ScheduleTypeFilter.BY_DATE -> s.scheduleType == "calendar"
                    ScheduleTypeFilter.BY_METER -> s.scheduleType == "meter"
                }
            }
            .filter { s ->
                when (status) {
                    ScheduleStatusFilter.ALL -> true
                    ScheduleStatusFilter.OVERDUE -> RruleHelper.isOverdue(s)
                    ScheduleStatusFilter.DUE_SOON -> RruleHelper.isDueSoon(s)
                    ScheduleStatusFilter.UPCOMING ->
                        !RruleHelper.isOverdue(s) && !RruleHelper.isDueSoon(s)
                }
            }
            .sortedWith(
                compareByDescending<ScheduleEntity> { RruleHelper.isOverdue(it) }
                    .thenByDescending { RruleHelper.isDueSoon(it) }
                    .thenBy { it.nextDueAt ?: Long.MAX_VALUE }
            )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onStatusFilterChanged(filter: ScheduleStatusFilter) {
        _statusFilter.value = filter
    }

    fun onTypeFilterChanged(filter: ScheduleTypeFilter) {
        _typeFilter.value = filter
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[ScheduleListVM] sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
