package com.avago.core.data.v2

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Who owns a v2 store file (`database_identity`, plan §14.4). */
data class V2StoreIdentity(val accountId: String, val userId: String, val deviceId: String) {
    init {
        require(V2Uuid.isValid(accountId) && V2Uuid.isValid(userId) && V2Uuid.isValid(deviceId)) {
            "v2 ids must be lowercase UUID strings"
        }
    }
}

/** The store file was built from a different contract DDL; the caller deletes and recreates it. */
class V2SchemaMismatchException(message: String) : IllegalStateException(message)

/**
 * The store was written by a newer app build (`user_version` above [V2Contract.V2_DB_SCHEMA_VERSION]).
 * It is refused, never deleted: its outbox may hold writes only the newer build can send.
 */
class V2StoreTooNewException(message: String) : IllegalStateException(message)

/** An in-place upgrade (plan 6.4) failed and was rolled back; the store is left untouched. */
class V2MigrationException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Inside [V2Database.write]: the connection plus change tracking for observers. */
class V2Transaction internal constructor(val connection: V2Connection) {
    internal val touched = mutableSetOf<String>()

    fun markChanged(vararg tables: String) {
        touched += tables
    }

    fun execute(sql: String, args: List<Any?> = emptyList()): Long = connection.execute(sql, args)
    fun query(sql: String, args: List<Any?> = emptyList()): List<V2Row> = connection.query(sql, args)
}

/**
 * One v2 SQLite database (business or chat) created from the generated contract DDL
 * (decision L29: the same file on iOS and Android): `foreign_keys=ON`, WAL, the whole DDL in one
 * transaction, then `user_version` = [V2Contract.V2_DB_SCHEMA_VERSION]. A store at an older
 * released version is upgraded in place by the embedded `migrations/<db>/vNNN_to_vMMM.sql` steps
 * (plan 6.4, L43); it is never deleted to upgrade it.
 *
 * One connection, serialised by a mutex; writes use `BEGIN IMMEDIATE`. [changes] emits the tables
 * each committed write touched, which drives [observe]. Client bookkeeping (sync chain, staged
 * positions, projection versions) lives in the client-only `v2_client_meta` table.
 */
