package com.avago.feature.workorders.model

/** RFC 5545 FREQ values supported by the RepeatsSheet. */
enum class RecurrenceFrequency(val rruleKey: String, val displayName: String) {
    DAILY("DAILY", "Daily"),
    WEEKLY("WEEKLY", "Weekly"),
    BIWEEKLY("WEEKLY", "Every 2 Weeks"),
    MONTHLY("MONTHLY", "Monthly"),
    QUARTERLY("MONTHLY", "Every 3 Months"),
    SEMIANNUAL("MONTHLY", "Every 6 Months"),
    YEARLY("YEARLY", "Yearly"),
    CUSTOM("WEEKLY", "Custom");  // Custom uses weekly base with configurable interval

    companion object {
        fun fromRrule(rrule: String?): RecurrenceFrequency {
            if (rrule == null) return MONTHLY
            val freq = rrule.split(";")
                .firstOrNull { it.startsWith("FREQ=") }
                ?.removePrefix("FREQ=")
            val interval = rrule.split(";")
                .firstOrNull { it.startsWith("INTERVAL=") }
                ?.removePrefix("INTERVAL=")
                ?.toIntOrNull() ?: 1
            return when {
                freq == "WEEKLY" && interval == 2 -> BIWEEKLY
                freq == "MONTHLY" && interval == 3 -> QUARTERLY
                freq == "MONTHLY" && interval == 6 -> SEMIANNUAL
                else -> entries.firstOrNull { it.rruleKey == freq && it != CUSTOM && it != BIWEEKLY && it != QUARTERLY && it != SEMIANNUAL } ?: MONTHLY
            }
        }
    }
}

enum class RecurrenceEndType(val key: String, val displayName: String) {
    NEVER("never", "Never"),
    AFTER_COUNT("count", "After"),
    ON_DATE("date", "On Date");

    companion object {
        fun fromKey(key: String?): RecurrenceEndType =
            entries.firstOrNull { it.key == key } ?: NEVER
    }
}

/**
 * Builds an RFC 5545 RRULE string from the given parameters.
 * e.g. "FREQ=MONTHLY;INTERVAL=1"
 */
fun buildRrule(
    frequency: RecurrenceFrequency,
    interval: Int = 1,
    endType: RecurrenceEndType = RecurrenceEndType.NEVER,
    count: Int? = null,
    until: String? = null,
): String = buildString {
    val freq = when (frequency) {
        RecurrenceFrequency.CUSTOM -> "WEEKLY"
        else -> frequency.rruleKey
    }
    append("FREQ=$freq")
    val resolvedInterval = when (frequency) {
        RecurrenceFrequency.BIWEEKLY -> 2
        RecurrenceFrequency.QUARTERLY -> 3
        RecurrenceFrequency.SEMIANNUAL -> 6
        else -> interval
    }
    append(";INTERVAL=$resolvedInterval")
    when (endType) {
        RecurrenceEndType.AFTER_COUNT -> if (count != null) append(";COUNT=$count")
        RecurrenceEndType.ON_DATE -> if (until != null) append(";UNTIL=$until")
        RecurrenceEndType.NEVER -> { /* no terminator */ }
    }
}

/**
 * Returns a human-readable summary of the rrule, e.g. "Monthly, never ends".
 */
fun summariseRrule(rrule: String?): String {
    if (rrule.isNullOrBlank()) return "Does not repeat"
    val parts = rrule.split(";").associate {
        val (k, v) = it.split("=", limit = 2).let { p ->
            if (p.size == 2) p[0] to p[1] else p[0] to ""
        }
        k to v
    }
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    val freqLabel = when (parts["FREQ"]) {
        "DAILY" -> "Daily"
        "WEEKLY" -> if (interval == 2) "Every 2 Weeks" else "Weekly"
        "MONTHLY" -> when (interval) {
            3 -> "Every 3 Months"
            6 -> "Every 6 Months"
            else -> "Monthly"
        }
        "YEARLY" -> "Yearly"
        else -> "Recurring"
    }
    val freqText = if (freqLabel.startsWith("Every ")) freqLabel else if (interval > 1) "Every $interval ${parts["FREQ"]?.lowercase()}s" else freqLabel
    return when {
        parts.containsKey("COUNT") -> "$freqText, ends after ${parts["COUNT"]} occurrences"
        parts.containsKey("UNTIL") -> "$freqText, ends on ${parts["UNTIL"]}"
        else -> "$freqText, never ends"
    }
}
