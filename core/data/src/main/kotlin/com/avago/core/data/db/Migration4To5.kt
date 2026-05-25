package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateParts(db)
        migrateDocs(db)
        migrateLog(db)
        migrateItems(db)
        migrateInventory(db)
        migrateAssetsDepreciation(db)
    }
}

private fun migrateParts(db: SupportSQLiteDatabase) {
    db.execSQL("""
        CREATE TABLE parts_new (
            part_id TEXT NOT NULL PRIMARY KEY,
            account_id TEXT NOT NULL,
            part_number TEXT,
            part_name TEXT NOT NULL,
            description TEXT,
            category TEXT,
            unit_of_measure TEXT,
            default_vendor_id TEXT,
            unit_cost REAL,
            currency TEXT,
            attributes TEXT,
            manufacturer TEXT,
            reorder_quantity REAL,
            status TEXT,
            entity_type TEXT,
            entity_id TEXT,
            quantity REAL,
            gtin TEXT,
            serial_number TEXT,
            notes TEXT,
            base_amount REAL,
            exchange_rate_used REAL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            deleted_at INTEGER,
            server_version INTEGER NOT NULL DEFAULT 0,
            seq INTEGER
        )
    """.trimIndent())

    db.execSQL("""
        INSERT INTO parts_new (
            part_id, account_id, part_number, part_name, description, category,
            unit_of_measure, default_vendor_id, unit_cost, currency, attributes,
            created_at, updated_at, deleted_at, server_version, seq
        )
        SELECT
            part_id, account_id, sku, name, description, category,
            unit_of_measure, default_vendor_id, cost, currency, attributes,
            created_at, updated_at, deleted_at, server_version, seq
        FROM parts
    """.trimIndent())

    db.execSQL("DROP TABLE parts")
    db.execSQL("ALTER TABLE parts_new RENAME TO parts")
    db.execSQL("CREATE INDEX index_parts_account_id_deleted_at ON parts (account_id, deleted_at)")
    db.execSQL("CREATE INDEX index_parts_part_number ON parts (part_number)")
    db.execSQL("CREATE INDEX index_parts_category ON parts (category)")
}

private fun migrateDocs(db: SupportSQLiteDatabase) {
    db.execSQL("""
        CREATE TABLE docs_new (
            doc_id TEXT NOT NULL PRIMARY KEY,
            asset_id TEXT,
            entity_id TEXT,
            entity_type TEXT,
            account_id TEXT NOT NULL,
            title TEXT NOT NULL,
            document_type TEXT,
            mime_type TEXT,
            storage_key TEXT,
            download_url TEXT,
            file_hash TEXT,
            file_size INTEGER,
            ocr_raw_text TEXT,
            ocr_extracted_json TEXT,
            vendor TEXT,
            total_amount REAL,
            currency TEXT,
            purchase_date INTEGER,
            warranty_end_date INTEGER,
            uploaded_by TEXT,
            uploaded_at INTEGER,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            deleted_at INTEGER,
            server_version INTEGER NOT NULL DEFAULT 0,
            seq INTEGER
        )
    """.trimIndent())

    db.execSQL("""
        INSERT INTO docs_new (
            doc_id, asset_id, entity_id, entity_type, account_id,
            title, document_type, mime_type, storage_key, download_url,
            file_hash, file_size, ocr_raw_text, ocr_extracted_json,
            vendor, total_amount, currency, purchase_date, warranty_end_date,
            uploaded_by, uploaded_at, created_at, updated_at, deleted_at,
            server_version, seq
        )
        SELECT
            doc_id, asset_id, entity_id, entity_type, account_id,
            name, doc_type, mime_type, storage_key, download_url,
            file_hash, file_size, ocr_raw_text, ocr_extracted_json,
            vendor, total, currency, purchase_date, warranty_end_date,
            uploaded_by, uploaded_at, created_at, updated_at, deleted_at,
            server_version, seq
        FROM docs
    """.trimIndent())

    db.execSQL("DROP TABLE docs")
    db.execSQL("ALTER TABLE docs_new RENAME TO docs")
    db.execSQL("CREATE INDEX index_docs_account_id ON docs (account_id)")
    db.execSQL("CREATE INDEX index_docs_asset_id ON docs (asset_id)")
    db.execSQL("CREATE INDEX index_docs_entity_id_entity_type ON docs (entity_id, entity_type)")
}

