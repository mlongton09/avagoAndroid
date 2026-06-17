package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema changes between versions 7 and 8.
        // Empty migration prevents fallbackToDestructiveMigration from wiping data on upgrade.
    }
}
