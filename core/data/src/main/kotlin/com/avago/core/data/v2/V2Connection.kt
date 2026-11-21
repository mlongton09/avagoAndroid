package com.avago.core.data.v2

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.Closeable

/** A SQLite error. [isForeignKey] lets the sync engine carry a page whose parent is on a later page. */
class V2SqlException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    val isForeignKey: Boolean get() = message.orEmpty().contains("FOREIGN KEY constraint failed")
    val isConstraint: Boolean
        get() = message.orEmpty().let {
            it.contains("constraint failed") || it.contains("SQLITE_CONSTRAINT") || it.contains("cannot store")
        }
}

/** One result row: column name → `null`, [Long], [Double], [String] or [ByteArray]. */
typealias V2Row = Map<String, Any?>

/**
 * Minimal synchronous SQLite seam for the v2 store. Production uses [BundledV2Connection]
 * (`androidx.sqlite:sqlite-bundled`, SQLite 3.53.4 on every API level, [V2SqliteRuntime]); JVM unit
 * tests use a JDBC adapter over the same generated DDL. Bind values: `null`, Long/Int, Double,
 * String, Boolean (0/1), ByteArray. Every failure is a [V2SqlException].
 */
interface V2Connection : Closeable {
    /** Runs one statement; returns `changes()`. */
    fun execute(sql: String, args: List<Any?> = emptyList()): Long

    fun query(sql: String, args: List<Any?> = emptyList()): List<V2Row>
}

fun V2Connection.scalarLong(sql: String, args: List<Any?> = emptyList()): Long? =
    query(sql, args).firstOrNull()?.values?.firstOrNull()?.let { (it as? Number)?.toLong() ?: it.toString().toLongOrNull() }

fun V2Connection.scalarText(sql: String, args: List<Any?> = emptyList()): String? =
    query(sql, args).firstOrNull()?.values?.firstOrNull()?.toString()

/** Runs a multi-statement script (the generated DDL) statement by statement. */
fun V2Connection.executeScript(script: String) {
    V2SqlScript.split(script).forEach { execute(it) }
}

object V2SqlScript {
    /** Splits on `;` outside quotes, dropping `--` comments. The generated DDL has no triggers. */
    fun split(script: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        var quote: Char? = null
        while (i < script.length) {
            val c = script[i]
            when {
                quote != null -> {
                    current.append(c)
                    if (c == quote) quote = null
                }
                c == '\'' || c == '"' -> {
                    quote = c
                    current.append(c)
                }
                c == '-' && i + 1 < script.length && script[i + 1] == '-' -> {
                    while (i < script.length && script[i] != '\n') i++
                    continue
                }
                c == ';' -> {
                    current.toString().trim().takeIf { it.isNotEmpty() }?.let(out::add)
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let(out::add)
        return out
    }

    fun quote(identifier: String): String = "\"" + identifier.replace("\"", "\"\"") + "\""
}

/**
 * The SQLite runtime floor of the v2 stores (plan 4.2, L16): STRICT tables need 3.37, and the
 * [WAL-reset fix](https://www.sqlite.org/wal.html#the_wal_reset_bug) ships in 3.51.3 with upstream
 * backports in 3.50.7 and 3.44.6. The app pins `androidx.sqlite:sqlite-bundled` (SQLite 3.53.4 on
 * every ABI and API level, never the framework build), and [check] refuses any other runtime.
 */
object V2SqliteRuntime {
    const val MIN_VERSION = "3.51.3"

    /** Upstream releases carrying the WAL-reset fix below [MIN_VERSION]: (first fixed, next minor). */
    private const val VERSION_PARTS = 3

    private val BACKPORTS = listOf("3.50.7" to "3.51.0", "3.44.6" to "3.45.0")

    fun hasWalResetFix(version: String): Boolean {
        val v = parse(version) ?: return false
        return compare(v, parse(MIN_VERSION)!!) >= 0 ||
            BACKPORTS.any { (fixed, next) -> compare(v, parse(fixed)!!) >= 0 && compare(v, parse(next)!!) < 0 }
    }

    /** Throws [V2SqlException] unless [connection] runs a SQLite with the WAL-reset fix. */
    fun check(connection: V2Connection) {
        val version = connection.scalarText("SELECT sqlite_version()").orEmpty()
        if (!hasWalResetFix(version)) {
            throw V2SqlException("SQLite $version lacks the WAL-reset fix (need >= $MIN_VERSION)")
        }
    }

    private fun parse(version: String): List<Int>? =
        version.split('.').takeIf { it.size == VERSION_PARTS }?.map { it.toIntOrNull() ?: return null }

    private fun compare(a: List<Int>, b: List<Int>): Int =
        a.zip(b).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
}

/** [V2Connection] over the bundled SQLite driver. Callers serialise access ([V2Database]). */
class BundledV2Connection(path: String) : V2Connection {
    private val connection: SQLiteConnection = wrap { BundledSQLiteDriver().open(path) }

    override fun execute(sql: String, args: List<Any?>): Long = wrap {
        connection.prepare(sql).use { statement ->
            bind(statement, args)
            @Suppress("ControlFlowWithEmptyBody")
            while (statement.step()) {
            }
        }
        connection.prepare("SELECT changes()").use { if (it.step()) it.getLong(0) else 0L }
    }

    override fun query(sql: String, args: List<Any?>): List<V2Row> = wrap {
        connection.prepare(sql).use { statement ->
            bind(statement, args)
            val rows = mutableListOf<V2Row>()
            val count = statement.getColumnCount()
            while (statement.step()) {
                val row = LinkedHashMap<String, Any?>(count)
                for (i in 0 until count) {
                    row[statement.getColumnName(i)] = when {
                        statement.isNull(i) -> null
                        else -> when (statement.getColumnType(i)) {
                            SQLITE_INTEGER -> statement.getLong(i)
                            SQLITE_FLOAT -> statement.getDouble(i)
                            SQLITE_BLOB -> statement.getBlob(i)
                            else -> statement.getText(i)
                        }
                    }
                }
                rows += row
            }
            rows
        }
    }

    override fun close() {
        runCatching { connection.close() }
    }

    private fun bind(statement: SQLiteStatement, args: List<Any?>) {
        args.forEachIndexed { index, value ->
            val i = index + 1
            when (value) {
                null -> statement.bindNull(i)
                is Long -> statement.bindLong(i, value)
                is Int -> statement.bindLong(i, value.toLong())
                is Boolean -> statement.bindLong(i, if (value) 1 else 0)
                is Double -> statement.bindDouble(i, value)
                is ByteArray -> statement.bindBlob(i, value)
                else -> statement.bindText(i, value.toString())
            }
        }
    }

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: V2SqlException) {
        throw e
    } catch (e: Exception) {
        throw V2SqlException(e.message ?: e.toString(), e)
    }

    private companion object {
        // sqlite3.h fundamental datatypes (SQLiteStatement.getColumnType).
        const val SQLITE_INTEGER = 1
        const val SQLITE_FLOAT = 2
        const val SQLITE_BLOB = 4
    }
}
