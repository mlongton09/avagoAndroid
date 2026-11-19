package com.avago.core.data.v2

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * Read mirror (P3-12b): projects v2 store rows into the legacy sync-pull JSON shape so the existing
 * Room-backed screens keep working with the v2 flag on. The mappings port the server's legacy
 * read-compat SQL (`AvagoSvc src/dal/<entity>.rs`): same key names, same enum folding, timestamps as ISO
 * strings (the legacy pull parser only reads ISO), quantities in units instead of micros.
 *
 * Each [Spec] lists the v2 tables it reads so callers can re-mirror only what a sync touched.
 */
class V2LegacyProjector {
    data class Spec(val entityType: String, val idKey: String, val tables: Set<String>, val sql: String)

    /** Projects every row of [entityType]; unknown types return an empty list. */
    fun project(conn: V2Connection, entityType: String): List<JsonObject> {
        val spec = SPECS_BY_TYPE[entityType] ?: return emptyList()
        return conn.query(spec.sql).map(::toJson)
    }

    /** The legacy JSON of one row, or null when v2 has no such row (or the type is not mirrored). */
    fun projectOne(conn: V2Connection, entityType: String, id: String): JsonObject? {
        val spec = SPECS_BY_TYPE[entityType] ?: return null
        return conn.query("SELECT * FROM (${spec.sql}) WHERE ${spec.idKey} = ?", listOf(id)).firstOrNull()?.let(::toJson)
    }

