package com.avago.feature.rentals.domain

import kotlinx.serialization.json.JsonObject

/** Mirrors handovers.rs: only a complete return changes the order to returned. */
object HandoverProgress {
    /** Do not offer another checkout when every rental/accessory line is already out. */
    fun actions(order: OrderView?, lines: List<LineView>): List<String> = order?.actions.orEmpty().filter { action ->
        when (action) {
            "checkout" -> lines.any { it.kind in setOf("rental", "accessory") && it.status in setOf(null, "open") }
            "checkin" -> lines.any { it.kind in setOf("rental", "accessory") && it.status in setOf(null, "out") }
            else -> true
        }
    }

    fun affectsAllocation(allocation: JsonObject, lineId: String): Boolean =
        allocation.str("order_line_id") == lineId

    fun status(current: String, direction: String, linesAfter: List<JsonObject>): String {
        if (direction == "out") return OrderStatus.ON_RENT
        val remaining = linesAfter.any {
            it.str("kind") in setOf("rental", "accessory") &&
                it.str("status") !in setOf("returned", "swapped", "cancelled")
        }
        return if (linesAfter.isNotEmpty() && !remaining) OrderStatus.RETURNED else current
    }

    fun mayHandover(order: OrderView?, direction: String): Boolean =
        order != null && !order.isLegacy &&
            (if (direction == "out") "checkout" else "checkin") in order.actions
}
