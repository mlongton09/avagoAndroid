package com.avago.feature.rentals.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Readiness checklist after a check-in (spec §11.1, rentals-api-stage-b.md §3). A unit is
 * **Not ready** while its latest check is `pending` or `failed`; it becomes available again once
 * the check is `passed`, or `waived` (needs `rental.override_ready` and a reason).
 *
 * Checklist items are `{key, label, done}`; the server stores `items` verbatim, so optional
 * photos ride on the item as `photos:[{file_id, storage_key}]` (the outbox fills them in from
 * the device's `photo_ids` once uploaded).
 */
object Readiness {
    const val PENDING = "pending"
    const val PASSED = "passed"
    const val FAILED = "failed"
    const val WAIVED = "waived"

    /** Permission needed to waive a checklist. */
    const val PERMISSION_WAIVE = "rental.override_ready"

    /** Local-only keys on a check record (never sent to the server). */
    const val KEY_PLACEHOLDER = "_placeholder"
    const val KEY_QUEUED = "_queued"
    const val KEY_BASE = "_base"
    const val KEY_HANDOVER_LOCAL_ID = "_handover_local_id"

    /** Server default checklist (handovers.rs `default_checklist`). */
    val DEFAULT_ITEMS = listOf(
        Item("inspected", "Inspected", false),
        Item("cleaned", "Cleaned", false),
        Item("refuelled", "Refuelled / charged", false),
        Item("accessories", "Accessories complete", false),
    )

    data class Item(
        val key: String,
        val label: String,
        val done: Boolean,
        /** Photos taken on this device (local photo ids, uploaded before the check syncs). */
        val photoIds: List<String> = emptyList(),
        /** Fields of the server item this client does not edit (kept verbatim). */
        val extra: JsonObject = JsonObject(emptyMap()),
    )

    data class Check(
        val id: String,
        val assetId: String,
        val status: String,
        val items: List<Item>,
        val notes: String?,
        val serverVersion: Long?,
        val orderId: String?,
        val handoverId: String?,
        val completedAt: Instant?,
        val createdAt: Instant?,
        /** Created on the device after an offline check-in (the server id is not known yet). */
        val placeholder: Boolean,
        val localState: String,
        val notice: String?,
    ) {
        val open: Boolean get() = status == PENDING || status == FAILED
        val pendingSync: Boolean get() = localState != RentalRecordEntity.STATE_SYNCED
    }

    fun parseItems(items: JsonArray?): List<Item> = items.orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val key = o.str("key") ?: return@mapNotNull null
        Item(
            key = key,
            label = o.str("label") ?: key,
            done = o.bool("done") ?: false,
            photoIds = o.strings("_photo_ids"),
            extra = JsonObject(o - "key" - "label" - "done" - "_photo_ids"),
        )
    }

    /** Items for the local record (keeps unsynced `_photo_ids`). */
    fun itemsJson(items: List<Item>, withLocal: Boolean = true): JsonArray = JsonArray(
        items.map { i ->
            JsonObject(
                i.extra + buildJsonObject {
                    put("key", i.key)
                    put("label", i.label)
                    put("done", i.done)
                    if (withLocal && i.photoIds.isNotEmpty()) put("_photo_ids", JsonArray(i.photoIds.map(::JsonPrimitive)))
                },
            )
        },
    )

    /**
     * Items for the server: local photo ids are replaced by uploaded `{file_id, storage_key}`
     * entries (appended to any photos the item already had); [storageKey] returns null for a
     * photo that is not uploaded (it is left out).
     */
    fun serverItems(items: List<Item>, storageKey: (String) -> String?): JsonArray = JsonArray(
        items.map { i ->
            val existing = (i.extra["photos"] as? JsonArray).orEmpty()
            val known = existing.mapNotNull { (it as? JsonObject)?.str("file_id") }.toSet()
            val added = i.photoIds.filter { it !in known }.mapNotNull { id ->
                storageKey(id)?.let { k -> buildJsonObject { put("file_id", id); put("storage_key", k) } }
            }
            val photos = existing + added
            JsonObject(
                (i.extra - "photos") + buildJsonObject {
                    put("key", i.key)
                    put("label", i.label)
                    put("done", i.done)
                    if (photos.isNotEmpty()) put("photos", JsonArray(photos))
                },
            )
        },
    )

    fun of(r: RentalRecordEntity): Check? {
        val o = r.obj
        if (o.bool("_deleted") == true) return null
        return Check(
            id = r.id,
            assetId = o.str("asset_id") ?: r.assetId ?: return null,
            status = o.str("status") ?: PENDING,
            items = parseItems(o["items"] as? JsonArray).ifEmpty { DEFAULT_ITEMS },
            notes = o.str("notes"),
            serverVersion = o.long("server_version"),
            orderId = o.str("order_id"),
            handoverId = o.str("handover_id"),
            completedAt = o.instant("completed_at"),
            createdAt = o.instant("created_at"),
            placeholder = o.bool(KEY_PLACEHOLDER) == true,
            localState = r.localState,
            notice = r.notice,
        )
    }

    /**
     * The check a unit's readiness screen edits: a local (unsynced) row first, then the newest
     * open check, then the newest check (same deciding rule as the fleet).
     */
    fun current(checks: List<Check>): Check? =
        checks.filter { it.pendingSync }.maxByOrNull { it.createdAt ?: Instant.EPOCH }
            ?: checks.maxWithOrNull(compareBy<Check> { it.createdAt ?: Instant.EPOCH }.thenBy { it.serverVersion ?: 0 })

    /** Allowed status changes (server `check_status_ok`): only an open check can change. */
    fun canTransition(from: String, to: String): Boolean =
        (from == PENDING || from == FAILED) && to in setOf(PENDING, PASSED, FAILED, WAIVED)

    enum class Problem { CLOSED, INCOMPLETE, REASON_REQUIRED, NOT_ALLOWED }

    /**
     * Validates a decision before it is queued. Passing needs every item done; failing needs a
     * reason (what is wrong); waiving needs [canWaive] and a reason (server `reason_required`).
     */
    fun validate(from: String, to: String, items: List<Item>, notes: String?, canWaive: Boolean): Problem? = when {
        !canTransition(from, to) -> Problem.CLOSED
        to == WAIVED && !canWaive -> Problem.NOT_ALLOWED
        to == WAIVED && notes.isNullOrBlank() -> Problem.REASON_REQUIRED
        to == FAILED && notes.isNullOrBlank() && items.all { it.done } -> Problem.REASON_REQUIRED
        to == PASSED && items.any { !it.done } -> Problem.INCOMPLETE
        else -> null
    }

    /**
     * Local row after a decision: status / items / notes applied, `completed_at` set for a final
     * status, the pre-edit server copy kept in [KEY_BASE] (restored if the change is discarded).
     */
    fun applyLocal(current: JsonObject, to: String, items: List<Item>, notes: String?, at: Instant): JsonObject {
        val base = current[KEY_BASE] ?: if (current.bool(KEY_PLACEHOLDER) == true) null else JsonObject(current - KEY_BASE)
        val done = to == PASSED || to == FAILED || to == WAIVED
        return JsonObject(
            current + buildJsonObject {
                put("status", to)
                put("items", itemsJson(items))
                put("notes", notes?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                if (done) put("completed_at", at.toString())
                put(KEY_QUEUED, true)
                base?.let { put(KEY_BASE, it) }
            },
        )
    }

    /** A placeholder pending check written on a local check-in (the server creates the real one). */
    fun placeholder(id: String, assetId: String, orderId: String?, handoverLocalId: String, at: Instant): JsonObject = buildJsonObject {
        put("id", id)
        put("asset_id", assetId)
        orderId?.let { put("order_id", it) }
        put("status", PENDING)
        put("items", itemsJson(DEFAULT_ITEMS))
        put("created_at", at.toString())
        put(KEY_PLACEHOLDER, true)
        put(KEY_HANDOVER_LOCAL_ID, handoverLocalId)
    }

    /** A fresh check started on the device for a unit without an open one. */
    fun fresh(id: String, assetId: String, at: Instant): JsonObject = buildJsonObject {
        put("id", id)
        put("asset_id", assetId)
        put("status", PENDING)
        put("items", itemsJson(DEFAULT_ITEMS))
        put("created_at", at.toString())
        put("_new", true)
    }

    /**
     * True when a server answer means the queued decision already happened (a replay after a
     * lost response: `invalid_transition` / version `conflict` whose current row has the target
     * status).
     */
    fun alreadyApplied(current: JsonObject?, target: String): Boolean = current?.str("status") == target
}
