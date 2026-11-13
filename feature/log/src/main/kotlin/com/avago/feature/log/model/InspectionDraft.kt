package com.avago.feature.log.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Autosaved inspection draft, in the web `draftStore.ts` `InspectionDraft` shape so a draft saved
 * here can be resumed on web through the server drafts API (and vice versa).
 */
data class InspectionDraft(
    val acct: String,
    val assetId: String?,
    val logId: String?,
    val savedAt: Long,
    val baseServerVersion: Long? = null,
    val category: String? = null,
    val configId: String? = null,
    val mode: String? = null,
    val subtype: String? = null,
    val values: Map<String, String> = emptyMap(),
    /** OpenDefect.key -> resolution, written as web `resolvedDefects`. */
    val resolutions: Map<String, String> = emptyMap(),
    val resolvedDefects: JsonArray? = null,
    val signOff: InspectionV2.SignOff? = null,
    val certification: InspectionV2.Certification? = null,
    /** Plain form fields: title, log_date, meter, notes, performed_by, performed_by_user_id. */
    val form: Map<String, String> = emptyMap(),
    /** Android-only: id the new entry will be saved under, so a restored draft keeps it. */
    val entryId: String? = null,
    /** Android-only: this device's edits relative to the stored values (edits of existing entries). */
    val edits: InspectionV2.ValueEdits? = null,
) {
    val key: String get() = draftKey(acct, assetId, logId)

    fun hasContent(): Boolean = values.isNotEmpty() || resolutions.isNotEmpty() || signOff != null || certification != null

    fun toJson(): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        out["key"] = JsonPrimitive(key)
        out["acct"] = JsonPrimitive(acct)
        out["assetId"] = assetId?.let { JsonPrimitive(it) } ?: JsonNull
        out["logId"] = logId?.let { JsonPrimitive(it) } ?: JsonNull
        out["savedAt"] = JsonPrimitive(savedAt)
        baseServerVersion?.let { out["baseServerVersion"] = JsonPrimitive(it) }
        out["category"] = JsonPrimitive(category ?: "inspection")
        out["configId"] = configId?.let { JsonPrimitive(it) } ?: JsonNull
        out["mode"] = mode?.let { JsonPrimitive(it) } ?: JsonNull
        out["subtype"] = subtype?.let { JsonPrimitive(it) } ?: JsonNull
        out["values"] = JsonObject(values.mapValues { JsonPrimitive(it.value) })
        resolvedDefects?.let { out["resolvedDefects"] = it }
        out["resolutions"] = JsonObject(resolutions.mapValues { JsonPrimitive(it.value) })
        out["signOff"] = with(InspectionV2) { signOff?.toJson() } ?: JsonNull
        out["certification"] = with(InspectionV2) { certification?.toJson() } ?: JsonNull
        out["form"] = JsonObject(form.mapValues { JsonPrimitive(it.value) })
        out["kind"] = JsonPrimitive("inspection")
        entryId?.let { out["entryId"] = JsonPrimitive(it) }
        edits?.let { e ->
            out["androidEdits"] = JsonObject(
                mapOf(
                    "changed" to JsonObject(e.changed.mapValues { JsonPrimitive(it.value) }),
                    "cleared" to JsonArray(e.cleared.map { JsonPrimitive(it) }),
                ),
            )
        }
        return JsonObject(out)
    }

    companion object {
        fun draftKey(acct: String?, assetId: String?, logId: String?): String =
            "${acct ?: "anon"}:${assetId ?: "none"}:${logId ?: "new"}"

        private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

        private fun JsonElement?.strMap(): Map<String, String> =
            (this as? JsonObject)?.mapNotNull { (k, v) ->
                when (v) {
                    is JsonNull -> null
                    is JsonPrimitive -> k to v.content
                    else -> k to v.toString()
                }
            }?.toMap() ?: emptyMap()

        /** Parses a local or server (web-written) draft payload; null when it isn't an inspection draft. */
        fun fromJson(o: JsonObject?, fallbackAcct: String = "anon"): InspectionDraft? {
            if (o == null) return null
            if (o["kind"].str() == "log") return null
            val values = o["values"].strMap()
            val resolvedArr = o["resolvedDefects"] as? JsonArray
            val resolutions = o["resolutions"].strMap().ifEmpty {
                resolvedArr?.mapNotNull { el ->
                    val r = el as? JsonObject ?: return@mapNotNull null
                    val key = r["key"].str() ?: r["itemId"].str() ?: return@mapNotNull null
                    val res = r["resolution"].str() ?: return@mapNotNull null
                    key to res
                }?.toMap() ?: emptyMap()
            }
            val env = InspectionV2.parseEnvelope(
                JsonObject(
                    buildMap {
                        put("values", JsonObject(emptyMap()))
                        o["signOff"]?.let { put("signOff", it) }
                        o["certification"]?.let { put("certification", it) }
                    },
                ),
            )
            return InspectionDraft(
                acct = o["acct"].str() ?: fallbackAcct,
                assetId = o["assetId"].str(),
                logId = o["logId"].str(),
                savedAt = o["savedAt"].str()?.toDoubleOrNull()?.toLong() ?: 0L,
                baseServerVersion = o["baseServerVersion"].str()?.toDoubleOrNull()?.toLong(),
                category = o["category"].str(),
                configId = o["configId"].str(),
                mode = o["mode"].str(),
                subtype = o["subtype"].str(),
                values = values,
                resolutions = resolutions,
                resolvedDefects = resolvedArr,
                signOff = env.signOff,
                certification = env.certification,
                form = o["form"].strMap(),
                entryId = o["entryId"].str(),
                edits = (o["androidEdits"] as? JsonObject)?.let { e ->
                    InspectionV2.ValueEdits(
                        changed = e["changed"].strMap(),
                        cleared = (e["cleared"] as? JsonArray)?.mapNotNull { it.str() }?.toSet() ?: emptySet(),
                    )
                },
            )
        }
    }
}
