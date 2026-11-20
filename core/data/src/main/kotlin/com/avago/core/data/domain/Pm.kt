package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

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
    val endCount: Int?,
    val endOn: LocalDate?,
    val timezone: String?,
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
    val priority: Int = 0,
    val assetId: String? = null,
    val meterCondition: String? = null,
    val thresholdValue: BigDecimal? = null,
    val generateOnce: Boolean = false,
    val version: Long = 0L,
)

/** How a plan's series ends (`pm_plans.end_type`). */
object PmEndType {
    const val NEVER = "never"
    const val COUNT = "count"
    const val DATE = "date"
}

/** Units of an interval trigger (`pm_triggers.interval_unit`). */
enum class PmIntervalUnit(val wire: String) {
    DAY("day"), WEEK("week"), MONTH("month"), YEAR("year");

    companion object {
        fun fromWire(raw: String?): PmIntervalUnit? = entries.firstOrNull { it.wire == raw }
    }
}

/** Threshold conditions of a meter trigger (`pm_triggers.meter_condition`). */
enum class PmMeterCondition(val wire: String) {
    ABOVE("above"), AT_LEAST("at_least"), BELOW("below"), AT_MOST("at_most"), EQUALS("equals"),
    INCREMENTED_BY("incremented_by"), DECREMENTED_BY("decremented_by");

    companion object {
        fun fromWire(raw: String?): PmMeterCondition? = entries.firstOrNull { it.wire == raw }
    }
}

/**
 * A trigger as edited. [id] is null for a new trigger. A calendar trigger needs [rrule]; an interval
 * trigger [intervalValue] and [intervalUnit]; a meter trigger [meterId] and either [meterInterval]
 * ("every N units") or [thresholdValue] with [meterCondition].
 */
data class PmTriggerDraft(
    val id: String? = null,
    val kind: PmTriggerKind,
    val rrule: String? = null,
    val intervalValue: Int? = null,
    val intervalUnit: PmIntervalUnit? = null,
    val meterId: String? = null,
    val meterInterval: BigDecimal? = null,
    val meterCondition: PmMeterCondition? = null,
    val thresholdValue: BigDecimal? = null,
    val generateOnce: Boolean = false,
) {
    companion object {
        fun of(t: PmTrigger) = PmTriggerDraft(
            id = t.id, kind = t.kind, rrule = t.rrule, intervalValue = t.intervalValue,
            intervalUnit = PmIntervalUnit.fromWire(t.intervalUnit), meterId = t.meterId, meterInterval = t.meterInterval,
            meterCondition = PmMeterCondition.fromWire(t.meterCondition), thresholdValue = t.thresholdValue,
            generateOnce = t.generateOnce,
        )
    }
}

/**
 * A PM plan as edited. The asset is fixed once the plan exists; [lastDoneAt] (when the work was last
 * done; time triggers fall due after it) is sent on create and when it changes.
 */
data class PmPlanDraft(
    val title: String,
    val description: String? = null,
    val assetId: String? = null,
    val templateId: String? = null,
    val active: Boolean = true,
    val endType: String = PmEndType.NEVER,
    val endCount: Int? = null,
    val endOn: LocalDate? = null,
    val lastDoneAt: Long? = null,
    val triggers: List<PmTriggerDraft> = emptyList(),
) {
    companion object {
        fun of(p: PmPlan) = PmPlanDraft(
            title = p.title, description = p.description, assetId = p.assetId, templateId = p.templateId,
            active = p.active, endType = p.endType, endCount = p.endCount, endOn = p.endOn,
            lastDoneAt = p.lastCompletedAt, triggers = p.triggers.map(PmTriggerDraft::of),
        )
    }
}

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

    /** Creates a plan with its triggers; returns its id. */
    suspend fun createPlan(draft: PmPlanDraft): String

    /** Applies the plan edits and the trigger additions, changes and removals. */
    suspend fun updatePlan(id: String, draft: PmPlanDraft)

    suspend fun deletePlan(id: String)
}
