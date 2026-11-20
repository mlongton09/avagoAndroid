package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.math.BigDecimal

/** Operational status of an asset (v2 `assets.status_code`). */
enum class AssetStatusCode(val wire: String) {
    ONLINE("online"), OFFLINE("offline"), IGNORED("ignored"), CUSTOM("custom");

    companion object {
        fun fromWire(raw: String?): AssetStatusCode = entries.firstOrNull { it.wire == raw } ?: ONLINE
    }
}

enum class DowntimeKind(val wire: String) {
    PLANNED("planned"), UNPLANNED("unplanned");

    companion object {
        fun fromWire(raw: String?): DowntimeKind? = entries.firstOrNull { it.wire == raw }
    }
}

/** A maintained asset (v2 `assets`). */
data class Asset(
    val id: String,
    val name: String,
    val assetTypeCode: String?,
    val make: String?,
    val model: String?,
    val modelYear: Int?,
    val serialNumber: String?,
    val vin: String?,
    val tags: List<String>,
    val locationId: String?,
    val locationName: String?,
    val parentAssetId: String?,
    val status: AssetStatusCode,
    val statusSince: Long,
    val downtimeKind: DowntimeKind?,
    val conditionScore: Int?,
    val rentable: Boolean,
    val avatarColor: String?,
    val avatarInitial: String?,
    /** Account-defined fields (`attributes`, JSON object text). */
    val attributes: String,
    val globalUid: String,
    val version: Long,
    val childCount: Int = 0,
    val openWorkOrders: Int = 0,
    /** First picture attached to the asset, if any. */
    val coverFileId: String? = null,
) {
    /** "2019 Toyota Hilux" style subtitle. */
    val makeModel: String?
        get() = listOfNotNull(modelYear?.toString(), make, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }

    val initial: String get() = avatarInitial?.takeIf { it.isNotBlank() } ?: name.trim().take(1).uppercase()
}

/** Fields the user edits on an asset. Blank text means "no value". */
data class AssetDraft(
    val name: String = "",
    val assetTypeCode: String? = null,
    val make: String? = null,
    val model: String? = null,
    val modelYear: Int? = null,
    val serialNumber: String? = null,
    val vin: String? = null,
    val tags: List<String> = emptyList(),
    val locationId: String? = null,
    val conditionScore: Int? = null,
    val rentable: Boolean = false,
    val avatarColor: String? = null,
    val avatarInitial: String? = null,
    /** Whole account-defined attributes object (JSON text); null leaves the stored object unchanged. */
    val attributes: String? = null,
) {
    companion object {
        fun of(a: Asset) = AssetDraft(
            name = a.name, assetTypeCode = a.assetTypeCode, make = a.make, model = a.model, modelYear = a.modelYear,
            serialNumber = a.serialNumber, vin = a.vin, tags = a.tags, locationId = a.locationId,
            conditionScore = a.conditionScore, rentable = a.rentable, avatarColor = a.avatarColor,
            avatarInitial = a.avatarInitial, attributes = a.attributes,
        )
    }
}

/** A meter on an asset (`asset_meters`) with its latest reading. */
data class AssetMeter(
    val id: String,
    val assetId: String,
    val kind: String,
    val unit: String,
    val isDefault: Boolean,
    val lastValue: BigDecimal?,
    val lastReadAt: Long?,
)

data class MeterReading(
    val id: String,
    val assetId: String,
    val meterId: String,
    val value: BigDecimal,
    val readAt: Long,
    val source: String,
    val activityId: String?,
    val correctionReason: String?,
    val version: Long,
)

/** Road-vehicle subtype of an asset (`asset_vehicle_details`); expiry dates are ISO `yyyy-MM-dd`. */
data class AssetVehicleDetails(
    val assetId: String,
    val bodyClass: String?,
    val fuelType: String?,
    val gvwr: String?,
    val registrationNumber: String?,
    val registrationExpiresOn: String?,
    val registrationFileId: String?,
    val insurancePolicyNumber: String?,
    val insuranceExpiresOn: String?,
    val insuranceFileId: String?,
)

/** Tool subtype of an asset (`asset_tool_details`); dates are ISO `yyyy-MM-dd`. */
data class AssetToolDetails(
    val assetId: String,
    val calibrationFileId: String?,
    val calibratedOn: String?,
    val calibrationIntervalDays: Int?,
    val calibrationDueOn: String?,
    /** `days` or `cycles`. */
    val usageUnit: String?,
    val usageValue: BigDecimal?,
    val replacementValue: BigDecimal?,
    val currency: String?,
    val trackerTagId: String?,
    /** `gps`, `bluetooth`, `uwb` or `other`. */
    val trackerKind: String?,
)

