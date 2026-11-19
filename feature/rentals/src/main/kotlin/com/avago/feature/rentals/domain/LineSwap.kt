package com.avago.feature.rentals.domain

import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.rental.LocalFleet
import java.time.Duration
import java.time.Instant

/**
 * Line swap (`rental.rental_order_lines.swap`): the checked-out unit of a rental line comes back
 * and a replacement of the same class goes out at the same instant. The repository applies the
 * server's effect optimistically (old line `swapped`, new line out on the replacement).
 */
object LineSwap {
    /** Lines a swap may start from (server: `kind = rental AND status = out`). */
    fun swappable(line: RentalOrderLine): Boolean =
        line.kind == "rental" && line.assetId != null && line.status == "out"

    /**
     * Replacement candidates from local availability: units of the line's class (any class when
     * the line has none and the old unit is unclassified), not the unit on rent, ready, not in
     * maintenance, and free from [at] to the line end (an open-ended line checks one hour).
     * Sorted by name.
     */
    fun candidates(
        fleet: Collection<LocalFleet.Result>,
        busy: List<Availability.Busy>,
        classId: String?,
        oldAssetId: String?,
        at: Instant,
        end: Instant?,
        now: Instant,
    ): List<LocalFleet.FleetUnit> {
        val cls = classId ?: fleet.firstOrNull { it.unit.assetId == oldAssetId }?.unit?.classId
        val to = end?.takeIf { it.isAfter(at) } ?: at.plus(Duration.ofHours(1))
        return fleet.asSequence()
            .map { it.unit to it }
            .filter { (u, _) -> u.assetId != oldAssetId && u.classId == cls }
            .filter { (u, r) -> u.ready && !u.maintenanceOpen && r.status != com.avago.core.data.rental.FleetStatus.IN_MAINTENANCE }
            .filter { (u, _) -> Availability.unit(u.assetId, busy, at, to, now).available }
            .map { it.first }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()
    }
}
