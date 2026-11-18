package com.avago.core.data.v2

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Types

/** JVM test adapter: the same generated DDL on xerial sqlite-jdbc. */
class JdbcV2Connection(url: String = "jdbc:sqlite::memory:") : V2Connection {
    private val conn: Connection = DriverManager.getConnection(url)

    override fun execute(sql: String, args: List<Any?>): Long = wrap {
        val trimmed = sql.trim()
        if (trimmed.startsWith("PRAGMA", ignoreCase = true) && args.isEmpty()) {
            conn.createStatement().use { it.execute(trimmed) }
            return@wrap 0L
        }
        conn.prepareStatement(sql).use { st ->
            bind(st, args)
            val hasResult = st.execute()
            if (hasResult) 0L else st.updateCount.toLong().coerceAtLeast(0)
        }
    }

    override fun query(sql: String, args: List<Any?>): List<V2Row> = wrap {
        conn.prepareStatement(sql).use { st ->
            bind(st, args)
            if (!st.execute()) return@wrap emptyList()
            st.resultSet.use { rs ->
                val md = rs.metaData
                val rows = mutableListOf<V2Row>()
                while (rs.next()) {
                    val row = LinkedHashMap<String, Any?>()
                    for (i in 1..md.columnCount) {
                        val v = rs.getObject(i)
                        row[md.getColumnLabel(i)] = when (v) {
                            null -> null
                            is Int -> v.toLong()
                            is Long -> v
                            is Short -> v.toLong()
                            is Float -> v.toDouble()
                            is Double -> if (md.getColumnType(i) == Types.INTEGER) v.toLong() else v
                            is String -> v
                            is ByteArray -> v
                            else -> v.toString()
                        }
                    }
                    rows += row
                }
                rows
            }
        }
    }

    override fun close() = conn.close()

    private fun bind(st: java.sql.PreparedStatement, args: List<Any?>) {
        args.forEachIndexed { i, a ->
            val idx = i + 1
            when (a) {
                null -> st.setNull(idx, Types.NULL)
                is Boolean -> st.setLong(idx, if (a) 1 else 0)
                is Int -> st.setLong(idx, a.toLong())
                is Long -> st.setLong(idx, a)
                is Double -> st.setDouble(idx, a)
                is Float -> st.setDouble(idx, a.toDouble())
                is String -> st.setString(idx, a)
                is ByteArray -> st.setBytes(idx, a)
                else -> st.setString(idx, a.toString())
            }
        }
    }

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: SQLException) {
        throw V2SqlException(e.message ?: "sqlite error", e)
    }
}