private fun migrateLog(db: SupportSQLiteDatabase) {
    // Read full schema by creating with new column names.
    // We keep ALL existing columns; only log_id/log_date/meter are renamed.
    db.execSQL("""
        CREATE TABLE log_new (
            log_id TEXT NOT NULL PRIMARY KEY,
            asset_id TEXT NOT NULL,
            account_id TEXT NOT NULL,
            title TEXT NOT NULL,
            log_date INTEGER NOT NULL,
            meter REAL,
            category TEXT,
            cost REAL,
            performed_by TEXT,
            performed_by_user_id TEXT,
            notes TEXT,
            data TEXT,
            attributes TEXT,
            cost_mode TEXT,
            cost_items REAL,
            cost_labor REAL,
            cost_tax REAL,
            currency TEXT,
            base_amount REAL,
            exchange_rate_used REAL,
            config_id TEXT,
            config_version INTEGER,
            service_id TEXT,
            cost_misc REAL,
            parent_id TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            deleted_at INTEGER,
            server_version INTEGER NOT NULL DEFAULT 0,
            seq INTEGER
        )
    """.trimIndent())

    db.execSQL("""
        INSERT INTO log_new (
            log_id, asset_id, account_id, title, log_date, meter,
            category, cost, performed_by, performed_by_user_id, notes, data,
            attributes, cost_mode, cost_items, cost_labor, cost_tax, currency,
            base_amount, exchange_rate_used, config_id, config_version,
            service_id, cost_misc, parent_id,
            created_at, updated_at, deleted_at, server_version, seq
        )
        SELECT
            entry_id, asset_id, account_id, title, entry_date, odometer_value,
            category, cost, performed_by, performed_by_user_id, notes, data,
            attributes, cost_mode, cost_items, cost_labor, cost_tax, currency,
            base_amount, exchange_rate_used, config_id, config_version,
            service_id, cost_misc, parent_id,
            created_at, updated_at, deleted_at, server_version, seq
        FROM log
    """.trimIndent())

    db.execSQL("DROP TABLE log")
    db.execSQL("ALTER TABLE log_new RENAME TO log")
    db.execSQL("CREATE INDEX index_log_asset_id_log_date ON log (asset_id, log_date)")
    db.execSQL("CREATE INDEX index_log_account_id_deleted_at ON log (account_id, deleted_at)")
}

private fun migrateItems(db: SupportSQLiteDatabase) {
    db.execSQL("""
        CREATE TABLE items_new (
            item_id TEXT NOT NULL PRIMARY KEY,
            log_id TEXT NOT NULL,
            account_id TEXT NOT NULL,
            part_id TEXT,
            name TEXT,
            quantity REAL NOT NULL,
            unit_price REAL,
            currency TEXT,
            notes TEXT,
            production_date INTEGER,
            part_number TEXT,
            gtin TEXT,
            manufacturer_id TEXT,
            serial_number TEXT,
            revision TEXT,
            model_number TEXT,
            lot_number TEXT,
            country TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            deleted_at INTEGER,
            server_version INTEGER NOT NULL DEFAULT 0,
            seq INTEGER
        )
    """.trimIndent())

    db.execSQL("""
        INSERT INTO items_new (
            item_id, log_id, account_id, part_id, name, quantity, unit_price,
            currency, notes, created_at, updated_at, deleted_at, server_version, seq
        )
        SELECT
            item_id, log_id, account_id, part_id, description, quantity, unit_cost,
            currency, notes, created_at, updated_at, deleted_at, server_version, seq
        FROM items
    """.trimIndent())

    db.execSQL("DROP TABLE items")
    db.execSQL("ALTER TABLE items_new RENAME TO items")
    db.execSQL("CREATE INDEX index_items_log_id ON items (log_id)")
    db.execSQL("CREATE INDEX index_items_account_id ON items (account_id)")
}

private fun migrateInventory(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE inventory ADD COLUMN bin_id TEXT")
    db.execSQL("CREATE INDEX index_inventory_bin_id ON inventory (bin_id)")
}

internal fun migrateAssetsDepreciation(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE assets ADD COLUMN purchase_price REAL")
    db.execSQL("ALTER TABLE assets ADD COLUMN salvage_value REAL")
    db.execSQL("ALTER TABLE assets ADD COLUMN useful_life_months INTEGER")
    db.execSQL("ALTER TABLE assets ADD COLUMN depreciation_method TEXT")
    db.execSQL("ALTER TABLE assets ADD COLUMN placed_in_service_date INTEGER")
}
