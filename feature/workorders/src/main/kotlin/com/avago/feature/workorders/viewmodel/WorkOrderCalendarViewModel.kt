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
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    val selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val displayMonth = MutableStateFlow<LocalDate>(LocalDate.now().withDayOfMonth(1))

    @OptIn(ExperimentalCoroutinesApi::class)
    val wosByDate: StateFlow<Map<LocalDate, List<WorkOrderEntity>>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeAll(accountId)
                    .catch { e -> Timber.e(e, "[CalendarVM] flow error"); emit(emptyList()) }
            }
            .map { wos ->
                val zone = ZoneId.systemDefault()
                wos.filter { it.dueDate != null && it.status !in listOf("cancelled") }
                    .groupBy { wo ->
                        Instant.ofEpochMilli(wo.dueDate ?: error("unreachable")).atZone(zone).toLocalDate()
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    val selectedDayWos: StateFlow<List<WorkOrderEntity>> =
        combine(wosByDate, selectedDate) { map, date ->
            map[date] ?: emptyList()
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun navigateMonth(delta: Int) {
        displayMonth.value = displayMonth.value.plusMonths(delta.toLong())
    }

    /**
     * Reschedule a WO by dragging it to a new day.
     */
    fun rescheduleWo(wo: WorkOrderEntity, newDate: LocalDate) {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val newDueMs = newDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val updated = wo.copy(
                    dueDate = newDueMs,
                    updatedAt = System.currentTimeMillis(),
                )
                repository.upsert(accountId, updated)
            } catch (e: Exception) {
                Timber.e(e, "[CalendarVM] rescheduleWo failed")
            }
        }
    }
}
