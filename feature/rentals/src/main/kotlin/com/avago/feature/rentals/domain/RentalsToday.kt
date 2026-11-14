package com.avago.feature.rentals.domain

import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet

/**
 * Today's agenda split into the counter's task lists (rentals home): overdue returns first,
 * then units going out (pickups / holds starting today), returns still due today, and
 * maintenance blocks. Input is [LocalSchedule.events] for the local day.
 */
data class TodayAgenda(
    val overdue: List<ScheduleEvent> = emptyList(),
    val goingOut: List<ScheduleEvent> = emptyList(),
    val dueBack: List<ScheduleEvent> = emptyList(),
    val maintenance: List<ScheduleEvent> = emptyList(),
) {
    companion object {
        fun of(events: List<ScheduleEvent>): TodayAgenda {
            val returns = events.filter { it.type == "return" }
            return TodayAgenda(
                overdue = returns.filter { it.overdue },
                goingOut = events.filter { it.type == "pickup" || it.type == "hold" },
                dueBack = returns.filterNot { it.overdue },
                maintenance = events.filter { it.type == "maintenance" },
            )
        }
    }
}

/**
 * The handover a fleet unit is waiting for, so scan / fleet results can offer the next step
 * directly: check-in of the rental it is out on, else check-out of the booking that is due to
 * start (status reserved). Null when the unit has no booking to hand over.
 */
data class HandoverTarget(val orderId: String, val direction: String) {
    companion object {
        const val OUT = "out"
        const val IN = "in"

        fun of(r: LocalFleet.Result): HandoverTarget? {
            val current = r.current
            val currentOrder = current?.orderId
            if (current != null && currentOrder != null) {
                when (current.kind) {
                    "on_rent" -> return HandoverTarget(currentOrder, IN)
                    "reserved" -> return HandoverTarget(currentOrder, OUT)
                }
            }
            val next = r.next ?: return null
            val nextOrder = next.orderId ?: return null
            return if (r.status == FleetStatus.RESERVED && next.kind == "reserved") HandoverTarget(nextOrder, OUT) else null
        }
    }
}
