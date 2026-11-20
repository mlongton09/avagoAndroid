package com.avago.feature.log.model

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** A value both this device and someone else changed differently since the form was opened. */
data class LogEditConflict(val key: String, val mine: String?, val theirs: String?) {
    companion object {
        const val FIELD_PREFIX = "field."
        const val ATTRIBUTE_PREFIX = "attr."
    }
}

/**
 * Three-way merge of a log edit onto the entry's latest synced version when it changed while the
 * form was open: what the user changed (draft vs. original) is applied on top of the latest row,
 * everything else keeps the latest values. Fields both sides changed differently are reported.
 */
object LogRebase {
    enum class Field(val key: String) {
        WORK_ORDER("work_order"), CATEGORY("category"), TITLE("title"), DATE("date"), METER("meter"),
        PERFORMER("performer"), PROVIDER("provider"), NOTES("notes"),
    }

    data class Result(val draft: ActivityDraft, val conflicts: List<LogEditConflict>)

    fun rebase(original: Activity, latest: Activity, draft: ActivityDraft, formatDate: (Long) -> String): Result {
        val conflicts = mutableListOf<LogEditConflict>()

        fun <T> pick(field: Field, orig: T, theirs: T, mine: T, same: (T, T) -> Boolean, show: (T) -> String?): T {
            if (same(mine, orig)) return theirs
            if (!same(theirs, orig) && !same(theirs, mine)) {
                conflicts += LogEditConflict(LogEditConflict.FIELD_PREFIX + field.key, show(mine), show(theirs))
            }
            return mine
        }
        val text = { a: String?, b: String? -> a.norm() == b.norm() }
        val asText = { v: String? -> v.norm() }

        val workOrder = pick(Field.WORK_ORDER, original.workOrderId, latest.workOrderId, draft.workOrderId, text, asText)
        val category = pick(Field.CATEGORY, original.categoryLabel, latest.categoryLabel, draft.categoryLabel, text, asText)
        val title = pick(Field.TITLE, original.title, latest.title, draft.title, text, asText)
        val occurred = pick(Field.DATE, original.occurredAt, latest.occurredAt, draft.occurredAt, { a, b -> a == b }, formatDate)
        val meter = pick(
            Field.METER, original.meterValue, latest.meterValue, draft.meterValue,
            { a, b -> a == b || (a != null && b != null && a.compareTo(b) == 0) },
            { it?.stripTrailingZeros()?.toPlainString() },
        )
        val performer = pick(
            Field.PERFORMER,
            original.performedByUserId to original.performedByName,
            latest.performedByUserId to latest.performedByName,
            draft.performedByUserId to draft.performedByName,
            { a, b -> a.first == b.first && a.second.norm() == b.second.norm() },
            { it.second.norm() },
        )
        val provider = pick(Field.PROVIDER, original.providerName, latest.providerName, draft.providerName, text, asText)
        val notes = pick(Field.NOTES, original.notes, latest.notes, draft.notes, text, asText)

        val attributes = draft.attributes?.let { mine ->
            val merged = rebaseJson(original.attributes.obj(), mine.obj(), latest.attributes.obj())
            merged.conflicts.forEach { k ->
                conflicts += LogEditConflict(LogEditConflict.ATTRIBUTE_PREFIX + k, mine.obj()[k].display(), latest.attributes.obj()[k].display())
            }
            merged.value.toString()
        }
        val payload = draft.payload?.let { mine -> rebaseJson(original.payload.obj(), mine.obj(), latest.payload.obj()).value.toString() }

        return Result(
            draft = draft.copy(
                workOrderId = workOrder,
                categoryLabel = category,
                title = title,
                occurredAt = occurred,
                meterValue = meter,
                performedByUserId = performer.first,
                performedByName = performer.second,
                providerName = provider,
                notes = notes,
                currency = draft.currency.takeIf { it != original.currency } ?: latest.currency,
                overtimeMultiplier = latest.overtimeMultiplier,
                attributes = attributes,
                payload = payload,
            ),
            conflicts = conflicts,
        )
    }

    data class JsonMerge(val value: JsonObject, val conflicts: List<String>)

    /** Key-level three-way merge: keys [mine] changed or removed relative to [original] win over [theirs]. */
    fun rebaseJson(original: JsonObject, mine: JsonObject, theirs: JsonObject): JsonMerge {
        val out = LinkedHashMap<String, JsonElement>(theirs)
        val conflicts = mutableListOf<String>()
        (original.keys + mine.keys).forEach { k ->
            val o = original[k]
            val m = mine[k]
            if (m == o) return@forEach
            val t = theirs[k]
            if (t != o && t != m) conflicts += k
            if (m == null) out.remove(k) else out[k] = m
        }
        return JsonMerge(JsonObject(out), conflicts.sorted())
    }

    private fun String?.norm(): String? = this?.trim()?.ifEmpty { null }

    private fun String?.obj(): JsonObject =
        this?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())

    private fun JsonElement?.display(): String? = when (this) {
        null -> null
        is JsonPrimitive -> content
        else -> toString()
    }
}
