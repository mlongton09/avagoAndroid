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

private val CHAT_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // DB created fresh at v4 already has these columns via entity definitions;
        // DB migrated from v3→v4 does not. Guard each ALTER so both paths succeed.
        val existing = database.query("PRAGMA table_info(chat_messages)").use { c ->
            val set = mutableSetOf<String>()
            while (c.moveToNext()) set += c.getString(1)
            set
        }
        if ("server_version" !in existing)
            database.execSQL("ALTER TABLE chat_messages ADD COLUMN server_version INTEGER NOT NULL DEFAULT 0")
        if ("parent_message_id" !in existing)
            database.execSQL("ALTER TABLE chat_messages ADD COLUMN parent_message_id TEXT")
        if ("is_pinned" !in existing)
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

private val CHAT_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Drop and recreate chat_messages to guarantee exact schema match with current entity
        // definitions. Safe because chat messages re-sync from the server. This resolves the
        // defaultValue mismatch that arises from two DB creation paths:
        //  - created fresh at v4: columns have no DEFAULT clause (defaultValue=undefined)
        //  - migrated v3→v4→v5: columns added via ALTER TABLE with DEFAULT 0 (defaultValue='0')
        // Both paths now converge to a canonical v6 schema.
        database.execSQL("DROP TABLE IF EXISTS `chat_messages`")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `chat_messages` (
                `message_id` TEXT NOT NULL,
                `thread_id` TEXT NOT NULL,
                `account_id` TEXT NOT NULL,
                `sender_id` TEXT NOT NULL,
                `sender_name` TEXT,
                `sender_avatar_url` TEXT,
                `body_md` TEXT NOT NULL,
                `body_preview` TEXT,
                `edited_at` INTEGER,
                `link_preview_title` TEXT,
                `link_preview_description` TEXT,
                `link_preview_image_url` TEXT,
                `link_preview_url` TEXT,
                `link_preview_site_name` TEXT,
                `photo_url` TEXT,
                `image_urls` TEXT,
                `mentioned_user_ids` TEXT,
                `mention_kinds` TEXT,
                `is_system` INTEGER NOT NULL DEFAULT 0,
                `system_kind` TEXT,
                `system_payload` TEXT,
                `reply_count` INTEGER NOT NULL DEFAULT 0,
                `latest_reply_at` INTEGER,
                `delivered_by_count` INTEGER NOT NULL DEFAULT 0,
                `read_by_count` INTEGER NOT NULL DEFAULT 0,
                `read_by_total` INTEGER NOT NULL DEFAULT 0,
                `reaction_counts` TEXT,
                `my_reactions` TEXT,
                `needs_reply` INTEGER NOT NULL DEFAULT 0,
                `client_ref` TEXT,
                `outbox_status` TEXT,
                `server_version` INTEGER NOT NULL DEFAULT 0,
                `deleted_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `parent_message_id` TEXT,
                `is_pinned` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`message_id`)
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_thread_id_created_at` ON `chat_messages`(`thread_id`, `created_at`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_thread_id_parent_message_id` ON `chat_messages`(`thread_id`, `parent_message_id`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_is_pinned` ON `chat_messages`(`is_pinned`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_needs_reply` ON `chat_messages`(`needs_reply`)")
    }
}

private val CHAT_MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN audio_url TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN attachment_url TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN attachment_name TEXT")
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN attachment_size INTEGER")
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
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    CHAT_MIGRATION_4_5,
                    CHAT_MIGRATION_5_6,
                    CHAT_MIGRATION_6_7,
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }

    /** Close and evict a specific account's database (e.g. on sign-out). */
    suspend fun close(accountId: String) = lock.withLock {
        openDbs.remove(accountId)?.close()
    }
}
