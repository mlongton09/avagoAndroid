package com.avago.feature.rentals.ops.domain

import java.time.Instant

/**
 * Class booking → later unit assignment (§7 P2). A line booked against a rental class
 * (`assign_unit_later` or no `asset_id`) gets a concrete unit before dispatch.
 *
 * Offline the candidate list is computed from the cached fleet/allocation feeds and marked
 * "not confirmed"; when online the server's `/availability/search` result is merged in and
 * the server remains the authority (PATCH line → 409 `allocation_conflict` is surfaced).
 */
object UnitAssignment {

    /** Allocation kinds that block a unit. `quote` never blocks. */
    val BLOCKING_KINDS = setOf("hold", "reserved", "on_rent", "buffer", "maintenance", "transfer")

    data class Line(
        val lineId: String,
        val orderId: String,
        val classId: String,
        val locationId: String?,
        val start: Instant,
        val end: Instant,
        /** The line's own allocation (if any) never conflicts with itself. */
        val ownAllocationIds: Set<String> = emptySet(),
    )

    data class Unit(
        val assetId: String,
        val name: String,
        val classId: String?,
        val locationId: String?,
        val ready: Boolean,
        val inMaintenance: Boolean = false,
        val deleted: Boolean = false,
    )

    data class Allocation(
        val allocationId: String,
        val assetId: String?,
        val kind: String,
        val start: Instant,
        val end: Instant,
        val active: Boolean = true,
        /** Server `blocking` flag (false for quote pencils / needs-review imports). */
        val blocking: Boolean = true,
        val orderLineId: String? = null,
    )

    data class Candidate(
        val unit: Unit,
        val sameLocation: Boolean,
        /** True once the server's availability search agreed (online). */
        val confirmed: Boolean,
        val nextFreeAt: Instant? = null,
    )

    /** Half-open overlap: [a1, a2) ∩ [b1, b2) ≠ ∅. */
    fun overlaps(a1: Instant, a2: Instant, b1: Instant, b2: Instant): Boolean = a1 < b2 && b1 < a2

    fun conflicts(line: Line, unit: Unit, allocations: List<Allocation>): List<Allocation> =
        allocations.filter {
            it.active &&
                it.blocking &&
                it.assetId == unit.assetId &&
                it.kind in BLOCKING_KINDS &&
                it.allocationId !in line.ownAllocationIds &&
                it.orderLineId != line.lineId &&
                overlaps(line.start, line.end, it.start, it.end)
        }

    /**
     * Eligible units for [line], best first: ready, then at the line's branch, then by name.
     * The first element is the suggested unit.
     */
    fun candidates(line: Line, units: List<Unit>, allocations: List<Allocation>): List<Candidate> =
        units.asSequence()
            .filter { !it.deleted && !it.inMaintenance && it.classId == line.classId }
            .filter { conflicts(line, it, allocations).isEmpty() }
            .map { Candidate(it, sameLocation = line.locationId != null && it.locationId == line.locationId, confirmed = false) }
            .sortedWith(ORDER)
            .toList()

    /** Server availability row (`units[]` of `/availability/search`). */
    data class ServerUnit(
        val assetId: String,
        val name: String?,
        val available: Boolean,
        val ready: Boolean?,
        val nextFreeAt: Instant?,
    )

    /**
     * Server wins: units the server marks unavailable are dropped, server-only units are added
     * (local cache may lag), and every remaining candidate is flagged confirmed.
     */
    fun mergeServer(line: Line, local: List<Candidate>, server: List<ServerUnit>, unitsById: Map<String, Unit>): List<Candidate> {
        val byId = server.associateBy { it.assetId }
        val kept = local.mapNotNull { c ->
            val s = byId[c.unit.assetId] ?: return@mapNotNull null
            if (!s.available) null
            else c.copy(confirmed = true, unit = c.unit.copy(ready = s.ready ?: c.unit.ready), nextFreeAt = s.nextFreeAt)
        }
        val known = kept.map { it.unit.assetId }.toSet()
        val added = server.filter { it.available && it.assetId !in known }.map { s ->
            val u = unitsById[s.assetId]
            val unit = u?.copy(ready = s.ready ?: u.ready)
                ?: Unit(s.assetId, s.name ?: s.assetId, line.classId, null, s.ready ?: false)
            Candidate(unit, sameLocation = line.locationId != null && unit.locationId == line.locationId, confirmed = true, nextFreeAt = s.nextFreeAt)
        }
        return (kept + added).sortedWith(ORDER)
    }

    /**
     * A line still needs a unit when it is class-booked (no `asset_id`) and not finished.
     * `assign_unit_later` only documents intent; a class line without a unit always qualifies.
     */
    fun needsAssignment(kind: String?, assetId: String?, classId: String?, lineStatus: String?): Boolean =
        (kind == null || kind == "rental") &&
            assetId.isNullOrBlank() && !classId.isNullOrBlank() && lineStatus !in CLOSED_LINE_STATUSES

    private val CLOSED_LINE_STATUSES = setOf("cancelled", "returned", "swapped")

    private val ORDER = compareByDescending<Candidate> { it.unit.ready }
        .thenByDescending { it.sameLocation }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.unit.name }
}
