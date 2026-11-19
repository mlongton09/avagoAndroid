package com.avago.core.reports

import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.reports.model.CostGroupMode
import com.avago.core.reports.model.CostPeriodSpec
import com.avago.core.reports.model.CostReportData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Live cost report over the v2 store (activities, assets, stock). */
@Singleton
class CostReportRepository @Inject constructor(
    private val activities: ActivityRepository,
    private val assets: AssetRepository,
    private val inventory: InventoryRepository,
) {
    fun observe(periods: List<CostPeriodSpec>, mode: CostGroupMode): Flow<CostReportData> =
        combine(
            activities.observeActivities(),
            assets.observeAssets(),
            inventory.observeProductStock(),
        ) { activityList, assetList, stock ->
            CostReportBuilder.build(activityList, assetList, stock, periods, mode)
        }.flowOn(Dispatchers.Default)
}
