package com.avago.core.reports

import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.reports.model.BacklogAgeData
import com.avago.core.reports.model.BacklogBucket
import com.avago.core.reports.model.CompletionRateData
import com.avago.core.reports.model.CompletionRatePoint
import com.avago.core.reports.model.CostByPerformedByRow
import com.avago.core.reports.model.CostByVendorRow
import com.avago.core.reports.model.EffortAccuracyRow
import com.avago.core.reports.model.FixedAssetRow
import com.avago.core.reports.model.InspectionRateData
import com.avago.core.reports.model.InventoryInvestmentRow
import com.avago.core.reports.model.MeterReadingPoint
import com.avago.core.reports.model.MonthlySpendPoint
import com.avago.core.reports.model.MttrData
import com.avago.core.reports.model.OpenDashboardData
import com.avago.core.reports.model.PeriodCloseRow
import com.avago.core.reports.model.PmComplianceByType
import com.avago.core.reports.model.PmComplianceData
import com.avago.core.reports.model.RecurringIssueRow
import com.avago.core.reports.model.ReportRange
import com.avago.core.reports.model.RepairVsReplaceRow
import com.avago.core.reports.model.ServiceFrequencyRow
import com.avago.core.reports.model.ServiceMixRow
import com.avago.core.reports.model.TechPerformanceRow
import com.avago.core.reports.model.VendorSummaryRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val MS_PER_HOUR = 3_600_000.0
private const val MS_PER_DAY = 86_400_000.0
private const val THRESHOLD_1099 = 600.0
private const val REPLACE_RATIO = 0.75 // lifetime cost / purchase price above which we suggest Replace

