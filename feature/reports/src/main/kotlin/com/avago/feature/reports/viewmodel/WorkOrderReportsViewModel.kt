package com.avago.feature.reports.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.reports.ReportAggregator
import com.avago.core.reports.model.BacklogAgeData
import com.avago.core.reports.model.CompletionRateData
import com.avago.core.reports.model.EffortAccuracyRow
import com.avago.core.reports.model.MttrData
import com.avago.core.reports.model.OpenDashboardData
import com.avago.core.reports.model.PmComplianceData
import com.avago.core.reports.model.RecurringIssueRow
import com.avago.core.reports.model.ReportRange
import com.avago.core.reports.model.ReportRangePreset
import com.avago.core.reports.model.TechPerformanceRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkOrderReportsViewModel @Inject constructor(
    private val aggregator: ReportAggregator,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _rangePreset = MutableStateFlow(ReportRangePreset.THIS_MONTH)
    val rangePreset = _rangePreset.asStateFlow()

    private val _customRange = MutableStateFlow<ReportRange?>(null)

    private val accountAndRange = combine(
        identity.activeAccountId.filterNotNull(),
        _rangePreset,
        _customRange,
    ) { accountId, preset, custom ->
        val range = if (preset == ReportRangePreset.CUSTOM && custom != null) custom
        else ReportRange.from(preset)
        Triple(accountId, preset, range)
    }

    val openDashboard = accountAndRange.flatMapLatest { (accountId, _, range) ->
        flow<OpenDashboardData?> {
            emit(null)
            emit(aggregator.openDashboard(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pmCompliance = accountAndRange.flatMapLatest { (accountId, _, range) ->
        flow<PmComplianceData?> {
            emit(null)
            emit(aggregator.pmCompliance(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val mttr = accountAndRange.flatMapLatest { (accountId, _, range) ->
        flow<MttrData?> {
            emit(null)
            emit(aggregator.mttr(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val completionRate = accountAndRange.flatMapLatest { (accountId, _, range) ->
        flow<CompletionRateData?> {
            emit(null)
            emit(aggregator.completionRate(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val techPerformance = accountAndRange.flatMapLatest { (accountId, _, range) ->
        flow<List<TechPerformanceRow>?> {
            emit(null)
            emit(aggregator.techPerformance(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val effortAccuracy = accountAndRange.flatMapLatest { (accountId, _, range) ->
        flow<List<EffortAccuracyRow>?> {
            emit(null)
            emit(aggregator.effortAccuracy(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val backlogAge = accountAndRange.flatMapLatest { (accountId, _, range) ->
        flow<BacklogAgeData?> {
            emit(null)
            emit(aggregator.backlogAge(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recurringIssues = accountAndRange.flatMapLatest { (accountId, _, range) ->
        flow<List<RecurringIssueRow>?> {
            emit(null)
            emit(aggregator.recurringIssues(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPreset(preset: ReportRangePreset) {
        _rangePreset.value = preset
    }

    fun setCustomRange(range: ReportRange) {
        _customRange.value = range
        _rangePreset.value = ReportRangePreset.CUSTOM
    }
}
