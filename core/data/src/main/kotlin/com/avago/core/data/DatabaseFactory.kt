package com.avago.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.MIGRATION_4_5
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
            .addMigrations(MIGRATION_4_5)
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
        instances.remove(accountId)?.close()
        val dbDir = java.io.File(context.filesDir, "accounts/$accountId")
        val dbFile = java.io.File(dbDir, "avago.db")
        dbFile.delete()
        java.io.File(dbDir, "avago.db-shm").delete()
        java.io.File(dbDir, "avago.db-wal").delete()
    }

}
