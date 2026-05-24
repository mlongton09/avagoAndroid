package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ProvisionRequest(
    val device_id: String,
    val platform: String = "android",
)

@Serializable
data class SignInRequest(
    val firebase_token: String,
    val device_id: String,
)

@Serializable
data class RefreshRequest(
    val refresh_token: String,
    val device_id: String,
)

@Serializable
data class AuthResponse(
    val access_token: String,
    val refresh_token: String,
    val account_id: String? = null,
    val device_id: String? = null,
)

@Serializable
data class UserResponse(
    val user_id: String,
    val display_name: String? = null,
    val email: String? = null,
    val photo_url: String? = null,
    val role: String? = null,
)

@Serializable
data class AccountResponse(
    val account_id: String,
    val name: String? = null,
    val tier: String? = null,
)

@Serializable
data class DeviceUpdateRequest(
    val push_token: String? = null,
    val platform: String = "android",
    val app_version: String? = null,
    val os_version: String? = null,
)

@Serializable
data class BulkInvitation(
    val email: String,
    val display_name: String? = null,
    val role: String,
)

@Serializable
data class RolePermissionResponse(
    val role: String,
    val permissions: List<String>,
)

@Serializable
data class InvitationStatusResponse(
    val email: String,
    val status: String,
    val invited_at: Long? = null,
    val accepted_at: Long? = null,
)

@Serializable
data class DispatchConfigResponse(
    val enabled: Boolean,
    val columns: List<String> = emptyList(),
)
