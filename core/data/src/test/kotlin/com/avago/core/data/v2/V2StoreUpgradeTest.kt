package com.avago.core.data.v2

import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * In-place upgrade of released stores (plan 6.4, L43): the frozen v1 DDL plus its fixture rows are
 * opened through [V2Database.open] and must come out at the current version with every kept row,
 * no dangling reference, the new DDL hash and the projection versions the sync engine expects.
 */
class V2StoreUpgradeTest {

    @TempDir
    lateinit var root: File

    private val identity = V2StoreIdentity(
        accountId = "00000002-0000-4000-8000-4e3eb86cf041",
        userId = "00000001-0000-4000-8000-4e3eb86cf041",
        deviceId = "00000003-0000-4000-8000-4e3eb86cf041",
    )

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResourceAsStream("/com/avago/core/data/v2/frozen/$path")) { "missing $path" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    /** A store exactly as a v1 client left it: v1 DDL, client meta, fixture rows, user_version 1. */
    private fun seedV1(conn: V2Connection, kind: V2StoreKind): V2Connection {
        val db = if (kind == V2StoreKind.BUSINESS) "business" else "chat"
        conn.execute("PRAGMA foreign_keys = ON")
        V2Database.transaction(conn) {
            conn.executeScript(resource("v1/$db.sql"))
            conn.execute(V2Database.META_DDL)
            V2Database.metaPut(conn, V2Database.META_DDL_SHA, "v1-ddl")
            if (kind == V2StoreKind.BUSINESS) {
                val v1Versions = V2GeneratedSchema.projections
                    .associate { it.entityType to it.schemaVersion }
                    .toSortedMap()
                V2Database.metaPut(
                    conn,
                    V2SyncEngine.META_PROJECTION_VERSIONS,
                    kotlinx.serialization.json.JsonObject(
                        v1Versions.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) },
                    ).toString(),
                )
            }
            conn.executeScript(resource("v1/fixtures/$db.sql"))
        }
        conn.execute("PRAGMA user_version = 1")
        return conn
    }

    private fun tables(conn: V2Connection): Set<String> =
        conn.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")
            .map { it["name"] as String }.toSet()

    private fun columns(conn: V2Connection, table: String): List<String> =
        conn.query("PRAGMA table_info(${V2SqlScript.quote(table)})")
            .map { "${it["name"]} ${it["type"]} ${it["notnull"]} ${it["pk"]}" }

    /** Every row in a stable order (several tables are WITHOUT ROWID). */
    private fun rows(conn: V2Connection, table: String): List<V2Row> =
        conn.query("SELECT * FROM ${V2SqlScript.quote(table)}").sortedBy { it.toString() }

    private fun open(conn: V2Connection, kind: V2StoreKind) =
        V2Database.open(conn, kind, identity, nowMillis = 2_000, dispatcher = Dispatchers.Unconfined)

    private fun assertUpgraded(kind: V2StoreKind) {
        val conn = seedV1(JdbcV2Connection(), kind)
        val before = tables(conn).associateWith { rows(conn, it) }
        val dropped = V2GeneratedSchema.migrationSteps.filter { it.store == kind }.flatMap { it.dropped }.toSet()
        assertTrue(before.keys.containsAll(dropped))

        open(conn, kind)

        assertEquals(2L, conn.scalarLong("PRAGMA user_version"))
        assertEquals("ok", conn.scalarText("PRAGMA integrity_check"))
        assertTrue(conn.query("PRAGMA foreign_key_check").isEmpty())
        assertEquals(1L, conn.scalarLong("PRAGMA foreign_keys"))
        val business = kind == V2StoreKind.BUSINESS
        val expectedSha = if (business) V2GeneratedSchema.BUSINESS_FILE_SHA256 else V2GeneratedSchema.CHAT_FILE_SHA256
        assertEquals(expectedSha, V2Database.metaGet(conn, V2Database.META_DDL_SHA))

        // Same tables and columns as a store created fresh at the current version.
        val fresh = JdbcV2Connection().also { open(it, kind) }
        val upgraded = tables(conn)
        assertEquals(tables(fresh), upgraded)
        upgraded.forEach { assertEquals(columns(fresh, it), columns(conn, it), "columns of $it") }
        dropped.forEach { assertFalse(it in upgraded, "$it should be dropped") }

        // Every kept row survives; tables whose shape did not change keep identical rows.
        for ((table, old) in before) {
            if (table in dropped) continue
            val now = rows(conn, table)
            assertEquals(old.size, now.size, "row count of $table")
            if (table != "v2_client_meta" && now.firstOrNull()?.keys == old.firstOrNull()?.keys) {
                assertEquals(old, now, "rows of $table")
            }
        }
        val durable = if (business) V2GeneratedSchema.businessDurableTables else V2GeneratedSchema.chatDurableTables
        durable.filter { it in before }.forEach {
            assertTrue(rows(conn, it).isNotEmpty(), "durable $it keeps its fixture rows")
        }
    }

    @Test
    fun `v1 business store upgrades in place with every kept row`() {
        assertUpgraded(V2StoreKind.BUSINESS)
    }

    @Test
    fun `v1 chat store upgrades in place and gains the outbox`() {
        assertUpgraded(V2StoreKind.CHAT)
        val conn = seedV1(JdbcV2Connection(), V2StoreKind.CHAT)
        assertFalse("outbox" in tables(conn))
        open(conn, V2StoreKind.CHAT)
        assertTrue("outbox" in tables(conn))
    }

    @Test
    fun `upgrade keeps the bootstrap - projection versions match what sync expects`() {
        val conn = seedV1(JdbcV2Connection(), V2StoreKind.BUSINESS)
        open(conn, V2StoreKind.BUSINESS)
        val versions = V2SyncEngine.parseVersions(V2Database.metaGet(conn, V2SyncEngine.META_PROJECTION_VERSIONS)!!)
        val expected = V2GeneratedSchema.projections
            .filter { it.mobileTable != null }
            .associate { it.entityType to it.schemaVersion }
        assertEquals(expected, versions)
        assertTrue(V2GeneratedSchema.projections.any { it.mobileTable == null && it.entityType !in versions })
    }

    @Test
    fun `reopening an upgraded store keeps its rows`() {
        val file = File(root, "business.sqlite")
        seedV1(JdbcV2Connection("jdbc:sqlite:${file.path}"), V2StoreKind.BUSINESS).close()
        open(JdbcV2Connection("jdbc:sqlite:${file.path}"), V2StoreKind.BUSINESS).close()
        open(JdbcV2Connection("jdbc:sqlite:${file.path}"), V2StoreKind.BUSINESS).close()
        JdbcV2Connection("jdbc:sqlite:${file.path}").use { conn ->
            assertEquals(2L, conn.scalarLong("PRAGMA user_version"))
            assertTrue(V2GeneratedSchema.businessDurableTables.any { rows(conn, it).isNotEmpty() })
        }
    }

    @Test
    fun `a store from a newer build is refused and left untouched`() {
        val file = File(root, "newer.sqlite")
        seedV1(JdbcV2Connection("jdbc:sqlite:${file.path}"), V2StoreKind.CHAT).apply {
            execute("PRAGMA user_version = ${V2Contract.V2_DB_SCHEMA_VERSION + 1}")
            close()
        }
        assertThrows<V2StoreTooNewException> { open(JdbcV2Connection("jdbc:sqlite:${file.path}"), V2StoreKind.CHAT) }
        JdbcV2Connection("jdbc:sqlite:${file.path}").use { conn ->
            assertEquals((V2Contract.V2_DB_SCHEMA_VERSION + 1).toLong(), conn.scalarLong("PRAGMA user_version"))
            assertTrue(rows(conn, "messages").isNotEmpty())
        }
    }

    @Test
    fun `an upgrade that would leave a dangling reference rolls back`() {
        val file = File(root, "dangling.sqlite")
        val seeded = seedV1(JdbcV2Connection("jdbc:sqlite:${file.path}"), V2StoreKind.CHAT)
        seeded.execute("PRAGMA foreign_keys = OFF")
        seeded.execute(
            "INSERT INTO messages (id, created_at, updated_at, thread_id, body_md) VALUES (?, 1, 1, ?, 'x')",
            listOf("0000aaaa-0000-4000-8000-000000000001", "0000aaaa-0000-4000-8000-0000000000ff"),
        )
        val before = tables(seeded).associateWith { rows(seeded, it) }
        seeded.close()

        assertThrows<V2MigrationException> { open(JdbcV2Connection("jdbc:sqlite:${file.path}"), V2StoreKind.CHAT) }

        JdbcV2Connection("jdbc:sqlite:${file.path}").use { conn ->
            assertEquals(1L, conn.scalarLong("PRAGMA user_version"))
            assertEquals(before, tables(conn).associateWith { rows(conn, it) })
        }
    }

    @Test
    fun `store files - the old chat file is renamed and the device-wide chat and rentalops files are deleted`() {
        val files = File(root, "files")
        val v2 = V2StorePaths.v2Directory(files, identity.accountId, identity.userId).apply { mkdirs() }
        File(v2, V2StorePaths.V2_CHAT_DB).writeText("chat")
        File(v2, V2StorePaths.V2_CHAT_DB + "-wal").writeText("wal")
        File(v2, V2StorePaths.V2_BUSINESS_DB).writeText("business")
        val account = V2StorePaths.accountDirectory(files, identity.accountId)
        for (db in V2StorePaths.RETIRED_ACCOUNT_DATABASES) {
            V2StorePaths.SQLITE_SIDECARS.forEach { File(account, db + it).writeText("x") }
        }

        val chat = V2StorePaths.prepareStoreFiles(files, identity.accountId, identity.userId)

        assertEquals("chat-${identity.userId}-${identity.accountId}.sqlite", chat.name)
        assertEquals(v2, chat.parentFile)
        assertEquals("chat", chat.readText())
        assertEquals("wal", File(chat.path + "-wal").readText())
        assertFalse(File(v2, V2StorePaths.V2_CHAT_DB).exists())
        assertEquals("business", File(v2, V2StorePaths.V2_BUSINESS_DB).readText())
        assertTrue(account.listFiles { f -> f.isFile }.isNullOrEmpty())

        // Idempotent; an existing new-name file is never overwritten.
        File(v2, V2StorePaths.V2_CHAT_DB).writeText("stale")
        assertEquals(chat, V2StorePaths.prepareStoreFiles(files, identity.accountId, identity.userId))
        assertEquals("chat", chat.readText())
    }
}
