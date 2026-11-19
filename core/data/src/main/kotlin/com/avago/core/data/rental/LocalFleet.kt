package com.avago.core.data.rental

import java.time.Instant

/**
 * Fleet status computed on the device with the server rule (`GET /rentals/fleet`, fleet.rs):
 * units with their readiness reasons, ACTIVE allocations (hold rows past `expires_at` dropped)
 * and the `on_rent` due time = line `end_at` ?: order `end_at` ?: allocation upper bound (the
 * server keeps extending overdue allocations, so the bound is not the due time).
 */
object LocalFleet {

    /** Readiness reason codes (fleet.rs `readiness`). */
    const val REASON_ASSET_STATUS = "asset_status"
    const val REASON_OPEN_DEFECT = "open_defect"
    const val REASON_PM_OVERDUE = "pm_overdue"
    const val REASON_PM_METER_DUE = "pm_meter_due"
    const val REASON_OPEN_WORK_ORDER = "open_work_order"
    const val READINESS_REASON = "readiness_checklist"

    /** Checklist statuses that keep a unit Not ready (server rule). */
    val READINESS_OPEN = setOf("pending", "failed")

    data class FleetUnit(
        val assetId: String,
        val name: String,
        val assetType: String?,
        val classId: String?,
        val className: String?,
        val locationId: String?,
        val timeZone: String?,
        val maintenanceOpen: Boolean,
        /** Reason codes keeping the unit Not ready (empty = ready). */
        val readiness: List<String>,
    ) {
        val ready: Boolean get() = readiness.isEmpty()
    }

    data class Alloc(
        val id: String,
        val assetId: String,
        /** Allocation purpose (`hold`, `reserved`, `on_rent`, `maintenance`, …). */
        val kind: String,
        val blocking: Boolean,
        val startAt: Instant,
        /** Upper bound of `during` (null = open-ended). */
        val endAt: Instant?,
        val orderId: String?,
        val orderLineId: String?,
        val expiresAt: Instant?,
    )

    /** The latest readiness checklist of a unit. */
    data class ReadinessFlag(
        val checkId: String,
        /** pending | passed | failed | waived */
        val status: String,
        /** True while the change made on this device is not confirmed by the server. */
        val pending: Boolean,
        val at: Instant,
    ) {
        val open: Boolean get() = status in READINESS_OPEN
    }

    data class Inputs(
        val units: List<FleetUnit>,
        val allocations: List<Alloc>,
        val lineEnds: Map<String, Instant?> = emptyMap(),
        val orderEnds: Map<String, Instant?> = emptyMap(),
        val checklists: Map<String, ReadinessFlag> = emptyMap(),
        val windowMinutes: Long = FleetInput.DEFAULT_RESERVED_WINDOW_MINUTES,
    ) {
        companion object {
            val EMPTY = Inputs(emptyList(), emptyList())
        }
    }

    data class Result(
        val unit: FleetUnit,
        val status: FleetStatus,
        val dueBackToday: Boolean,
        val nextChangeAt: Instant?,
        val current: Alloc?,
        val next: Alloc?,
        /** Due time of [current] when it is an on-rent allocation. */
        val dueAt: Instant?,
        /** The unit's deciding readiness checklist (null = none). */
        val checklist: ReadinessFlag? = null,
    ) {
        /** A readiness change made on this device is still waiting to sync. */
        val readinessPending: Boolean get() = checklist?.pending == true
    }

    /** `on_rent` due time = line end ?: order end ?: allocation upper bound (server rule). */
    fun dueAt(a: Alloc, lineEnds: Map<String, Instant?>, orderEnds: Map<String, Instant?>): Instant? =
        a.orderLineId?.let { lineEnds[it] } ?: a.orderId?.let { orderEnds[it] } ?: a.endAt

    /** Allocations still in force at [now] (hold rows past `expires_at` no longer count). */
    fun live(allocations: List<Alloc>, now: Instant): List<Alloc> =
        allocations.filter { a -> !(a.kind == "hold" && a.expiresAt != null && a.expiresAt.isBefore(now)) }

    /** Every unit's status, keyed by asset id. */
    fun compute(inputs: Inputs, now: Instant): Map<String, Result> {
        val allocs = live(inputs.allocations, now).groupBy { it.assetId }
        return inputs.units.associate { u ->
            u.assetId to computeUnit(u, allocs[u.assetId].orEmpty(), inputs, now).copy(checklist = inputs.checklists[u.assetId])
        }
    }

    fun computeUnit(u: FleetUnit, unitAllocs: List<Alloc>, inputs: Inputs, now: Instant): Result {
        val sorted = unitAllocs.sortedWith(compareBy<Alloc> { it.startAt }.thenBy { it.id })
        val ends = sorted.map { a -> if (a.kind == "on_rent") dueAt(a, inputs.lineEnds, inputs.orderEnds) else a.endAt }
        val out = FleetStatusRule.compute(
            FleetInput(
                now = now,
                timeZone = u.timeZone,
                reservedWindowMinutes = inputs.windowMinutes,
                maintenanceOpen = u.maintenanceOpen,
                ready = u.ready,
                allocations = sorted.mapIndexed { i, a -> FleetAlloc(a.kind, a.startAt, ends[i], a.blocking) },
            ),
        )
        val current = out.current?.let(sorted::getOrNull)
        return Result(
            unit = u,
            status = out.status,
            dueBackToday = out.dueBackToday,
            nextChangeAt = out.nextChangeAt,
            current = current,
            next = out.next?.let(sorted::getOrNull),
            dueAt = out.current?.takeIf { current?.kind == "on_rent" }?.let { ends[it] },
        )
    }

    /** Counts per status (every status present, zero included) + due-back-today. */
    fun counts(results: Collection<Result>): Pair<Map<FleetStatus, Int>, Int> {
        val m = FleetStatus.entries.associateWith { 0 }.toMutableMap()
        results.forEach { m[it.status] = (m[it.status] ?: 0) + 1 }
        return m to results.count { it.dueBackToday }
    }
}
