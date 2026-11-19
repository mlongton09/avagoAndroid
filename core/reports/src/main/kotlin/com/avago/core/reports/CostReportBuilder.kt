package com.avago.core.reports

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.ProductStock
import com.avago.core.reports.model.CostCategoryBreakdown
import com.avago.core.reports.model.CostGroupMode
import com.avago.core.reports.model.CostGroupRow
import com.avago.core.reports.model.CostPeriodSpec
import com.avago.core.reports.model.CostReportData

private const val UNCATEGORIZED = "Uncategorized"
private const val UNKNOWN_TYPE = "Unknown"
private const val PALETTE_SIZE = 8

/** Pure aggregation of v2 activities, assets and stock into the cost report. */
object CostReportBuilder {

    fun build(
        activities: List<Activity>,
        assets: List<Asset>,
        stock: List<ProductStock>,
        periods: List<CostPeriodSpec>,
        mode: CostGroupMode,
    ): CostReportData {
        val assetMap = assets.associateBy { it.id }
        val colors = mutableMapOf<String, Int>()
        fun colorFor(category: String) = colors.getOrPut(category) { colors.size % PALETTE_SIZE }

        fun Activity.cost() = totalCost.toDouble()
        fun Activity.category() = categoryLabel?.takeIf { it.isNotBlank() } ?: UNCATEGORIZED
        fun Activity.assetType() = assetId?.let { assetMap[it]?.assetTypeCode } ?: UNKNOWN_TYPE
        fun inPeriod(a: Activity, p: CostPeriodSpec) = a.occurredAt in p.startMs..p.endMs
        fun periodCost(list: List<Activity>, p: CostPeriodSpec) = list.filter { inPeriod(it, p) }.sumOf { it.cost() }

        fun breakdowns(list: List<Activity>, costsOf: (List<Activity>) -> List<Double>) =
            list.groupBy { it.category() }
                .entries
                .sortedByDescending { (_, items) -> items.sumOf { it.cost() } }
                .map { (category, items) ->
                    CostCategoryBreakdown(category = category, costs = costsOf(items), colorIndex = colorFor(category))
                }

        val periodCosts = { list: List<Activity> -> periods.map { p -> periodCost(list, p) } }
        val window = activities.filter { a -> periods.any { inPeriod(a, it) } }

        fun groupedRows(keyOf: (Activity) -> String, labelOf: (String) -> String): List<CostGroupRow> {
            val lifetime = activities.groupBy(keyOf).mapValues { (_, items) -> items.sumOf { it.cost() } }
            return window.groupBy(keyOf).map { (key, items) ->
                CostGroupRow(
                    key = key,
                    label = labelOf(key),
                    periodCosts = periodCosts(items),
                    lifetimeCost = lifetime[key] ?: 0.0,
                    categories = breakdowns(items, periodCosts),
                )
            }.sortedByDescending { it.periodCosts.lastOrNull() ?: 0.0 }
        }

        val rows = when (mode) {
            CostGroupMode.ALL -> listOf(
                CostGroupRow(
                    key = "total",
                    label = "Total Cost",
                    periodCosts = periodCosts(window),
                    lifetimeCost = activities.sumOf { it.cost() },
                    categories = breakdowns(window, periodCosts),
                ),
            )
            CostGroupMode.BY_ASSET -> groupedRows(
                keyOf = { it.assetId ?: "" },
                labelOf = { key -> assetMap[key]?.name ?: activities.firstOrNull { it.assetId == key }?.assetName ?: key },
            ).filter { it.key.isNotEmpty() }
            CostGroupMode.BY_TYPE -> groupedRows(keyOf = { it.assetType() }, labelOf = { it })
            CostGroupMode.TCO -> {
                val byAsset = activities.filter { it.assetId != null }.groupBy { it.assetId!! }
                assets.map { asset ->
                    val items = byAsset[asset.id].orEmpty()
                    val lifetime = items.sumOf { it.cost() }
                    CostGroupRow(
                        key = asset.id,
                        label = asset.name,
                        periodCosts = listOf(0.0, 0.0, lifetime),
                        lifetimeCost = lifetime,
                        categories = breakdowns(items) { listOf(0.0, 0.0, it.sumOf { a -> a.cost() }) },
                    )
                }.sortedByDescending { it.lifetimeCost }
            }
        }

        val inventoryByCategory = stock
            .groupBy { it.product.categoryName?.takeIf { name -> name.isNotBlank() } ?: UNCATEGORIZED }
            .mapValues { (_, items) ->
                items.sumOf { s -> (s.product.standardUnitCost?.let { s.onHand.multiply(it) }?.toDouble() ?: 0.0) }
            }
            .entries
            .sortedByDescending { it.value }
            .map { it.toPair() }

        return CostReportData(
            periods = periods,
            rows = rows,
            inventoryValue = inventoryByCategory.sumOf { it.second },
            inventoryByCategory = inventoryByCategory,
        )
    }
}
