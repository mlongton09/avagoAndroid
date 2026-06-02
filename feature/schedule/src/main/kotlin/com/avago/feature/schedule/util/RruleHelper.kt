package com.avago.feature.schedule.util

import com.avago.core.data.db.entity.ScheduleEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Minimal RRULE parser and schedule-status helpers for the PM Schedules feature.
 *
 * Only supports the FREQ/INTERVAL subset needed for preventive-maintenance use cases
 * (DAILY, WEEKLY, MONTHLY, YEARLY). Complex recurrence rules (BYDAY, BYMONTH, COUNT,
 * UNTIL) are not expanded; their presence is preserved in storage and passed through
 * to the Android Calendar intent unchanged.
 */
object RruleHelper {

    // -------------------------------------------------------------------------
    // RRULE → human-readable description
    // -------------------------------------------------------------------------

    /**
     * Returns a human-readable description of [rrule].
     *
     * Examples:
     * - `null`                         → "One-time"
     * - `"FREQ=DAILY"`                 → "Daily"
     * - `"FREQ=WEEKLY;INTERVAL=2"`     → "Every 2 weeks"
     * - `"FREQ=MONTHLY;INTERVAL=3"`    → "Every 3 months"
     * - `"FREQ=YEARLY"`                → "Yearly"
     */
    fun describe(rrule: String?): String {
        if (rrule.isNullOrBlank()) return "One-time"
        val parts = parseRrule(rrule)
        val freq = parts["FREQ"] ?: return rrule
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        return when (freq) {
            "DAILY" -> if (interval == 1) "Daily" else "Every $interval days"
            "WEEKLY" -> if (interval == 1) "Weekly" else "Every $interval weeks"
            "MONTHLY" -> if (interval == 1) "Monthly" else "Every $interval months"
            "YEARLY" -> if (interval == 1) "Yearly" else "Every $interval years"
            else -> rrule
        }
    }

    // -------------------------------------------------------------------------
    // Next occurrence calculation
    // -------------------------------------------------------------------------

    /**
     * Returns the next occurrence of [rrule] starting from [fromDate] (exclusive).
     *
     * Returns `null` if the RRULE cannot be parsed or the FREQ is unrecognised.
     */
    fun nextOccurrence(rrule: String, fromDate: LocalDate): LocalDate? {
        val parts = parseRrule(rrule)
        val freq = parts["FREQ"] ?: return null
        val interval = parts["INTERVAL"]?.toLongOrNull() ?: 1L
        return when (freq) {
            "DAILY" -> fromDate.plusDays(interval)
            "WEEKLY" -> fromDate.plusWeeks(interval)
            "MONTHLY" -> fromDate.plusMonths(interval)
            "YEARLY" -> fromDate.plusYears(interval)
            else -> null
        }
    }

    // -------------------------------------------------------------------------
    // Status helpers (operate on epoch-millisecond nextDueAt field)
    // -------------------------------------------------------------------------

    /** Returns `true` if the schedule's next due date is in the past. */
    fun isOverdue(schedule: ScheduleEntity): Boolean {
        val nextDue = schedule.nextDueAt ?: return false
        return nextDue < System.currentTimeMillis()
    }

    /**
     * Returns `true` if the schedule is due within [windowMs] milliseconds from now
     * (and is not already overdue).
     *
     * Default window: 7 days.
     */
    fun isDueSoon(
        schedule: ScheduleEntity,
        windowMs: Long = 7L * 24 * 60 * 60 * 1_000,
    ): Boolean {
        val nextDue = schedule.nextDueAt ?: return false
        val now = System.currentTimeMillis()
        return nextDue in now..(now + windowMs)
    }

    // -------------------------------------------------------------------------
    // Date formatting helpers
    // -------------------------------------------------------------------------

    /**
     * Converts an epoch-millisecond timestamp to a [LocalDate] in the system default
     * time zone, or returns `null` if [epochMs] is null.
     */
    fun epochMsToLocalDate(epochMs: Long?): LocalDate? {
        if (epochMs == null) return null
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    // -------------------------------------------------------------------------
    // Common presets → RRULE strings (for the Add/Edit form)
    // -------------------------------------------------------------------------

    fun rruleForPreset(preset: ScheduleFrequencyPreset): String = when (preset) {
        ScheduleFrequencyPreset.DAILY -> "FREQ=DAILY;INTERVAL=1"
        ScheduleFrequencyPreset.WEEKLY -> "FREQ=WEEKLY;INTERVAL=1"
        ScheduleFrequencyPreset.BIWEEKLY -> "FREQ=WEEKLY;INTERVAL=2"
        ScheduleFrequencyPreset.MONTHLY -> "FREQ=MONTHLY;INTERVAL=1"
        ScheduleFrequencyPreset.QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3"
        ScheduleFrequencyPreset.BIANNUAL -> "FREQ=MONTHLY;INTERVAL=6"
        ScheduleFrequencyPreset.YEARLY -> "FREQ=YEARLY;INTERVAL=1"
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun parseRrule(rrule: String): Map<String, String> =
        rrule.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx < 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
}

/** Preset frequency options shown in the Add/Edit form. */
enum class ScheduleFrequencyPreset(val displayName: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    BIWEEKLY("Every 2 Weeks"),
    MONTHLY("Monthly"),
    QUARTERLY("Every 3 Months"),
    BIANNUAL("Every 6 Months"),
    YEARLY("Yearly"),
    ;

    companion object {
        fun fromRrule(rrule: String?): ScheduleFrequencyPreset {
            if (rrule.isNullOrBlank()) return MONTHLY
            val parts = rrule.split(";").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx < 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }.toMap()
            val freq = parts["FREQ"]
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
            return when {
                freq == "DAILY" -> DAILY
                freq == "WEEKLY" && interval == 2 -> BIWEEKLY
                freq == "WEEKLY" -> WEEKLY
                freq == "MONTHLY" && interval == 3 -> QUARTERLY
                freq == "MONTHLY" && interval == 6 -> BIANNUAL
                freq == "MONTHLY" -> MONTHLY
                freq == "YEARLY" -> YEARLY
                else -> MONTHLY
            }
        }
    }
}
