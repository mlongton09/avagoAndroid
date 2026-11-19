package com.avago.core.data.v2

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** One REST request of the v2 write bridge; [path] starts with `/accounts/…`. */
data class V2RestCall(val method: String, val path: String, val body: JsonObject?)

sealed interface V2BridgePlan {
    /** Send [calls] in order; an empty list means "nothing left to do" (drop the queue row). */
    data class Send(val calls: List<V2RestCall>) : V2BridgePlan

    /** The v2 server has no write path for this queue row; it stays queued with [reason]. */
    data class Unsupported(val reason: String) : V2BridgePlan
}

/**
 * Write bridge (P3-12b): turns a legacy `sync_queue` row into the REST calls the v2 server still
 * serves for that entity (the mobile command outbox only covers stock documents, sync-wire.md §4).
 *
 * [payload] is the legacy push JSON built from the Room row ([SyncPayloadBuilder]); [current] is
 * the same entity projected from the v2 store ([V2LegacyProjector.projectOne]) — null means the
 * server does not have it yet, so the row is a create. Updates send only the fields that differ
 * from [current], which keeps partial-update routes from tripping over unrelated fields.
 */
class V2LegacyWritePlanner {
    fun plan(
        accountId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payload: JsonObject?,
        current: JsonObject?,
    ): V2BridgePlan {
        val deleting = operation.equals("delete", ignoreCase = true) || payload.str("deleted_at") != null
        if (!deleting && payload == null) return none()
        if (deleting && current == null) return none()
        val a = "/accounts/$accountId"
        val p = payload ?: JsonObject(emptyMap())
        val create = current == null
        return when (entityType) {
            "asset" -> crud(
                deleting, create, "$a/assets", entityId,
                body = pick(p, ASSET_FIELDS), current = current, idKey = "asset_id", updateMethod = "PUT",
            )
            "work_order" -> workOrder(a, entityId, deleting, create, p, current)
            "wo_assignment" -> assignment(a, entityId, deleting, create, p, current)
            "wo_checklist_item" -> checklist(a, entityId, deleting, create, p, current)
            "wo_comment" -> comment(a, entityId, deleting, create, p, current)
            "log" -> crud(
                deleting, create, "$a/log", entityId,
                body = pick(p, if (create) LOG_CREATE_FIELDS else LOG_UPDATE_FIELDS), current = current,
                idKey = "log_id", updateMethod = "PUT",
            )
            "log_cost_line" -> {
                val logId = p.str("log_id") ?: current.str("log_id") ?: return V2BridgePlan.Unsupported("cost line without log_id")
                crud(
                    deleting, create, "$a/log/$logId/cost-lines", entityId,
                    body = pick(p, if (create) COST_LINE_CREATE_FIELDS else COST_LINE_UPDATE_FIELDS),
                    current = current, idKey = "line_id", updateMethod = "PUT",
                )
            }
            "location" -> location(a, entityId, deleting, create, p, current)
            "bin" -> bin(a, entityId, deleting, create, p, current)
            "part" -> part(a, entityId, deleting, create, p, current)
            "tech_profile" -> {
                if (deleting) return V2BridgePlan.Unsupported("tech profiles cannot be deleted on v2")
                val userId = p.str("user_id") ?: return V2BridgePlan.Unsupported("tech profile without user_id")
                val body = rename(pick(p, TECH_PROFILE_FIELDS), mapOf("rate_currency" to "rate_currency"))
                val changed = if (current == null) JsonObject(body) else diff(body, current)
                if (changed.isEmpty()) none() else send(V2RestCall("PUT", "$a/tech-profiles/$userId", changed))
            }
            else -> V2BridgePlan.Unsupported("$entityType writes are not supported on the v2 server yet")
        }
    }

    private fun workOrder(
        a: String, id: String, deleting: Boolean, create: Boolean, p: JsonObject, current: JsonObject?,
    ): V2BridgePlan {
        val base = "$a/work-orders"
        if (deleting) return send(V2RestCall("DELETE", "$base/$id", null))
        if (create) {
            if (p.str("title").isNullOrBlank()) return V2BridgePlan.Unsupported("work order without a title")
            return send(V2RestCall("POST", base, withId(pick(p, WO_CREATE_FIELDS), "wo_id", id)))
        }
        val calls = mutableListOf<V2RestCall>()
        val changed = diff(pick(p, WO_UPDATE_FIELDS), current!!)
        if (changed.isNotEmpty()) calls += V2RestCall("PATCH", "$base/$id", changed)
        val status = p.str("status")
        if (status != null && status != current.str("status")) {
            calls += V2RestCall("POST", "$base/$id/transition", JsonObject(mapOf("status" to JsonPrimitive(status))))
        }
        return send(calls)
    }

