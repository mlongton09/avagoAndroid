package com.avago.feature.workorders.model

/** RFC 5545 FREQ values supported by the RepeatsSheet. */
enum class RecurrenceFrequency(val rruleKey: String, val displayName: String) {
    DAILY("DAILY", "Daily"),
    WEEKLY("WEEKLY", "Weekly"),
    MONTHLY("MONTHLY", "Monthly"),
    YEARLY("YEARLY", "Yearly"),
    CUSTOM("WEEKLY", "Custom");  // Custom uses weekly base with configurable interval

    companion object {
        fun fromRrule(rrule: String?): RecurrenceFrequency {
            if (rrule == null) return MONTHLY
            val freq = rrule.split(";")
                .firstOrNull { it.startsWith("FREQ=") }
                ?.removePrefix("FREQ=")
            return entries.firstOrNull { it.rruleKey == freq && it != CUSTOM } ?: MONTHLY
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
    append(";INTERVAL=$interval")
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
    val freqLabel = when (parts["FREQ"]) {
        "DAILY" -> "Daily"
        "WEEKLY" -> "Weekly"
        "MONTHLY" -> "Monthly"
        "YEARLY" -> "Yearly"
        else -> "Recurring"
    }
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    val freqText = if (interval > 1) "Every $interval ${parts["FREQ"]?.lowercase()}s" else freqLabel
    return when {
        parts.containsKey("COUNT") -> "$freqText, ends after ${parts["COUNT"]} occurrences"
        parts.containsKey("UNTIL") -> "$freqText, ends on ${parts["UNTIL"]}"
        else -> "$freqText, never ends"
    }
}
