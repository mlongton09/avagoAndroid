package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Change 51: stocking_levels — safety_stock_quantity (REAL, nullable)
        db.execSQL(
            "ALTER TABLE stocking_levels ADD COLUMN safety_stock_quantity REAL"
        )

        // Change 52: stocking_levels — account_id, consumption_window, last_reviewed_at, seq, deleted_at
        db.execSQL(
            "ALTER TABLE stocking_levels ADD COLUMN account_id TEXT"
        )
        db.execSQL(
            "ALTER TABLE stocking_levels ADD COLUMN consumption_window INTEGER"
        )
        db.execSQL(
            "ALTER TABLE stocking_levels ADD COLUMN last_reviewed_at INTEGER"
        )
        db.execSQL(
            "ALTER TABLE stocking_levels ADD COLUMN seq INTEGER"
        )
        db.execSQL(
            "ALTER TABLE stocking_levels ADD COLUMN deleted_at INTEGER"
        )

        // Change 57: log_cost_lines — cost_status, approved_by, approved_at
        db.execSQL(
            "ALTER TABLE log_cost_lines ADD COLUMN cost_status TEXT NOT NULL DEFAULT 'ACTUAL'"
        )
        db.execSQL(
            "ALTER TABLE log_cost_lines ADD COLUMN approved_by TEXT"
        )
        db.execSQL(
            "ALTER TABLE log_cost_lines ADD COLUMN approved_at INTEGER"
        )

        // Change 59: assets — ancestors_json (TEXT, nullable)
        db.execSQL(
            "ALTER TABLE assets ADD COLUMN ancestors_json TEXT"
        )
    }
}