    private fun assignment(
        a: String, id: String, deleting: Boolean, create: Boolean, p: JsonObject, current: JsonObject?,
    ): V2BridgePlan {
        if (deleting) return V2BridgePlan.Unsupported("assignments cannot be deleted on v2")
        val woId = p.str("wo_id") ?: current.str("wo_id") ?: return V2BridgePlan.Unsupported("assignment without wo_id")
        val base = "$a/work-orders/$woId/assignments"
        if (create) {
            val techId = p.str("tech_id") ?: return V2BridgePlan.Unsupported("assignment without tech_id")
            val body = pick(p, setOf("scheduled_start", "scheduled_end")) + ("tech_id" to JsonPrimitive(techId))
            return send(V2RestCall("POST", base, JsonObject(body)))
        }
        val calls = mutableListOf<V2RestCall>()
        val changed = diff(pick(p, ASSIGNMENT_PATCH_FIELDS), current!!)
        if (changed.isNotEmpty()) calls += V2RestCall("PATCH", "$base/$id", changed)
        val status = p.str("status")?.lowercase()
        if (status != null && status != current.str("status")?.lowercase()) {
            when (status) {
                "accepted" -> calls += V2RestCall("POST", "$base/$id/accept", null)
                "declined", "rejected" -> calls += V2RestCall("POST", "$base/$id/decline", null)
            }
        }
        return send(calls)
    }

    private fun checklist(
        a: String, id: String, deleting: Boolean, create: Boolean, p: JsonObject, current: JsonObject?,
    ): V2BridgePlan {
        val woId = p.str("wo_id") ?: current.str("wo_id") ?: return V2BridgePlan.Unsupported("checklist item without wo_id")
        val base = "$a/work-orders/$woId/checklist"
        if (deleting) return send(V2RestCall("DELETE", "$base/$id", null))
        if (create) {
            val body = rename(
                pick(p, setOf("title", "display_order", "row_type", "row_description", "urls_json", "required")),
                mapOf("display_order" to "step_order", "row_description" to "description", "urls_json" to "urls"),
            )
            return send(V2RestCall("POST", base, normalizeBody(body)))
        }
        val nowDone = p.bool("is_completed") == true
        val wasDone = current.bool("completed") == true
        if (nowDone && !wasDone) {
            val notes = p["notes"]?.takeIf { it !is JsonNull }
            return send(V2RestCall("PUT", "$base/$id", JsonObject(if (notes != null) mapOf("notes" to notes) else emptyMap())))
        }
        return none()
    }

    private fun comment(
        a: String, id: String, deleting: Boolean, create: Boolean, p: JsonObject, current: JsonObject?,
    ): V2BridgePlan {
        val woId = p.str("wo_id") ?: current.str("wo_id") ?: return V2BridgePlan.Unsupported("comment without wo_id")
        val base = "$a/work-orders/$woId/comments"
        if (deleting) return send(V2RestCall("DELETE", "$base/$id", null))
        if (!create) return none() // comments are immutable on the server
        return send(V2RestCall("POST", base, normalizeBody(pick(p, COMMENT_FIELDS))))
    }

    private fun location(
        a: String, id: String, deleting: Boolean, create: Boolean, p: JsonObject, current: JsonObject?,
    ): V2BridgePlan {
        val base = "$a/locations"
        if (deleting) return send(V2RestCall("DELETE", "$base/$id", null))
        val fields = pick(p, LOCATION_FIELDS)
        if (create) {
            val name = p.str("name") ?: return V2BridgePlan.Unsupported("location without a name")
            val body = rename(fields, mapOf("address" to "address_line1")) +
                ("short_code" to JsonPrimitive(p.str("short_code") ?: name.take(40)))
            return send(V2RestCall("POST", base, normalizeBody(body)))
        }
        val changed = rename(diff(fields + pick(p, setOf("archived")), current!!), mapOf("address" to "address_line1"))
        return if (changed.isEmpty()) none() else send(V2RestCall("PUT", "$base/$id", JsonObject(changed)))
    }

