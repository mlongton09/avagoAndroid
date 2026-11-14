package com.avago.core.data.rental

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Fleet status rule (spec §11.1) — a 1:1 port of the server's
 * `domain::rental_fleet_status::fleet_status` (AvagoSvc). Every client runs the shared
 * fixtures (`rental_status_cases.json`) against its implementation, so the status shown
 * offline matches `rental_status` from `GET /rentals/fleet`.
 *
 * Precedence: in_maintenance → overdue → on_rent (with due_back_today) → reserved
 * (hold/reserved running now or starting within the window) → not_ready → available.
 */
enum class FleetStatus(val wire: String) {
    IN_MAINTENANCE("in_maintenance"),
    OVERDUE("overdue"),
    ON_RENT("on_rent"),
    RESERVED("reserved"),
    NOT_READY("not_ready"),
    AVAILABLE("available"),
    ;

    companion object {
        fun fromWire(value: String?): FleetStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** An ACTIVE allocation of the asset; `endAt = null` means open-ended. */
data class FleetAlloc(
    /** quote | hold | reserved | on_rent | buffer | maintenance */
    val kind: String,
    val startAt: Instant,
    val endAt: Instant?,
    val blocking: Boolean = true,
)

data class FleetInput(
    val now: Instant,
    /** IANA zone of the asset's location (for "due back today"); null = UTC. */
    val timeZone: String? = null,
    val reservedWindowMinutes: Long = DEFAULT_RESERVED_WINDOW_MINUTES,
    /** Open out-of-service defect, blocking work order or asset status — from readiness. */
    val maintenanceOpen: Boolean = false,
    /** Readiness checklist complete. */
    val ready: Boolean = true,
    val allocations: List<FleetAlloc> = emptyList(),
) {
    companion object {
        const val DEFAULT_RESERVED_WINDOW_MINUTES = 1440L
    }
}

data class FleetOutput(
    val status: FleetStatus,
    val dueBackToday: Boolean = false,
    /** When the status next changes on its own (null when only a user action changes it). */
    val nextChangeAt: Instant?,
    /** Index into `allocations` of the allocation that drives the status. */
    val current: Int?,
    /** Index of the next future blocking reservation/hold/rental. */
    val next: Int?,
)

object FleetStatusRule {

    private fun covers(a: FleetAlloc, now: Instant): Boolean =
        !a.startAt.isAfter(now) && (a.endAt == null || a.endAt.isAfter(now))

    private fun zoneOf(tz: String?): ZoneId =
        tz?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC

    private fun localDate(t: Instant, zone: ZoneId): LocalDate = t.atZone(zone).toLocalDate()

    fun compute(inp: FleetInput): FleetOutput {
        val now = inp.now
        val windowSec = inp.reservedWindowMinutes.coerceAtLeast(0L) * 60L
        val allocs = inp.allocations
        fun booking(a: FleetAlloc) = a.blocking && (a.kind == "hold" || a.kind == "reserved")

        val next = allocs.withIndex()
            .filter { (_, a) -> a.blocking && a.startAt.isAfter(now) && a.kind in setOf("hold", "reserved", "on_rent") }
            .minByOrNull { it.value.startAt }?.index

        val upcoming = allocs.asSequence()
            .filter { it.blocking }
            .mapNotNull { a ->
                when (a.kind) {
                    "hold", "reserved" -> a.startAt.minusSeconds(windowSec)
                    "maintenance", "on_rent" -> a.startAt
                    else -> null
                }
            }
            .filter { it.isAfter(now) }
            .minOrNull()

        fun out(status: FleetStatus, current: Int?, change: Instant?, today: Boolean) =
            FleetOutput(status, today, change, current, next)

        // 1. In maintenance.
        val maint = allocs.withIndex().firstOrNull { (_, a) -> a.kind == "maintenance" && covers(a, now) }
        if (inp.maintenanceOpen || maint != null) {
            val until = if (inp.maintenanceOpen) null else maint?.value?.endAt
            return out(FleetStatus.IN_MAINTENANCE, maint?.index, until, false)
        }
        // 2. Overdue / 3. On rent (earliest due among rentals that have started).
        val onRent = allocs.withIndex()
            .filter { (_, a) -> a.kind == "on_rent" && !a.startAt.isAfter(now) }
            .minByOrNull { it.value.endAt ?: Instant.MAX }
        if (onRent != null) {
            val e = onRent.value.endAt
            return when {
                e != null && !e.isAfter(now) -> out(FleetStatus.OVERDUE, onRent.index, null, false)
                e != null -> {
                    val zone = zoneOf(inp.timeZone)
                    out(FleetStatus.ON_RENT, onRent.index, e, localDate(e, zone) == localDate(now, zone))
                }
                else -> out(FleetStatus.ON_RENT, onRent.index, null, false)
            }
        }
        // 4. Reserved: a hold/reservation running now or starting within the window.
        val reserved = allocs.withIndex()
            .filter { (_, a) ->
                booking(a) && !a.startAt.minusSeconds(windowSec).isAfter(now) && (a.endAt == null || a.endAt.isAfter(now))
            }
            .minByOrNull { it.value.startAt }
        if (reserved != null) {
            val a = reserved.value
            val change = if (a.startAt.isAfter(now)) a.startAt else a.endAt
            return out(FleetStatus.RESERVED, reserved.index, change, false)
        }
        // 5. Not ready / 6. Available.
        return out(if (inp.ready) FleetStatus.AVAILABLE else FleetStatus.NOT_READY, null, upcoming, false)
    }
}
