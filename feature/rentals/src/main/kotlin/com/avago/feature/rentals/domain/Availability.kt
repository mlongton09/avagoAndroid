package com.avago.feature.rentals.domain

import java.time.Instant

/**
 * Offline availability maths over synced allocations (spec §11.1 availability search).
 * The server (`GET /rentals/availability/search`) stays authoritative: results computed
 * here are labelled "not confirmed" in the UI until the server answers.
 *
 * Intervals are half-open `[start, end)`; a null end is open-ended.
 */
object Availability {

    /** Allocation kinds that make a unit unavailable when `blocking`. */
    val BLOCKING_KINDS = setOf("hold", "reserved", "on_rent", "buffer", "maintenance")

    data class Interval(val start: Instant, val end: Instant?)

    data class Busy(
        val assetId: String,
        val kind: String,
        val start: Instant,
        val end: Instant?,
        val blocking: Boolean = true,
        val allocationId: String? = null,
    )

    data class UnitResult(
        val assetId: String,
        val available: Boolean,
        val conflicts: List<Busy>,
        /** Earliest instant ≥ the requested start from which the unit is free for the whole duration. */
        val nextFreeAt: Instant?,
    )

    data class ClassResult(val classId: String?, val totalUnits: Int, val freeUnits: Int, val qty: Int) {
        val enough: Boolean get() = freeUnits >= qty
    }

    fun overlaps(aStart: Instant, aEnd: Instant?, bStart: Instant, bEnd: Instant?): Boolean =
        (bEnd == null || aStart.isBefore(bEnd)) && (aEnd == null || bStart.isBefore(aEnd))

    /**
     * The effective busy interval. An on-rent allocation that is past due still occupies the
     * unit until it is checked in. The server worker (`extend_overdue`) keeps such allocations
     * covering now + 1 h, so the same horizon is applied locally: nobody is offered a unit
     * that hasn't come back.
     */
    fun effective(b: Busy, now: Instant): Busy {
        val trigger = now.plus(java.time.Duration.ofMinutes(15))
        return if (b.kind == "on_rent" && b.end != null && b.end.isBefore(trigger)) b.copy(end = now.plus(OVERDUE_HORIZON)) else b
    }

    /** Mirrors the server's `extend_overdue` (due < now + 15 min → busy until now + 1 h). */
    val OVERDUE_HORIZON: java.time.Duration = java.time.Duration.ofHours(1)

    private fun blocks(b: Busy) = b.blocking && b.kind in BLOCKING_KINDS

    fun unit(assetId: String, busy: List<Busy>, from: Instant, to: Instant, now: Instant): UnitResult {
        require(to.isAfter(from)) { "to must be after from" }
        val mine = busy.filter { it.assetId == assetId && blocks(it) }.map { effective(it, now) }
        val conflicts = mine.filter { overlaps(from, to, it.start, it.end) }
        val next = if (conflicts.isEmpty()) from else nextFreeAt(mine, from, java.time.Duration.between(from, to))
        return UnitResult(assetId, conflicts.isEmpty(), conflicts.sortedBy { it.start }, next)
    }

    /**
     * Earliest start ≥ [from] for a gap of at least [duration] between blocking intervals
     * (null when the unit is blocked open-endedly).
     */
    fun nextFreeAt(busy: List<Busy>, from: Instant, duration: java.time.Duration): Instant? {
        var candidate = from
        val sorted = busy.sortedBy { it.start }
        var changed = true
        while (changed) {
            changed = false
            for (b in sorted) {
                val candEnd = candidate.plus(duration)
                if (overlaps(candidate, candEnd, b.start, b.end)) {
                    candidate = b.end ?: return null
                    changed = true
                }
            }
        }
        return candidate
    }

    /** Free/total units of one class for `qty` units over `[from, to)`. */
    fun forClass(
        classId: String?,
        unitIds: List<String>,
        busy: List<Busy>,
        from: Instant,
        to: Instant,
        qty: Int,
        now: Instant,
    ): Pair<ClassResult, List<UnitResult>> {
        val units = unitIds.distinct().map { unit(it, busy, from, to, now) }
        return ClassResult(classId, units.size, units.count { it.available }, qty.coerceAtLeast(1)) to units
    }
}
