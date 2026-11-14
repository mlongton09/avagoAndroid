package com.avago.core.sync

import com.avago.core.data.db.entity.InspectionDefectEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

/**
 * Pure rules for the inspection-defect outbox (offline lifecycle changes on the open-defects
 * register). Queued changes for register rows (UUID `defect_id`) go through `/sync/push` as
 * `inspection_defect` operations ([isSyncPushable] / [syncPushData], AvagoSvc docs/sync-defects.md).
 * Derived defects (`${assetId}:${key}`, known only from local history) cannot be addressed by
 * `/sync`; [DefectRegisterSync] drains them through `PATCH /defects/{id}` and these helpers decide
 * what each such change does against the server's current row. Server wins on status conflicts.
 */
object DefectOutbox {

    /** `sync_queue.entity_type` for queued defect lifecycle changes. */
    const val ENTITY_TYPE = "inspection_defect"

    /**
     * `sync_metadata` row recording register availability (`last_server_seq` holds
     * [REGISTER_SUPPORTED] / [REGISTER_UNSUPPORTED], `last_sync_at` the last refresh). Distinct from
     * the `/sync/pull` watermark row, which is keyed by [ENTITY_TYPE].
     */
    const val META_KEY = "inspection_defect_register"

    /**
     * Builds before `/sync` defect support stored the register flag under [ENTITY_TYPE]; that row
     * is now the pull watermark and must not be read as one (see [DefectRegisterSync.migrateLegacyMeta]).
     */
    const val LEGACY_META_KEY = ENTITY_TYPE
    const val REGISTER_SUPPORTED = 1L
    const val REGISTER_UNSUPPORTED = -1L

    val ACTIVE_STATUSES = setOf("open", "monitoring", "deferred")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * One queued lifecycle change. [baseStatus] is the status the user saw before their first
     * still-queued change (kept across later edits) — the conflict baseline. [derived] marks
     * defects known only from local inspection history (entity id `${assetId}:${key}`); they are
     * resolved to a register row by [itemKey] / [logId] when the queue drains.
     */
    data class Op(
        val assetId: String,
        val itemKey: String,
        val status: String,
        val resolutionNote: String?,
        val baseStatus: String,
        val derived: Boolean,
        val logId: String?,
        val label: String,
    ) {
        fun toJson(): String = buildJsonObject {
            put("asset_id", assetId)
            put("item_key", itemKey)
            put("status", status)
            resolutionNote?.let { put("resolution_note", it) }
            put("base_status", baseStatus)
            put("derived", derived)
            logId?.let { put("log_id", it) }
            put("label", label)
        }.toString()

        companion object {
            fun fromJson(raw: String?): Op? {
                if (raw.isNullOrBlank()) return null
                val o = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
                return Op(
                    assetId = o.str("asset_id") ?: return null,
                    itemKey = o.str("item_key") ?: return null,
                    status = o.str("status") ?: return null,
                    resolutionNote = o.str("resolution_note"),
                    baseStatus = o.str("base_status") ?: "open",
                    derived = o.str("derived") == "true",
                    logId = o.str("log_id"),
                    label = o.str("label") ?: o.str("item_key").orEmpty(),
                )
            }
        }
    }

    sealed interface Merge {
        data class Enqueue(val op: Op) : Merge
        /** The change undoes the queued one (back to the baseline): drop it, nothing to push. */
        data object Cancel : Merge
    }

    /** Collapse [next] onto an [existing] queued change for the same defect (one row per defect). */
    fun merge(existing: Op?, next: Op): Merge {
        val merged = next.copy(
            baseStatus = existing?.baseStatus ?: next.baseStatus,
            resolutionNote = next.resolutionNote?.trim()?.takeIf { it.isNotEmpty() },
        )
        return if (sameStatus(merged.status, merged.baseStatus) && merged.resolutionNote == null) {
            Merge.Cancel
        } else {
            Merge.Enqueue(merged)
        }
    }

    /** `open` and `monitoring` are the same lifecycle state (the server maps open on an M row to monitoring). */
    fun sameStatus(a: String?, b: String?): Boolean = norm(a) == norm(b)

    private fun norm(s: String?): String = if (s == "monitoring" || s.isNullOrBlank()) "open" else s