class V2Database private constructor(
    private val connection: V2Connection,
    val kind: V2StoreKind,
    private val dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val mutex = Mutex()
    private val _changes = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64)
    val changes: SharedFlow<Set<String>> = _changes.asSharedFlow()

    suspend fun <T> read(block: (V2Connection) -> T): T = mutex.withLock {
        withContext(dispatcher) { block(connection) }
    }

    /** One `BEGIN IMMEDIATE` transaction; rolls back on any throwable (including a failing COMMIT). */
    suspend fun <T> write(block: (V2Transaction) -> T): T = mutex.withLock {
        withContext(dispatcher) {
            val tx = V2Transaction(connection)
            val result = transaction(connection) { block(tx) }
            if (tx.touched.isNotEmpty()) _changes.tryEmit(tx.touched.toSet())
            result
        }
    }

    /**
     * Runs [block] with `foreign_keys=OFF` in one transaction (the bootstrap wipe; the pragma is a
     * no-op inside a transaction, so it is switched around it).
     */
    suspend fun <T> writeWithoutForeignKeys(block: (V2Transaction) -> T): T = mutex.withLock {
        withContext(dispatcher) {
            connection.execute("PRAGMA foreign_keys = OFF")
            try {
                val tx = V2Transaction(connection)
                val result = transaction(connection) { block(tx) }
                if (tx.touched.isNotEmpty()) _changes.tryEmit(tx.touched.toSet())
                result
            } finally {
                connection.execute("PRAGMA foreign_keys = ON")
            }
        }
    }

    /** Re-runs [query] whenever a write touches one of [tables]. */
    fun <T> observe(tables: Set<String>, query: (V2Connection) -> T): Flow<T> =
        changes.filter { changed -> changed.any { it in tables } }
            .map { }
            .onStart { emit(Unit) }
            .map { read(query) }

    suspend fun meta(key: String): String? = read { metaGet(it, key) }

    override fun close() {
        connection.close()
    }

    companion object {
        const val META_TABLE = "v2_client_meta"
        internal const val META_DDL =
            "CREATE TABLE IF NOT EXISTS v2_client_meta (key TEXT NOT NULL PRIMARY KEY, value TEXT) STRICT"
        internal const val META_DDL_SHA = "ddl_sha256"

        fun metaGet(conn: V2Connection, key: String): String? =
            conn.scalarText("SELECT value FROM v2_client_meta WHERE key = ?", listOf(key))

        fun metaPut(conn: V2Connection, key: String, value: String?) {
            if (value == null) {
                conn.execute("DELETE FROM v2_client_meta WHERE key = ?", listOf(key))
            } else {
                conn.execute(
                    "INSERT INTO v2_client_meta (key, value) VALUES (?, ?) " +
                        "ON CONFLICT (key) DO UPDATE SET value = excluded.value",
                    listOf(key, value),
                )
            }
        }

        fun ddl(kind: V2StoreKind): String {
            val resource = when (kind) {
                V2StoreKind.BUSINESS -> V2GeneratedSchema.BUSINESS_SQL_RESOURCE
                V2StoreKind.CHAT -> V2GeneratedSchema.CHAT_SQL_RESOURCE
            }
            val bytes = V2Database::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
                ?: error("missing generated DDL resource $resource")
            val expected = when (kind) {
                V2StoreKind.BUSINESS -> V2GeneratedSchema.BUSINESS_FILE_SHA256
                V2StoreKind.CHAT -> V2GeneratedSchema.CHAT_FILE_SHA256
            }
            check(V2Canonical.sha256Hex(bytes) == expected) {
                "generated DDL resource $resource does not match V2GeneratedSchema (rerun scripts/gen_v2_contract.py)"
            }
            return bytes.toString(Charsets.UTF_8)
        }

        /**
         * Opens (creating on first use) a store on [connection]. A store at an older released
         * `user_version` is upgraded in place ([upgrade]); a same-version DDL drift is rebuilt
         * ([migrate]). Throws [V2SchemaMismatchException] when the file belongs to another
         * user/account, [V2StoreTooNewException] for a newer store and [V2MigrationException] when
         * an upgrade fails (rolled back).
         */
        fun open(
            connection: V2Connection,
            kind: V2StoreKind,
            identity: V2StoreIdentity,
            nowMillis: Long = System.currentTimeMillis(),
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): V2Database {
            val expectedSha = when (kind) {
                V2StoreKind.BUSINESS -> V2GeneratedSchema.BUSINESS_FILE_SHA256
                V2StoreKind.CHAT -> V2GeneratedSchema.CHAT_FILE_SHA256
            }
            try {
                V2SqliteRuntime.check(connection)
                connection.execute("PRAGMA foreign_keys = ON")
                connection.query("PRAGMA journal_mode = WAL")
                connection.query("PRAGMA busy_timeout = 5000")
                val userVersion = connection.scalarLong("PRAGMA user_version") ?: 0L
                val current = V2Contract.V2_DB_SCHEMA_VERSION.toLong()
                if (userVersion == 0L) {
                    val script = ddl(kind)
                    transaction(connection) {
                        connection.executeScript(script)
                        connection.execute(META_DDL)
                        metaPut(connection, META_DDL_SHA, expectedSha)
                        insertIdentity(connection, kind, identity, nowMillis)
                    }
                    connection.execute("PRAGMA user_version = ${V2Contract.V2_DB_SCHEMA_VERSION}")
                } else {
                    if (userVersion > current) {
                        throw V2StoreTooNewException("v2 $kind store has user_version $userVersion > $current")
                    }
                    val row = connection.query("SELECT user_id, account_id FROM database_identity").firstOrNull()
                    if (row == null || row["user_id"] != identity.userId || row["account_id"] != identity.accountId) {
                        throw V2SchemaMismatchException("v2 $kind store belongs to another user/account")
                    }
                    if (userVersion < current) {
                        upgrade(connection, kind, userVersion.toInt(), expectedSha)
                    } else {
                        val stored = runCatching { metaGet(connection, META_DDL_SHA) }.getOrNull()
                        if (stored != expectedSha) migrate(connection, kind, expectedSha)
                    }
                }
            } catch (e: Exception) {
                connection.close()
                throw e
            }
            return V2Database(connection, kind, dispatcher)
        }

        /**
         * In-place upgrade of a store at the released version [from] (plan 6.4, L43): with
         * `foreign_keys=OFF`, one `BEGIN IMMEDIATE` transaction runs every embedded step up to the
         * current version, then `PRAGMA foreign_key_check` must be empty (else ROLLBACK). Synced rows,
         * the outbox and every durable table are kept. The new DDL hash and the projection versions
         * minus the retired projections are written to meta, so neither [migrate] nor a
         * re-bootstrap follows.
         */
        private fun upgrade(conn: V2Connection, kind: V2StoreKind, from: Int, expectedSha: String) {
            val target = V2Contract.V2_DB_SCHEMA_VERSION
            val scripts = mutableListOf<String>()
            var version = from
            while (version < target) {
                val step = V2GeneratedSchema.migrationSteps.firstOrNull { it.store == kind && it.from == version }
                    ?: throw V2MigrationException("v2 $kind store: no upgrade step from user_version $version")
                scripts += migrationScript(step)
                version = step.to
            }
            conn.execute("PRAGMA foreign_keys = OFF")
            try {
                transaction(conn) {
                    try {
                        scripts.forEach { conn.executeScript(it) }
                    } catch (e: V2SqlException) {
                        throw V2MigrationException("v2 $kind store upgrade from $from failed: ${e.message}", e)
                    }
                    val violations = conn.query("PRAGMA foreign_key_check")
                    if (violations.isNotEmpty()) {
                        throw V2MigrationException(
                            "v2 $kind store upgrade from $from left ${violations.size} dangling references",
                        )
                    }
                    val reached = conn.scalarLong("PRAGMA user_version")
                    if (reached != target.toLong()) {
                        throw V2MigrationException("v2 $kind store upgrade from $from ended at user_version $reached")
                    }
                    conn.execute(META_DDL)
                    metaPut(conn, META_DDL_SHA, expectedSha)
                    if (kind == V2StoreKind.BUSINESS) {
                        metaGet(conn, V2SyncEngine.META_PROJECTION_VERSIONS)?.let { stored ->
                            metaPut(conn, V2SyncEngine.META_PROJECTION_VERSIONS, keptProjectionVersions(stored))
                        }
                    }
                }
            } finally {
                conn.execute("PRAGMA foreign_keys = ON")
            }
        }

        /**
         * The stored projection versions without the projections that no longer have a mobile
         * table, i.e. what [V2SyncEngine] computes as its desired versions after the upgrade.
         */
        internal fun keptProjectionVersions(stored: String): String {
            val kept = V2SyncEngine.parseVersions(stored).filterKeys { name ->
                V2GeneratedSchema.projectionsByType[name]?.mobileTable != null
            }.toSortedMap()
            return JsonObject(kept.mapValues { JsonPrimitive(it.value) }).toString()
        }

        private fun migrationScript(step: V2MigrationStep): String {
            val bytes = V2Database::class.java.getResourceAsStream(step.resource)?.use { it.readBytes() }
                ?: error("missing migration resource ${step.resource}")
            check(V2Canonical.sha256Hex(bytes) == step.sha256) {
                "migration resource ${step.resource} does not match V2GeneratedSchema " +
                    "(rerun scripts/gen_v2_contract.py)"
            }
            return bytes.toString(Charsets.UTF_8)
        }

        /**
         * Store rebuild for a DDL change within one schema version (pre-release contract drift; a
         * released version never changes, L43). Projection rows are disposable,
         * so every table is rebuilt from the new DDL and sync restarts from a bootstrap; the identity
         * (device id, scope epoch), device registration and the outbox (pending commands, their
         * dependencies, overlays and upload jobs) are carried over so no local write is lost.
         */
        private fun migrate(conn: V2Connection, kind: V2StoreKind, expectedSha: String) {
            val script = ddl(kind)
            val carried = CARRIED_TABLES.getValue(kind).associateWith { table ->
                if (conn.scalarLong("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?", listOf(table)) == 0L) {
                    emptyList()
                } else {
                    conn.query("SELECT * FROM ${V2SqlScript.quote(table)}")
                }
            }
            val carriedMeta = CARRIED_META.associateWith { runCatching { metaGet(conn, it) }.getOrNull() }
            conn.execute("PRAGMA foreign_keys = OFF")
            try {
                transaction(conn) {
                    val tables = conn.query(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                    ).map { it.getValue("name") as String }
                    for (table in tables) conn.execute("DROP TABLE IF EXISTS ${V2SqlScript.quote(table)}")
                    conn.executeScript(script)
                    conn.execute(META_DDL)
                    metaPut(conn, META_DDL_SHA, expectedSha)
                    carriedMeta.forEach { (key, value) -> metaPut(conn, key, value) }
                    for ((table, rows) in carried) reinsert(conn, table, rows)
                    if (kind == V2StoreKind.BUSINESS) {
                        conn.execute(
                            "UPDATE database_identity SET contract_version = ?, feed_generation = 0, global_feed_generation = 0",
                            listOf(V2Contract.CLIENT_CONTRACT_VERSION),
                        )
                        for (stream in V2Stream.entries) {
                            conn.execute("INSERT INTO sync_state (stream) VALUES (?)", listOf(stream.wire))
                        }
                    }
                    val violations = conn.query("PRAGMA foreign_key_check")
                    check(violations.isEmpty()) { "v2 $kind store migration left ${violations.size} dangling references" }
                }
            } finally {
                conn.execute("PRAGMA foreign_keys = ON")
            }
        }

        /** Re-inserts [rows] into the rebuilt [table], keeping the columns both DDLs share. */
        private fun reinsert(conn: V2Connection, table: String, rows: List<Map<String, Any?>>) {
            if (rows.isEmpty()) return
            val columns = conn.query("PRAGMA table_info(${V2SqlScript.quote(table)})").map { it.getValue("name") as String }.toSet()
            for (row in rows) {
                val shared = row.filterKeys { it in columns }
                conn.execute(
                    "INSERT INTO ${V2SqlScript.quote(table)} (${shared.keys.joinToString { V2SqlScript.quote(it) }}) " +
                        "VALUES (${shared.keys.joinToString { "?" }})",
                    shared.values.toList(),
                )
            }
        }

        /** Parent tables first so the carried rows satisfy their foreign keys in order. */
        private val CARRIED_TABLES = mapOf(
            V2StoreKind.BUSINESS to listOf(
                "database_identity", "pending_commands", "command_dependencies", "local_overlays", "upload_jobs",
            ),
            V2StoreKind.CHAT to listOf("database_identity", "outbox"),
        )
        private val CARRIED_META = listOf(V2SyncEngine.META_DEVICE_REGISTERED)

        private fun insertIdentity(conn: V2Connection, kind: V2StoreKind, identity: V2StoreIdentity, now: Long) {
            when (kind) {
                V2StoreKind.BUSINESS -> {
                    conn.execute(
                        "INSERT INTO database_identity (user_id, account_id, device_id, contract_version, " +
                            "scope_epoch, feed_generation, global_feed_generation, created_at) VALUES (?, ?, ?, ?, 0, 0, 0, ?)",
                        listOf(identity.userId, identity.accountId, identity.deviceId, V2Contract.CLIENT_CONTRACT_VERSION, now),
                    )
                    for (stream in V2Stream.entries) {
                        conn.execute("INSERT INTO sync_state (stream) VALUES (?)", listOf(stream.wire))
                    }
                }
                V2StoreKind.CHAT -> conn.execute(
                    "INSERT INTO database_identity (user_id, account_id, contract_version, created_at) VALUES (?, ?, ?, ?)",
                    listOf(identity.userId, identity.accountId, V2Contract.CLIENT_CONTRACT_VERSION, now),
                )
            }
        }

        internal fun <T> transaction(connection: V2Connection, block: () -> T): T {
            connection.execute("BEGIN IMMEDIATE")
            val result = try {
                block().also { connection.execute("COMMIT") }
            } catch (t: Throwable) {
                runCatching { connection.execute("ROLLBACK") }
                throw t
            }
            return result
        }
    }
}
