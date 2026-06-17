package com.avago.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.MIGRATION_4_5
import com.avago.core.data.db.MIGRATION_5_6
import com.avago.core.data.db.MIGRATION_9_10
import com.avago.core.data.db.MIGRATION_10_11
import com.avago.core.data.db.MIGRATION_11_12
import com.avago.core.data.db.MIGRATION_12_13
import com.avago.core.data.db.MIGRATION_13_14
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Singleton

@Singleton
class DatabaseFactory(
    private val context: Context,
) {
    private val mutex = Mutex()
    private val instances = mutableMapOf<String, AvagoDatabase>()

    suspend fun get(accountId: String): AvagoDatabase = mutex.withLock {
        instances.getOrPut(accountId) { buildDatabase(accountId) }
    }

    private fun buildDatabase(accountId: String): AvagoDatabase {
        val dbDir = File(context.filesDir, "accounts/$accountId")
        dbDir.mkdirs()
        val dbFile = File(dbDir, "avago.db")

        return Room.databaseBuilder(
            context,
            AvagoDatabase::class.java,
            dbFile.absolutePath,
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    fun close(accountId: String) {
        instances.remove(accountId)?.close()
    }

    fun closeAll() {
        instances.values.forEach { it.close() }
        instances.clear()
    }

    fun databaseFile(accountId: String): File = File(File(context.filesDir, "accounts/$accountId"), "avago.db")

    fun deleteDatabase(accountId: String) {
        // Multiple concurrent signOut() invocations can race on the same accountId
        // (each non-stale 403 from in-flight sync requests emits accountGoneEvents,
        // so a single failing account can produce a burst of signOuts). Closing an
        // already-closed Room database throws IllegalStateException; swallow it so
        // the second signOut still reaches the file-delete step below.
        runCatching { instances.remove(accountId)?.close() }
        val dbDir = File(context.filesDir, "accounts/$accountId")
        runCatching { File(dbDir, "avago.db").delete() }
        runCatching { File(dbDir, "avago.db-shm").delete() }
        runCatching { File(dbDir, "avago.db-wal").delete() }
    }

}
