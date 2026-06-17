package com.avago.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // -----------------------------------------------------------------------
        // CHANGE 1 / 5 / 14 / 64 — wo_comments
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE wo_comments ADD COLUMN mentioned_user_ids TEXT")
        db.execSQL("ALTER TABLE wo_comments ADD COLUMN is_internal INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wo_comments ADD COLUMN comment_type TEXT")
        db.execSQL("ALTER TABLE wo_comments ADD COLUMN created_by_id TEXT")
        db.execSQL("ALTER TABLE wo_comments ADD COLUMN updated_by_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 8 / 29 / 64 — wo_assignments
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE wo_assignments ADD COLUMN actual_hours REAL")
        db.execSQL("ALTER TABLE wo_assignments ADD COLUMN role TEXT")
        db.execSQL("ALTER TABLE wo_assignments ADD COLUMN estimated_hours REAL")
        db.execSQL("ALTER TABLE wo_assignments ADD COLUMN created_by_id TEXT")
        db.execSQL("ALTER TABLE wo_assignments ADD COLUMN updated_by_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 10 / 22 / 31 / 61 / 106 — assets
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE assets ADD COLUMN current_status TEXT")
        db.execSQL("ALTER TABLE assets ADD COLUMN downtime_type TEXT")
        db.execSQL("ALTER TABLE assets ADD COLUMN custom_status_id TEXT")
        db.execSQL("ALTER TABLE assets ADD COLUMN custom_fields TEXT")
        db.execSQL("ALTER TABLE assets ADD COLUMN criticality_id TEXT")
        db.execSQL("ALTER TABLE assets ADD COLUMN global_uuid TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 11 — log
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE log ADD COLUMN overtime_multiplier REAL")

        // -----------------------------------------------------------------------
        // CHANGE 15 / 19 / 21 / 22 / 83 — work_orders
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE work_orders ADD COLUMN child_wo_count INTEGER")
        db.execSQL("ALTER TABLE work_orders ADD COLUMN child_wo_ids TEXT")
        db.execSQL("ALTER TABLE work_orders ADD COLUMN procedure_template_id TEXT")
        db.execSQL("ALTER TABLE work_orders ADD COLUMN permit_status_summary TEXT")
        db.execSQL("ALTER TABLE work_orders ADD COLUMN custom_fields TEXT")
        db.execSQL("ALTER TABLE work_orders ADD COLUMN assigned_team_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 19 / 42 / 64 — wo_checklist_items
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE wo_checklist_items ADD COLUMN response TEXT")
        db.execSQL("ALTER TABLE wo_checklist_items ADD COLUMN notes TEXT")
        db.execSQL("ALTER TABLE wo_checklist_items ADD COLUMN pass_fail TEXT")
        db.execSQL("ALTER TABLE wo_checklist_items ADD COLUMN signature_url TEXT")
        db.execSQL("ALTER TABLE wo_checklist_items ADD COLUMN created_by_id TEXT")
        db.execSQL("ALTER TABLE wo_checklist_items ADD COLUMN updated_by_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 22 / 108 / 150 — parts
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE parts ADD COLUMN custom_fields TEXT")
        db.execSQL("ALTER TABLE parts ADD COLUMN committed_reserved_quantity REAL")
        db.execSQL("ALTER TABLE parts ADD COLUMN committed_in_progress_quantity REAL")
        db.execSQL("ALTER TABLE parts ADD COLUMN committed_completed_quantity REAL")

        // -----------------------------------------------------------------------
        // CHANGE 50 — inventory_transactions
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE inventory_transactions ADD COLUMN reference_number TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 57 / 55 — log_cost_lines (cost_status, approved_by, approved_at already exist
        // from prior migration, no-op guard not needed since we're bumping to a fresh v16)
        // cost_status / approved_by / approved_at were added in an earlier migration;
        // they already exist in the v15 schema. Nothing to add here.

        // -----------------------------------------------------------------------
        // CHANGE 58 / 62 — locations
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE locations ADD COLUMN address_street TEXT")
        db.execSQL("ALTER TABLE locations ADD COLUMN parent_location_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 64 — bins, stocking_levels
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE bins ADD COLUMN capacity INTEGER")
        db.execSQL("ALTER TABLE bins ADD COLUMN current_count INTEGER")
        db.execSQL("ALTER TABLE bins ADD COLUMN bin_type TEXT")
        db.execSQL("ALTER TABLE bins ADD COLUMN created_by_id TEXT")
        db.execSQL("ALTER TABLE bins ADD COLUMN updated_by_id TEXT")

        db.execSQL("ALTER TABLE stocking_levels ADD COLUMN created_by_id TEXT")
        db.execSQL("ALTER TABLE stocking_levels ADD COLUMN updated_by_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 71 / 109 / 64 — wo_templates
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE wo_templates ADD COLUMN rows_json TEXT")
        db.execSQL("ALTER TABLE wo_templates ADD COLUMN seq INTEGER")
        db.execSQL("ALTER TABLE wo_templates ADD COLUMN created_by_id TEXT")
        db.execSQL("ALTER TABLE wo_templates ADD COLUMN updated_by_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 77 — po_lines
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE po_lines ADD COLUMN wo_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 86 — items
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE items ADD COLUMN deduct_inventory INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE items ADD COLUMN inventory_transaction_id TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 90 — cycle_counts
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE cycle_counts ADD COLUMN scheduled_date TEXT")
        db.execSQL("ALTER TABLE cycle_counts ADD COLUMN completed_date TEXT")

        // -----------------------------------------------------------------------
        // CHANGE 127 / 149 — jobs
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE jobs ADD COLUMN budget_alert_threshold REAL")
        db.execSQL("ALTER TABLE jobs ADD COLUMN budget_amount REAL")
        db.execSQL("ALTER TABLE jobs ADD COLUMN spent_amount REAL")

        // -----------------------------------------------------------------------
        // CHANGE 131 / 132 — asset_location_history
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE asset_location_history ADD COLUMN moved_by_user_id TEXT")
        db.execSQL("ALTER TABLE asset_location_history ADD COLUMN move_reason TEXT")
        db.execSQL("ALTER TABLE asset_location_history ADD COLUMN lat REAL")
        db.execSQL("ALTER TABLE asset_location_history ADD COLUMN lng REAL")

        // -----------------------------------------------------------------------
        // NEW TABLES — Changes 10, 12, 13, 17, 20, 23, 26, 27, 28, 30, 31, 34,
        //              40, 89, 100, 110, 113, 124, 146
        // -----------------------------------------------------------------------

        // asset_custom_statuses (Change 10)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS asset_custom_statuses (
                custom_status_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                name TEXT NOT NULL,
                color TEXT,
                downtime_type TEXT,
                is_downtime INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_custom_statuses_account_id ON asset_custom_statuses(account_id)")

        // asset_statuses (Change 13)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS asset_statuses (
                asset_status_id TEXT NOT NULL PRIMARY KEY,
                asset_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                status TEXT NOT NULL,
                downtime_type TEXT,
                started_at INTEGER,
                ended_at INTEGER,
                recorded_by TEXT,
                notes TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_statuses_asset_id ON asset_statuses(asset_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_statuses_account_id ON asset_statuses(account_id)")

        // meter_readings (Change 12)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS meter_readings (
                meter_reading_id TEXT NOT NULL PRIMARY KEY,
                asset_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                meter_type TEXT NOT NULL,
                reading_value REAL NOT NULL,
                read_at INTEGER NOT NULL,
                recorded_by TEXT,
                triggered_wo_ids TEXT,
                notes TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meter_readings_asset_id ON meter_readings(asset_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meter_readings_account_id ON meter_readings(account_id)")

        // work_order_assets (Change 17)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS work_order_assets (
                wo_asset_id TEXT NOT NULL PRIMARY KEY,
                wo_id TEXT NOT NULL,
                asset_id TEXT NOT NULL,
                seq_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_order_assets_wo_id ON work_order_assets(wo_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_order_assets_asset_id ON work_order_assets(asset_id)")

        // work_permits (Change 23)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS work_permits (
                permit_id TEXT NOT NULL PRIMARY KEY,
                wo_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                status TEXT NOT NULL,
                permit_type TEXT,
                required_approvers TEXT,
                approved_by TEXT,
                approved_at INTEGER,
                rejected_by TEXT,
                rejected_at INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_permits_wo_id ON work_permits(wo_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_permits_account_id ON work_permits(account_id)")

        // work_permit_signatures (Change 20)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS work_permit_signatures (
                signature_id TEXT NOT NULL PRIMARY KEY,
                permit_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                signer_id TEXT NOT NULL,
                signer_name TEXT,
                signature_url TEXT,
                signed_at INTEGER,
                all_signed INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_permit_signatures_permit_id ON work_permit_signatures(permit_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_permit_signatures_account_id ON work_permit_signatures(account_id)")

        // meter_triggers (Change 26)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS meter_triggers (
                trigger_id TEXT NOT NULL PRIMARY KEY,
                asset_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                meter_type TEXT NOT NULL,
                threshold_value REAL NOT NULL,
                wo_template_id TEXT,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meter_triggers_asset_id ON meter_triggers(asset_id)")

        // pm_plans (Change 27)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pm_plans (
                pm_plan_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                asset_id TEXT,
                title TEXT NOT NULL,
                trigger_type TEXT NOT NULL,
                wo_template_id TEXT,
                is_active INTEGER NOT NULL DEFAULT 1,
                next_due_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pm_plans_asset_id ON pm_plans(asset_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pm_plans_account_id ON pm_plans(account_id)")

        // pm_plan_intervals (Change 28)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pm_plan_intervals (
                interval_id TEXT NOT NULL PRIMARY KEY,
                pm_plan_id TEXT NOT NULL,
                cycle_number INTEGER NOT NULL,
                interval_value REAL NOT NULL,
                interval_unit TEXT,
                wo_template_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pm_plan_intervals_pm_plan_id ON pm_plan_intervals(pm_plan_id)")

        // custom_field_definitions (Change 30)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS custom_field_definitions (
                definition_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                field_name TEXT NOT NULL,
                field_type TEXT NOT NULL,
                label TEXT,
                options_json TEXT,
                is_required INTEGER NOT NULL DEFAULT 0,
                display_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_field_definitions_account_id_entity_type ON custom_field_definitions(account_id, entity_type)")

        // asset_criticalities (Change 31)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS asset_criticalities (
                criticality_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                name TEXT NOT NULL,
                level INTEGER NOT NULL,
                color TEXT,
                description TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_criticalities_account_id ON asset_criticalities(account_id)")

        // rca_reports (Change 34)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS rca_reports (
                report_id TEXT NOT NULL PRIMARY KEY,
                wo_id TEXT,
                asset_id TEXT,
                account_id TEXT NOT NULL,
                summary TEXT,
                root_cause TEXT,
                corrective_actions TEXT,
                status TEXT NOT NULL,
                author_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_rca_reports_wo_id ON rca_reports(wo_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_rca_reports_asset_id ON rca_reports(asset_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_rca_reports_account_id ON rca_reports(account_id)")

        // asset_models (Change 40/41/92)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS asset_models (
                model_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                name TEXT NOT NULL,
                manufacturer TEXT,
                default_procedure_template_id TEXT,
                recommended_parts TEXT,
                serial_number_pattern TEXT,
                help_text TEXT,
                examples TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_models_account_id ON asset_models(account_id)")

        // categories (Change 89/93/94)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS categories (
                category_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                name TEXT NOT NULL,
                entity_type TEXT,
                color TEXT,
                icon TEXT,
                default_priority TEXT,
                default_sla_hours REAL,
                parent_category_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_account_id_entity_type ON categories(account_id, entity_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parent_category_id ON categories(parent_category_id)")

        // po_comments (Change 100)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS po_comments (
                comment_id TEXT NOT NULL PRIMARY KEY,
                po_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                author_id TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_po_comments_po_id ON po_comments(po_id)")

        // sync_conflicts (Change 110/117)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_conflicts (
                conflict_id TEXT NOT NULL PRIMARY KEY,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                client_payload TEXT,
                server_payload TEXT,
                conflict_resolution TEXT,
                resolution_status TEXT NOT NULL DEFAULT 'PENDING',
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_entity_type_entity_id ON sync_conflicts(entity_type, entity_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_resolution_status ON sync_conflicts(resolution_status)")

        // owner_assignments (Change 124/125)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS owner_assignments (
                assignment_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                resource_type TEXT NOT NULL,
                resource_id TEXT NOT NULL,
                owner_user_id TEXT NOT NULL,
                secondary_owner_id TEXT,
                fallback_enabled INTEGER NOT NULL DEFAULT 0,
                role TEXT,
                assigned_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_owner_assignments_resource_type_resource_id ON owner_assignments(resource_type, resource_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_owner_assignments_owner_user_id ON owner_assignments(owner_user_id)")

        // part_transfer_requests (Change 146)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS part_transfer_requests (
                request_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                part_id TEXT NOT NULL,
                quantity REAL NOT NULL,
                from_location_id TEXT,
                to_location_id TEXT,
                status TEXT NOT NULL,
                requested_by TEXT,
                approved_by TEXT,
                approved_at INTEGER,
                notes TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_part_transfer_requests_part_id ON part_transfer_requests(part_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_part_transfer_requests_account_id ON part_transfer_requests(account_id)")

        // request_portals (Change 113)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS request_portals (
                portal_id TEXT NOT NULL PRIMARY KEY,
                account_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                server_version INTEGER NOT NULL DEFAULT 0,
                seq INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_request_portals_account_id ON request_portals(account_id)")
    }
}
