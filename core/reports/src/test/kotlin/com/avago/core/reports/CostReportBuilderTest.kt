package com.avago.core.reports

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetStatusCode
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.ProductStock
import com.avago.core.data.domain.TrackingMode
import com.avago.core.reports.model.CostGroupMode
import com.avago.core.reports.model.CostPeriodSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CostReportBuilderTest {

    private val periods = listOf(
        CostPeriodSpec("P1", 0, 99),
        CostPeriodSpec("P2", 100, 199),
        CostPeriodSpec("P3", 200, 299),
    )

    private val assets = listOf(asset("a1", "Truck", "vehicle"), asset("a2", "Pump", "equipment"))

    private val activities = listOf(
        activity("x1", "a1", at = 50, cost = "10", category = "Fuel"),
        activity("x2", "a1", at = 250, cost = "30", category = "Repair"),
        activity("x3", "a2", at = 150, cost = "5", category = null),
        activity("x4", "a2", at = 1_000, cost = "100", category = "Repair"),
    )

    @Test
    fun `all mode sums window per period and lifetime over everything`() {
        val report = CostReportBuilder.build(activities, assets, emptyList(), periods, CostGroupMode.ALL)
        val row = report.rows.single()
        assertEquals(listOf(10.0, 5.0, 30.0), row.periodCosts)
        assertEquals(145.0, row.lifetimeCost)
        assertEquals(listOf("Repair", "Fuel", "Uncategorized"), row.categories.map { it.category })
    }

    @Test
    fun `by asset groups window activities with asset names`() {
        val report = CostReportBuilder.build(activities, assets, emptyList(), periods, CostGroupMode.BY_ASSET)
        assertEquals(listOf("Truck", "Pump"), report.rows.map { it.label })
        assertEquals(105.0, report.rows.first { it.key == "a2" }.lifetimeCost)
    }

    @Test
    fun `by type uses the asset type code`() {
        val report = CostReportBuilder.build(activities, assets, emptyList(), periods, CostGroupMode.BY_TYPE)
        assertEquals(setOf("vehicle", "equipment"), report.rows.map { it.key }.toSet())
    }

    @Test
    fun `tco lists every asset by lifetime cost`() {
        val report = CostReportBuilder.build(activities, assets, emptyList(), periods, CostGroupMode.TCO)
        assertEquals(listOf("a2", "a1"), report.rows.map { it.key })
        assertEquals(listOf(0.0, 0.0, 105.0), report.rows.first().periodCosts)
    }

    @Test
    fun `inventory value is on hand times standard cost by category`() {
        val stock = listOf(
            stock("p1", "Filters", onHand = "4", cost = "2.5"),
            stock("p2", "Filters", onHand = "1", cost = "10"),
            stock("p3", null, onHand = "3", cost = null),
        )
        val report = CostReportBuilder.build(emptyList(), emptyList(), stock, periods, CostGroupMode.ALL)
        assertEquals(20.0, report.inventoryValue)
        assertEquals(listOf("Filters" to 20.0, "Uncategorized" to 0.0), report.inventoryByCategory)
    }

    private fun asset(id: String, name: String, type: String) = Asset(
        id = id, name = name, assetTypeCode = type, make = null, model = null, modelYear = null,
        serialNumber = null, vin = null, tags = emptyList(), locationId = null, locationName = null,
        parentAssetId = null, status = AssetStatusCode.ONLINE, statusSince = 0, downtimeKind = null,
        conditionScore = null, rentable = false, avatarColor = null, avatarInitial = null,
        attributes = "{}", globalUid = id, version = 1,
    )

    private fun activity(id: String, assetId: String, at: Long, cost: String, category: String?) = Activity(
        id = id, assetId = assetId, assetName = null, workOrderId = null, parentActivityId = null,
        type = ActivityType.SERVICE, categoryLabel = category, title = null, occurredAt = at,
        performedByUserId = null, performedByName = null, providerName = null, currency = null,
        overtimeMultiplier = null, notes = null, payload = "{}", attributes = "{}", meterValue = null,
        totalCost = BigDecimal(cost), createdAt = at, version = 1,
    )

    private fun stock(id: String, category: String?, onHand: String, cost: String?) = ProductStock(
        product = Product(
            id = id, sku = id, name = id, categoryId = category, categoryName = category, uomCode = "ea",
            trackingMode = TrackingMode.NONE, manufacturer = null, modelNumber = null, revision = null,
            originCountryCode = null, gtin = null,
            standardUnitCost = cost?.let(::BigDecimal), costCurrency = null, defaultReorderQty = null,
            abcClass = null, notes = null, archived = false, version = 1,
        ),
        onHand = BigDecimal(onHand),
        policies = emptyList(),
    )
}
