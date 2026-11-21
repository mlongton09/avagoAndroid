package com.avago.core.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avago.core.data.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Upgrades real previous-version databases (schemas exported from the commits that shipped
 * them) and asserts pending outbox rows survive: both the exact migrated schema
 * ([MigrationTestHelper.runMigrationsAndValidate]) and the production builders, whose
 * destructive fallback is restricted to pre-release versions and downgrades. (The Room chat
 * database is retired; chat lives in the per-user/account v2 chat file.)
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val accountId = "migration-test-account"
    private val accountDir = File(context.filesDir, "accounts/$accountId")

    @get:Rule
    val avagoHelper = MigrationTestHelper(instrumentation, AvagoDatabase::class.java)

    @After
    fun tearDown() {
        accountDir.deleteRecursively()
    }

    @Test
    fun avago_previousVersion_migratesToCurrent_keepingOutbox() {
        val name = "avago-migration-prev.db"
        avagoHelper.createDatabase(name, AVAGO_DB_VERSION - 1).use { insertOutbox(it) }

        val db = avagoHelper.runMigrationsAndValidate(name, AVAGO_DB_VERSION, true, *AVAGO_MIGRATIONS)

        db.use { assertOutbox(it) }
    }

    @Test
    fun avago_twoVersionsBack_migratesThroughChain_keepingOutbox() {
        val name = "avago-migration-prev2.db"
        avagoHelper.createDatabase(name, AVAGO_DB_VERSION - 2).use { insertOutbox(it) }

        val db = avagoHelper.runMigrationsAndValidate(name, AVAGO_DB_VERSION, true, *AVAGO_MIGRATIONS)

        db.use { assertOutbox(it) }
    }

    @Test
    fun productionDatabaseFactory_upgradesPreviousVersion_withoutWipingOutbox() {
        val path = File(accountDir.apply { mkdirs() }, "avago.db").absolutePath
        avagoHelper.createDatabase(path, AVAGO_DB_VERSION - 1).use { insertOutbox(it) }

        val factory = DatabaseFactory(context)
        try {
            val db = runBlocking { factory.get(accountId) }
            assertOutbox(db.openHelper.readableDatabase)
        } finally {
            factory.closeAll()
        }
    }

    private fun insertOutbox(db: SupportSQLiteDatabase) {
        for ((id, status) in OUTBOX_ROWS) {
            db.insert(
                "sync_queue",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("queue_id", id)
                    put("entity_type", "log")
                    put("entity_id", "log-$id")
                    put("operation", "create")
                    put("payload", """{"id":"log-$id"}""")
                    put("sync_status", status)
                    put("attempts", 2)
                    put("created_at", 1_700_000_000_000L)
                    put("updated_at", 1_700_000_000_001L)
                },
            )
        }
    }

    private fun assertOutbox(db: SupportSQLiteDatabase) {
        db.query("SELECT queue_id, sync_status, payload, attempts FROM sync_queue ORDER BY queue_id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            assertEquals(OUTBOX_ROWS, rows)
            c.moveToFirst()
            assertEquals("""{"id":"log-${OUTBOX_ROWS[0].first}"}""", c.getString(2))
            assertEquals(2, c.getInt(3))
        }
    }

    private companion object {
        val OUTBOX_ROWS = listOf("q-1" to "pending", "q-2" to "failed")
    }
}
