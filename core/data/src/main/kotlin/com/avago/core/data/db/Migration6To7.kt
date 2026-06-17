package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema changes between versions 6 and 7.
        // Empty migration prevents fallbackToDestructiveMigration from wiping data on upgrade.
    }
}
