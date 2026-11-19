package com.avago.core.data.rental

import java.time.Duration
import java.time.Instant

/**
 * The stored facts behind a unit's readiness (server fleet rule, fleet.rs), kept apart from the
 * clock: time-bound reasons (an overdue PM, a work order whose planned window covers now) are
 * decided by [toInputs] at the moment the fleet is shown, not when the rows last changed.
 */
data class FleetFacts(
    val units: List<UnitFacts>,
    val allocations: List<LocalFleet.Alloc>,
    val lineEnds: Map<String, Instant?>,
    val orderEnds: Map<String, Instant?>,
    val checklists: Map<String, LocalFleet.ReadinessFlag>,
    val windowMinutes: Long = FleetInput.DEFAULT_RESERVED_WINDOW_MINUTES,
    /** Lowest defect severity rank that keeps a unit Not ready (minor 1, major 2, out of service 3). */
    val minDefectRank: Int = DEFAULT_MIN_DEFECT_RANK,
) {
    data class UnitFacts(
        val assetId: String,
        val name: String,
        val assetType: String?,
        val classId: String?,
        val className: String?,
        val locationId: String?,
        val timeZone: String?,
        /** Asset status `offline` or sold. */
        val offline: Boolean,
        /** Highest severity rank of the unit's open / monitoring defects (0 = none). */
        val defectRank: Int,
        val workOrders: List<WorkWindow>,
        val pm: List<PmDue>,
        /** A readiness checklist of the unit is pending or failed. */
        val checklistOpen: Boolean,
    )

    /** An open work order on the unit: in progress, or planned over [start, end). */
    data class WorkWindow(val inProgress: Boolean, val start: Instant?, val end: Instant?) {
        fun coversNow(now: Instant): Boolean = inProgress ||
            (start != null && !start.isAfter(now) && (end ?: start.plus(Duration.ofDays(1))).isAfter(now))
    }

    /** An open PM occurrence on the unit: due by date and / or already due by its meter. */
    data class PmDue(val dueAt: Instant?, val meterDue: Boolean) {
        fun overdue(now: Instant): Boolean = dueAt != null && dueAt.isBefore(now)
    }

    fun toInputs(now: Instant): LocalFleet.Inputs = LocalFleet.Inputs(
        units = units.map { u -> unitAt(u, now) },
        allocations = allocations,
        lineEnds = lineEnds,
        orderEnds = orderEnds,
        checklists = checklists,
        windowMinutes = windowMinutes,
    )

    private fun unitAt(u: UnitFacts, now: Instant): LocalFleet.FleetUnit {
        val workNow = u.workOrders.any { it.coversNow(now) }
        val due = u.pm.filter { it.overdue(now) || it.meterDue }
        val reasons = buildList {
            if (u.offline) add(LocalFleet.REASON_ASSET_STATUS)
            if (u.defectRank >= minDefectRank) add(LocalFleet.REASON_OPEN_DEFECT)
            if (due.any { it.overdue(now) }) add(LocalFleet.REASON_PM_OVERDUE)
            if (due.any { !it.overdue(now) }) add(LocalFleet.REASON_PM_METER_DUE)
            if (workNow) add(LocalFleet.REASON_OPEN_WORK_ORDER)
            if (u.checklistOpen) add(LocalFleet.READINESS_REASON)
        }
        return LocalFleet.FleetUnit(
            assetId = u.assetId,
            name = u.name,
            assetType = u.assetType,
            classId = u.classId,
            className = u.className,
            locationId = u.locationId,
            timeZone = u.timeZone,
            maintenanceOpen = u.defectRank >= OUT_OF_SERVICE_RANK || workNow,
            readiness = reasons,
        )
    }

    companion object {
        const val OUT_OF_SERVICE_RANK = 3
        const val DEFAULT_MIN_DEFECT_RANK = 2
        val EMPTY = FleetFacts(emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap())

        /** Server severity rank: minor 1, out_of_service 3, anything else (major) 2. */
        fun severityRank(severity: String?): Int = when (severity) {
            "minor" -> 1
            "out_of_service" -> OUT_OF_SERVICE_RANK
            else -> 2
        }
    }
}
