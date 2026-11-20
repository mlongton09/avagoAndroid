package com.avago.feature.log.model

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.DefectState
import com.avago.core.data.domain.InspectionDefect
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

    /** Id prefix of rows derived from an inspection the register has not recorded yet. */
    const val PENDING_PREFIX = "pending:"

    /**
     * Defects reported by the asset's [latest] inspection that the register does not hold yet: the
     * server derives register rows when the inspection syncs, so until then they are derived from the
     * inspection itself. Once any register row references the inspection it has synced and nothing
     * is derived. Keys the register already tracks as active are not repeated.
     */
    fun pendingFromInspection(register: List<InspectionDefect>, latest: Activity?): List<InspectionV2.OpenDefect> {
        if (latest == null || latest.type != ActivityType.INSPECTION) return emptyList()
        if (register.any { it.sourceActivityId == latest.id || it.lastSeenActivityId == latest.id }) return emptyList()
        val active = register.filter { it.status != DefectState.REPAIRED }.mapTo(HashSet()) { it.itemKey }
        val at = Instant.ofEpochMilli(latest.occurredAt).toString()
        return InspectionV2.parseEnvelope(InspectionV2.parseDataObject(latest.payload)).defects
            .filter { it.key !in active }
            .map { d ->
                InspectionV2.OpenDefect(
                    id = "$PENDING_PREFIX${latest.id}:${d.key}",
                    assetId = latest.assetId,
                    itemId = d.itemId,
                    key = d.key,
                    label = d.label,
                    cornerLabel = d.cornerLabel,
                    sectionTitle = d.sectionTitle,
                    bucket = d.bucket,
                    severity = d.severity,
                    note = d.note,
                    location = d.location,
                    sourceLogId = latest.id,
                    reportedAt = at,
                    lastSeenLogId = latest.id,
                    lastSeenAt = at,
                    status = if (d.bucket == InspectionV2.Bucket.M) "monitoring" else "open",
                )
            }
    }

    fun isPending(d: InspectionV2.OpenDefect): Boolean = d.id.startsWith(PENDING_PREFIX)

    /** Rows visible in the panel: all when expanded, else the first [COLLAPSED_COUNT]. */
    fun <T> visible(list: List<T>, expanded: Boolean): List<T> =
        if (expanded) list else list.take(COLLAPSED_COUNT)
}
