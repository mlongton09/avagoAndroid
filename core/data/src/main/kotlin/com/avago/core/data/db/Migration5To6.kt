package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Devices that upgraded 4→5 with migrateAssetsDepreciation already have
        // these columns. Devices that got a fresh v5 install before the entity
        // was updated do not. Check before adding to avoid "duplicate column" errors.
        val existing = assetsColumnNames(db)
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
    }
}

private fun assetsColumnNames(db: SupportSQLiteDatabase): Set<String> {
    val names = mutableSetOf<String>()
    val cursor = db.query("PRAGMA table_info(assets)")
    val nameIdx = cursor.getColumnIndex("name")
    while (cursor.moveToNext()) {
        if (nameIdx >= 0) names.add(cursor.getString(nameIdx))
    }
    cursor.close()
    return names
}
