package com.avago.core.data.v2.repo

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetStatusCode
import com.avago.core.data.domain.Attachment
import com.avago.core.data.domain.Certification
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineType
import com.avago.core.data.domain.DefectState
import com.avago.core.data.domain.DowntimeKind
import com.avago.core.data.domain.InspectionDefect
import com.avago.core.data.domain.ReferenceValue
import com.avago.core.data.domain.StoredFile
import com.avago.core.data.domain.TimeEntry
import com.avago.core.data.domain.Worker
import com.avago.core.data.domain.WorkerRate
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.commands.parseJsonList
import com.avago.core.data.v2.decimalOrNull
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.long
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.micros
import com.avago.core.data.v2.microsOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/** Row mappers and shared queries of the maintenance tables (overlays applied). */
internal object V2MaintRows {

    // ------------------------------------------------------------------ assets

    fun assets(conn: V2Connection, where: String? = null, args: List<Any?> = emptyList(), predicate: (V2Row) -> Boolean = { true }): List<Asset> {
        val rows = V2OverlayMerge.query(conn, "assets", where, args, predicate)
        if (rows.isEmpty()) return emptyList()
        val locations = conn.query("SELECT id, name FROM locations").associate { it.str("id") to it.str("name") }
        val children = conn.query(
            "SELECT parent_asset_id, COUNT(*) AS n FROM assets WHERE parent_asset_id IS NOT NULL GROUP BY parent_asset_id",
        ).associate { it.str("parent_asset_id") to it.long("n").toInt() }
        val openWork = conn.query(
            "SELECT a.asset_id, COUNT(*) AS n FROM work_order_assets a JOIN work_orders w ON w.id = a.work_order_id " +
                "WHERE w.status NOT IN ('completed','closed','cancelled','skipped') GROUP BY a.asset_id",
        ).associate { it.str("asset_id") to it.long("n").toInt() }
        val covers = conn.query(
            "SELECT a.asset_id, a.file_object_id FROM attachments a JOIN files f ON f.id = a.file_object_id " +
                "WHERE a.asset_id IS NOT NULL AND f.mime_type LIKE 'image/%' ORDER BY a.sort_order, a.created_at",
        ).groupBy { it.str("asset_id") }.mapValues { it.value.first().str("file_object_id") }
        return rows.map { r ->
            val id = r.str("id")
            asset(r, locations[r.strOrNull("location_id")]).copy(
                childCount = children[id] ?: 0,
                openWorkOrders = openWork[id] ?: 0,
                coverFileId = covers[id],
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Asset::name).thenBy(Asset::id))
    }

    fun asset(r: V2Row, locationName: String?): Asset {
        val id = r.str("id")
        return Asset(
            id = id,
            name = r.str("name"),
            assetTypeCode = r.strOrNull("asset_type_code"),
            make = r.strOrNull("make_override"),
            model = r.strOrNull("model_override"),
            modelYear = r.intOrNull("model_year"),
            serialNumber = r.strOrNull("serial_number"),
            vin = r.strOrNull("vin"),
            tags = parseJsonList(r.strOrNull("tags")),
            locationId = r.strOrNull("location_id"),
            locationName = locationName,
            parentAssetId = r.strOrNull("parent_asset_id"),
            status = AssetStatusCode.fromWire(r.strOrNull("status_code")),
            statusSince = r.longOrNull("status_since") ?: 0L,
            downtimeKind = null,
            conditionScore = r.intOrNull("condition_score"),
            rentable = r.bool("rentable"),
            avatarColor = r.strOrNull("avatar_color"),
            avatarInitial = r.strOrNull("avatar_initial"),
            attributes = r.strOrNull("attributes") ?: "{}",
            globalUid = r.strOrNull("global_uid") ?: id,
            version = r.longOrNull("version") ?: 0L,
        )
    }

    fun assetNames(conn: V2Connection): Map<String, String> =
        V2OverlayMerge.query(conn, "assets").associate { it.str("id") to it.str("name") }

