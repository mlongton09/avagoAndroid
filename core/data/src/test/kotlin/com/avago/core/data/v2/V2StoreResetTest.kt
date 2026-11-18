package com.avago.core.data.v2

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `a store built from another contract DDL is refused`() = runTest {
        val db = V2Fixtures.business(conn())
        db.write { V2Database.metaPut(it.connection, "ddl_sha256", "0".repeat(64)) }
        db.close()

        assertThrows(V2SchemaMismatchException::class.java) { V2Fixtures.business(conn()) }
    }
}
