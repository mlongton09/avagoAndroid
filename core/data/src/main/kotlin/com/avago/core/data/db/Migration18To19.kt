package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the local `inspection_defects` register cache (open-defects panel, offline-first). */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `inspection_defects` (" +
                "`defect_id` TEXT NOT NULL, `asset_id` TEXT NOT NULL, `item_key` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `row_json` TEXT NOT NULL, `server_updated_at` TEXT, " +
                "`cached_at` INTEGER NOT NULL, PRIMARY KEY(`defect_id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_inspection_defects_asset_id` ON `inspection_defects` (`asset_id`)",
        )
    }
}
