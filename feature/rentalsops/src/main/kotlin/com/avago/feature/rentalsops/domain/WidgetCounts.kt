package com.avago.feature.rentalsops.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Home-screen widget numbers, computed from the locally cached `rental_fleet` feed so the
 * widget works offline. Status precedence follows rentals-api §10.1: the cached
 * `rental_status` can be stale (an on-rent unit becomes overdue when its due time passes
 * without a sync), so the due time is re-evaluated against [now].
 */
object WidgetCounts {

    data class FleetRow(
        val rentalStatus: String?,
        val dueAt: Instant?,
        val dueBackToday: Boolean = false,
        val timeZone: String? = null,
        val deleted: Boolean = false,
    )

    data class Counts(val out: Int, val dueToday: Int, val overdue: Int) {
        companion object { val EMPTY = Counts(0, 0, 0) }
    }

    fun compute(rows: List<FleetRow>, now: Instant, fallbackZone: ZoneId = ZoneId.systemDefault()): Counts {
        var out = 0
        var dueToday = 0
        var overdue = 0
        for (r in rows) {
            if (r.deleted) continue
            val status = r.rentalStatus ?: continue
            if (status != "on_rent" && status != "overdue") continue
            out++
            val isOverdue = status == "overdue" || (r.dueAt != null && r.dueAt < now)
            if (isOverdue) {
                overdue++
                continue
            }
            val zone = r.timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallbackZone
            val today = now.atZone(zone).toLocalDate()
            val due = r.dueAt
            if (due != null) {
                if (due.atZone(zone).toLocalDate() == today) dueToday++
            } else if (r.dueBackToday) {
                dueToday++
            }
        }
        return Counts(out, dueToday, overdue)
    }
}
