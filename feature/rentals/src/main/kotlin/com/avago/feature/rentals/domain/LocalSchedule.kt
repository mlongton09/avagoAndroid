package com.avago.feature.rentals.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.LocalFleet
import java.time.Instant

/**
 * Agenda derived on the device from synced rows (spec §11.1 / §15) — the same event shapes as
 * `GET /rentals/schedule`: pickups (reserved / hold starts), returns (on-rent due times,
 * flagged overdue when past), maintenance blocks. Used offline and as the default source of
 * the schedule screen.
 */
object LocalSchedule {

    fun events(records: List<RentalRecordEntity>, from: Instant, to: Instant, now: Instant): List<ScheduleEvent> {
        val units = records.filter { it.entity == LocalFleet.ENTITY_FLEET }
            .mapNotNull(LocalFleet::unitOf).associateBy { it.assetId }
        val orders = records.filter { it.entity == LocalFleet.ENTITY_ORDER }.associateBy { it.id }
        val lineEnds = records.filter { it.entity == LocalFleet.ENTITY_ORDER_LINE }
            .associate { it.id to it.obj.instant("end_at") }
        val orderEnds = orders.mapValues { it.value.obj.instant("end_at") }
        val serverDue = LocalFleet.serverDueAts(records)
        val allocs = records.filter { it.entity == LocalFleet.ENTITY_ALLOCATION }.mapNotNull(LocalFleet::allocOf)
        val out = mutableListOf<ScheduleEvent>()
        for (a in allocs) {
            val order = a.orderId?.let(orders::get)
            val oj = order?.obj
            fun ev(type: String, at: Instant, overdue: Boolean, end: Instant?) = ScheduleEvent(
                type = type,
                at = at,
                overdue = overdue,
                allocationId = a.id,
                kind = a.kind,
                assetId = a.assetId,
                assetName = units[a.assetId]?.name,
                orderId = a.orderId,
                orderNumber = oj?.str("number"),
                customerName = oj?.obj("customer")?.str("name") ?: oj?.str("customer_name") ?: order?.name,
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
                    val due = (if (a.kind == "on_rent") LocalFleet.dueAt(a, lineEnds, orderEnds, serverDue) else a.endAt) ?: continue
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

    /** Blocking intervals for [Availability] from active allocation rows. */
    fun busy(records: List<RentalRecordEntity>): List<Availability.Busy> {
        val lineEnds = records.filter { it.entity == LocalFleet.ENTITY_ORDER_LINE }
            .associate { it.id to it.obj.instant("end_at") }
        val orderEnds = records.filter { it.entity == LocalFleet.ENTITY_ORDER }
            .associate { it.id to it.obj.instant("end_at") }
        val serverDue = LocalFleet.serverDueAts(records)
        return records.filter { it.entity == LocalFleet.ENTITY_ALLOCATION }
            .mapNotNull(LocalFleet::allocOf)
            .map { a ->
                Availability.Busy(
                    assetId = a.assetId,
                    kind = a.kind,
                    start = a.startAt,
                    // An on-rent unit is busy until its due time (later if overdue).
                    end = if (a.kind == "on_rent") LocalFleet.dueAt(a, lineEnds, orderEnds, serverDue) ?: a.endAt else a.endAt,
                    blocking = a.blocking,
                    allocationId = a.id,
                )
            }
    }
}
