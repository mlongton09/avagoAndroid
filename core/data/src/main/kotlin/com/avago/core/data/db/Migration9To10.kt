package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scout_pending (
                id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                transcript TEXT NOT NULL,
                screen_context TEXT NOT NULL,
                skill_hint TEXT,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scout_pending_created_at ON scout_pending(created_at)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scout_history (
                id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                transcript TEXT NOT NULL,
                skill_name TEXT,
                target_screen TEXT,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scout_history_created_at ON scout_history(created_at)")
    }
}
