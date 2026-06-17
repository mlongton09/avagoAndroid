package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema changes between versions 8 and 9.
        // Empty migration prevents fallbackToDestructiveMigration from wiping data on upgrade.
    }
}
