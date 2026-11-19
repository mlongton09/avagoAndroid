package com.avago.core.reports.model

// Cost report

enum class CostGroupMode { ALL, BY_ASSET, BY_TYPE, TCO }
enum class CostPeriodMode { YEAR, MONTH }

data class CostPeriodSpec(
    val label: String,
    val startMs: Long,
    val endMs: Long,
)

data class CostCategoryBreakdown(
    val category: String,
    val costs: List<Double>,   // length 3, left-padded zeros for missing
    val colorIndex: Int,
)

data class CostGroupRow(
    val key: String,
    val label: String,
    val periodCosts: List<Double>,  // length 3; in TCO only [2] is meaningful
    val lifetimeCost: Double,
    val categories: List<CostCategoryBreakdown>,
)

data class CostReportData(
    val periods: List<CostPeriodSpec>,
    val rows: List<CostGroupRow>,
    val inventoryValue: Double,
    val inventoryByCategory: List<Pair<String, Double>>,
)