@Singleton
class ReportAggregator @Inject constructor(
    private val dbFactory: DatabaseFactory,
    @Suppress("UnusedPrivateMember")
    private val identity: IdentityManager,
) {

    // ─── Work Orders ─────────────────────────────────────────────────────────

    suspend fun openDashboard(accountId: String, range: ReportRange): OpenDashboardData =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val wos = db.workOrderDao().observeAll(accountId).first()
            val now = Clock.System.now().toEpochMilliseconds()
            val openStatuses = setOf("open", "in_progress", "on_hold", "pending")

            val openWos = wos.filter { it.status in openStatuses }
            val overdue = openWos.count { wo ->
                val due = wo.dueDate ?: return@count false
                due < now
            }
            val avgAgeDays = if (openWos.isEmpty()) 0.0 else {
                openWos.map { (now - it.createdAt) / MS_PER_DAY }.average()
            }
            val byStatus = openWos.groupBy { it.status }.mapValues { it.value.size }

            OpenDashboardData(
                totalOpen = openWos.size,
                overdue = overdue,
                avgAgeDays = avgAgeDays,
                byStatus = byStatus,
            )
        }

    suspend fun pmCompliance(accountId: String, range: ReportRange): PmComplianceData =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val wos = db.workOrderDao().observeAll(accountId).first()
            val assets = db.assetDao().observeAll(accountId).first()
            val assetMap = assets.associateBy { it.assetId }

            // PM = scheduled or preventive WOs created within range
            val rangeWos = wos.filter { it.createdAt in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds() }
            val pmWos = rangeWos.filter { it.woKind == "pm" || it.scheduleId != null }
            val completed = pmWos.count { it.status == "completed" }
            val compliancePct = if (pmWos.isEmpty()) 100.0 else completed.toDouble() / pmWos.size * 100

            val byType = pmWos.groupBy { wo ->
                assetMap[wo.assetId]?.assetType ?: "Unknown"
            }.map { (type, list) ->
                val c = list.count { it.status == "completed" }
                PmComplianceByType(
                    assetType = type,
                    scheduled = list.size,
                    completed = c,
                    compliancePct = if (list.isEmpty()) 100.0 else c.toDouble() / list.size * 100,
                )
            }

            PmComplianceData(
                scheduled = pmWos.size,
                completed = completed,
                compliancePct = compliancePct,
                byAssetType = byType,
            )
        }

    suspend fun mttr(accountId: String, range: ReportRange): MttrData =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val wos = db.workOrderDao().observeAll(accountId).first()
            val assets = db.assetDao().observeAll(accountId).first()
            val assetMap = assets.associateBy { it.assetId }

            val completed = wos.filter {
                it.status == "completed" &&
                    it.completedAt != null &&
                    it.createdAt in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }

            fun resolutionHours(wo: com.avago.core.data.db.entity.WorkOrderEntity): Double {
                val start = wo.startedAt ?: wo.createdAt
                val end = wo.completedAt ?: return 0.0
                return (end - start) / MS_PER_HOUR
            }

            val avgHours = if (completed.isEmpty()) 0.0 else completed.map { resolutionHours(it) }.average()

            val byPriority = completed.groupBy { it.priority ?: "none" }.mapValues { (_, list) ->
                list.map { resolutionHours(it) }.average()
            }
            val byAssetType = completed.groupBy { wo ->
                assetMap[wo.assetId]?.assetType ?: "Unknown"
            }.mapValues { (_, list) ->
                list.map { resolutionHours(it) }.average()
            }

            MttrData(avgHours = avgHours, byPriority = byPriority, byAssetType = byAssetType)
        }

    suspend fun completionRate(accountId: String, range: ReportRange): CompletionRateData =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val wos = db.workOrderDao().observeAll(accountId).first()
            val tz = TimeZone.currentSystemDefault()

            val rangeWos = wos.filter {
                it.createdAt in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }

            fun periodLabel(ms: Long): String {
                val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
                return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}"
            }

            val byPeriod = rangeWos.groupBy { periodLabel(it.createdAt) }
            val points = byPeriod.entries.sortedBy { it.key }.map { (period, list) ->
                CompletionRatePoint(
                    periodLabel = period,
                    created = list.size,
                    completed = list.count { it.status == "completed" },
                )
            }

            val totalCreated = points.sumOf { it.created }
            val totalCompleted = points.sumOf { it.completed }
            val overallRate = if (totalCreated == 0) 0.0 else totalCompleted.toDouble() / totalCreated * 100

            CompletionRateData(points = points, overallRate = overallRate)
        }

    suspend fun techPerformance(accountId: String, range: ReportRange): List<TechPerformanceRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val wos = db.workOrderDao().observeAll(accountId).first()

            val completed = wos.filter {
                it.status == "completed" &&
                    it.completedAt != null &&
                    it.completedAt in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }

            completed.groupBy { it.assignedTo ?: "Unassigned" }.map { (tech, list) ->
                val avgMttr = list.map { wo ->
                    val start = wo.startedAt ?: wo.createdAt
                    val end = wo.completedAt ?: start
                    (end - start) / MS_PER_HOUR
                }.average()
                TechPerformanceRow(
                    techName = tech,
                    wosCompleted = list.size,
                    avgMttrHours = avgMttr,
                    totalCost = list.sumOf { it.totalCost ?: 0.0 },
                )
            }.sortedByDescending { it.wosCompleted }
        }

    suspend fun effortAccuracy(accountId: String, range: ReportRange): List<EffortAccuracyRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val wos = db.workOrderDao().observeAll(accountId).first()

            val rangeWos = wos.filter {
                it.status == "completed" &&
                    it.estimatedEffortMinutes != null &&
                    it.actualEffortMinutes != null &&
                    it.createdAt in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }

            rangeWos.groupBy { it.category ?: "Uncategorized" }.map { (category, list) ->
                val estimatedHrs = list.sumOf { (it.estimatedEffortMinutes ?: 0L) / 60.0 }
                val actualHrs = list.sumOf { (it.actualEffortMinutes ?: 0L) / 60.0 }
                val variancePct = if (estimatedHrs == 0.0) 0.0 else (actualHrs - estimatedHrs) / estimatedHrs * 100
                EffortAccuracyRow(
                    category = category,
                    estimatedHours = estimatedHrs,
                    actualHours = actualHrs,
                    variancePct = variancePct,
                )
            }.sortedBy { it.category }
        }

    suspend fun backlogAge(accountId: String, range: ReportRange): BacklogAgeData =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val wos = db.workOrderDao().observeAll(accountId).first()
            val now = Clock.System.now().toEpochMilliseconds()
            val openStatuses = setOf("open", "in_progress", "on_hold", "pending")

            val openWos = wos.filter { it.status in openStatuses }
            val ageBuckets = mapOf(
                "0–7 days" to 0,
                "7–30 days" to 0,
                "30–90 days" to 0,
                "90+ days" to 0,
            ).toMutableMap()

            openWos.forEach { wo ->
                val ageDays = (now - wo.createdAt) / MS_PER_DAY
                when {
                    ageDays <= 7 -> ageBuckets["0–7 days"] = (ageBuckets["0–7 days"] ?: 0) + 1
                    ageDays <= 30 -> ageBuckets["7–30 days"] = (ageBuckets["7–30 days"] ?: 0) + 1
                    ageDays <= 90 -> ageBuckets["30–90 days"] = (ageBuckets["30–90 days"] ?: 0) + 1
                    else -> ageBuckets["90+ days"] = (ageBuckets["90+ days"] ?: 0) + 1
                }
            }

            BacklogAgeData(
                buckets = ageBuckets.map { BacklogBucket(it.key, it.value) },
                totalOpen = openWos.size,
            )
        }

    suspend fun recurringIssues(accountId: String, range: ReportRange): List<RecurringIssueRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val wos = db.workOrderDao().observeAll(accountId).first()
            val assets = db.assetDao().observeAll(accountId).first()
            val assetMap = assets.associateBy { it.assetId }

            val rangeWos = wos.filter {
                it.assetId != null &&
                    it.createdAt in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }

            data class Key(val assetId: String, val category: String)
            rangeWos.groupBy { Key(it.assetId!!, it.category ?: "Uncategorized") }
                .filter { it.value.size >= 2 }
                .map { (key, list) ->
                    RecurringIssueRow(
                        assetId = key.assetId,
                        assetName = assetMap[key.assetId]?.name,
                        category = key.category,
                        repeatCount = list.size,
                    )
                }
                .sortedByDescending { it.repeatCount }
        }

    // ─── Maintenance ─────────────────────────────────────────────────────────

    suspend fun serviceHistory(
        accountId: String,
        assetId: String?,
        range: ReportRange,
    ): List<LogEntity> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val logs = db.logDao().observeAll(accountId).first()
            logs.filter { log ->
                log.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds() &&
                    (assetId == null || log.assetId == assetId)
            }.sortedByDescending { it.entryDate }
        }

    suspend fun serviceFrequency(accountId: String, range: ReportRange): List<ServiceFrequencyRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val logs = db.logDao().observeAll(accountId).first()
            val assets = db.assetDao().observeAll(accountId).first()
            val assetMap = assets.associateBy { it.assetId }

            val rangeLogs = logs.filter {
                it.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }

            rangeLogs.groupBy { it.assetId }.map { (assetId, assetLogs) ->
                val sorted = assetLogs.sortedBy { it.entryDate }
                val avgDays = if (sorted.size < 2) 0.0 else {
                    val gaps = sorted.zipWithNext { a, b -> (b.entryDate - a.entryDate) / MS_PER_DAY }
                    gaps.average()
                }
                ServiceFrequencyRow(
                    assetId = assetId,
                    assetName = assetMap[assetId]?.name,
                    category = assetMap[assetId]?.assetType,
                    avgDaysBetweenServices = avgDays,
                    serviceCount = assetLogs.size,
                )
            }.sortedBy { it.avgDaysBetweenServices }
        }

    suspend fun meterTrend(
        accountId: String,
        assetId: String,
        range: ReportRange,
    ): List<MeterReadingPoint> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val logs = db.logDao().observeAll(accountId).first()
            logs.filter { log ->
                log.assetId == assetId &&
                    log.odometerValue != null &&
                    log.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }
                .sortedBy { it.entryDate }
                .map { MeterReadingPoint(epochMs = it.entryDate, value = it.odometerValue!!) }
        }

    suspend fun inspectionRate(accountId: String, range: ReportRange): InspectionRateData =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val logs = db.logDao().observeAll(accountId).first()
            val rangeLogs = logs.filter {
                it.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }
            val inspections = rangeLogs.count { log ->
                log.category?.lowercase()?.contains("inspect") == true
            }
            val ratePct = if (rangeLogs.isEmpty()) 0.0 else inspections.toDouble() / rangeLogs.size * 100
            InspectionRateData(
                totalLogs = rangeLogs.size,
                inspectionLogs = inspections,
                ratePct = ratePct,
            )
        }

    suspend fun serviceMix(accountId: String, range: ReportRange): List<ServiceMixRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val logs = db.logDao().observeAll(accountId).first()
            val rangeLogs = logs.filter {
                it.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }
            val totalCostAll = rangeLogs.sumOf { it.cost ?: 0.0 }

            rangeLogs.groupBy { it.category ?: "Uncategorized" }.map { (category, list) ->
                val cost = list.sumOf { it.cost ?: 0.0 }
                ServiceMixRow(
                    category = category,
                    count = list.size,
                    totalCost = cost,
                    costPct = if (totalCostAll == 0.0) 0.0 else cost / totalCostAll * 100,
                )
            }.sortedByDescending { it.totalCost }
        }

    // ─── Financial ───────────────────────────────────────────────────────────

    suspend fun itemizedCost(accountId: String, range: ReportRange): List<LogCostLineEntity> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            db.logCostLineDao().observeAll(accountId).first().filter {
                it.createdAt in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }.sortedByDescending { it.createdAt }
        }

    suspend fun transactionJournal(accountId: String, range: ReportRange): List<LogEntity> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            db.logDao().observeAll(accountId).first().filter {
                it.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }.sortedByDescending { it.entryDate }
        }

    suspend fun periodClose(accountId: String, range: ReportRange): List<PeriodCloseRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val logs = db.logDao().observeAll(accountId).first().filter {
                it.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }
            val tz = TimeZone.currentSystemDefault()

            fun periodOf(ms: Long): String {
                val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
                return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}"
            }

            logs.groupBy { periodOf(it.entryDate) }.entries.sortedBy { it.key }.map { (period, list) ->
                PeriodCloseRow(
                    period = period,
                    logCount = list.size,
                    totalCost = list.sumOf { it.cost ?: 0.0 },
                    laborCost = list.sumOf { it.costLabor ?: 0.0 },
                    partsCost = list.sumOf { it.costItems ?: 0.0 },
                    taxCost = list.sumOf { it.costTax ?: 0.0 },
                )
            }
        }

    suspend fun vendorSummary1099(accountId: String, range: ReportRange): List<VendorSummaryRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val logs = db.logDao().observeAll(accountId).first().filter {
                it.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds() &&
                    !it.performedBy.isNullOrBlank()
            }

            logs.groupBy { it.performedBy!! }.map { (vendor, list) ->
                val total = list.sumOf { it.cost ?: 0.0 }
                VendorSummaryRow(
                    vendorName = vendor,
                    totalCost = total,
                    logCount = list.size,
                    flag1099 = total > THRESHOLD_1099,
                )
            }.sortedByDescending { it.totalCost }
        }

    suspend fun fixedAssetRegister(accountId: String): List<FixedAssetRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val assets = db.assetDao().observeAll(accountId).first()
            val logs = db.logDao().observeAll(accountId).first()
            val now = Clock.System.now().toEpochMilliseconds()

            assets.map { asset ->
                // Parse purchase_price from attributes JSON or default 0.0
                val purchasePrice = parseAttributeDouble(asset.attributes, "purchase_price") ?: 0.0
                val usefulLife = parseAttributeInt(asset.attributes, "useful_life_years") ?: 10
                val salvage = parseAttributeDouble(asset.attributes, "salvage_value") ?: 0.0
                val ageYears = (now - asset.createdAt) / (365.0 * MS_PER_DAY)

                val slDep = DepreciationCalc.straightLine(purchasePrice, salvage, usefulLife, ageYears)
                val ddbDep = DepreciationCalc.doubleDeclining(purchasePrice, usefulLife, ageYears)
                val nbv = DepreciationCalc.netBookValue(purchasePrice, slDep)

                // Lifetime cost from logs
                val lifetimeCost = logs.filter { it.assetId == asset.assetId }.sumOf { it.cost ?: 0.0 }

                FixedAssetRow(
                    assetId = asset.assetId,
                    name = asset.name,
                    assetType = asset.assetType,
                    purchasePrice = purchasePrice,
                    ageYears = ageYears,
                    straightLineDepreciation = slDep,
                    doubleDecliningDepreciation = ddbDep,
                    netBookValue = nbv,
                )
            }.sortedBy { it.name }
        }

    suspend fun costByVendor(accountId: String, range: ReportRange): List<CostByVendorRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val allLogs = db.logDao().observeAll(accountId).first().filter {
                !it.performedBy.isNullOrBlank()
            }
            val yearStart = run {
                val tz = TimeZone.currentSystemDefault()
                val now = Clock.System.now().toLocalDateTime(tz)
                LocalDate(now.year, 1, 1)
                    .atStartOfDayIn(tz)
                    .toEpochMilliseconds()
            }

            val rangeLogs = allLogs.filter {
                it.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }
            val ytdLogs = allLogs.filter { it.entryDate >= yearStart }

            val rangeByVendor = rangeLogs.groupBy { it.performedBy!! }
            val ytdByVendor = ytdLogs.groupBy { it.performedBy!! }

            rangeByVendor.keys.map { vendor ->
                CostByVendorRow(
                    vendorName = vendor,
                    periodCost = rangeByVendor[vendor]?.sumOf { it.cost ?: 0.0 } ?: 0.0,
                    ytdCost = ytdByVendor[vendor]?.sumOf { it.cost ?: 0.0 } ?: 0.0,
                    logCount = rangeByVendor[vendor]?.size ?: 0,
                )
            }.sortedByDescending { it.periodCost }
        }

    suspend fun costByPerformedBy(accountId: String, range: ReportRange): List<CostByPerformedByRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val logs = db.logDao().observeAll(accountId).first().filter {
                it.entryDate in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds()
            }

            logs.groupBy { it.performedBy ?: "Unknown" }.map { (performer, list) ->
                CostByPerformedByRow(
                    performedBy = performer,
                    totalCost = list.sumOf { it.cost ?: 0.0 },
                    logCount = list.size,
                )
            }.sortedByDescending { it.totalCost }
        }

    suspend fun partsSpendTrend(accountId: String, range: ReportRange): List<MonthlySpendPoint> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val txns = db.inventoryTransactionDao().observeAll(accountId).first().filter {
                it.createdAt in range.start.toEpochMilliseconds()..range.end.toEpochMilliseconds() &&
                    it.unitCost != null && it.unitCost!! > 0.0
            }
            val tz = TimeZone.currentSystemDefault()

            fun periodOf(ms: Long): String {
                val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
                return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}"
            }

            txns.groupBy { periodOf(it.createdAt) }.entries.sortedBy { it.key }.map { (period, list) ->
                MonthlySpendPoint(
                    period = period,
                    totalSpend = list.sumOf { (it.unitCost ?: 0.0) * it.quantity },
                    transactionCount = list.size,
                )
            }
        }

    suspend fun inventoryInvestment(accountId: String): List<InventoryInvestmentRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val inventory = db.inventoryDao().observeAll(accountId).first()
            val parts = db.partDao().observeAll(accountId).first()
            val partMap = parts.associateBy { it.partId }

            inventory.groupBy { partMap[it.partId]?.category ?: "Uncategorized" }.map { (category, items) ->
                InventoryInvestmentRow(
                    category = category,
                    partCount = items.size,
                    totalValue = items.sumOf { inv ->
                        val unitCost = partMap[inv.partId]?.cost ?: 0.0
                        inv.quantityOnHand * unitCost
                    },
                )
            }.sortedByDescending { it.totalValue }
        }

    suspend fun repairVsReplace(accountId: String): List<RepairVsReplaceRow> =
        withContext(Dispatchers.IO) {
            val db = dbFactory.get(accountId)
            val assets = db.assetDao().observeAll(accountId).first()
            val logs = db.logDao().observeAll(accountId).first()
            val now = Clock.System.now().toEpochMilliseconds()

            assets.map { asset ->
                val purchasePrice = parseAttributeDouble(asset.attributes, "purchase_price") ?: 0.0
                val usefulLife = parseAttributeInt(asset.attributes, "useful_life_years") ?: 10
                val salvage = parseAttributeDouble(asset.attributes, "salvage_value") ?: 0.0
                val ageYears = (now - asset.createdAt) / (365.0 * MS_PER_DAY)
                val slDep = DepreciationCalc.straightLine(purchasePrice, salvage, usefulLife, ageYears)
                val nbv = DepreciationCalc.netBookValue(purchasePrice, slDep)
                val lifetimeCost = logs.filter { it.assetId == asset.assetId }.sumOf { it.cost ?: 0.0 }

                val recommendation = when {
                    purchasePrice <= 0.0 -> "Review"
                    lifetimeCost / purchasePrice > REPLACE_RATIO -> "Replace"
                    lifetimeCost > nbv -> "Replace"
                    else -> "Repair"
                }

                RepairVsReplaceRow(
                    assetId = asset.assetId,
                    name = asset.name,
                    assetType = asset.assetType,
                    purchasePrice = purchasePrice,
                    lifetimeCost = lifetimeCost,
                    netBookValue = nbv,
                    recommendation = recommendation,
                )
            }.sortedByDescending { it.lifetimeCost }
        }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Minimal JSON attribute extractor that avoids pulling in kotlinx.serialization
     * into the reports layer. Parses {"key":123.0} style attributes strings.
     */
    private fun parseAttributeDouble(attributes: String?, key: String): Double? {
        if (attributes.isNullOrBlank()) return null
        val pattern = Regex(""""$key"\s*:\s*([\d.]+)""")
        return pattern.find(attributes)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun parseAttributeInt(attributes: String?, key: String): Int? {
        return parseAttributeDouble(attributes, key)?.toInt()
    }
}
