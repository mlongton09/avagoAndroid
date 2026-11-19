package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderStatus
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class WorkOrderCalendarViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    workers: WorkerRepository,
    permissionsManager: PermissionsManager,
) : ViewModel() {

    val selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val displayMonth = MutableStateFlow<LocalDate>(LocalDate.now().withDayOfMonth(1))

    val canSeeAllScope: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_ASSIGN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_ASSIGN))

    private val _showAll = MutableStateFlow(false)
    val showAll: StateFlow<Boolean> = _showAll.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggleScope() { _showAll.value = !_showAll.value }

    val wosByDate: StateFlow<Map<LocalDate, List<WorkOrder>>> =
        combine(
            repository.observeWorkOrders().catch { e -> Timber.e(e, "[CalendarVM] flow error"); emit(emptyList()) },
            workers.observeMe().catch { emit(null) },
            _showAll,
        ) { wos, me, showAll ->
            val zone = ZoneId.systemDefault()
            wos.filter { wo ->
                wo.dueAt != null && wo.status != WorkOrderStatus.CANCELLED &&
                    (showAll || me == null || wo.assignedWorkerId == me.id || (me.userId != null && wo.createdBy == me.userId))
            }.groupBy { wo -> Instant.ofEpochMilli(requireNotNull(wo.dueAt)).atZone(zone).toLocalDate() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val selectedDayWos: StateFlow<List<WorkOrder>> =
        combine(wosByDate, selectedDate) { map, date -> map[date] ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Asset subtitle labels keyed by asset id. */
    val assetLabels: StateFlow<Map<String, String?>> = wosByDate
        .map { byDate -> byDate.values.flatten().mapNotNull { wo -> wo.primaryAssetId?.let { it to wo.primaryAssetName } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun navigateMonth(delta: Int) {
        displayMonth.value = displayMonth.value.plusMonths(delta.toLong())
    }

    fun navigateToToday() {
        val today = LocalDate.now()
        displayMonth.value = today.withDayOfMonth(1)
        selectedDate.value = today
    }

    /** Moves [wo] to [newDate] (start of day, local time). */
    fun rescheduleWo(wo: WorkOrder, newDate: LocalDate) {
        val due = newDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        viewModelScope.launch {
            try {
                repository.edit(wo.id) { it.copy(dueAt = due) }
            } catch (e: Exception) {
                Timber.e(e, "[CalendarVM] rescheduleWo failed")
                _error.value = e.message
            }
        }
    }
}
