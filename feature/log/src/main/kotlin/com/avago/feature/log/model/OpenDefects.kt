package com.avago.feature.log.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Open-defect panel helpers (web `OpenDefectsPanel.tsx`); rows come from the v2 defect register. */
object OpenDefects {

    /** Rows shown before "Show all (n)". */
    const val COLLAPSED_COUNT = 4

    private const val DAY_MS = 86_400_000L

    /** Web `severityColor`: out of service → red, NR bucket or major → orange, else amber. */
    enum class Tone { RED, ORANGE, AMBER }

    fun tone(d: InspectionV2.OpenDefect): Tone = when {
        d.severity == InspectionV2.SEV_OUT_OF_SERVICE -> Tone.RED
        d.bucket == InspectionV2.Bucket.NR || d.severity == InspectionV2.SEV_MAJOR -> Tone.ORANGE
        else -> Tone.AMBER
    }

    /**
     * Web `defectAgeDays`: whole days since [reportedAt] (ISO instant or `yyyy-MM-dd`, read as UTC
     * midnight like `Date.parse`), never negative; 0 when unparseable.
     */
    fun ageDays(reportedAt: String?, nowMs: Long): Int {
        val t = parseMillis(reportedAt) ?: return 0
        return ((nowMs - t) / DAY_MS).coerceAtLeast(0L).toInt()
    }

    internal fun parseMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(iso.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    }

    /** Rows visible in the panel: all when expanded, else the first [COLLAPSED_COUNT]. */
    fun <T> visible(list: List<T>, expanded: Boolean): List<T> =
        if (expanded) list else list.take(COLLAPSED_COUNT)
}
