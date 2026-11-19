package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Drops the legacy rentals offline store (`rental_records`, `rental_sync_state`): rentals read
 * from and write to the schema v2 store only (decision U6, fresh start; nothing to carry over).
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `rental_records`")
        db.execSQL("DROP TABLE IF EXISTS `rental_sync_state`")
    }
}