    /** Status the register will hold after applying [target] (web/server: open on a Monitor row → monitoring). */
    fun localStatus(target: String, bucket: String?): String =
        if (target == "open" && bucket == "M") "monitoring" else target

    /** `bucket` of a cached row's JSON ("NR" / "M"), null when unreadable. */
    fun bucketOf(rowJson: String): String? =
        runCatching { json.parseToJsonElement(rowJson).jsonObject.str("bucket") }.getOrNull()

    sealed interface Decision {
        data class Patch(val defectId: String, val current: JsonObject) : Decision
        /** The server already holds the requested status — nothing to send. */
        data class AlreadyApplied(val current: JsonObject) : Decision
        /** Someone else changed the status since the baseline — keep the server's. */
        data class ServerWins(val current: JsonObject) : Decision
        /** The register row no longer exists. */
        data object Gone : Decision
        /** Derived defect whose inspection has not reached the register yet — retry later. */
        data object NotYetRegistered : Decision
    }

    /** What to do with a queued [op] (queue entity id [entityId]) given the asset's current register [rows]. */
    fun decide(entityId: String, op: Op, rows: List<JsonObject>): Decision {
        val current = if (op.derived) locateDerived(op, rows) else rows.firstOrNull { it.str("defect_id").equals(entityId, ignoreCase = true) }
        val id = current?.str("defect_id")
        if (current == null || id == null) return if (op.derived) Decision.NotYetRegistered else Decision.Gone
        val status = current.str("status") ?: "open"
        return when {
            sameStatus(status, op.status) && op.resolutionNote == null -> Decision.AlreadyApplied(current)
            !sameStatus(status, op.baseStatus) && !sameStatus(status, op.status) -> Decision.ServerWins(current)
            else -> Decision.Patch(id, current)
        }
    }

    /**
     * Register row for a derived defect: the row for this slot reported or last seen by the same
     * inspection, else the slot's active row (most recently seen).
     */
    internal fun locateDerived(op: Op, rows: List<JsonObject>): JsonObject? {
        val slot = rows.filter {
            it.str("item_key") == op.itemKey && (it.str("asset_id")?.equals(op.assetId, ignoreCase = true) ?: true)
        }
        if (op.logId != null) {
            slot.firstOrNull {
                it.str("last_seen_log_id").equals(op.logId, ignoreCase = true) ||
                    it.str("source_log_id").equals(op.logId, ignoreCase = true)
            }?.let { return it }
        }
        return slot.filter { it.str("status") in ACTIVE_STATUSES }.maxByOrNull { it.str("last_seen_at").orEmpty() }
    }

    private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * True when a queued change can go through `/sync/push`: a register row (UUID `defect_id`),
     * not a derived defect. Malformed / derived rows stay on [DefectRegisterSync.drain].
     */
    fun isSyncPushable(entityId: String, op: Op?): Boolean =
        op != null && !op.derived && UUID_RE.matches(entityId)

    /**
     * `/sync/push` `data` for a queued change. [mutationId] is the queue row id: stable across
     * retries of the same change (a later edit replaces the row with a new id), so the server
     * dedupes replays.
     */
    fun syncPushData(mutationId: String, op: Op): JsonObject = buildJsonObject {
        put("mutation_id", mutationId)
        put("status", op.status)
        op.resolutionNote?.let { put("resolution_note", it) }
        put("base_status", op.baseStatus)
    }

    /** 4xx other than timeout / rate limit: the server will never accept this change. */
    fun isPermanent(code: Int): Boolean = code in 400..499 && !com.avago.core.data.sync.QueueRetryPolicy.isTransientHttp(code)

    /** 404 / 405 on the register: the service predates it. */
    fun isNotDeployed(code: Int): Boolean = code == 404 || code == 405

    /** Server register row → cache entity (null when it lacks an id / asset). */
    fun toEntity(row: JsonObject, now: Long): InspectionDefectEntity? {
        val id = row.str("defect_id") ?: return null
        val asset = row.str("asset_id") ?: return null
        return InspectionDefectEntity(
            defectId = id,
            assetId = asset,
            itemKey = row.str("item_key") ?: row.str("item_id").orEmpty(),
            status = row.str("status") ?: "open",
            rowJson = row.toString(),
            serverUpdatedAt = row.str("updated_at"),
            cachedAt = now,
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
