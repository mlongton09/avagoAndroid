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
 * destructive fallback is restricted to pre-release versions and downgrades.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val accountId = "migration-test-account"
    private val accountDir = File(context.filesDir, "accounts/$accountId")

    @get:Rule
    val avagoHelper = MigrationTestHelper(instrumentation, AvagoDatabase::class.java)

    @get:Rule
    val chatHelper = MigrationTestHelper(instrumentation, ChatDatabase::class.java)

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
    fun chat_previousVersion_migratesToCurrent_keepingUnsentMessages() {
        val name = "chat-migration-prev.db"
        chatHelper.createDatabase(name, CHAT_DB_VERSION - 1).use { insertChatOutbox(it) }

        val db = chatHelper.runMigrationsAndValidate(name, CHAT_DB_VERSION, true, *CHAT_MIGRATIONS)

        db.use { assertChatOutbox(it) }
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

    @Test
    fun productionChatDatabaseFactory_upgradesPreviousVersion_withoutWipingUnsentMessages() {
        val path = File(accountDir.apply { mkdirs() }, "chat.db").absolutePath
        chatHelper.createDatabase(path, CHAT_DB_VERSION - 1).use { insertChatOutbox(it) }

        val factory = ChatDatabaseFactory(context)
        try {
            val db = runBlocking { factory.get(accountId) }
            assertChatOutbox(db.openHelper.readableDatabase)
        } finally {
            runBlocking { factory.close(accountId) }
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

    private fun insertChatOutbox(db: SupportSQLiteDatabase) {
        for ((id, status) in CHAT_OUTBOX_ROWS) {
            db.insert(
                "outbox",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("local_id", id)
                    put("thread_id", "thread-1")
                    put("account_id", "acct-1")
                    put("sender_id", "user-1")
                    put("body_md", "unsent $id")
                    put("client_ref", "ref-$id")
                    put("status", status)
                    put("created_at", 1_700_000_000_000L)
                    put("updated_at", 1_700_000_000_001L)
                },
            )
        }
    }

    private fun assertChatOutbox(db: SupportSQLiteDatabase) {
        db.query("SELECT local_id, status, body_md, client_ref FROM outbox ORDER BY local_id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            assertEquals(CHAT_OUTBOX_ROWS, rows)
            c.moveToFirst()
            assertEquals("unsent ${CHAT_OUTBOX_ROWS[0].first}", c.getString(2))
            assertEquals("ref-${CHAT_OUTBOX_ROWS[0].first}", c.getString(3))
        }
    }

    private companion object {
        val OUTBOX_ROWS = listOf("q-1" to "pending", "q-2" to "failed")
        val CHAT_OUTBOX_ROWS = listOf("m-1" to "pending", "m-2" to "failed")
    }
}
