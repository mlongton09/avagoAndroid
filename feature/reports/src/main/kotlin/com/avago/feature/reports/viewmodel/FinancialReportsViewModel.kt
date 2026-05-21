package com.avago.feature.reports.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.reports.ReportAggregator
import com.avago.core.reports.model.CostByPerformedByRow
import com.avago.core.reports.model.CostByVendorRow
import com.avago.core.reports.model.FixedAssetRow
import com.avago.core.reports.model.InventoryInvestmentRow
import com.avago.core.reports.model.MonthlySpendPoint
import com.avago.core.reports.model.PeriodCloseRow
import com.avago.core.reports.model.ReportRange
import com.avago.core.reports.model.ReportRangePreset
import com.avago.core.reports.model.RepairVsReplaceRow
import com.avago.core.reports.model.VendorSummaryRow
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
class FinancialReportsViewModel @Inject constructor(
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
        Pair(accountId, range)
    }

    val itemizedCost = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<LogCostLineEntity>?> {
            emit(null)
            emit(aggregator.itemizedCost(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactionJournal = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<LogEntity>?> {
            emit(null)
            emit(aggregator.transactionJournal(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val periodClose = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<PeriodCloseRow>?> {
            emit(null)
            emit(aggregator.periodClose(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val vendorSummary1099 = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<VendorSummaryRow>?> {
            emit(null)
            emit(aggregator.vendorSummary1099(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val fixedAssetRegister = identity.activeAccountId.filterNotNull().flatMapLatest { accountId ->
        flow<List<FixedAssetRow>?> {
            emit(null)
            emit(aggregator.fixedAssetRegister(accountId))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val costByVendor = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<CostByVendorRow>?> {
            emit(null)
            emit(aggregator.costByVendor(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val costByPerformedBy = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<CostByPerformedByRow>?> {
            emit(null)
            emit(aggregator.costByPerformedBy(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partsSpendTrend = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<MonthlySpendPoint>?> {
            emit(null)
            emit(aggregator.partsSpendTrend(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val inventoryInvestment = identity.activeAccountId.filterNotNull().flatMapLatest { accountId ->
        flow<List<InventoryInvestmentRow>?> {
            emit(null)
            emit(aggregator.inventoryInvestment(accountId))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val repairVsReplace = identity.activeAccountId.filterNotNull().flatMapLatest { accountId ->
        flow<List<RepairVsReplaceRow>?> {
            emit(null)
            emit(aggregator.repairVsReplace(accountId))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPreset(preset: ReportRangePreset) { _rangePreset.value = preset }
    fun setCustomRange(range: ReportRange) {
        _customRange.value = range
        _rangePreset.value = ReportRangePreset.CUSTOM
    }
}
