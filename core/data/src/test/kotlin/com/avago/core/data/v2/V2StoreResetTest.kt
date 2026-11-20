package com.avago.core.data.v2

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The v2 store is a disposable cache (L12): a mismatching file is detected so it can be rebuilt. */
class V2StoreResetTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, V2StorePaths.V2_BUSINESS_DB)
    private fun conn() = JdbcV2Connection("jdbc:sqlite:${file.absolutePath}")

    @Test
    fun `a store reopens for the same user and is refused for another`() = runTest {
        val db = V2Fixtures.business(conn())
        db.write { it.execute("UPDATE sync_state SET cursor = 7") }
        db.close()

        val again = V2Fixtures.business(conn())
        assertEquals(7L, again.read { it.scalarLong("SELECT cursor FROM sync_state WHERE stream = 'account'") })
        again.close()

        val other = V2StoreIdentity(V2Fixtures.ACCOUNT, V2Uuid.v7(), V2Fixtures.DEVICE)
        assertThrows(V2SchemaMismatchException::class.java) { V2Fixtures.business(conn(), other) }

        V2StoreFactory.deleteDatabase(file)
        assertFalse(file.exists())
        val fresh = V2Fixtures.business(conn(), other)
        assertEquals(0L, fresh.read { it.scalarLong("SELECT cursor FROM sync_state WHERE stream = 'account'") })
        assertEquals("none", fresh.read { it.scalarText("SELECT bootstrap_state FROM sync_state WHERE stream = 'account'") })
        fresh.close()
    }

    @Test
    fun `a store built from another contract DDL migrates and keeps the outbox`() = runTest {
        val db = V2Fixtures.business(conn())
        val command = V2Uuid.v7()
        db.write { tx ->
            val c = tx.connection
            c.execute("UPDATE sync_state SET cursor = 7, bootstrap_state = 'complete'")
            c.execute("UPDATE database_identity SET scope_epoch = 3")
            c.execute(
                "INSERT INTO pending_commands (id, user_id, account_id, scope_epoch, operation, payload, request_hash, " +
                    "causal_order, created_at) VALUES (?, ?, ?, 3, 'maint.workers.update', '{}', ?, 1, 1)",
                listOf(command, V2Fixtures.USER, V2Fixtures.ACCOUNT, "a".repeat(64)),
            )
            c.execute(
                "INSERT INTO local_overlays (id, entity_type, entity_key, kind, command_id, reducer_version, causal_order, " +
                    "patch, created_at, updated_at) VALUES (?, 'workers', 'k', 'command', ?, 1, 1, '{}', 1, 1)",
                listOf(V2Uuid.v7(), command),
            )
            // A column the new DDL no longer has, and the old contract's sha.
            c.execute("ALTER TABLE pending_commands ADD COLUMN retired_field TEXT")
            V2Database.metaPut(c, V2SyncEngine.META_DEVICE_REGISTERED, "1")
            V2Database.metaPut(c, V2SyncEngine.META_CHAIN, "{}")
            V2Database.metaPut(c, "ddl_sha256", "0".repeat(64))
        }
        db.close()

        val migrated = V2Fixtures.business(conn())
        migrated.read { c ->
            assertEquals(command, c.scalarText("SELECT id FROM pending_commands"))
            assertEquals(1L, c.scalarLong("SELECT count(*) FROM local_overlays WHERE command_id = ?", listOf(command)))
            assertEquals(0L, c.scalarLong("SELECT count(*) FROM pragma_table_info('pending_commands') WHERE name = 'retired_field'"))
            assertEquals(3L, c.scalarLong("SELECT scope_epoch FROM database_identity"))
            assertEquals(V2Fixtures.DEVICE, c.scalarText("SELECT device_id FROM database_identity"))
            assertEquals("none", c.scalarText("SELECT bootstrap_state FROM sync_state WHERE stream = 'account'"))
            assertEquals(0L, c.scalarLong("SELECT cursor FROM sync_state WHERE stream = 'account'"))
            assertEquals("1", V2Database.metaGet(c, V2SyncEngine.META_DEVICE_REGISTERED))
            assertNull(V2Database.metaGet(c, V2SyncEngine.META_CHAIN))
            assertEquals(V2GeneratedSchema.BUSINESS_FILE_SHA256, V2Database.metaGet(c, "ddl_sha256"))
        }
        migrated.close()
    }
}
