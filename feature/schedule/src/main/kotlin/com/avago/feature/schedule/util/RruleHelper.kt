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

    /** The FREQ (null when not one the editor offers) and INTERVAL of [rrule]; a blank rule reads as monthly. */
    data class Rule(val freq: CalendarFreq?, val interval: Int)

    fun parse(rrule: String?): Rule {
        if (rrule.isNullOrBlank()) return Rule(CalendarFreq.MONTHLY, 1)
        val parts = parseRrule(rrule)
        val freq = CalendarFreq.entries.firstOrNull { it.name == parts["FREQ"] }
        return Rule(freq, parts["INTERVAL"]?.toIntOrNull() ?: 1)
    }

    /**
     * [stored] with its FREQ and INTERVAL set to [freq] and [interval]. [stored] is returned unchanged
     * when it already has them. Parts other than FREQ/INTERVAL are kept; BY* parts are dropped when the
     * frequency changes, as they are specific to it.
     */
    fun compose(stored: String?, freq: CalendarFreq, interval: Int): String {
        require(interval > 0) { "interval must be positive" }
        if (stored != null && parse(stored) == Rule(freq, interval)) return stored
        val old = stored?.takeIf { it.isNotBlank() }?.let(::parseRrule).orEmpty()
        val sameFreq = old["FREQ"] == freq.name
        val rest = old.filterKeys { it != "FREQ" && it != "INTERVAL" && (sameFreq || !it.startsWith("BY")) }
        val parts = listOf("FREQ" to freq.name) + (if (interval > 1) listOf("INTERVAL" to interval.toString()) else emptyList()) + rest.toList()
        val prefix = if (stored?.startsWith("RRULE:") == true) "RRULE:" else ""
        return prefix + parts.joinToString(";") { (k, v) -> "$k=$v" }
    }

    private fun parseRrule(rrule: String): Map<String, String> =
        rrule.removePrefix("RRULE:").split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx < 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
}
