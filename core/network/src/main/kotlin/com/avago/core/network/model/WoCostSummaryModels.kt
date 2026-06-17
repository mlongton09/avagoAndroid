package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class CostBucket(
    val estimate: Double,
    val actual: Double,
)

@Serializable
data class WoCostSummaryResponse(
    val work_order_id: String,
    val labor: CostBucket,
    val parts: CostBucket,
    val other: CostBucket,
    val grand_total: CostBucket,
)
