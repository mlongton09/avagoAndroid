package com.avago.core.data.v2

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The optimistic effect of one queued command on the mobile tables (`local_overlays.patch`,
 * reducer version [REDUCER_VERSION]). A command can touch several rows (a work order and its
 * asset links, a stock movement and its lines), so a patch is a list of row operations:
 *
 * ```
 * {"ops":[{"table":"work_orders","key":{"id":"…"},"op":"upsert","set":{"title":"…","due_at":null}},
 *         {"table":"work_order_assets","key":{"work_order_id":"…","asset_id":"…"},"op":"delete"}]}
 * ```
 *
 * `upsert` sets columns over the base row (creating it when absent; `null` clears a column) and
 * adds `add` deltas to integer columns (stock quantities: the delta stays right when sync brings a
 * newer base row before the command retires); `delete` hides the row. Reads merge the base rows with every live overlay in `causal_order`
 * ([V2OverlayMerge]); a rejected command deletes its overlay, which is the rollback.
 */
data class V2OverlayPatch(val ops: List<V2RowOp>) {
    fun toJson(): JsonObject = JsonObject(mapOf("ops" to JsonArray(ops.map(V2RowOp::toJson))))

    companion object {
        const val REDUCER_VERSION = 1

        fun of(vararg ops: V2RowOp) = V2OverlayPatch(ops.toList())

        fun parse(json: JsonElement): V2OverlayPatch? = runCatching {
            V2OverlayPatch(json.jsonObject.getValue("ops").jsonArray.map { V2RowOp.parse(it.jsonObject) })
        }.getOrNull()
    }
}

data class V2RowOp(
    val table: String,
    val key: Map<String, Any?>,
    val kind: Kind,
    val set: Map<String, Any?> = emptyMap(),
    val add: Map<String, Long> = emptyMap(),
) {
    enum class Kind(val wire: String) { UPSERT("upsert"), DELETE("delete") }

    fun matches(row: V2Row): Boolean = key.all { (column, value) -> row[column] == value }

    fun toJson(): JsonObject = JsonObject(
        buildMap {
            put("table", JsonPrimitive(table))
            put("key", JsonObject(key.mapValues { toJsonValue(it.value) }))
            put("op", JsonPrimitive(kind.wire))
            if (kind == Kind.UPSERT) put("set", JsonObject(set.mapValues { toJsonValue(it.value) }))
            if (kind == Kind.UPSERT && add.isNotEmpty()) put("add", JsonObject(add.mapValues { JsonPrimitive(it.value) }))
        },
    )

    companion object {
        fun upsert(table: String, id: String, set: Map<String, Any?>) = V2RowOp(table, mapOf("id" to id), Kind.UPSERT, set)

        fun upsert(table: String, key: Map<String, Any?>, set: Map<String, Any?>, add: Map<String, Long> = emptyMap()) =
            V2RowOp(table, key, Kind.UPSERT, set, add)

        fun delete(table: String, id: String) = V2RowOp(table, mapOf("id" to id), Kind.DELETE)

        fun delete(table: String, key: Map<String, Any?>) = V2RowOp(table, key, Kind.DELETE)

        internal fun parse(o: JsonObject): V2RowOp = V2RowOp(
            table = o.getValue("table").jsonPrimitive.content,
            key = o.getValue("key").jsonObject.mapValues { fromJsonValue(it.value) },
            kind = Kind.entries.first { it.wire == o.getValue("op").jsonPrimitive.content },
            set = (o["set"] as? JsonObject)?.mapValues { fromJsonValue(it.value) } ?: emptyMap(),
            add = (o["add"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content.toLong() } ?: emptyMap(),
        )

        /** Row values are SQLite storage values: Long, Double, String or null (booleans are 0/1). */
        fun toJsonValue(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(if (value) 1L else 0L)
            is Int -> JsonPrimitive(value.toLong())
            is Long -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is java.math.BigDecimal -> JsonPrimitive(value.toPlainString())
            is String -> JsonPrimitive(value)
            else -> error("unsupported overlay value ${value::class.simpleName}")
        }

        fun fromJsonValue(element: JsonElement): Any? {
            if (element is JsonNull) return null
            val p = element.jsonPrimitive
            if (p.isString) return p.content
            p.booleanOrNull?.let { return if (it) 1L else 0L }
            p.longOrNull?.let { return it }
            return p.content.toDouble()
        }
    }
}

/**
 * Reads mobile tables with the live overlays applied. Base rows come from SQL (with an optional
 * WHERE for speed); rows an overlay touches are re-read by key, patched in `causal_order` and
 * re-checked with [predicate], so overlay-created or overlay-moved rows show up in filtered lists.
 */
object V2OverlayMerge {
    fun ops(conn: V2Connection, table: String): List<V2RowOp> =
        conn.query("SELECT patch FROM local_overlays ORDER BY causal_order, id").flatMap { row ->
            val patch = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(row.str("patch")) }.getOrNull()
            patch?.let(V2OverlayPatch::parse)?.ops.orEmpty().filter { it.table == table }
        }

    /**
     * @param where SQL condition over the base table (without `WHERE`), mirroring [predicate].
     * @param predicate the same filter in Kotlin, applied to overlay-touched rows.
     */
    fun query(
        conn: V2Connection,
        table: String,
        where: String? = null,
        args: List<Any?> = emptyList(),
        predicate: (V2Row) -> Boolean = { true },
    ): List<V2Row> {
        val sql = "SELECT * FROM $table" + (where?.let { " WHERE $it" } ?: "")
        val base = conn.query(sql, args)
        val ops = ops(conn, table)
        if (ops.isEmpty()) return base
        return apply(base, ops, predicate) { key ->
            conn.query(
                "SELECT * FROM $table WHERE " + key.keys.joinToString(" AND ") { "$it IS ?" },
                key.values.toList(),
            ).firstOrNull()
        }
    }

    /** Pure merge, exposed for tests. [lookup] fetches a base row by key regardless of the filter. */
    fun apply(
        base: List<V2Row>,
        ops: List<V2RowOp>,
        predicate: (V2Row) -> Boolean,
        lookup: (Map<String, Any?>) -> V2Row?,
    ): List<V2Row> {
        val result = base.map { it.toMutableMap() as MutableMap<String, Any?>? }.toMutableList()
        for (op in ops) {
            var index = result.indexOfFirst { it != null && op.matches(it) }
            if (index < 0) {
                val fetched = lookup(op.key)?.toMutableMap()
                    ?: if (op.kind == V2RowOp.Kind.UPSERT) op.key.toMutableMap() else null
                if (fetched == null) continue
                result += fetched
                index = result.lastIndex
            }
            when (op.kind) {
                V2RowOp.Kind.DELETE -> result[index] = null
                V2RowOp.Kind.UPSERT -> {
                    val row = result[index]!!
                    row.putAll(op.set)
                    for ((column, delta) in op.add) row[column] = ((row[column] as? Number)?.toLong() ?: 0L) + delta
                }
            }
        }
        val touched = ops.map { it.key }.toSet()
        return result.filterNotNull().filter { row -> touched.none { key -> key.all { row[it.key] == it.value } } || predicate(row) }
    }
}
