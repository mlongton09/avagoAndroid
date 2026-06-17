package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class AssetCriticalityResponse(
    val criticality_id: String,
    val account_id: String,
    val label: String,
    val level: Int,
    val color: String? = null,
    val description: String? = null,
    val is_system: Boolean = false,
    val server_version: Int = 1,
    val seq: Long = 0,
    val created_at: String = "",
    val updated_at: String = "",
    val deleted_at: String? = null,
)

@Serializable
data class AssetModelResponse(
    val model_id: String,
    val account_id: String,
    val manufacturer: String? = null,
    val model_name: String? = null,
    val model_number: String? = null,
    val description: String? = null,
    val default_procedure_template_id: String? = null,
    // Change 92: serial number pattern fields
    val serial_number_pattern: String? = null,
    val pattern_help_text: String? = null,
    val pattern_examples: String? = null,
    val server_version: Int = 1,
    val seq: Long = 0,
    val created_at: String = "",
    val updated_at: String = "",
    val deleted_at: String? = null,
)

@Serializable
data class RecommendedPartResponse(
    val id: String,
    val model_id: String,
    val account_id: String,
    val part_id: String,
    val quantity: Int = 1,
    val notes: String? = null,
    val created_at: String = "",
)

@Serializable
data class RcaReportResponse(
    val rca_id: String,
    val account_id: String,
    val wo_id: String? = null,
    val asset_id: String? = null,
    val problem_description: String? = null,
    val immediate_action: String? = null,
    val root_cause: String? = null,
    val corrective_action: String? = null,
    val preventive_action: String? = null,
    val status: String = "DRAFT",
    val completed_at: String? = null,
    val completed_by: String? = null,
    val created_by: String = "",
    val server_version: Int = 1,
    val seq: Long = 0,
    val created_at: String = "",
    val updated_at: String = "",
    val deleted_at: String? = null,
)

@Serializable
data class CreateAssetCriticalityRequest(
    val label: String,
    val level: Int,
    val color: String? = null,
    val description: String? = null,
)

@Serializable
data class CreateAssetModelRequest(
    val manufacturer: String? = null,
    val model_name: String? = null,
    val model_number: String? = null,
    val description: String? = null,
    val default_procedure_template_id: String? = null,
)

// Change 92: asset create/update requests with serial_number and model_id
@Serializable
data class CreateAssetRequest(
    val name: String,
    val asset_type: String? = null,
    val serial_number: String? = null,
    val model_id: String? = null,
    val make: String? = null,
    val model: String? = null,
    val location_id: String? = null,
)

@Serializable
data class UpdateAssetRequest(
    val name: String? = null,
    val asset_type: String? = null,
    val serial_number: String? = null,
    val model_id: String? = null,
    val make: String? = null,
    val model: String? = null,
    val location_id: String? = null,
)

@Serializable
data class CreateRcaReportRequest(
    val wo_id: String? = null,
    val asset_id: String? = null,
    val problem_description: String? = null,
    val immediate_action: String? = null,
    val root_cause: String? = null,
    val corrective_action: String? = null,
    val preventive_action: String? = null,
    val status: String? = null,
)

// New: Asset ancestor chain
@Serializable
data class AncestorItem(
    val id: String,
    val name: String,
)

// New: Asset location history entry (typed)
@Serializable
data class LocationHistoryEntry(
    val id: String,
    val from_location_id: String? = null,
    val to_location_id: String? = null,
    val moved_by_user_id: String? = null,
    val move_reason: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val moved_at: String? = null,
)

// New: QR batch job request/response
@Serializable
data class QrBatchJobRequest(
    val asset_ids: List<String>,
    val format: String = "PDF",
    val page_size: String = "A4",
)

@Serializable
data class QrBatchJobResponse(
    val job_id: String,
    val status: String,
    val asset_count: Int,
)

// New: QR scan entry
@Serializable
data class QrScanEntry(
    val id: String,
    val asset_id: String,
    val scanned_by_user_id: String? = null,
    val scanned_at: String? = null,
    val device_info: String? = null,
)

@Serializable
data class AddRecommendedPartRequest(
    val part_id: String,
    val quantity: Int = 1,
    val notes: String? = null,
)

@Serializable
data class FileUploadResponse(
    val file_upload_id: String,
    val account_id: String,
    val entity_id: String? = null,
    val entity_type: String? = null,
    val storage_key: String = "",
    val original_name: String? = null,
    val content_type: String? = null,
    val file_size_bytes: Long? = null,
    val upload_status: String = "pending",
    val created_by: String? = null,
    val created_at: String = "",
    val expires_at: String = "",
    val deleted_at: String? = null,
)

@Serializable
data class PresignedUploadRequest(
    val entity_id: String? = null,
    val entity_type: String? = null,
    val filename: String? = null,
    val content_type: String? = null,
)

@Serializable
data class PresignedUploadResponse(
    val file_upload_id: String,
    val upload_url: String,
    val storage_key: String,
    val expires_in_seconds: Int,
)
