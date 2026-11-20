package com.avago.core.data.v2.repo

import androidx.core.net.toUri
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetDraft
import com.avago.core.data.domain.AssetFilter
import com.avago.core.data.domain.AssetMeter
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.AssetStatusCode
import com.avago.core.data.domain.AssetStatusInterval
import com.avago.core.data.domain.AssetToolDetails
import com.avago.core.data.domain.AssetVehicleDetails
import com.avago.core.data.domain.Attachment
import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.DowntimeKind
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.MeterReading
import com.avago.core.data.domain.NoMeterException
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.ToolDetailsDraft
import com.avago.core.data.domain.VehicleDetailsDraft
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2FileStager
import com.avago.core.data.v2.V2Money
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.commands.MaintCommands
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.micros
import com.avago.core.data.v2.scalarLong
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2AssetRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
    private val stager: V2FileStager,
) : AssetRepository {

    override fun observeAssets(filter: AssetFilter): Flow<List<Asset>> =
        store.observe(ASSET_TABLES, emptyList()) { conn -> assets(conn, filter) }

    override fun observeAsset(id: String): Flow<Asset?> = store.observe(ASSET_TABLES, null) { conn -> assetById(conn, id) }

    override suspend fun asset(id: String): Asset? = store.read(null) { conn -> assetById(conn, id) }

    override suspend fun findByCode(code: String): Asset? {
        val c = code.trim()
        if (c.isEmpty()) return null
        return store.read(null) { conn ->
            V2MaintRows.assets(conn, "global_uid = ? OR serial_number = ? OR vin = ? OR id = ?", listOf(c, c, c, c)) {
                it["global_uid"] == c || it["serial_number"] == c || it["vin"] == c || it["id"] == c
            }.firstOrNull()
        }
    }

    override fun observeMeters(assetId: String): Flow<List<AssetMeter>> =
        store.observe(setOf("asset_meters", "meter_readings"), emptyList()) { conn -> meters(conn, assetId) }

    override fun observeReadings(assetId: String, limit: Int): Flow<List<MeterReading>> =
        store.observe(setOf("meter_readings"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "meter_readings", "asset_id = ?", listOf(assetId)) { it["asset_id"] == assetId }
                .map(::reading)
                .sortedByDescending { it.readAt }
                .take(limit)
        }

    override fun observeStatusHistory(assetId: String): Flow<List<AssetStatusInterval>> =
        store.observe(setOf("asset_status_intervals"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "asset_status_intervals", "asset_id = ?", listOf(assetId)) { it["asset_id"] == assetId }
                .map { r ->
                    AssetStatusInterval(
                        id = r.str("id"),
                        status = AssetStatusCode.fromWire(r.strOrNull("status_code")),
                        downtimeKind = DowntimeKind.fromWire(r.strOrNull("downtime_type")),
                        start = r.longOrNull("valid_during_start") ?: 0L,
                        end = r.longOrNull("valid_during_end"),
                        reason = r.strOrNull("reason"),
                    )
                }
                .sortedByDescending { it.start }
        }

    override fun observeVehicleDetails(assetId: String): Flow<AssetVehicleDetails?> =
        store.observe(setOf("asset_vehicle_details"), null) { conn -> vehicleDetails(conn, assetId) }

    override fun observeToolDetails(assetId: String): Flow<AssetToolDetails?> =
        store.observe(setOf("asset_tool_details"), null) { conn -> toolDetails(conn, assetId) }

    override fun observeAttachments(target: AttachmentTarget): Flow<List<Attachment>> {
        val (column, id) = target.column()
        return store.observe(setOf("attachments", "files"), emptyList()) { conn ->
            V2MaintRows.attachments(conn, "$column = ?", listOf(id), { it[column] == id }, stager::localFile)
        }
    }

    override fun observeImages(limit: Int): Flow<List<Attachment>> =
        store.observe(setOf("attachments", "files"), emptyList()) { conn ->
            V2MaintRows.attachments(conn, "file_object_id IS NOT NULL", emptyList(), { it["file_object_id"] != null }, stager::localFile)
                .filter { it.file?.isImage == true }
                .sortedByDescending { it.createdAt }
                .take(limit)
        }

    override suspend fun create(draft: AssetDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(MaintCommands.createAsset(id, draft, now, commands.currentUserId()))
        return id
    }

    override suspend fun update(id: String, draft: AssetDraft) {
        val stored = asset(id) ?: throw RecordNotFoundException("asset", id)
        MaintCommands.updateAsset(stored, draft, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun delete(id: String) {
        val stored = asset(id) ?: throw RecordNotFoundException("asset", id)
        commands.submit(MaintCommands.deleteAsset(stored))
    }

    override suspend fun setStatus(id: String, status: AssetStatusCode, downtime: DowntimeKind?, reason: String?) {
        val stored = asset(id) ?: throw RecordNotFoundException("asset", id)
        val now = System.currentTimeMillis()
        commands.submit(
            MaintCommands.setAssetStatus(stored, status, downtime, reason, V2Uuid.v7(now), now, commands.currentUserId()),
        )
    }

    override suspend fun setParent(id: String, parentAssetId: String?) {
        require(id != parentAssetId) { "an asset cannot be its own parent" }
        val stored = asset(id) ?: throw RecordNotFoundException("asset", id)
        commands.submit(MaintCommands.setAssetParent(stored, parentAssetId, System.currentTimeMillis()))
    }

    override suspend fun recordReading(assetId: String, value: BigDecimal, readAt: Long?, meterId: String?): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val meter = store.read(null) { conn ->
            val all = meters(conn, assetId)
            if (meterId != null) all.firstOrNull { it.id == meterId } else all.firstOrNull { it.isDefault } ?: all.firstOrNull()
        } ?: throw NoMeterException(assetId)
        commands.submit(MaintCommands.recordReading(id, assetId, meter.id, value, readAt ?: now, now, commands.currentUserId()))
        return id
    }

    override suspend fun correctReading(id: String, value: BigDecimal, reason: String) {
        val stored = store.read(null) { conn ->
            V2OverlayMerge.query(conn, "meter_readings", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()?.let(::reading)
        } ?: throw RecordNotFoundException("meter reading", id)
        commands.submit(MaintCommands.correctReading(stored, value, reason, System.currentTimeMillis()))
    }

    override suspend fun attach(target: AttachmentTarget, file: LocalFileRef): String {
        val staged = stager.stage(file.uri.toUri(), file.fallbackMediaType)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(MaintCommands.attach(id, target, staged, null, now, commands.currentUserId()))
        return id
    }

    override suspend fun detach(attachmentId: String) {
        val version = store.read(null) { conn -> V2MaintRows.version(conn, "attachments", attachmentId) }
            ?: throw RecordNotFoundException("attachment", attachmentId)
        commands.submit(MaintCommands.detach(attachmentId, version))
    }

    override suspend fun setPrimaryAttachment(target: AttachmentTarget, attachmentId: String) {
        val (column, id) = target.column()
        val ordered = store.read(emptyList()) { conn ->
            V2MaintRows.attachments(conn, "$column = ?", listOf(id), { it[column] == id }, stager::localFile)
        }
        if (ordered.none { it.id == attachmentId }) throw RecordNotFoundException("attachment", attachmentId)
        MaintCommands.reorderAttachments(ordered, attachmentId, System.currentTimeMillis()).forEach { commands.submit(it) }
    }

    override suspend fun updateToolDetails(assetId: String, draft: ToolDetailsDraft) {
        val stored = store.read(null) { conn -> toolDetails(conn, assetId) }
        MaintCommands.updateToolDetails(assetId, stored, draft, System.currentTimeMillis(), commands.currentUserId())
            ?.let { commands.submit(it) }
    }

    override suspend fun updateVehicleDetails(assetId: String, draft: VehicleDetailsDraft) {
        val stored = store.read(null) { conn -> vehicleDetails(conn, assetId) }
        MaintCommands.updateVehicleDetails(assetId, stored, draft, System.currentTimeMillis(), commands.currentUserId())
            ?.let { commands.submit(it) }
    }

    override suspend fun createMeter(assetId: String, kind: String, unit: String): String {
        val existing = store.read(emptyList()) { conn -> meters(conn, assetId) }
        require(existing.none { it.kind == kind }) { "the asset already has a $kind meter" }
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(MaintCommands.createMeter(id, assetId, kind, unit, existing.isEmpty(), now, commands.currentUserId()))
        return id
    }

    private companion object {
        val ASSET_TABLES = setOf("assets", "locations", "work_orders", "work_order_assets", "attachments", "files")

        fun assets(conn: V2Connection, filter: AssetFilter): List<Asset> {
            val q = filter.query.trim().lowercase()
            return V2MaintRows.assets(conn).filter { a ->
                (filter.locationId == null || a.locationId == filter.locationId) &&
                    (filter.status == null || a.status == filter.status) &&
                    when {
                        filter.parentAssetId != null -> a.parentAssetId == filter.parentAssetId
                        filter.topLevelOnly -> a.parentAssetId == null
                        else -> true
                    } &&
                    (
                        q.isEmpty() || listOfNotNull(a.name, a.make, a.model, a.serialNumber, a.vin, a.assetTypeCode, a.locationName)
                            .any { it.lowercase().contains(q) } || a.tags.any { it.lowercase().contains(q) }
                        )
            }
        }

        fun assetById(conn: V2Connection, id: String): Asset? {
            val asset = V2MaintRows.assets(conn, "id = ?", listOf(id)) { it["id"] == id }.firstOrNull() ?: return null
            val downtime = conn.query(
                "SELECT downtime_type FROM asset_status_intervals WHERE asset_id = ? AND valid_during_end IS NULL " +
                    "ORDER BY valid_during_start DESC LIMIT 1",
                listOf(id),
            ).firstOrNull()?.strOrNull("downtime_type")
            val children = conn.scalarLong("SELECT COUNT(*) FROM assets WHERE parent_asset_id = ?", listOf(id)) ?: 0L
            return asset.copy(downtimeKind = V2MaintRows.downtime(downtime), childCount = children.toInt())
        }

        fun meters(conn: V2Connection, assetId: String): List<AssetMeter> {
            val latest = V2OverlayMerge.query(conn, "meter_readings", "asset_id = ?", listOf(assetId)) { it["asset_id"] == assetId }
                .groupBy { it.str("meter_id") }
                .mapValues { (_, rows) -> rows.maxBy { it.longOrNull("read_at") ?: 0L } }
            return V2OverlayMerge.query(conn, "asset_meters", "asset_id = ?", listOf(assetId)) { it["asset_id"] == assetId }
                .map { r ->
                    val id = r.str("id")
                    val last = latest[id]
                    AssetMeter(
                        id = id,
                        assetId = assetId,
                        kind = r.str("meter_kind"),
                        unit = r.str("unit"),
                        isDefault = r.bool("is_default"),
                        lastValue = last?.micros("value_micros"),
                        lastReadAt = last?.longOrNull("read_at"),
                    )
                }
                .sortedWith(compareByDescending<AssetMeter> { it.isDefault }.thenBy { it.kind })
        }

        fun vehicleDetails(conn: V2Connection, assetId: String): AssetVehicleDetails? =
            V2OverlayMerge.query(conn, "asset_vehicle_details", "asset_id = ?", listOf(assetId)) { it["asset_id"] == assetId }
                .firstOrNull()?.let { r ->
                    AssetVehicleDetails(
                        assetId = r.str("asset_id"),
                        bodyClass = r.strOrNull("body_class"),
                        fuelType = r.strOrNull("fuel_type"),
                        gvwr = r.strOrNull("gvwr"),
                        registrationNumber = r.strOrNull("registration_number"),
                        registrationExpiresOn = r.strOrNull("registration_expires_on"),
                        registrationFileId = r.strOrNull("registration_file_id"),
                        insurancePolicyNumber = r.strOrNull("insurance_policy_number"),
                        insuranceExpiresOn = r.strOrNull("insurance_expires_on"),
                        insuranceFileId = r.strOrNull("insurance_file_id"),
                    )
                }

        fun toolDetails(conn: V2Connection, assetId: String): AssetToolDetails? =
            V2OverlayMerge.query(conn, "asset_tool_details", "asset_id = ?", listOf(assetId)) { it["asset_id"] == assetId }
                .firstOrNull()?.let { r ->
                    val currency = r.strOrNull("currency")
                    AssetToolDetails(
                        assetId = r.str("asset_id"),
                        calibrationFileId = r.strOrNull("calibration_file_id"),
                        calibratedOn = r.strOrNull("calibrated_on"),
                        calibrationIntervalDays = r.longOrNull("calibration_interval_days")?.toInt(),
                        calibrationDueOn = r.strOrNull("calibration_due_on"),
                        usageUnit = r.strOrNull("usage_unit"),
                        usageValue = r.longOrNull("usage_value_micros")?.let { BigDecimal.valueOf(it, 6).stripTrailingZeros() },
                        replacementValue = r.longOrNull("replacement_value_minor")?.let { V2Money.toMajor(it, currency) },
                        currency = currency,
                        trackerTagId = r.strOrNull("tracker_tag_id"),
                        trackerKind = r.strOrNull("tracker_kind"),
                    )
                }

        fun AttachmentTarget.column(): Pair<String, String> = when (this) {
            is AttachmentTarget.ToAsset -> "asset_id" to assetId
            is AttachmentTarget.ToActivity -> "activity_id" to activityId
            is AttachmentTarget.ToWorkOrder -> "work_order_id" to workOrderId
            is AttachmentTarget.ToCostLine -> "cost_line_id" to costLineId
        }

        fun reading(r: V2Row) = MeterReading(
            id = r.str("id"),
            assetId = r.str("asset_id"),
            meterId = r.str("meter_id"),
            value = r.micros("value_micros"),
            readAt = r.longOrNull("read_at") ?: 0L,
            source = r.strOrNull("source") ?: "manual",
            activityId = r.strOrNull("activity_id"),
            correctionReason = r.strOrNull("correction_reason"),
            version = r.longOrNull("version") ?: 0L,
        )
    }
}
