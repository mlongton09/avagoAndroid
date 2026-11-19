package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** A preventive-maintenance plan (`pm_plans`) with its triggers. */
data class PmPlan(
    val id: String,
    val title: String,
    val description: String?,
    val assetId: String?,
    val assetName: String?,
    val templateId: String?,
    val active: Boolean,
    val endType: String,
    val cycleNo: Int,
    val lastCompletedAt: Long?,
    val triggers: List<PmTrigger>,
    val version: Long,
) {
    /** Earliest calendar due date over the triggers. */
    val nextDueAt: Long? get() = triggers.mapNotNull { it.nextDueAt }.minOrNull()
}

enum class PmTriggerKind(val wire: String) {
    CALENDAR("calendar"), INTERVAL("interval"), METER("meter");

    companion object {
        fun fromWire(raw: String?): PmTriggerKind = entries.firstOrNull { it.wire == raw } ?: CALENDAR
    }
}

data class PmTrigger(
    val id: String,
    val kind: PmTriggerKind,
    val rrule: String?,
    val intervalValue: Int?,
    val intervalUnit: String?,
    val meterId: String?,
    val meterInterval: BigDecimal?,
    val nextDueAt: Long?,
    val nextDueValue: BigDecimal?,
)

enum class PmOccurrenceState(val wire: String) {
    OPEN("open"), COMPLETED("completed"), SKIPPED("skipped"), CANCELLED("cancelled");

    companion object {
        fun fromWire(raw: String?): PmOccurrenceState = entries.firstOrNull { it.wire == raw } ?: OPEN
    }
}

/** One due cycle of a plan (`pm_occurrences`); "schedules" in the app. */
data class PmOccurrence(
    val id: String,
    val planId: String,
    val planTitle: String,
    val assetId: String?,
    val assetName: String?,
    val cycleNo: Int,
    val state: PmOccurrenceState,
    val dueAt: Long?,
    val dueValue: BigDecimal?,
    val workOrderId: String?,
    val openedAt: Long,
    val closedAt: Long?,
    val version: Long,
) {
    fun isOverdue(now: Long): Boolean = state == PmOccurrenceState.OPEN && dueAt != null && dueAt < now
}

data class PmFilter(
    val assetId: String? = null,
    val states: Set<PmOccurrenceState> = setOf(PmOccurrenceState.OPEN),
    val dueFrom: Long? = null,
    val dueTo: Long? = null,
)

interface PmRepository {
    fun observeOccurrences(filter: PmFilter = PmFilter()): Flow<List<PmOccurrence>>

    fun observeOccurrence(id: String): Flow<PmOccurrence?>

    fun observePlans(assetId: String? = null): Flow<List<PmPlan>>

    fun observePlan(id: String): Flow<PmPlan?>

    suspend fun complete(occurrenceId: String, completedAt: Long? = null, meterValue: BigDecimal? = null)

    suspend fun skip(occurrenceId: String, reason: String)

    suspend fun defer(occurrenceId: String, dueAt: Long)
}
