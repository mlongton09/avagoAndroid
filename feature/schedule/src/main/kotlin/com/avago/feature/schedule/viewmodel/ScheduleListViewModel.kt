package com.avago.feature.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.PmFilter
import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmRepository
import com.avago.core.data.domain.SyncRepository
import com.avago.feature.schedule.util.PmSchedule
import com.avago.feature.schedule.util.ScheduleStatusFilter
import com.avago.feature.schedule.util.ScheduleTypeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Open PM occurrences ("schedules"), optionally for one asset. */
@HiltViewModel
class ScheduleListViewModel @Inject constructor(
    private val pm: PmRepository,
    private val sync: SyncRepository,
) : ViewModel() {

    /** When non-null, shows only this asset's schedules. */
    val assetId: MutableStateFlow<String?> = MutableStateFlow(null)

    private val _statusFilter = MutableStateFlow(ScheduleStatusFilter.ALL)
    val statusFilter: StateFlow<ScheduleStatusFilter> = _statusFilter.asStateFlow()

    private val _typeFilter = MutableStateFlow(ScheduleTypeFilter.ALL)
    val typeFilter: StateFlow<ScheduleTypeFilter> = _typeFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val schedules: StateFlow<List<PmOccurrence>> = combine(
        assetId.flatMapLatest { pm.observeOccurrences(PmFilter(assetId = it)) },
        _statusFilter,
        _typeFilter,
    ) { all, status, type -> PmSchedule.filter(all, status, type, System.currentTimeMillis()) }
        .catch { e ->
            Timber.e(e, "[ScheduleListVM] observe failed")
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                sync.syncNow()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