/**
 * Edited tool details (`maint.asset_tool_details.update`). A null [calibrationDueOn] lets the server
 * derive the due date from [calibratedOn] + [calibrationIntervalDays]. A null [usageUnit] uses the
 * asset type's usage meter; a null [usageValue] resets the usage counter to 0.
 */
data class ToolDetailsDraft(
    val calibratedOn: java.time.LocalDate? = null,
    val calibrationIntervalDays: Int? = null,
    val calibrationDueOn: java.time.LocalDate? = null,
    val usageUnit: String? = null,
    val usageValue: BigDecimal? = null,
    val replacementValue: BigDecimal? = null,
    val currency: String? = null,
    val trackerTagId: String? = null,
    val trackerKind: String? = null,
)

/** Edited vehicle details (`maint.asset_vehicle_details.update`). */
data class VehicleDetailsDraft(
    val bodyClass: String? = null,
    val fuelType: String? = null,
    val gvwr: String? = null,
    val registrationNumber: String? = null,
    val registrationExpiresOn: java.time.LocalDate? = null,
    val insurancePolicyNumber: String? = null,
    val insuranceExpiresOn: java.time.LocalDate? = null,
)

/** One stretch of an asset's status history (`asset_status_intervals`). */
data class AssetStatusInterval(
    val id: String,
    val status: AssetStatusCode,
    val downtimeKind: DowntimeKind?,
    val start: Long,
    val end: Long?,
    val reason: String?,
)

/** File metadata of a `files` row. */
data class StoredFile(
    val id: String,
    val name: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    /** Upload state on the server: pending, uploaded, verified, failed, quarantined. */
    val status: String,
    /** This device's copy, when the file was added here. */
    val localFile: File?,
) {
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
    val isPdf: Boolean get() = mimeType == "application/pdf"

    /** The server serves the bytes (`files/{id}/download`) only once the upload is verified. */
    val isDownloadable: Boolean get() = status == "verified"
}

/** A file linked to an asset, activity, work order or cost line (`attachments`). */
data class Attachment(
    val id: String,
    val file: StoredFile?,
    val fileId: String?,
    val documentId: String?,
    val assetId: String?,
    val activityId: String?,
    val workOrderId: String?,
    val costLineId: String? = null,
    val sortOrder: Int,
    val createdAt: Long,
)

/** What an attachment hangs off; exactly one target per link. */
sealed interface AttachmentTarget {
    data class ToAsset(val assetId: String) : AttachmentTarget
    data class ToActivity(val activityId: String) : AttachmentTarget
    data class ToWorkOrder(val workOrderId: String) : AttachmentTarget

    /** Photos of a fitted part (`activity_cost_lines`). */
    data class ToCostLine(val costLineId: String) : AttachmentTarget
}

/** Content picked or captured on the device, staged for upload (see `V2FileStager`). */
data class LocalFileRef(
    val uri: String,
    val fallbackMediaType: String = "application/octet-stream",
)

data class AssetFilter(
    val query: String = "",
    val locationId: String? = null,
    val status: AssetStatusCode? = null,
    val parentAssetId: String? = null,
    /** Only top-level assets (no parent). Ignored when [parentAssetId] is set. */
    val topLevelOnly: Boolean = false,
)

/** Reads and writes of assets, meters and their files. */
interface AssetRepository {
    fun observeAssets(filter: AssetFilter = AssetFilter()): Flow<List<Asset>>

    fun observeAsset(id: String): Flow<Asset?>

    suspend fun asset(id: String): Asset?

    /** Matches the global uid, serial number or VIN (QR and barcode scans). */
    suspend fun findByCode(code: String): Asset?

    fun observeMeters(assetId: String): Flow<List<AssetMeter>>

    fun observeReadings(assetId: String, limit: Int = 50): Flow<List<MeterReading>>

    fun observeStatusHistory(assetId: String): Flow<List<AssetStatusInterval>>

    /** Vehicle registration/insurance details; null when the asset has none. */
    fun observeVehicleDetails(assetId: String): Flow<AssetVehicleDetails?>

    /** Tool calibration / usage / tracker details; null when the asset has none. */
    fun observeToolDetails(assetId: String): Flow<AssetToolDetails?>

    fun observeAttachments(target: AttachmentTarget): Flow<List<Attachment>>

    /** Every picture in the account, newest first (gallery). */
    fun observeImages(limit: Int = 500): Flow<List<Attachment>>

    suspend fun create(draft: AssetDraft): String