    // ------------------------------------------------------------------ files and attachments

    fun file(r: V2Row, localFile: (String) -> File?): StoredFile {
        val id = r.str("id")
        return StoredFile(
            id = id,
            name = r.strOrNull("original_name"),
            mimeType = r.strOrNull("mime_type"),
            sizeBytes = r.longOrNull("size_bytes"),
            status = r.strOrNull("status") ?: "pending",
            localFile = localFile(id),
        )
    }

    fun attachments(
        conn: V2Connection,
        where: String,
        args: List<Any?>,
        predicate: (V2Row) -> Boolean,
        localFile: (String) -> File?,
    ): List<Attachment> {
        val rows = V2OverlayMerge.query(conn, "attachments", where, args, predicate)
        if (rows.isEmpty()) return emptyList()
        val fileIds = rows.mapNotNull { it.strOrNull("file_object_id") }.toSet()
        val files = V2OverlayMerge.query(conn, "files") { it["id"] in fileIds }
            .filter { it.str("id") in fileIds }
            .associate { it.str("id") to file(it, localFile) }
        return rows.map { r ->
            val fileId = r.strOrNull("file_object_id")
            Attachment(
                id = r.str("id"),
                file = fileId?.let(files::get),
                fileId = fileId,
                documentId = r.strOrNull("document_id"),
                assetId = r.strOrNull("asset_id"),
                activityId = r.strOrNull("activity_id"),
                workOrderId = r.strOrNull("work_order_id"),
                sortOrder = r.intOrNull("sort_order") ?: 0,
                createdAt = r.longOrNull("created_at") ?: 0L,
            )
        }.sortedWith(compareBy<Attachment> { it.sortOrder }.thenBy { it.createdAt })
    }

    // ------------------------------------------------------------------ workers and codes

