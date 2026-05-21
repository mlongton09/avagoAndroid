package com.avago.core.reports.model

// ─── Work Orders ─────────────────────────────────────────────────────────────

data class OpenDashboardData(
    val totalOpen: Int,
    val overdue: Int,
    val avgAgeDays: Double,
    val byStatus: Map<String, Int>,
)

data class PmComplianceByType(
    val assetType: String,
    val scheduled: Int,
    val completed: Int,
    val compliancePct: Double,
)

data class PmComplianceData(
    val scheduled: Int,
    val completed: Int,
    val compliancePct: Double,
    val byAssetType: List<PmComplianceByType>,
)

data class MttrData(
    val avgHours: Double,
    val byPriority: Map<String, Double>,
    val byAssetType: Map<String, Double>,
)

data class CompletionRatePoint(
    val periodLabel: String, // e.g. "2025-01"
    val created: Int,
    val completed: Int,
)

data class CompletionRateData(
    val points: List<CompletionRatePoint>,
    val overallRate: Double,
)

data class TechPerformanceRow(
    val techName: String,
    val wosCompleted: Int,
    val avgMttrHours: Double,
    val totalCost: Double,
)

data class EffortAccuracyRow(
    val category: String,
    val estimatedHours: Double,
    val actualHours: Double,
    val variancePct: Double,
)

data class BacklogBucket(
    val label: String,
    val count: Int,
)

data class BacklogAgeData(
    val buckets: List<BacklogBucket>, // 0-7d, 7-30d, 30-90d, 90+d
    val totalOpen: Int,
)

data class RecurringIssueRow(
    val assetId: String,
    val assetName: String?,
    val category: String,
    val repeatCount: Int,
)

// ─── Maintenance ─────────────────────────────────────────────────────────────

data class ServiceFrequencyRow(
    val assetId: String,
    val assetName: String?,
    val category: String?,
    val avgDaysBetweenServices: Double,
    val serviceCount: Int,
)

data class MeterReadingPoint(
    val epochMs: Long,
    val value: Double,
)

data class InspectionRateData(
    val totalLogs: Int,
    val inspectionLogs: Int,
    val ratePct: Double,
)

data class ServiceMixRow(
    val category: String,
    val count: Int,
    val totalCost: Double,
    val costPct: Double,
)

// ─── Financial ───────────────────────────────────────────────────────────────

data class PeriodCloseRow(
    val period: String, // "2025-01"
    val logCount: Int,
    val totalCost: Double,
    val laborCost: Double,
    val partsCost: Double,
    val taxCost: Double,
)

data class VendorSummaryRow(
    val vendorName: String,
    val totalCost: Double,
    val logCount: Int,
    val flag1099: Boolean, // cost > $600
)

data class FixedAssetRow(
    val assetId: String,
    val name: String,
    val assetType: String?,
    val purchasePrice: Double,
    val ageYears: Double,
    val straightLineDepreciation: Double,
    val doubleDecliningDepreciation: Double,
    val netBookValue: Double,
)

data class CostByVendorRow(
    val vendorName: String,
    val periodCost: Double,
    val ytdCost: Double,
    val logCount: Int,
)

data class CostByPerformedByRow(
    val performedBy: String,
    val totalCost: Double,
    val logCount: Int,
)

data class MonthlySpendPoint(
    val period: String, // "2025-01"
    val totalSpend: Double,
    val transactionCount: Int,
)

data class InventoryInvestmentRow(
    val category: String,
    val partCount: Int,
    val totalValue: Double,
)

data class RepairVsReplaceRow(
    val assetId: String,
    val name: String,
    val assetType: String?,
    val purchasePrice: Double,
    val lifetimeCost: Double,
    val netBookValue: Double,
    val recommendation: String, // "Repair" | "Replace" | "Review"
)
