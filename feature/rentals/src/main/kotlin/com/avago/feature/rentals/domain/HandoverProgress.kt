package com.avago.feature.rentals.domain

/** Which hand-overs an order offers (mirrors handovers.rs). */
object HandoverProgress {
    /** Do not offer another checkout when every rental/accessory line is already out. */
    fun actions(order: OrderView?, lines: List<LineView>): List<String> = order?.actions.orEmpty().filter { action ->
        when (action) {
            "checkout" -> lines.any { it.kind in setOf("rental", "accessory") && it.status in setOf(null, "open") }
            "checkin" -> lines.any { it.kind in setOf("rental", "accessory") && it.status in setOf(null, "out") }
            else -> true
        }
    }

    fun mayHandover(order: OrderView?, direction: String): Boolean =
        order != null && !order.isLegacy &&
            (if (direction == "out") "checkout" else "checkin") in order.actions
}