    fun workers(conn: V2Connection): List<Worker> {
        val active = conn.query(
            "SELECT assigned_worker_id, COUNT(*) AS n FROM work_orders WHERE assigned_worker_id IS NOT NULL " +
                "AND status NOT IN ('completed','closed','cancelled','skipped') GROUP BY assigned_worker_id",
        ).associate { it.str("assigned_worker_id") to it.long("n").toInt() }
        val rates = currentRates(conn)
        return V2OverlayMerge.query(conn, "workers", "status != 'removed'") { it["status"] != "removed" }
            .map { worker(it).copy(activeWorkOrders = active[it.str("id")] ?: 0, hourlyRate = rates[it.str("id")]) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Worker::displayName).thenBy(Worker::id))
    }

    fun worker(r: V2Row) = Worker(
        id = r.str("id"),
        userId = r.strOrNull("user_id"),
        displayName = r.strOrNull("display_name") ?: r.strOrNull("invite_email") ?: "",
        kind = r.strOrNull("worker_kind") ?: "member",
        role = r.strOrNull("role"),
        status = r.strOrNull("status") ?: "active",
        skills = parseJsonList(r.strOrNull("skills")),
        maxActiveWorkOrders = r.intOrNull("max_active_work_orders"),
        available = r.bool("available"),
        homeLocationId = r.strOrNull("home_location_id"),
        notes = r.strOrNull("notes"),
        version = r.longOrNull("version") ?: 0L,
        certifications = certifications(r.strOrNull("attributes")),
        attributes = r.strOrNull("attributes") ?: "{}",
    )

    fun workerByUser(conn: V2Connection, userId: String?): Worker? = userId?.let { uid ->
        V2OverlayMerge.query(conn, "workers", "user_id = ?", listOf(uid)) { it["user_id"] == uid }.firstOrNull()
            ?.let { r -> worker(r).copy(hourlyRate = currentRates(conn)[r.str("id")]) }
    }

    /** Worker id to the hourly cost rate valid today (role "", kind hourly; the latest start wins). */
    fun currentRates(conn: V2Connection, today: LocalDate = LocalDate.now()): Map<String, WorkerRate> {
        val day = today.toString()
        return V2OverlayMerge.query(conn, "worker_rates", "role = '' AND rate_kind = 'hourly'") { it["role"] == "" && it["rate_kind"] == "hourly" }
            .filter { r -> r.str("valid_during_start") <= day && r.strOrNull("valid_during_end").let { it == null || it > day } }
            .groupBy { it.str("worker_id") }
            .mapValues { (_, rows) ->
                rows.maxBy { it.str("valid_during_start") }.let { r ->
                    WorkerRate(
                        id = r.str("id"),
                        costRate = r.decimalOrNull("cost_rate"),
                        currency = r.str("currency"),
                        validFrom = LocalDate.parse(r.str("valid_during_start")),
                        version = r.longOrNull("version") ?: 0L,
                    )
                }
            }
    }

    /** Certifications in a worker's `attributes` JSON; entries without a name are skipped. */
    fun certifications(attributes: String?): List<Certification> {
        val array = attributes?.let { runCatching { Json.parseToJsonElement(it).jsonObject["certifications"] as? JsonArray }.getOrNull() }
            ?: return emptyList()
        return array.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun text(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }
            val name = text("name") ?: return@mapNotNull null
            Certification(
                name = name,
                issuer = text("issuer"),
                number = text("number"),
                expiresOn = text("expires_on")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
            )
        }
    }

    fun referenceValues(conn: V2Connection, kind: String): List<ReferenceValue> =
        V2OverlayMerge.query(conn, "reference_values", "kind = ? AND archived_at IS NULL", listOf(kind)) {
            it["kind"] == kind && it["archived_at"] == null
        }.map(::referenceValue).sortedWith(compareBy<ReferenceValue> { it.sortOrder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    fun referenceValue(r: V2Row) = ReferenceValue(
        id = r.str("id"),
        kind = r.str("kind"),
        code = r.str("code"),
        label = r.str("label"),
        description = r.strOrNull("description"),
        color = r.strOrNull("color"),
        sortOrder = r.intOrNull("sort_order") ?: 0,
    )

    // ------------------------------------------------------------------ activities and cost lines

    fun activities(conn: V2Connection, where: String?, args: List<Any?>, predicate: (V2Row) -> Boolean): List<Activity> {
        val rows = V2OverlayMerge.query(conn, "activity_records", where, args, predicate)
        if (rows.isEmpty()) return emptyList()
        val names = assetNames(conn)
        val ids = rows.map { it.str("id") }.toSet()
        val totals = costLines(conn, null, emptyList()) { true }
            .filter { it.activityId in ids }
            .groupBy { it.activityId }
            .mapValues { (_, lines) -> lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total } }
        val meters = conn.query("SELECT activity_id, value_micros FROM meter_readings WHERE activity_id IS NOT NULL")
            .associate { it.str("activity_id") to it.micros("value_micros") }
        return rows.map { r ->
            val id = r.str("id")
            activity(r, names[r.strOrNull("asset_id")], meters[id], totals[id] ?: BigDecimal.ZERO)
        }.sortedWith(compareByDescending<Activity> { it.occurredAt }.thenByDescending { it.id })
    }

    fun activity(r: V2Row, assetName: String?, meterValue: BigDecimal?, total: BigDecimal) = Activity(
        id = r.str("id"),
        assetId = r.strOrNull("asset_id"),
        assetName = assetName,
        workOrderId = r.strOrNull("work_order_id"),
        parentActivityId = r.strOrNull("parent_activity_id"),
        type = ActivityType.fromWire(r.strOrNull("kind")),
        categoryLabel = r.strOrNull("category_label"),
        title = r.strOrNull("title"),
        occurredAt = r.longOrNull("occurred_at") ?: 0L,
        performedByUserId = r.strOrNull("performed_by_user_id"),
        performedByName = r.strOrNull("performed_by_name"),
        providerName = r.strOrNull("provider_name"),
        currency = r.strOrNull("currency"),
        overtimeMultiplier = r.decimalOrNull("overtime_multiplier"),
        notes = r.strOrNull("notes"),
        payload = r.strOrNull("payload") ?: "{}",
        attributes = r.strOrNull("attributes") ?: "{}",
        meterValue = meterValue,
        totalCost = total,
        createdAt = r.longOrNull("created_at") ?: 0L,
        version = r.longOrNull("version") ?: 0L,
    )

    fun costLines(conn: V2Connection, where: String?, args: List<Any?>, predicate: (V2Row) -> Boolean): List<CostLine> =
        V2OverlayMerge.query(conn, "activity_cost_lines", where, args, predicate)
            .map(::costLine)
            .sortedWith(compareBy<CostLine> { it.sortOrder }.thenBy { it.id })

    fun costLine(r: V2Row) = CostLine(
        id = r.str("id"),
        activityId = r.strOrNull("activity_id"),
        workOrderId = r.strOrNull("work_order_id"),
        kind = CostLineType.fromWire(r.strOrNull("kind")),
        sortOrder = r.intOrNull("sort_order") ?: 0,
        productId = r.strOrNull("product_id"),
        performerWorkerId = r.strOrNull("performer_worker_id"),
        description = r.strOrNull("description"),
        quantity = r.microsOrNull("quantity_micros") ?: BigDecimal.ZERO,
        uomCode = r.strOrNull("uom_code"),
        unitCost = r.decimalOrNull("unit_cost"),
        taxAmountMinor = r.longOrNull("tax_amount_minor"),
        glAccountId = r.strOrNull("gl_account_id"),
        costStatus = r.strOrNull("cost_status") ?: "actual",
        partNumber = r.strOrNull("part_number"),
        notes = r.strOrNull("notes"),
        version = r.longOrNull("version") ?: 0L,
    )

    fun defect(r: V2Row) = InspectionDefect(
        id = r.str("id"),
        assetId = r.str("asset_id"),
        itemId = r.strOrNull("item_id"),
        itemKey = r.strOrNull("item_key") ?: "",
        label = r.strOrNull("label"),
        cornerLabel = r.strOrNull("corner_label"),
        sectionTitle = r.strOrNull("section_title"),
        bucket = r.strOrNull("bucket") ?: "nr",
        severity = r.strOrNull("severity"),
        note = r.strOrNull("note"),
        locationLabel = r.strOrNull("location_label"),
        status = DefectState.fromWire(r.strOrNull("status")),
        sourceActivityId = r.strOrNull("source_activity_id"),
        lastSeenActivityId = r.strOrNull("last_seen_activity_id"),
        reportedAt = r.longOrNull("reported_at") ?: 0L,
        lastSeenAt = r.longOrNull("last_seen_at"),
        resolvedAt = r.longOrNull("resolved_at"),
        resolutionNote = r.strOrNull("resolution_note"),
        workOrderId = r.strOrNull("work_order_id"),
        version = r.longOrNull("version") ?: 0L,
    )

    fun timeEntry(r: V2Row, workerName: String? = null) = TimeEntry(
        id = r.str("id"),
        workerId = r.str("worker_id"),
        workOrderId = r.strOrNull("work_order_id"),
        start = r.longOrNull("during_start") ?: 0L,
        end = r.longOrNull("during_end"),
        breakMinutes = r.intOrNull("break_minutes") ?: 0,
        status = r.strOrNull("status") ?: "open",
        workerName = workerName,
        notes = r.strOrNull("notes"),
        rejectReason = r.strOrNull("reject_reason"),
        approvedAt = r.longOrNull("approved_at"),
        version = r.longOrNull("version") ?: 0L,
    )

    fun downtime(raw: String?): DowntimeKind? = DowntimeKind.fromWire(raw)

    /** Row version of [table] [id] (overlay applied), for base_version of id-only deletes. */
    fun version(conn: V2Connection, table: String, id: String): Long? =
        V2OverlayMerge.query(conn, table, "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()?.longOrNull("version")
}
