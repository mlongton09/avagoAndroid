package com.avago.core.data.rental

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Free units of a rental class over a window, from the synced units and allocations — the same
 * count the server's `class_free_sql` gives (rentals_api/common.rs): the class's rentable units
 * at the branch (or without one) minus the peak, over the window, of busy units plus class-level
 * holds of live orders.
 */
object ClassAvailability {

    /** Order statuses whose class-level holds take stock. */
    val LIVE_ORDER_STATUSES = setOf("held", "reserved", "on_rent", "off_rent_requested")

    /** A rentable unit of a class. */
    data class Unit(val assetId: String, val classId: String, val locationId: String?)

    /** An active, blocking unit allocation. */
    data class UnitBusy(val assetId: String, val start: Instant, val end: Instant?)

    /** An active class-level allocation (`resource_kind = 'class'`) with its order's status. */
    data class ClassHold(
        val classId: String,
        val locationId: String?,
        val qty: BigDecimal,
        val start: Instant,
        val end: Instant?,
        val expiresAt: Instant?,
        val orderStatus: String?,
    )

    data class Facts(val units: List<Unit>, val busy: List<UnitBusy>, val holds: List<ClassHold>) {
        companion object {
            val EMPTY = Facts(emptyList(), emptyList(), emptyList())
        }
    }

    /** Free units of [classId] at [locationId] (any branch when null) over [start, end); never negative. */
    fun freeUnits(facts: Facts, classId: String, locationId: String?, start: Instant, end: Instant?, now: Instant): Int {
        val units = facts.units
            .filter { it.classId == classId && (locationId == null || it.locationId == null || it.locationId == locationId) }
            .map { it.assetId }
            .toSet()
        val busy = facts.busy.filter { it.assetId in units && overlaps(it.start, it.end, start, end) }
        val holds = facts.holds.filter { h ->
            h.classId == classId &&
                overlaps(h.start, h.end, start, end) &&
                (locationId == null || h.locationId == null || h.locationId == locationId) &&
                h.orderStatus in LIVE_ORDER_STATUSES &&
                (h.expiresAt == null || h.expiresAt > now)
        }
        val points = (listOf(start) + busy.map { it.start }.filter { it > start } + holds.map { it.start }.filter { it > start })
            .filter { end == null || it < end }
            .toSet()
        val peak = points.maxOfOrNull { p ->
            val taken = busy.filter { contains(it.start, it.end, p) }.map { it.assetId }.toSet().size
            val held = holds.filter { contains(it.start, it.end, p) }.fold(BigDecimal.ZERO) { acc, h -> acc + h.qty }
            BigDecimal(taken) + held
        } ?: BigDecimal.ZERO
        val free = BigDecimal(units.size) - peak
        return free.setScale(0, RoundingMode.HALF_UP).toInt().coerceAtLeast(0)
    }

    /** Half-open ranges `[s1, e1) && [s2, e2)`, a null end being unbounded. */
    private fun overlaps(s1: Instant, e1: Instant?, s2: Instant, e2: Instant?): Boolean =
        (e2 == null || s1 < e2) && (e1 == null || s2 < e1)

    private fun contains(s: Instant, e: Instant?, p: Instant): Boolean = s <= p && (e == null || p < e)
}
