package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Change 91: cycle count variance fields
        db.execSQL("ALTER TABLE cycle_count_lines ADD COLUMN unit_cost REAL")
        db.execSQL("ALTER TABLE cycle_count_lines ADD COLUMN variance_quantity REAL")
        db.execSQL("ALTER TABLE cycle_count_lines ADD COLUMN variance_value REAL")

        // Change 92: asset serial number and model association
        db.execSQL("ALTER TABLE assets ADD COLUMN serial_number TEXT")
        db.execSQL("ALTER TABLE assets ADD COLUMN model_id TEXT")

        // Change 99: photo thumbnails
        db.execSQL("ALTER TABLE photos ADD COLUMN thumbnail_url TEXT")
        db.execSQL("ALTER TABLE photos ADD COLUMN thumbnail_width INTEGER")
        db.execSQL("ALTER TABLE photos ADD COLUMN thumbnail_height INTEGER")
    }
}
