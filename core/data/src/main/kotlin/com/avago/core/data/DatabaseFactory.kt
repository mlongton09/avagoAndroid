package com.avago.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.avago.core.data.db.AvagoDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseFactory @Inject constructor(
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
            .addCallback(WalCallback)
            .build()
    }

    fun close(accountId: String) {
        instances.remove(accountId)?.close()
    }

    fun closeAll() {
        instances.values.forEach { it.close() }
        instances.clear()
    }

    private object WalCallback : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA journal_mode=WAL")
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("PRAGMA synchronous=NORMAL")
            db.execSQL("PRAGMA temp_store=MEMORY")
            db.execSQL("PRAGMA mmap_size=134217728")
        }
    }
}
