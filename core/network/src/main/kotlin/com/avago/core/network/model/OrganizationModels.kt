package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrganizationResponse(
    val organization_id: String,
    val name: String,
    val parent_org_id: String? = null,
    val timezone: String = "UTC",
    val plan: String = "standard",
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class OrgSummaryResponse(
    @SerialName("organization_id") val org_id: String,
    val name: String,
    val total_accounts: Long = 0,
    val open_work_orders: Long = 0,
    val overdue_work_orders: Long = 0,
    val total_assets: Long = 0,
    val active_users_30d: Long = 0,
)

@Serializable
data class RequestPortalResponse(
    val id: String,
    val account_id: String,
    val name: String,
    val token: String = "",
    val allowed_location_ids: List<String> = emptyList(),
    val require_email: Boolean = false,
    val welcome_message: String? = null,
    val is_active: Boolean = true,
    val created_at: String = "",
)

@Serializable
data class CreatePortalRequest(
    val name: String,
    val allowed_location_ids: List<String>? = null,
    val require_email: Boolean = false,
    val welcome_message: String? = null,
)

@Serializable
data class PortalSubmissionRequest(
    val submitter_name: String,
    val title: String,
    val submitter_email: String? = null,
    val description: String? = null,
    val location_id: String? = null,
)

@Serializable
data class WorkRequestPortalResponse(
    val id: String,
    val account_id: String,
    val name: String,
    val slug: String,
    val is_active: Boolean = true,
    val requires_email: Boolean = false,
    val requires_phone: Boolean = false,
    val allow_attachments: Boolean = false,
    val welcome_message: String? = null,
    val qr_code_url: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class CreateWorkRequestPortalRequest(
    val name: String,
    val requires_email: Boolean = false,
    val requires_phone: Boolean = false,
    val allow_attachments: Boolean = false,
    val welcome_message: String? = null,
)

@Serializable
data class SyncCheckpointResponse(
    val device_id: String,
    val checkpoint_cursor: String,
    val last_synced_at: String,
    val pending_upload_count: Int = 0,
    val recovery_required: Boolean = false,
)