    private fun bin(
        a: String, id: String, deleting: Boolean, create: Boolean, p: JsonObject, current: JsonObject?,
    ): V2BridgePlan {
        val base = "$a/bins"
        if (deleting) return send(V2RestCall("DELETE", "$base/$id", null))
        val fields = rename(pick(p, setOf("name", "code", "barcode", "bin_type", "capacity", "is_active")), mapOf("is_active" to "active"))
        if (create) {
            val locationId = p.str("location_id") ?: return V2BridgePlan.Unsupported("bin without location_id")
            val code = p.str("code") ?: p.str("name") ?: return V2BridgePlan.Unsupported("bin without code or name")
            val body = fields - "active" + mapOf("location_id" to JsonPrimitive(locationId), "code" to JsonPrimitive(code))
            return send(V2RestCall("POST", base, JsonObject(body)))
        }
        val changed = diff(fields, current!!)
        return if (changed.isEmpty()) none() else send(V2RestCall("PUT", "$base/$id", changed))
    }

    private fun part(
        a: String, id: String, deleting: Boolean, create: Boolean, p: JsonObject, current: JsonObject?,
    ): V2BridgePlan {
        val base = "$a/inventory/parts"
        if (deleting) {
            // No delete route: parts are archived.
            return send(V2RestCall("PUT", "$base/$id", JsonObject(mapOf("status" to JsonPrimitive("inactive")))))
        }
        if (create) {
            if (p.str("part_name").isNullOrBlank()) return V2BridgePlan.Unsupported("part without a name")
            return send(V2RestCall("POST", base, withId(pick(p, PART_FIELDS), "part_id", id)))
        }
        val changed = diff(pick(p, PART_FIELDS), current!!)
        return if (changed.isEmpty()) none() else send(V2RestCall("PUT", "$base/$id", changed))
    }

    private fun crud(
        deleting: Boolean,
        create: Boolean,
        base: String,
        id: String,
        body: Map<String, JsonElement>,
        current: JsonObject?,
        idKey: String,
        updateMethod: String,
    ): V2BridgePlan {
        if (deleting) return send(V2RestCall("DELETE", "$base/$id", null))
        if (create) return send(V2RestCall("POST", base, withId(body, idKey, id)))
        val changed = diff(body, current!!)
        return if (changed.isEmpty()) none() else send(V2RestCall(updateMethod, "$base/$id", changed))
    }

