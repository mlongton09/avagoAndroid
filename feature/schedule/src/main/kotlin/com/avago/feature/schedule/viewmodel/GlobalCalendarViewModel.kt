package com.avago.feature.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ScheduleEntity
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
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class GlobalCalendarViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val identityManager: IdentityManager,
) : ViewModel() {

    val selectedMonth = MutableStateFlow(YearMonth.now())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val schedules: StateFlow<List<ScheduleEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeAll(accountId)
                    .catch { e -> Timber.e(e, "[CalendarVM] flow error"); emit(emptyList()) }
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Map of [LocalDate] → list of schedules whose next due date falls on that day.
     * Only dates within [selectedMonth] are included.
     */
    val schedulesByDate: StateFlow<Map<LocalDate, List<ScheduleEntity>>> =
        combine(schedules, selectedMonth) { list, month ->
            val result = mutableMapOf<LocalDate, MutableList<ScheduleEntity>>()
            val monthStart = month.atDay(1)
            val monthEnd = month.atEndOfMonth()

            list.filter { it.isActive }.forEach { schedule ->
                val dueDate = RruleHelper.epochMsToLocalDate(schedule.nextDueAt)
                    ?: return@forEach
                if (!dueDate.isBefore(monthStart) && !dueDate.isAfter(monthEnd)) {
                    result.getOrPut(dueDate) { mutableListOf() }.add(schedule)
                }
            }
            result.mapValues { (_, v) -> v.toList() }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Schedules due on [selectedDate]. */
    val schedulesForSelectedDay: StateFlow<List<ScheduleEntity>> =
        combine(schedulesByDate, _selectedDate) { byDate, date ->
            byDate[date] ?: emptyList()
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun navigateMonth(delta: Int) {
        selectedMonth.value = selectedMonth.value.plusMonths(delta.toLong())
        // If selected date is outside new month, move it to the first
        val newMonth = selectedMonth.value
        if (_selectedDate.value.year != newMonth.year ||
            _selectedDate.value.monthValue != newMonth.monthValue
        ) {
            _selectedDate.value = newMonth.atDay(1)
        }
    }

    /**
     * Returns all schedules due on [date] (for imperative callers such as tests).
     * Prefer [schedulesForSelectedDay] in Compose.
     */
    fun schedulesForDay(date: LocalDate): List<ScheduleEntity> =
        schedulesByDate.value[date] ?: emptyList()
}
