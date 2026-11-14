package com.avago.feature.reports.ui.charts

/**
 * Chart display state (AvagoWeb docs/chart-style-guide.md § 8). Emptiness is
 * never inferred from all-zero data — zero can be a real value — so callers
 * pass [EMPTY] explicitly when the query succeeded without rows.
 */
enum class ChartState { READY, LOADING, EMPTY }
