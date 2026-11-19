package com.avago.feature.schedule.util

import java.time.LocalDate

/**
 * Minimal RRULE helpers for PM calendar triggers: a human-readable FREQ/INTERVAL summary and the
 * next occurrence (DAILY, WEEKLY, MONTHLY, YEARLY). Other rule parts are passed through unchanged.
 */
object RruleHelper {

    /**
     * Examples: `null` ? "One-time", `FREQ=WEEKLY;INTERVAL=2` ? "Every 2 weeks", `FREQ=YEARLY` ? "Yearly".
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

    /** Next occurrence of [rrule] after [fromDate]; null when the FREQ is missing or unknown. */
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

    private fun parseRrule(rrule: String): Map<String, String> =
        rrule.removePrefix("RRULE:").split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx < 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
}
