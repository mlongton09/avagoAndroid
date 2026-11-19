package com.avago.feature.rentals.domain

import com.avago.core.data.rental.LocalFleet
import java.time.Instant

/**
 * Agenda derived on the device from the local store (spec §11.1 / §15) — the same event shapes as
 * `GET /rentals/schedule`: pickups (reserved / hold starts), returns (on-rent due times,
 * flagged overdue when past), maintenance blocks. Used offline and as the default source of
 * the schedule screen.
 */
object LocalSchedule {

    /** What the agenda shows of an order. */
    data class OrderRef(val number: String?, val customerName: String?)

    fun events(inputs: LocalFleet.Inputs, orders: Map<String, OrderRef>, from: Instant, to: Instant, now: Instant): List<ScheduleEvent> {
        val units = inputs.units.associateBy { it.assetId }
        val out = mutableListOf<ScheduleEvent>()
        for (a in LocalFleet.live(inputs.allocations, now)) {
            val order = a.orderId?.let(orders::get)
            fun ev(type: String, at: Instant, overdue: Boolean, end: Instant?) = ScheduleEvent(
                type = type,
                at = at,
                overdue = overdue,
                allocationId = a.id,
                kind = a.kind,
                assetId = a.assetId,
                assetName = units[a.assetId]?.name,
                orderId = a.orderId,
                orderNumber = order?.number,
                customerName = order?.customerName,
                startAt = a.startAt,
                endAt = end,
            )
            when (a.kind) {
                "reserved", "hold" -> if (!a.startAt.isBefore(from) && a.startAt.isBefore(to)) {
                    out += ev(if (a.kind == "hold") "hold" else "pickup", a.startAt, false, a.endAt)
                }
            }
            when (a.kind) {
                // Server rule: returns of rentals *and* reservations (due = range end for reservations).
                "on_rent", "reserved" -> {
                    val due = (if (a.kind == "on_rent") LocalFleet.dueAt(a, inputs.lineEnds, inputs.orderEnds) else a.endAt) ?: continue
                    val overdue = !due.isAfter(now)
                    // Overdue rentals stay on today's agenda until checked in.
                    if (a.kind == "on_rent" && due.isBefore(from) && !from.isAfter(now) && to.isAfter(now)) {
                        out += ev("return", due, true, due)
                    } else if (!due.isBefore(from) && due.isBefore(to)) {
                        out += ev("return", due, overdue, due)
                    }
                }
                "maintenance" -> if (Availability.overlaps(from, to, a.startAt, a.endAt)) {
                    out += ev("maintenance", maxOf(a.startAt, from), false, a.endAt)
                }
            }
        }
        return out.sortedWith(compareByDescending<ScheduleEvent> { it.overdue }.thenBy { it.at }.thenBy { it.assetName })
    }

    /** Blocking intervals for [Availability] from the active allocations. */
    fun busy(inputs: LocalFleet.Inputs): List<Availability.Busy> = inputs.allocations.map { a ->
        Availability.Busy(
            assetId = a.assetId,
            kind = a.kind,
            start = a.startAt,
            // An on-rent unit is busy until its due time (later if overdue).
            end = if (a.kind == "on_rent") LocalFleet.dueAt(a, inputs.lineEnds, inputs.orderEnds) ?: a.endAt else a.endAt,
            blocking = a.blocking,
            allocationId = a.id,
        )
    }
}
