package com.avago.feature.log.model

import java.util.UUID

data class LogCostLineDraft(
    val lineId: String = UUID.randomUUID().toString(),
    val kind: String, // "part" | "labor"
    val inventoryId: String? = null,
    val inventoryName: String? = null, // display only
    val userId: String? = null,
    val userName: String? = null, // display only
    val description: String = "",
    val quantity: Double = 1.0,
    val unitCost: Double = 0.0,
    val taxAmount: Double? = null,
    val glCode: String? = null,
    val displayOrder: Int = 0,
) {
    val lineTotal: Double get() = quantity * unitCost + (taxAmount ?: 0.0)
}
