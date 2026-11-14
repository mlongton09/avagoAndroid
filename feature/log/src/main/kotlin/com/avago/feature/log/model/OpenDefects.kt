package com.avago.feature.log.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Asset-level open-defect register helpers (web `lib/api/defects.ts` + `OpenDefectsPanel.tsx`):
 * the register snapshot cached in Room by the sync engine when the `/defects` endpoint is deployed,
 * otherwise derived by replaying the asset's inspection history ([InspectionV2.deriveOpenDefects]).
 */
object OpenDefects {

    /** Rows shown before "Show all (n)". */
    const val COLLAPSED_COUNT = 4

    private const val DAY_MS = 86_400_000L

    /** Statuses the web panel requests (`status=open,monitoring,deferred`). */
    val ACTIVE_STATUSES = setOf("open", "monitoring", "deferred")

    enum class Source { SERVER, DERIVED }

    data class Result(val source: Source, val defects: List<InspectionV2.OpenDefect>)

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

    /** Server register rows → active defects, most severe first. */
    fun fromServerRows(rows: JsonArray): List<InspectionV2.OpenDefect> =
        InspectionV2.sortOpen(
            rows.mapNotNull { it as? JsonObject }
                .map { InspectionV2.fromServerDefect(it) }
                .filter { it.status in ACTIVE_STATUSES },
        )

    /**
     * Web `deriveDefectsByAsset` for one asset: only that asset's inspection entries, ids
     * `${assetId}:${key}` (stable across the inspections that re-report the same slot).
     */
    fun deriveForAsset(entries: List<InspectionV2.HistoryEntry>, assetId: String): List<InspectionV2.OpenDefect> =
        InspectionV2.deriveOpenDefects(
            entries.filter { it.assetId == assetId && it.category?.contains("inspect", ignoreCase = true) == true },
        ).map { it.copy(assetId = it.assetId ?: assetId, id = "$assetId:${it.key}") }

    /** Rows visible in the panel: all when expanded, else the first [COLLAPSED_COUNT]. */
    fun <T> visible(list: List<T>, expanded: Boolean): List<T> =
        if (expanded) list else list.take(COLLAPSED_COUNT)

    /**
     * Log entry a row opens: the most recent inspection that still saw it, else the one that first
     * reported it — only when that entry exists locally (web looks it up in the loaded history).
     */
    fun openTarget(d: InspectionV2.OpenDefect, localLogIds: Set<String>): String? =
        d.lastSeenLogId?.takeIf { it in localLogIds } ?: d.sourceLogId?.takeIf { it in localLogIds }

    /**
     * Local lifecycle change on the rows matching [matches] (repaired rows leave the register; a
     * reopened Monitor row reads `monitoring`, as the server maps it).
     */
    fun applyStatus(
        list: List<InspectionV2.OpenDefect>,
        status: String,
        matches: (InspectionV2.OpenDefect) -> Boolean,
    ): List<InspectionV2.OpenDefect> =
        if (status == InspectionV2.RESOLUTION_REPAIRED) {
            list.filterNot(matches)
        } else {
            list.map { d ->
                if (!matches(d)) d
                else d.copy(status = if (status == "open" && d.bucket == InspectionV2.Bucket.M) "monitoring" else status)
            }
        }

    // ------------------------------------------------------------------
    // Offline-first local view (Room register cache + sync queue overlay)
    // ------------------------------------------------------------------

    /** Whether the service's register exists, as last recorded by `DefectRegisterSync.pull`. */
    enum class Register { UNKNOWN, SUPPORTED, UNSUPPORTED }

    /** `sync_metadata.last_server_seq` of the register row → [Register] (1 / -1, else unknown). */
    fun registerOf(lastServerSeq: Long?): Register = when (lastServerSeq) {
        1L -> Register.SUPPORTED
        -1L -> Register.UNSUPPORTED
        else -> Register.UNKNOWN
    }

    /** One queued, not yet synced lifecycle change (`sync_queue` row of type `inspection_defect`). */
    data class PendingChange(val defectId: String, val itemKey: String, val status: String, val derived: Boolean)

    data class LocalView(
        val source: Source,
        val defects: List<InspectionV2.OpenDefect>,
        /** Rows with a local change (or a local inspection) that has not synced yet. */
        val pendingIds: Set<String>,
        /** Rows known only from local inspection history (changes queue as derived ops). */
        val derivedIds: Set<String>,
    )

    /** Cached register row (`row_json`) with its local [status] (may hold an unsynced change). */
    fun fromCachedRow(rowJson: String, status: String): InspectionV2.OpenDefect? =
        InspectionV2.parseDataObject(rowJson)?.let { InspectionV2.fromServerDefect(it).copy(status = status) }

    /**
     * What the panel shows, entirely from local data. With a register snapshot ([Register.SUPPORTED])
     * the cached rows are authoritative, plus defects reported by inspections that have not synced
     * yet; otherwise the defects derived from local history. Queued changes are overlaid last.
     */
    fun localView(
        register: Register,
        cached: List<InspectionV2.OpenDefect>,
        derived: List<InspectionV2.OpenDefect>,
        unsyncedLogIds: Set<String>,
        pending: List<PendingChange>,
    ): LocalView {
        val pendingIds = HashSet<String>()
        val derivedIds = HashSet<String>()
        var list: List<InspectionV2.OpenDefect>
        val source: Source
        if (register == Register.SUPPORTED) {
            source = Source.SERVER
            list = cached.filter { it.status in ACTIVE_STATUSES }
            val keys = list.mapTo(HashSet()) { it.key }
            val local = derived.filter { d ->
                d.key !in keys && (d.lastSeenLogId in unsyncedLogIds || d.sourceLogId in unsyncedLogIds)
            }
            local.forEach { pendingIds += it.id; derivedIds += it.id }
            list = list + local
        } else {
            source = Source.DERIVED
            list = derived
            derived.forEach { derivedIds += it.id }
        }
        for (p in pending) {
            val matches: (InspectionV2.OpenDefect) -> Boolean = { d ->
                // Derived changes follow their slot onto the register row once the inspection syncs
                // (the outbox resolves them the same way when draining).
                d.id.equals(p.defectId, ignoreCase = true) || (p.derived && d.key == p.itemKey)
            }
            list.filter(matches).forEach { pendingIds += it.id }
            list = applyStatus(list, p.status, matches)
        }
        list = InspectionV2.sortOpen(list.filter { it.status in ACTIVE_STATUSES })
        return LocalView(source, list, pendingIds.filterTo(HashSet()) { id -> list.any { it.id == id } }, derivedIds)
    }
}
