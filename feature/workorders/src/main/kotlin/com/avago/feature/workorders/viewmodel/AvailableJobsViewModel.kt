package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderFilter
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderStatus
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AvailableJobsViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val workers: WorkerRepository,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Work orders being claimed. */
    private val _claimingIds = MutableStateFlow<Set<String>>(emptySet())
    val claimingIds: StateFlow<Set<String>> = _claimingIds.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Unassigned open work orders, most urgent first. */
    val availableJobs: StateFlow<List<WorkOrder>> = repository
        .observeWorkOrders(WorkOrderFilter(statuses = setOf(WorkOrderStatus.OPEN), unassignedOnly = true))
        .map { wos -> wos.sortedBy { it.priority.ordinal } }
        .catch { e -> Timber.e(e, "[AvailableJobsVM] flow error"); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Assigns [wo] to the signed-in user as lead; the server rejects a job someone else claimed first. */
    fun claimJob(wo: WorkOrder) {
        viewModelScope.launch {
            _claimingIds.value = _claimingIds.value + wo.id
            try {
                val me = workers.observeMe().first()?.userId ?: error("no worker profile for the signed-in user")
                repository.assign(wo.id, me, lead = true)
            } catch (e: Exception) {
                Timber.e(e, "[AvailableJobsVM] claimJob failed for ${wo.id}")
                _error.value = e.message
            } finally {
                _claimingIds.value = _claimingIds.value - wo.id
            }
        }
    }

    fun clearError() { _error.value = null }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { syncEngine.sync() } catch (e: Exception) {
                Timber.e(e, "[AvailableJobsVM] sync failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
