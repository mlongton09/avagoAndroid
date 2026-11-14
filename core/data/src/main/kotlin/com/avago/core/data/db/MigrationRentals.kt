package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Rentals offline store (spec §15): `rental_records` (every rentals sync feed row plus
 * local drafts / handovers / ledger entries) and `rental_sync_state` (per-feed cursor and
 * "last synced"). Purely additive — no existing table changes.
 *
 * NOTE: numbered after the inspection-defects migration (18 → 19) that lands separately;
 * renumber on rebase if that has not landed yet.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createRentalTables(db)
    }
}

internal fun createRentalTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `rental_records` (
            `entity` TEXT NOT NULL, `id` TEXT NOT NULL, `json` TEXT NOT NULL,
            `asset_id` TEXT, `order_id` TEXT, `customer_id` TEXT, `class_id` TEXT, `location_id` TEXT,
            `status` TEXT, `name` TEXT, `start_at` INTEGER, `end_at` INTEGER,
            `seq` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `local_state` TEXT NOT NULL,
            `client_uuid` TEXT, `notice` TEXT,
            PRIMARY KEY(`entity`, `id`)
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_rental_records_entity_asset_id` ON `rental_records` (`entity`, `asset_id`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_rental_records_entity_order_id` ON `rental_records` (`entity`, `order_id`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_rental_records_entity_customer_id` ON `rental_records` (`entity`, `customer_id`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_rental_records_entity_start_at` ON `rental_records` (`entity`, `start_at`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_rental_records_local_state` ON `rental_records` (`local_state`)")
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `rental_sync_state` (
            `entity` TEXT NOT NULL, `cursor` TEXT, `server_time` TEXT, `last_synced_at` INTEGER, `last_error` TEXT,
            PRIMARY KEY(`entity`)
        )
        """.trimIndent(),
    )
}
