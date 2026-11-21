package com.avago.feature.rentals.ops.domain

import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet

/**
 * Dispatcher home tiles (out / due today / overdue). Computed from the same
 * [LocalFleet.compute] results that P1's home-screen widget (`RentalFleetWidget`) uses, so
 * the in-app numbers and the widget never disagree. P2 does not ship its own widget.
 *
 * "Out" = everything with a customer: on rent + overdue. "Due today" is P1's
 * `dueBackToday` (on-rent units due before the end of the location's day, not yet overdue).
 */
data class OpsCounts(val out: Int, val dueToday: Int, val overdue: Int) {
    companion object {
        val EMPTY = OpsCounts(0, 0, 0)

        fun of(results: Collection<LocalFleet.Result>): OpsCounts {
            val (byStatus, dueToday) = LocalFleet.counts(results)
            val overdue = byStatus[FleetStatus.OVERDUE] ?: 0
            return OpsCounts(out = (byStatus[FleetStatus.ON_RENT] ?: 0) + overdue, dueToday = dueToday, overdue = overdue)
        }
    }
}
