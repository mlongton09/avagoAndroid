package com.avago.feature.reports.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.reports.ReportAggregator
import com.avago.core.reports.model.CostGroupMode
import com.avago.core.reports.model.CostPeriodMode
import com.avago.core.reports.model.CostPeriodSpec
import com.avago.core.reports.model.CostReportData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CostReportViewModel @Inject constructor(
    private val aggregator: ReportAggregator,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _groupMode = MutableStateFlow(CostGroupMode.ALL)
    val groupMode: StateFlow<CostGroupMode> = _groupMode.asStateFlow()

    private val _periodMode = MutableStateFlow(CostPeriodMode.YEAR)
    val periodMode: StateFlow<CostPeriodMode> = _periodMode.asStateFlow()

    // 0 = most recent window; increasing goes further back in time
    private val _periodOffset = MutableStateFlow(0)
    val periodOffset: StateFlow<Int> = _periodOffset.asStateFlow()

    private val _expandedKeys = MutableStateFlow<Set<String>>(emptySet())
    val expandedKeys: StateFlow<Set<String>> = _expandedKeys.asStateFlow()

    private val _expandedInventory = MutableStateFlow(false)
    val expandedInventory: StateFlow<Boolean> = _expandedInventory.asStateFlow()

    val reportData: StateFlow<CostReportData?> = combine(
        identity.activeAccountId.filterNotNull(),
        _groupMode,
        _periodMode,
        _periodOffset,
    ) { accountId, groupMode, periodMode, offset ->
        val periods = computePeriods(periodMode, offset)
        Triple(accountId, groupMode, periods)
    }.flatMapLatest { (accountId, groupMode, periods) ->
        flow<CostReportData?> {
            emit(null)
            emit(aggregator.costReport(accountId, periods, groupMode))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGroupMode(mode: CostGroupMode) {
        _groupMode.value = mode
        _expandedKeys.value = emptySet()
        _expandedInventory.value = false
        if (mode == CostGroupMode.TCO) _periodOffset.value = 0
    }

    fun setPeriodMode(mode: CostPeriodMode) {
        _periodMode.value = mode
        _periodOffset.value = 0
    }

    fun prevPeriod() { _periodOffset.value += 1 }
    fun nextPeriod() { if (_periodOffset.value > 0) _periodOffset.value -= 1 }

    fun toggleExpanded(key: String) {
        _expandedKeys.value = _expandedKeys.value.toMutableSet().apply {
            if (contains(key)) remove(key) else add(key)
        }
    }

    fun expandAll(keys: List<String>) { _expandedKeys.value = keys.toSet() }
    fun collapseAll() { _expandedKeys.value = emptySet() }

    fun toggleInventory() { _expandedInventory.value = !_expandedInventory.value }

    fun periodRangeLabel(data: CostReportData?): String {
        val periods = data?.periods ?: return ""
        if (periods.isEmpty()) return ""
        return if (periods.size == 1) periods[0].label
        else "${periods.first().label} – ${periods.last().label}"
    }

    fun canNavigateNext(): Boolean = _periodOffset.value > 0

    private fun computePeriods(mode: CostPeriodMode, offset: Int): List<CostPeriodSpec> {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        return when (mode) {
            CostPeriodMode.YEAR -> {
                val endYear = now.year - offset
                (2 downTo 0).map { i ->
                    val year = endYear - i
                    val start = LocalDate(year, 1, 1).atStartOfDayIn(tz).toEpochMilliseconds()
                    val end = LocalDate(year + 1, 1, 1).atStartOfDayIn(tz).toEpochMilliseconds() - 1
                    CostPeriodSpec(label = year.toString(), startMs = start, endMs = end)
                }
            }
            CostPeriodMode.MONTH -> {
                val nowMonthIdx = now.year * 12 + (now.monthNumber - 1)
                val endMonthIdx = nowMonthIdx - offset
                (2 downTo 0).map { i ->
                    val idx = endMonthIdx - i
                    val year = idx / 12
                    val month = idx % 12 + 1
                    val start = LocalDate(year, month, 1).atStartOfDayIn(tz).toEpochMilliseconds()
                    val nextYear = if (month == 12) year + 1 else year
                    val nextMonth = if (month == 12) 1 else month + 1
                    val end = LocalDate(nextYear, nextMonth, 1).atStartOfDayIn(tz).toEpochMilliseconds() - 1
                    val label = "${Month(month).name.take(3).replaceFirstChar { it.uppercase() }} '${(year % 100).toString().padStart(2, '0')}"
                    CostPeriodSpec(label = label, startMs = start, endMs = end)
                }
            }
        }
    }
}
