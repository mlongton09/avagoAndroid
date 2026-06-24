package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Some DBs were recreated via fallbackToDestructiveMigration at an intermediate
        // schema and may already have some of these columns. Guard every ALTER.
        val cursor = db.query("PRAGMA table_info(assets)")
        val existing = mutableSetOf<String>()
        val nameIdx = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) existing.add(cursor.getString(nameIdx))
        cursor.close()

        // Rental workflow columns (Change: rental feature)
        if ("is_rental" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN is_rental INTEGER NOT NULL DEFAULT 0")
        if ("rental_rate" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN rental_rate REAL")
        if ("rental_rate_unit" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN rental_rate_unit TEXT")

        // Depreciation fields
        if ("purchase_price" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN purchase_price REAL")
        if ("salvage_value" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN salvage_value REAL")
        if ("useful_life_months" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN useful_life_months INTEGER")
        if ("depreciation_method" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN depreciation_method TEXT")
        if ("placed_in_service_date" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN placed_in_service_date INTEGER")

        // Asset hierarchy / identification (Change 92, 106)
        if ("ancestors_json" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN ancestors_json TEXT")
        if ("serial_number" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN serial_number TEXT")
        if ("model_id" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN model_id TEXT")
        if ("tags_json" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN tags_json TEXT")

        // year was present in many original schemas but may be absent from DBs that
        // were created before it was added to the entity
        if ("year" !in existing)
            db.execSQL("ALTER TABLE assets ADD COLUMN year INTEGER")
    }
}
