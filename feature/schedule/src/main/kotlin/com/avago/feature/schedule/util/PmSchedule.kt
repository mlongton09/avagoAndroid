package com.avago.feature.schedule.util

import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmOccurrenceState
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Display status of a PM occurrence ("schedule"). */
enum class PmDueStatus { OVERDUE, DUE_SOON, ON_TRACK, COMPLETED, SKIPPED, CANCELLED }

/** Status filter applied to the schedule list. */
enum class ScheduleStatusFilter { ALL, DUE_SOON, UPCOMING, OVERDUE }

/** Due-basis filter / grouping applied to the schedule list. */
enum class ScheduleTypeFilter { ALL, BY_DATE, BY_METER, BY_ASSET }

object PmSchedule {
    const val DUE_SOON_WINDOW_MS = 7L * 24 * 60 * 60 * 1_000

    fun status(o: PmOccurrence, now: Long): PmDueStatus = when (o.state) {
        PmOccurrenceState.COMPLETED -> PmDueStatus.COMPLETED
        PmOccurrenceState.SKIPPED -> PmDueStatus.SKIPPED
        PmOccurrenceState.CANCELLED -> PmDueStatus.CANCELLED
        PmOccurrenceState.OPEN -> {
            val due = o.dueAt
            when {
                due == null -> PmDueStatus.ON_TRACK
                due < now -> PmDueStatus.OVERDUE
                due <= now + DUE_SOON_WINDOW_MS -> PmDueStatus.DUE_SOON
                else -> PmDueStatus.ON_TRACK
            }
        }
    }

    /** Open occurrences matching the list filters: overdue first, then due soon, then by due date. */
    fun filter(
        all: List<PmOccurrence>,
        status: ScheduleStatusFilter,
        type: ScheduleTypeFilter,
        now: Long,
    ): List<PmOccurrence> = all
        .filter { it.state == PmOccurrenceState.OPEN }
        .filter { o ->
            when (type) {
                ScheduleTypeFilter.BY_DATE -> o.dueAt != null
                ScheduleTypeFilter.BY_METER -> o.dueValue != null
                ScheduleTypeFilter.ALL, ScheduleTypeFilter.BY_ASSET -> true
            }
        }
        .filter { o ->
            val s = status(o, now)
            when (status) {
                ScheduleStatusFilter.ALL -> true
                ScheduleStatusFilter.OVERDUE -> s == PmDueStatus.OVERDUE
                ScheduleStatusFilter.DUE_SOON -> s == PmDueStatus.DUE_SOON
                ScheduleStatusFilter.UPCOMING -> s == PmDueStatus.ON_TRACK
            }
        }
        .sortedWith(
            compareBy<PmOccurrence> { status(it, now).ordinal }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.planTitle },
        )

    /** Other cycles of the same plan, newest first. */
    fun history(all: List<PmOccurrence>, current: PmOccurrence): List<PmOccurrence> =
        all.filter { it.planId == current.planId && it.id != current.id }.sortedByDescending { it.cycleNo }

    fun localDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun formatValue(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    /** Meter reading typed by the user (comma or dot decimal); null when blank or not a number. */
    fun parseValue(text: String): BigDecimal? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
}
