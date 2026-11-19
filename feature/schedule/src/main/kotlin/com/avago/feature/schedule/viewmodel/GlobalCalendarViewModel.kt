package com.avago.feature.schedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.PmFilter
import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmRepository
import com.avago.feature.schedule.util.PmSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Month calendar of open, dated PM occurrences. */
@HiltViewModel
class GlobalCalendarViewModel @Inject constructor(
    pm: PmRepository,
) : ViewModel() {

    val selectedMonth = MutableStateFlow(YearMonth.now())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** Open occurrences due within [selectedMonth], by due day. */
    val schedulesByDate: StateFlow<Map<LocalDate, List<PmOccurrence>>> =
        combine(pm.observeOccurrences(PmFilter()), selectedMonth) { list, month -> byDate(list, month) }
            .catch { e ->
                Timber.e(e, "[CalendarVM] observe failed")
                emit(emptyMap())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val schedulesForSelectedDay: StateFlow<List<PmOccurrence>> =
        combine(schedulesByDate, _selectedDate) { byDate, date -> byDate[date].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun navigateMonth(delta: Int) {
        val month = selectedMonth.value.plusMonths(delta.toLong())
        selectedMonth.value = month
        if (YearMonth.from(_selectedDate.value) != month) _selectedDate.value = month.atDay(1)
    }

    companion object {
        fun byDate(list: List<PmOccurrence>, month: YearMonth): Map<LocalDate, List<PmOccurrence>> =
            list.mapNotNull { o -> o.dueAt?.let { PmSchedule.localDate(it) to o } }
                .filter { (date, _) -> YearMonth.from(date) == month }
                .groupBy({ it.first }, { it.second })
    }
}
