package com.avago.core.reports.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

enum class ReportRangePreset {
    THIS_MONTH,
    LAST_MONTH,
    THIS_QUARTER,
    LAST_QUARTER,
    YTD,
    LAST_12_MONTHS,
    CUSTOM,
}

data class ReportRange(val start: Instant, val end: Instant) {
    companion object {
        private val zone = TimeZone.currentSystemDefault()

        fun thisMonth(): ReportRange {
            val now = Clock.System.now().toLocalDateTime(zone)
            val start = LocalDate(now.year, now.monthNumber, 1).atStartOfDayIn(zone)
            val end = Clock.System.now()
            return ReportRange(start, end)
        }

        fun lastMonth(): ReportRange {
            val now = Clock.System.now().toLocalDateTime(zone)
            val (year, month) = if (now.monthNumber == 1) {
                Pair(now.year - 1, 12)
            } else {
                Pair(now.year, now.monthNumber - 1)
            }
            val start = LocalDate(year, month, 1).atStartOfDayIn(zone)
            val endLocal = LocalDate(now.year, now.monthNumber, 1).atStartOfDayIn(zone)
            val end = Instant.fromEpochMilliseconds(endLocal.toEpochMilliseconds() - 1)
            return ReportRange(start, end)
        }

        fun thisQuarter(): ReportRange {
            val now = Clock.System.now().toLocalDateTime(zone)
            val quarterStartMonth = ((now.monthNumber - 1) / 3) * 3 + 1
            val start = LocalDate(now.year, quarterStartMonth, 1).atStartOfDayIn(zone)
            return ReportRange(start, Clock.System.now())
        }

        fun lastQuarter(): ReportRange {
            val now = Clock.System.now().toLocalDateTime(zone)
            val currentQStartMonth = ((now.monthNumber - 1) / 3) * 3 + 1
            val (prevQYear, prevQStartMonth) = if (currentQStartMonth == 1) {
                Pair(now.year - 1, 10)
            } else {
                Pair(now.year, currentQStartMonth - 3)
            }
            val start = LocalDate(prevQYear, prevQStartMonth, 1).atStartOfDayIn(zone)
            val end = Instant.fromEpochMilliseconds(
                LocalDate(now.year, currentQStartMonth, 1).atStartOfDayIn(zone).toEpochMilliseconds() - 1
            )
            return ReportRange(start, end)
        }

        fun ytd(): ReportRange {
            val now = Clock.System.now().toLocalDateTime(zone)
            val start = LocalDate(now.year, 1, 1).atStartOfDayIn(zone)
            return ReportRange(start, Clock.System.now())
        }

        fun last12Months(): ReportRange {
            val end = Clock.System.now()
            val start = Instant.fromEpochMilliseconds(end.toEpochMilliseconds() - 365L * 24 * 60 * 60 * 1000)
            return ReportRange(start, end)
        }

        fun custom(start: Instant, end: Instant): ReportRange = ReportRange(start, end)

        fun from(preset: ReportRangePreset): ReportRange = when (preset) {
            ReportRangePreset.THIS_MONTH -> thisMonth()
            ReportRangePreset.LAST_MONTH -> lastMonth()
            ReportRangePreset.THIS_QUARTER -> thisQuarter()
            ReportRangePreset.LAST_QUARTER -> lastQuarter()
            ReportRangePreset.YTD -> ytd()
            ReportRangePreset.LAST_12_MONTHS -> last12Months()
            ReportRangePreset.CUSTOM -> last12Months() // fallback; callers pass explicit range for CUSTOM
        }
    }
}
