package com.avago.feature.workorders.model

/** RFC 5545 frequency presets offered by the repeats sheet; [CUSTOM] is weekly with a chosen interval. */
enum class RecurrenceFrequency(val freq: String, val fixedInterval: Int?) {
    DAILY("DAILY", 1),
    WEEKLY("WEEKLY", 1),
    BIWEEKLY("WEEKLY", 2),
    MONTHLY("MONTHLY", 1),
    QUARTERLY("MONTHLY", 3),
    SEMIANNUAL("MONTHLY", 6),
    YEARLY("YEARLY", 1),
    CUSTOM("WEEKLY", null),
    ;

    companion object {
        /** The preset matching [rrule]; unmatched intervals fall back to [CUSTOM], blank rules to [MONTHLY]. */
        fun fromRrule(rrule: String?): RecurrenceFrequency {
            val parts = rruleParts(rrule)
            val freq = parts["FREQ"] ?: return MONTHLY
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
            return entries.firstOrNull { it.freq == freq && it.fixedInterval == interval } ?: CUSTOM
        }
    }
}

/** The INTERVAL of [rrule], 1 when absent. */
fun rruleInterval(rrule: String?): Int = rruleParts(rrule)["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

/** `FREQ=..;INTERVAL=..`; ends are carried separately (end type / count / date) by `set_recurrence`. */
fun buildRrule(frequency: RecurrenceFrequency, interval: Int = 1): String =
    "FREQ=${frequency.freq};INTERVAL=${frequency.fixedInterval ?: interval.coerceAtLeast(1)}"

private fun rruleParts(rrule: String?): Map<String, String> =
    rrule.orEmpty().split(";").mapNotNull { part ->
        val kv = part.split("=", limit = 2)
        if (kv.size == 2 && kv[0].isNotBlank()) kv[0].trim().uppercase() to kv[1].trim().uppercase() else null
    }.toMap()
