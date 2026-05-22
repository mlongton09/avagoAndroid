package com.avago.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatDatabaseFactory @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val openDbs = mutableMapOf<String, ChatDatabase>()
    private val lock = Mutex()

    suspend fun get(accountId: String): ChatDatabase = lock.withLock {
        openDbs.getOrPut(accountId) {
            val dir = File(ctx.filesDir, "accounts/$accountId").apply { mkdirs() }
            Room.databaseBuilder(ctx, ChatDatabase::class.java, File(dir, "chat.db").path)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA journal_mode=WAL")
                        db.execSQL("PRAGMA foreign_keys=ON")
                        db.execSQL("PRAGMA synchronous=NORMAL")
                        db.execSQL("PRAGMA temp_store=MEMORY")
                        db.execSQL("PRAGMA mmap_size=134217728")
                    }
                })
                .build()
        }
    }

    /** Close and evict a specific account's database (e.g. on sign-out). */
    suspend fun close(accountId: String) = lock.withLock {
        openDbs.remove(accountId)?.close()
    }
}