    suspend fun update(id: String, draft: AssetDraft)

    suspend fun delete(id: String)

    suspend fun setStatus(id: String, status: AssetStatusCode, downtime: DowntimeKind?, reason: String?)

    suspend fun setParent(id: String, parentAssetId: String?)

    /**
     * Records a reading on [meterId], or on the asset's default meter when null. Throws
     * [NoMeterException] when the asset has no meter (the server rejects such readings).
     */
    suspend fun recordReading(assetId: String, value: BigDecimal, readAt: Long? = null, meterId: String? = null): String

    suspend fun correctReading(id: String, value: BigDecimal, reason: String)

    /** Uploads [file] (E12) and links it to [target]; returns the attachment id. */
    suspend fun attach(target: AttachmentTarget, file: LocalFileRef): String

    suspend fun detach(attachmentId: String)

    /** Makes [attachmentId] the first (cover) attachment of [target], keeping the others in order. */
    suspend fun setPrimaryAttachment(target: AttachmentTarget, attachmentId: String)

    /** Sets the asset's tool details (the row is created on first use). */
    suspend fun updateToolDetails(assetId: String, draft: ToolDetailsDraft)

    /** Sets the asset's vehicle details (the row is created on first use). */
    suspend fun updateVehicleDetails(assetId: String, draft: VehicleDetailsDraft)

    /**
     * Adds a meter of [kind] (one per kind and asset) measured in [unit]; the asset's first meter
     * becomes its default. Returns the meter id.
     */
    suspend fun createMeter(assetId: String, kind: String, unit: String): String
}

/** A person who can be assigned work (`workers`). */
data class Worker(
    val id: String,
    val userId: String?,
    val displayName: String,
    val kind: String,
    val role: String?,
    val status: String,
    val skills: List<String>,
    val maxActiveWorkOrders: Int?,
    val available: Boolean,
    val homeLocationId: String?,
    val notes: String?,
    val version: Long,
    val activeWorkOrders: Int = 0,
    val certifications: List<Certification> = emptyList(),
    /** Current hourly cost rate (`worker_rates`, role "", kind hourly). */
    val hourlyRate: WorkerRate? = null,
    /** Raw `attributes` JSON (certifications live under `certifications`). */
    val attributes: String = "{}",
)

/** A technician certification (kept in `workers.attributes.certifications`). */
data class Certification(
    val name: String,
    val issuer: String? = null,
    val number: String? = null,
    val expiresOn: java.time.LocalDate? = null,
) {
    fun isExpired(today: java.time.LocalDate): Boolean = expiresOn != null && expiresOn < today
}

/** A worker's current hourly cost rate row (`worker_rates`). */
data class WorkerRate(
    val id: String,
    val costRate: BigDecimal?,
    val currency: String,
    val validFrom: java.time.LocalDate,
    val version: Long,
)

/**
 * A worker profile edit. [certifications] replaces the list when not null; [hourlyRate] /
 * [rateCurrency] start a new rate today when they differ from the current one, and a null
 * [hourlyRate] ends the current rate today. A null [displayName] clears it (the dispatch name
 * falls back to the user's name).
 */
data class WorkerDraft(
    val displayName: String?,
    val skills: List<String>,
    val maxActiveWorkOrders: Int?,
    val available: Boolean,
    val homeLocationId: String?,
    val certifications: List<Certification>? = null,
    val hourlyRate: BigDecimal? = null,
    val rateCurrency: String? = null,
)

interface WorkerRepository {
    /** Active workers by name. */
    fun observeWorkers(): Flow<List<Worker>>

    /** The signed-in user's worker row. */
    fun observeMe(): Flow<Worker?>

    suspend fun worker(id: String): Worker?

    fun observeWorker(id: String): Flow<Worker?>

    suspend fun update(id: String, draft: WorkerDraft)
}

/** An account-defined code (`reference_values`), e.g. failure/cause/remedy codes. */
data class ReferenceValue(
    val id: String,
    val kind: String,
    val code: String,
    val label: String,
    val description: String?,
    val color: String?,
    val sortOrder: Int,
)

interface ReferenceRepository {
    /** Live values of [kind] in sort order. */
    fun observeValues(kind: String): Flow<List<ReferenceValue>>

    /** Categories of [kind] (`work_order`, `asset`, `product`, `request`) by name. */
    fun observeCategories(kind: String): Flow<List<Category>>

    companion object {
        const val WO_FAILURE = "wo_failure"
        const val WO_CAUSE = "wo_cause"
        const val WO_REMEDY = "wo_remedy"
    }
}
