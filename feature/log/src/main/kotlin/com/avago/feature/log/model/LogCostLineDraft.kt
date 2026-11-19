package com.avago.feature.log.model

import java.util.UUID

/** A cost line being edited on the log form (`activity_cost_lines`). */
data class LogCostLineDraft(
    val lineId: String = UUID.randomUUID().toString(),
    val kind: String, // "part" | "labor" | "misc"
    /** Catalog product (parts), e.g. from a barcode scan. */
    val productId: String? = null,
    val productName: String? = null, // display only
    /** Technician (labor). */
    val performerUserId: String? = null,
    val description: String = "",
    val quantity: Double = 1.0,
    val unitCost: Double = 0.0,
    val taxAmount: Double? = null,
    val displayOrder: Int = 0,
) {
    val lineTotal: Double get() = quantity * unitCost + (taxAmount ?: 0.0)
}

/** A person selectable as performer / technician. */
data class LogPersonOption(val userId: String, val name: String)
