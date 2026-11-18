package com.avago.core.data.v2

import kotlinx.serialization.json.JsonObject

/**
 * A projection item that cannot be written. The page fails as a whole (sync-wire.md: apply every
 * page in one transaction); nothing is skipped silently.
 */
class V2ApplyException(
    val entityType: String,
    val entityKey: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("$entityType[$entityKey]: $message", cause) {
    /** True when the only problem is a foreign key (a parent may arrive on a later page). */
    val isForeignKey: Boolean get() = (cause as? V2SqlException)?.isForeignKey == true
}

/**
 * Writes projection items into the generated mobile tables (sync-protocol §7.3). Column lists come
 * from `PRAGMA table_info`, so the applier follows the generated DDL without hand-written mappings
 * (port of the iOS `V2ProjectionApplier`, but every unmappable item throws instead of being skipped).
 *
 * Wire body → column rules (server loaders, sync-wire.md):
 * - a body key matching a column is converted by the column's declared type ([V2WireValue]);
 * - a `<name>_micros` column takes the decimal body key `<name>`;
 * - body keys without a column (e.g. `account_id`) are ignored;
 * - a nullable column missing from the body becomes NULL ("replace the row with body"); a NOT NULL
 *   one keeps its default/current value;
 * - primary-key columns missing from the body are filled from `entity_key`.
 */
class V2ProjectionApplier {
    data class Column(val name: String, val type: String, val notNull: Boolean, val primaryKeyIndex: Int)

    data class TableInfo(val name: String, val columns: List<Column>) {
        val primaryKey: List<String> = columns.filter { it.primaryKeyIndex > 0 }.sortedBy { it.primaryKeyIndex }.map { it.name }
        val hasVersion: Boolean = columns.any { it.name == "version" }
    }

    private val cache = mutableMapOf<String, TableInfo>()

    fun tableInfo(conn: V2Connection, table: String): TableInfo? = cache[table] ?: run {
        val rows = conn.query("PRAGMA table_info(${V2SqlScript.quote(table)})")
        if (rows.isEmpty()) return null
        TableInfo(
            table,
            rows.map {
                Column(
                    name = it["name"].toString(),
                    type = it["type"]?.toString().orEmpty(),
                    notNull = (it["notnull"] as? Number)?.toLong() != 0L,
                    primaryKeyIndex = (it["pk"] as? Number)?.toInt() ?: 0,
                )
            },
        ).also { cache[table] = it }
    }

    /** `INSERT … ON CONFLICT(pk) DO UPDATE … WHERE excluded.version >= version` (never `OR REPLACE`). */
    fun upsert(conn: V2Connection, projection: V2Projection, key: String, body: JsonObject) {
        val info = info(conn, projection, key)
        val table = info.name
        val keyValues = keyColumnValues(info, projection, key)
        val names = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        for (column in info.columns) {
            val wire = body[column.name]
                ?: if (column.name.endsWith(MICROS)) body[column.name.removeSuffix(MICROS)] else null
            when {
                wire != null -> {
                    val value = V2WireValue.sqlValue(wire, column.type, column.name)
                        ?: throw V2ApplyException(projection.entityType, key, "cannot convert $table.${column.name}")
                    names += column.name
                    values += value.value
                }
                keyValues.containsKey(column.name) -> {
                    names += column.name
                    values += keyValue(keyValues.getValue(column.name), column)
                }
                !column.notNull -> {
                    names += column.name
                    values += null
                }
            }
        }
        if (names.isEmpty()) throw V2ApplyException(projection.entityType, key, "empty body for $table")
        val q = V2SqlScript::quote
        val quotedTable = q(table)
        var sql = "INSERT INTO $quotedTable (${names.joinToString(", ") { q(it) }}) VALUES (${names.joinToString(", ") { "?" }})"
        val pk = info.primaryKey
        run(projection, key) {
            if (pk.isEmpty()) {
                // Singleton projection (one row per account).
                conn.execute("DELETE FROM $quotedTable")
                conn.execute(sql, values)
                return@run
            }
            val updates = names.filter { it !in pk }
            sql += if (updates.isEmpty()) {
                " ON CONFLICT (${pk.joinToString(", ") { q(it) }}) DO NOTHING"
            } else {
                " ON CONFLICT (${pk.joinToString(", ") { q(it) }}) DO UPDATE SET " +
                    updates.joinToString(", ") { "${q(it)} = excluded.${q(it)}" } +
                    if (info.hasVersion && "version" in names) " WHERE excluded.\"version\" >= $quotedTable.\"version\"" else ""
            }
            conn.execute(sql, values)
        }
    }

    /** Removes the row by `entity_key`; deleting an unknown key is a no-op. */
    fun delete(conn: V2Connection, projection: V2Projection, key: String) {
        val info = info(conn, projection, key)
        val quotedTable = V2SqlScript.quote(info.name)
        if (info.primaryKey.isEmpty()) {
            run(projection, key) { conn.execute("DELETE FROM $quotedTable") }
            return
        }
        val keyValues = keyColumnValues(info, projection, key)
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        for (column in info.columns.filter { it.primaryKeyIndex > 0 }) {
            val value = keyValues[column.name] ?: throw V2ApplyException(projection.entityType, key, "bad key")
            clauses += "${V2SqlScript.quote(column.name)} = ?"
            args += keyValue(value, column)
        }
        run(projection, key) { conn.execute("DELETE FROM $quotedTable WHERE ${clauses.joinToString(" AND ")}", args) }
    }

    /**
     * Maps `entity_key` parts onto the mobile primary key. A per-user key may carry a leading
     * `<user_id>` part the mobile table does not have (`effective_permissions`).
     */
    fun keyColumnValues(info: TableInfo, projection: V2Projection, key: String): Map<String, String> {
        val pk = info.primaryKey
        if (pk.isEmpty()) return emptyMap()
        var parts = V2EntityKey.split(key)
        if (parts.size == pk.size + 1 && projection.perUser) parts = parts.drop(1)
        if (parts.size != pk.size) throw V2ApplyException(projection.entityType, key, "key does not match ${info.name} primary key")
        var serverOrder = projection.keyColumns
        if (serverOrder.size == pk.size + 1 && projection.perUser) serverOrder = serverOrder.drop(1)
        val order = if (serverOrder.toSet() == pk.toSet()) serverOrder else pk
        return order.zip(parts).toMap()
    }

    private fun info(conn: V2Connection, projection: V2Projection, key: String): TableInfo {
        val table = projection.mobileTable ?: throw V2ApplyException(projection.entityType, key, "server-only projection")
        return tableInfo(conn, table)
            ?: throw V2ApplyException(projection.entityType, key, "mobile table $table missing from the store")
    }

    private inline fun run(projection: V2Projection, key: String, block: () -> Unit) {
        try {
            block()
        } catch (e: V2SqlException) {
            throw V2ApplyException(projection.entityType, key, e.message.orEmpty(), e)
        }
    }

    /** Key parts are text; INTEGER key columns (e.g. `ordinal`) bind as integers. */
    private fun keyValue(part: String, column: Column): Any? =
        V2WireValue.sqlValue(kotlinx.serialization.json.JsonPrimitive(part), column.type, column.name)?.value ?: part

    private companion object {
        const val MICROS = "_micros"
    }
}
