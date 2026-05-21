package com.avago.feature.reports.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.reports.ReportAggregator
import com.avago.core.reports.model.InspectionRateData
import com.avago.core.reports.model.MeterReadingPoint
import com.avago.core.reports.model.ReportRange
import com.avago.core.reports.model.ReportRangePreset
import com.avago.core.reports.model.ServiceFrequencyRow
import com.avago.core.reports.model.ServiceMixRow
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
class MaintenanceReportsViewModel @Inject constructor(
    private val aggregator: ReportAggregator,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _rangePreset = MutableStateFlow(ReportRangePreset.THIS_MONTH)
    val rangePreset = _rangePreset.asStateFlow()

    private val _customRange = MutableStateFlow<ReportRange?>(null)

    /** Asset ID filter for service history / meter trend (null = all assets). */
    private val _selectedAssetId = MutableStateFlow<String?>(null)
    val selectedAssetId = _selectedAssetId.asStateFlow()

    private val accountAndRange = combine(
        identity.activeAccountId.filterNotNull(),
        _rangePreset,
        _customRange,
    ) { accountId, preset, custom ->
        val range = if (preset == ReportRangePreset.CUSTOM && custom != null) custom
        else ReportRange.from(preset)
        Pair(accountId, range)
    }

    val serviceHistory = combine(accountAndRange, _selectedAssetId) { (accountId, range), assetId ->
        Triple(accountId, range, assetId)
    }.flatMapLatest { (accountId, range, assetId) ->
        flow<List<LogEntity>?> {
            emit(null)
            emit(aggregator.serviceHistory(accountId, assetId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val serviceFrequency = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<ServiceFrequencyRow>?> {
            emit(null)
            emit(aggregator.serviceFrequency(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val meterTrend = combine(accountAndRange, _selectedAssetId) { (accountId, range), assetId ->
        Triple(accountId, range, assetId)
    }.flatMapLatest { (accountId, range, assetId) ->
        flow<List<MeterReadingPoint>?> {
            emit(null)
            val id = assetId ?: return@flow
            emit(aggregator.meterTrend(accountId, id, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val inspectionRate = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<InspectionRateData?> {
            emit(null)
            emit(aggregator.inspectionRate(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val serviceMix = accountAndRange.flatMapLatest { (accountId, range) ->
        flow<List<ServiceMixRow>?> {
            emit(null)
            emit(aggregator.serviceMix(accountId, range))
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPreset(preset: ReportRangePreset) { _rangePreset.value = preset }
    fun setCustomRange(range: ReportRange) {
        _customRange.value = range
        _rangePreset.value = ReportRangePreset.CUSTOM
    }
    fun selectAsset(assetId: String?) { _selectedAssetId.value = assetId }
}
