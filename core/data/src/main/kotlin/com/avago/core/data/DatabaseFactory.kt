package com.avago.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.MIGRATION_4_5
import com.avago.core.data.db.MIGRATION_5_6
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
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
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

    fun deleteDatabase(accountId: String) {
        // Multiple concurrent signOut() invocations can race on the same accountId
        // (each non-stale 403 from in-flight sync requests emits accountGoneEvents,
        // so a single failing account can produce a burst of signOuts). Closing an
        // already-closed Room database throws IllegalStateException; swallow it so
        // the second signOut still reaches the file-delete step below.
        runCatching { instances.remove(accountId)?.close() }
        val dbDir = java.io.File(context.filesDir, "accounts/$accountId")
        runCatching { java.io.File(dbDir, "avago.db").delete() }
        runCatching { java.io.File(dbDir, "avago.db-shm").delete() }
        runCatching { java.io.File(dbDir, "avago.db-wal").delete() }
    }

}
