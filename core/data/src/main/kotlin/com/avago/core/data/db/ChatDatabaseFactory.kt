package com.avago.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_threads ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_threads ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_threads ADD COLUMN notification_pref TEXT")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN server_version INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN parent_message_id TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_thread_id_parent_message_id ON chat_messages(thread_id, parent_message_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_is_pinned ON chat_messages(is_pinned)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_needs_reply ON chat_messages(needs_reply)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN sender_avatar_url TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN link_preview_site_name TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN image_urls TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN mentioned_user_ids TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN mention_kinds TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN is_system INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN system_kind TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN system_payload TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN reply_count INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN latest_reply_at INTEGER")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN delivered_by_count INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN read_by_count INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN read_by_total INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN reaction_counts TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN my_reactions TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN needs_reply INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN client_ref TEXT")
        // outbox new fields
        database.execSQL("ALTER TABLE outbox ADD COLUMN client_ref TEXT")
        database.execSQL("ALTER TABLE outbox ADD COLUMN image_urls TEXT")
        database.execSQL("ALTER TABLE outbox ADD COLUMN mentions TEXT")
        database.execSQL("ALTER TABLE outbox ADD COLUMN needs_reply INTEGER NOT NULL DEFAULT 0")
    }
}

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
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }

    /** Close and evict a specific account's database (e.g. on sign-out). */
    suspend fun close(accountId: String) = lock.withLock {
        openDbs.remove(accountId)?.close()
    }
}