    companion object {
        /** Parents first, so the Room rows a child references already exist. */
        val SPECS: List<Spec> = listOf(
            Spec(
                "location", "location_id", setOf("locations"),
                """
                SELECT id AS location_id, name, code AS short_code, address_line1 AS address,
                       locality AS city, region AS state, postal_code, country_code AS country,
                       latitude, longitude, timezone, is_primary,
                       CASE WHEN archived_at IS NULL THEN 0 ELSE 1 END AS archived,
                       created_at, updated_at, version AS server_version
                FROM locations
                """,
            ),
            Spec(
                "bin", "bin_id", setOf("bins"),
                """
                SELECT id AS bin_id, location_id, COALESCE(name, code, '') AS name, code, barcode,
                       CASE WHEN archived_at IS NULL THEN 1 ELSE 0 END AS active,
                       created_at, updated_at, version AS server_version
                FROM bins
                """,
            ),
            Spec(
                "category", "category_id", setOf("categories"),
                """
                SELECT id AS category_id, name,
                       CASE kind WHEN 'product' THEN 'part' ELSE kind END AS entity_type,
                       color, icon, default_priority, default_sla_hours, parent_id AS parent_category_id,
                       created_at, updated_at, version AS server_version
                FROM categories
                """,
            ),
            Spec(
                "user", "user_id", setOf("account_members", "workers"),
                """
                SELECT m.user_id, w.display_name, m.role,
                       CASE m.status WHEN 'active' THEN 1 ELSE 0 END AS is_active,
                       m.created_at, m.updated_at, m.version AS server_version
                FROM account_members m
                LEFT JOIN workers w ON w.user_id = m.user_id
                """,
            ),
            Spec(
                "tech_profile", "tech_profile_id", setOf("workers"),
                """
                SELECT id AS tech_profile_id, user_id, display_name, skills,
                       COALESCE(max_active_work_orders, 5) AS max_active_wos,
                       COALESCE(available, 1) AS is_available, speed_factor, home_location_id,
                       created_at, updated_at, version AS server_version
                FROM workers
                WHERE user_id IS NOT NULL
                """,
            ),
            Spec(
                "asset", "asset_id",
                setOf("assets", "account_asset_models", "account_asset_manufacturers", "locations"),
                """
                SELECT a.id AS asset_id, a.name,
                       COALESCE(a.make_override, m.make_override, mf.name) AS make,
                       COALESCE(a.model_override, m.name) AS model,
                       a.serial_number, a.model_year AS year, a.asset_type_code AS asset_type,
                       a.avatar_color, a.avatar_initial,
                       l.address_line1, l.address_line2, l.locality AS city, l.region AS state,
                       l.postal_code, l.country_code AS country,
                       a.location_id, a.attributes, a.parent_asset_id,
                       CASE
                           WHEN a.status_code IS NULL THEN NULL
                           WHEN a.status_code = 'online' THEN 'ONLINE'
                           WHEN a.status_code = 'offline' THEN 'OFFLINE'
                           WHEN a.status_code = 'ignored' THEN 'IGNORE'
                           ELSE 'CUSTOM'
                       END AS current_status,
                       a.global_uid AS global_uuid, COALESCE(a.rentable, 0) AS is_rental,
                       (SELECT COUNT(*) FROM assets c WHERE c.parent_asset_id = a.id) AS child_count,
                       a.created_at, a.updated_at, a.version AS server_version
                FROM assets a
                LEFT JOIN account_asset_models m ON m.id = a.account_asset_model_id
                LEFT JOIN account_asset_manufacturers mf ON mf.id = m.account_manufacturer_id
                LEFT JOIN locations l ON l.id = a.location_id
                """,
            ),
            Spec(
                "wo_template", "template_id", setOf("work_order_templates", "categories"),
                """
                SELECT t.id AS template_id, t.title, t.description_template AS description,
                       c.name AS category, t.estimated_effort_minutes,
                       t.created_at, t.updated_at, t.version AS server_version
                FROM work_order_templates t
                LEFT JOIN categories c ON c.id = t.category_id
                """,
            ),
            Spec(
                "work_order", "wo_id",
                setOf("work_orders", "work_order_assets", "categories", "workers"),
                """
                SELECT w.id AS wo_id,
                       (SELECT wa.asset_id FROM work_order_assets wa WHERE wa.work_order_id = w.id
                        ORDER BY wa.is_primary DESC, wa.sort_order LIMIT 1) AS asset_id,
                       w.location_id, w.completion_activity_id AS log_id, w.title, w.description,
                       c.name AS category, w.priority,
                       CASE w.status WHEN 'pending_approval' THEN 'pending_review'
                                     WHEN 'open' THEN 'reviewed' ELSE w.status END AS status,
                       w.complexity, w.requester_user_id AS requester_id, wk.user_id AS assigned_to,
                       w.dispatcher_notes, w.required_skills, w.estimated_effort_minutes, w.currency,
                       w.due_at AS due_date, w.started_at, w.completed_at, w.attributes,
                       w.created_by, w.timezone, w.assigned_team_id, w.approval_state,
                       w.created_at, w.updated_at, w.version AS server_version
                FROM work_orders w
                LEFT JOIN categories c ON c.id = w.category_id
                LEFT JOIN workers wk ON wk.id = w.assigned_worker_id
                """,
            ),
            Spec(
                "work_order_asset", "wo_asset_id", setOf("work_order_assets"),
                """
                SELECT work_order_id || ':' || asset_id AS wo_asset_id, work_order_id AS wo_id, asset_id,
                       sort_order AS seq_order, created_at
                FROM work_order_assets
                """,
            ),
            Spec(
                "wo_assignment", "assignment_id", setOf("work_order_assignments", "workers"),
                """
                SELECT a.id AS assignment_id, a.work_order_id AS wo_id,
                       COALESCE(wk.user_id, a.worker_id) AS tech_id, a.assigned_by,
                       a.created_at AS assigned_at,
                       CASE a.acceptance_status WHEN 'assigned' THEN 'pending' ELSE a.acceptance_status END AS status,
                       a.notes,
                       CASE a.role WHEN 'lead' THEN 'LEAD' WHEN 'helper' THEN 'HELPER' ELSE 'SUPPORT' END AS role,
                       a.estimated_hours, a.created_by AS created_by_id, a.updated_by AS updated_by_id,
                       a.created_at, a.updated_at, a.version AS server_version
                FROM work_order_assignments a
                LEFT JOIN workers wk ON wk.id = a.worker_id
                """,
            ),
            Spec(
                "wo_checklist_item", "checklist_item_id",
                setOf("procedure_responses", "procedure_instances", "procedure_template_rows"),
                """
                SELECT r.id AS checklist_item_id, i.work_order_id AS wo_id, tr.sort_order AS step_order,
                       tr.label AS title, tr.required, r.completed, r.completed_by, r.completed_at,
                       r.notes, upper(tr.row_type) AS row_type, tr.description, tr.urls,
                       r.response, r.pass_fail, r.created_by AS created_by_id, r.updated_by AS updated_by_id,
                       r.created_at, r.updated_at, r.version AS server_version
                FROM procedure_responses r
                JOIN procedure_instances i ON i.id = r.instance_id
                JOIN procedure_template_rows tr ON tr.id = r.row_id AND tr.version_id = r.version_id
                WHERE i.work_order_id IS NOT NULL AND i.template_name IS NULL
                """,
            ),
            Spec(
                "wo_comment", "comment_id", setOf("comments", "comment_mentions"),
                """
                SELECT c.id AS comment_id, c.work_order_id AS wo_id,
                       COALESCE(c.author_user_id, c.created_by, '') AS author_id, c.body, c.comment_type,
                       CASE WHEN c.audience = 'internal' THEN 1 ELSE 0 END AS is_internal,
                       (SELECT json_group_array(cm.user_id) FROM comment_mentions cm
                        WHERE cm.comment_id = c.id) AS mentioned_user_ids,
                       c.created_by AS created_by_id, c.updated_by AS updated_by_id,
                       c.created_at, c.updated_at, c.version AS server_version
                FROM comments c
                WHERE c.work_order_id IS NOT NULL
                """,
            ),
            Spec(
                "log", "log_id", setOf("activity_records", "activity_cost_lines"),
                """
                SELECT a.id AS log_id, a.asset_id, a.parent_activity_id AS parent_id, a.title,
                       a.occurred_at AS log_date, COALESCE(a.category_label, a.kind) AS category,
                       CASE a.cost_mode WHEN 'detailed' THEN 'itemized' ELSE 'total' END AS cost_mode,
                       a.performed_by_name AS performed_by, a.performed_by_user_id, a.notes,
                       a.payload AS data, a.attributes, a.currency, a.exchange_rate AS exchange_rate_used,
                       a.overtime_multiplier, a.work_order_id AS wo_id,
                       k.cost_items, k.cost_labor, k.cost_misc, k.cost_tax,
                       CASE WHEN k.activity_id IS NULL THEN NULL
                            ELSE COALESCE(k.cost_items, 0) + COALESCE(k.cost_labor, 0)
                               + COALESCE(k.cost_misc, 0) + COALESCE(k.cost_tax, 0) END AS cost,
                       a.created_at, a.updated_at, a.version AS server_version
                FROM activity_records a
                LEFT JOIN (
                    SELECT activity_id,
                           SUM(CASE WHEN kind = 'part' THEN quantity_micros / 1000000.0 * CAST(unit_cost AS REAL) END) AS cost_items,
                           SUM(CASE WHEN kind = 'labor' THEN quantity_micros / 1000000.0 * CAST(unit_cost AS REAL) END) AS cost_labor,
                           SUM(CASE WHEN kind = 'misc' THEN quantity_micros / 1000000.0 * CAST(unit_cost AS REAL) END) AS cost_misc,
                           SUM(tax_amount_minor) / 100.0 AS cost_tax
                    FROM activity_cost_lines WHERE activity_id IS NOT NULL GROUP BY activity_id
                ) k ON k.activity_id = a.id
                WHERE a.asset_id IS NOT NULL
                """,
            ),
            Spec(
                "log_cost_line", "line_id", setOf("activity_cost_lines", "workers"),
                """
                SELECT l.id AS line_id, l.activity_id AS log_id, l.kind, l.sort_order AS display_order,
                       l.work_order_id AS wo_id, l.quantity_micros / 1000000.0 AS quantity, l.unit_cost,
                       l.tax_amount_minor / 100.0 AS tax_amount, l.description, l.notes,
                       wk.user_id AS user_id, upper(l.cost_status) AS cost_status, l.approved_by, l.approved_at,
                       l.created_at, l.updated_at, l.version AS server_version
                FROM activity_cost_lines l
                LEFT JOIN workers wk ON wk.id = l.performer_worker_id
                WHERE l.activity_id IS NOT NULL
                """,
            ),
            Spec(
                "part", "part_id", setOf("products", "categories"),
                """
                SELECT p.id AS part_id, p.sku AS part_number, p.name AS part_name,
                       p.manufacturer_name AS manufacturer, c.name AS category,
                       CASE p.uom_code WHEN 'ea' THEN 'each' ELSE p.uom_code END AS unit_of_measure,
                       p.standard_unit_cost AS unit_cost, p.standard_cost_currency AS currency, p.notes,
                       CASE WHEN p.archived_at IS NULL THEN 'active' ELSE 'inactive' END AS status,
                       p.default_reorder_qty_micros / 1000000.0 AS reorder_quantity, p.abc_class, p.gtin,
                       p.created_at, p.updated_at, p.version AS server_version
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                """,
            ),
            Spec(
                "inventory", "inventory_id", setOf("stock_positions"),
                """
                SELECT id AS inventory_id, product_id AS part_id, location_id, bin_id,
                       on_hand_qty_micros / 1000000.0 AS quantity_on_hand,
                       created_at, COALESCE(updated_at, created_at) AS updated_at, version AS server_version
                FROM stock_positions
                """,
            ),
            Spec(
                "stocking_level", "stocking_level_id", setOf("stocking_policies"),
                """
                SELECT id AS stocking_level_id, product_id AS part_id, location_id,
                       min_qty_micros / 1000000.0 AS min_qty, max_qty_micros / 1000000.0 AS max_qty,
                       reorder_qty_micros / 1000000.0 AS reorder_qty, safety_qty_micros / 1000000.0 AS safety_stock,
                       created_at, updated_at, version AS server_version
                FROM stocking_policies
                WHERE location_id IS NOT NULL
                """,
            ),
            Spec(
                "pm_plan", "pm_plan_id", setOf("pm_plans", "pm_triggers"),
                """
                SELECT p.id AS pm_plan_id, p.title, p.asset_id, p.work_order_template_id AS wo_template_id,
                       COALESCE(p.active, 1) AS is_active,
                       CASE
                           WHEN EXISTS (SELECT 1 FROM pm_triggers t WHERE t.pm_plan_id = p.id AND t.kind = 'meter')
                            AND EXISTS (SELECT 1 FROM pm_triggers t WHERE t.pm_plan_id = p.id AND t.kind <> 'meter')
                               THEN 'CALENDAR_OR_METER'
                           WHEN EXISTS (SELECT 1 FROM pm_triggers t WHERE t.pm_plan_id = p.id AND t.kind = 'meter')
                               THEN 'METER'
                           ELSE 'CALENDAR'
                       END AS trigger_type,
                       (SELECT MIN(t.next_due_at) FROM pm_triggers t WHERE t.pm_plan_id = p.id) AS next_due_at,
                       p.created_at, p.updated_at, p.version AS server_version
                FROM pm_plans p
                """,
            ),
            Spec(
                "meter_reading", "meter_reading_id", setOf("meter_readings", "asset_meters"),
                """
                SELECT r.id AS meter_reading_id, r.asset_id, COALESCE(m.meter_kind, 'odometer') AS meter_type,
                       r.value_micros / 1000000.0 AS reading_value, r.read_at, r.recorded_by,
                       r.created_at, r.updated_at, r.version AS server_version
                FROM meter_readings r
                LEFT JOIN asset_meters m ON m.id = r.meter_id
                """,
            ),
            Spec(
                // Photos are file objects linked by an attachment; the latest link wins (attach() re-homes).
                "photo", "photo_id", setOf("files", "attachments"),
                """
                SELECT f.id AS photo_id,
                       COALESCE(a.asset_id, a.activity_id, a.work_order_id, a.asset_loan_id) AS entity_id,
                       CASE WHEN a.asset_id IS NOT NULL THEN 'asset'
                            WHEN a.activity_id IS NOT NULL THEN 'log'
                            WHEN a.work_order_id IS NOT NULL THEN 'work_order'
                            ELSE 'asset_loan' END AS entity_type,
                       f.storage_key, COALESCE(a.sort_order, 0) AS sort_order,
                       f.thumbnail_storage_key, f.image_width, f.image_height,
                       f.created_at, f.updated_at, f.version AS server_version
                FROM files f
                JOIN attachments a ON a.id = (
                    SELECT a2.id FROM attachments a2
                    WHERE a2.file_object_id = f.id AND a2.document_id IS NULL
                      AND COALESCE(a2.asset_id, a2.activity_id, a2.work_order_id, a2.asset_loan_id) IS NOT NULL
                    ORDER BY a2.created_at DESC, a2.id DESC LIMIT 1)
                """,
            ),
        ).map { it.copy(sql = it.sql.trimIndent()) }

        val SPECS_BY_TYPE: Map<String, Spec> = SPECS.associateBy { it.entityType }

        /** Entity types (in [SPECS] order) whose source tables intersect [changedTables]. */
        fun typesFor(changedTables: Set<String>): List<String> =
            SPECS.filter { spec -> spec.tables.any { it in changedTables } }.map { it.entityType }

        private val DATE_KEYS = setOf("due_date", "log_date")

        /** v2 row → legacy JSON: ms timestamps become ISO-8601, everything else keeps its SQLite type. */
        fun toJson(row: V2Row): JsonObject = JsonObject(
            row.mapValues { (key, value) ->
                when (value) {
                    null -> JsonNull
                    is Long, is Int -> if (key.endsWith("_at") || key in DATE_KEYS) {
                        JsonPrimitive(Instant.ofEpochMilli((value as Number).toLong()).toString())
                    } else {
                        JsonPrimitive(value as Number)
                    }
                    is Number -> JsonPrimitive(value)
                    is ByteArray -> JsonNull
                    else -> JsonPrimitive(value.toString())
                }
            },
        )
    }
}
