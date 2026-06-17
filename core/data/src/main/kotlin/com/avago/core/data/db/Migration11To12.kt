package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Change 71: wo_procedure_rows — row_type, description, urls
        db.execSQL(
            "ALTER TABLE wo_checklist_items ADD COLUMN row_type TEXT NOT NULL DEFAULT 'STEP'"
        )
        db.execSQL(
            "ALTER TABLE wo_checklist_items ADD COLUMN description TEXT"
        )
        db.execSQL(
            "ALTER TABLE wo_checklist_items ADD COLUMN urls TEXT"
        )

        // Change 72: work_orders — skip_reason_code, skip_reason
        db.execSQL(
            "ALTER TABLE work_orders ADD COLUMN skip_reason_code TEXT"
        )
        db.execSQL(
            "ALTER TABLE work_orders ADD COLUMN skip_reason TEXT"
        )

        // Change 73: inventory_transactions — reason_code
        db.execSQL(
            "ALTER TABLE inventory_transactions ADD COLUMN reason_code TEXT"
        )

        // Change 74: inventory_transactions — reversed_transaction_id
        db.execSQL(
            "ALTER TABLE inventory_transactions ADD COLUMN reversed_transaction_id TEXT"
        )

        // Change 77: po_lines — work_order_id
        db.execSQL(
            "ALTER TABLE po_lines ADD COLUMN work_order_id TEXT"
        )

        // Change 79: vendor_contacts — new table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vendor_contacts (
                contact_id TEXT NOT NULL PRIMARY KEY,
                vendor_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                name TEXT NOT NULL,
                email TEXT,
                phone TEXT,
                title TEXT,
                is_primary INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vendor_contacts_vendor_id ON vendor_contacts (vendor_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vendor_contacts_account_id ON vendor_contacts (account_id)"
        )
    }
}