    companion object {
        val SUPPORTED_TYPES = setOf(
            "asset", "work_order", "wo_assignment", "wo_checklist_item", "wo_comment", "log", "log_cost_line",
            "location", "bin", "part", "tech_profile",
        )

        private val ASSET_FIELDS = setOf(
            "name", "make", "model", "year", "asset_type", "avatar_color", "avatar_initial", "location_id",
            "attributes", "vin", "serial_number", "is_rental", "rental_rate", "rental_rate_unit",
        )
        private val WO_UPDATE_FIELDS = setOf(
            "title", "description", "category", "priority", "complexity", "assigned_to", "location_id",
            "dispatcher_notes", "required_skills", "estimated_effort_minutes", "actual_effort_minutes", "due_date",
            "parts_needed", "attributes", "currency",
        )
        private val WO_CREATE_FIELDS = WO_UPDATE_FIELDS - "actual_effort_minutes" + setOf("asset_id", "schedule_id", "parent_wo_id")
        private val ASSIGNMENT_PATCH_FIELDS = setOf("tech_id", "estimated_hours", "scheduled_start", "scheduled_end", "notes")
        private val COMMENT_FIELDS = setOf("body", "comment_type", "is_internal", "mentioned_user_ids")
        private val LOG_UPDATE_FIELDS = setOf(
            "parent_id", "title", "log_date", "meter", "category", "cost", "cost_mode", "cost_misc", "performed_by",
            "performed_by_user_id", "notes", "data", "attributes", "currency", "base_amount", "exchange_rate_used",
            "overtime_multiplier",
        )
        private val LOG_CREATE_FIELDS = LOG_UPDATE_FIELDS + setOf(
            "asset_id", "cost_items", "cost_labor", "cost_tax", "config_id", "config_version", "wo_id",
        )
        private val COST_LINE_UPDATE_FIELDS = setOf(
            "display_order", "inventory_id", "user_id", "description", "quantity", "unit_cost", "tax_amount",
            "gl_code", "notes", "cost_status",
        )
        private val COST_LINE_CREATE_FIELDS = COST_LINE_UPDATE_FIELDS + "kind"
        private val LOCATION_FIELDS = setOf(
            "name", "short_code", "address", "city", "state", "postal_code", "country", "timezone", "latitude",
            "longitude", "parent_location_id",
        )
        private val PART_FIELDS = setOf(
            "part_name", "part_number", "manufacturer", "category", "unit_of_measure", "unit_cost", "currency",
            "reorder_quantity", "notes", "status", "abc_class", "gtin",
        )
        private val TECH_PROFILE_FIELDS = setOf("skills", "is_available", "hourly_rate", "rate_currency")

        /** Room keeps these as JSON text; the server's typed request fields want real JSON. */
        private val JSON_KEYS = setOf(
            "attributes", "data", "required_skills", "parts_needed", "urls", "custom_fields", "tags", "asset_tags",
            "mentioned_user_ids", "skills", "certifications",
        )

        /** Fields the server types as `Vec<…>`: a bare comma list becomes an array. */
        private val LIST_KEYS = setOf("required_skills", "mentioned_user_ids", "skills", "asset_tags")

        private val TIME_KEYS = setOf("log_date", "due_date", "scheduled_start", "scheduled_end")

        /** Server `Option<String>` fields the payload may carry as numbers. */
        private val STRING_KEYS = setOf("meter")

        private val DATE_ONLY = Regex("""^\d{4}-\d{2}-\d{2}$""")

        private fun none() = V2BridgePlan.Send(emptyList())

        private fun send(vararg calls: V2RestCall) = V2BridgePlan.Send(calls.toList())

        private fun send(calls: List<V2RestCall>) = V2BridgePlan.Send(calls)

        private fun pick(p: JsonObject, keys: Set<String>): Map<String, JsonElement> =
            p.filterKeys { it in keys }.filterValues { it !is JsonNull }

        private fun rename(body: Map<String, JsonElement>, names: Map<String, String>): Map<String, JsonElement> =
            body.entries.associate { (k, v) -> (names[k] ?: k) to v }

        private fun withId(body: Map<String, JsonElement>, key: String, id: String): JsonObject =
            normalizeBody(body + (key to JsonPrimitive(id)))

        fun normalizeBody(body: Map<String, JsonElement>): JsonObject =
            JsonObject(body.mapValues { (k, v) -> normalize(k, v) }.filterValues { it !is JsonNull })

        /** Legacy push value → the JSON type the v2 REST request struct deserialises. */
        fun normalize(key: String, value: JsonElement): JsonElement {
            if (value !is JsonPrimitive || value is JsonNull) return value
            val text = value.content
            return when {
                key in JSON_KEYS && value.isString -> {
                    val trimmed = text.trim()
                    val parsed = if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                        runCatching { Json.parseToJsonElement(trimmed) }.getOrNull()
                    } else {
                        null
                    }
                    when {
                        parsed != null -> parsed
                        key in LIST_KEYS -> JsonArray(trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive))
                        trimmed.isEmpty() -> JsonNull
                        else -> value
                    }
                }
                key in TIME_KEYS && value.isString && DATE_ONLY.matches(text) -> JsonPrimitive("${text}T12:00:00Z")
                key in STRING_KEYS && !value.isString -> JsonPrimitive(text)
                (key.endsWith("_id") || key == "assigned_to") && value.isString && text.isBlank() -> JsonNull
                else -> value
            }
        }

        /** The normalised fields of [desired] whose value differs from [current]. */
        fun diff(desired: Map<String, JsonElement>, current: JsonObject): JsonObject {
            val out = linkedMapOf<String, JsonElement>()
            for ((key, raw) in desired) {
                val want = normalize(key, raw)
                val have = current[key]?.let { normalize(key, it) } ?: JsonNull
                if (!same(key, want, have)) out[key] = want
            }
            return JsonObject(out.filterValues { it !is JsonNull })
        }

        private fun same(key: String, a: JsonElement, b: JsonElement): Boolean {
            if (a is JsonNull || b is JsonNull) return a is JsonNull && b is JsonNull
            if (a is JsonPrimitive && b is JsonPrimitive) {
                if (key.endsWith("_at") || key in TIME_KEYS) {
                    val ma = V2WireValue.epochMillis(a.content)
                    val mb = V2WireValue.epochMillis(b.content)
                    if (ma != null && mb != null) return ma == mb
                }
                val na = number(a)
                val nb = number(b)
                if (na != null && nb != null) return na == nb
                return a.content == b.content
            }
            return a == b
        }

        private fun number(p: JsonPrimitive): Double? =
            p.booleanOrNull?.let { if (it) 1.0 else 0.0 } ?: p.doubleOrNull

        private fun JsonObject?.str(key: String): String? =
            (this?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

        private fun JsonObject?.bool(key: String): Boolean? =
            (this?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.booleanOrNull ?: it.doubleOrNull?.let { d -> d != 0.0 } }
    }
}
