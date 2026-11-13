package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds `log.wo_id` (link from a log entry to the work order it records). */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA table_info(log)")
        val existing = mutableSetOf<String>()
        val nameIdx = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) existing.add(cursor.getString(nameIdx))
        cursor.close()
        if ("wo_id" !in existing) db.execSQL("ALTER TABLE log ADD COLUMN wo_id TEXT")
    }
}
