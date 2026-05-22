package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: String? = null,
)

@Serializable
data class UserPreferencesResponse(
    val theme: String? = null,
    val language: String? = null,
    val distance_unit: String? = null,
    val currency: String? = null,
    val locale: String? = null,
    val disable_quotes: Boolean? = null,
    val notifications_enabled: Boolean? = null,
)

@Serializable
data class UpdatePreferencesRequest(
    val distance_unit: String? = null,
    val currency: String? = null,
    val locale: String? = null,
    val disable_quotes: Boolean? = null,
)
