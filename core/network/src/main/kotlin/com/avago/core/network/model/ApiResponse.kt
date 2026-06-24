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
    val rental_default_rate_unit: String? = null,
)

@Serializable
data class UpdatePreferencesRequest(
    val distance_unit: String? = null,
    val currency: String? = null,
    val locale: String? = null,
    val disable_quotes: Boolean? = null,
    /** "gallon" | "liter" — mirrors iOS AVDefaultsKeyFuelVolumeUnit */
    val fuel_volume_unit: String? = null,
    /** When true the Scout AI populates a form for user review before saving. */
    val enable_human_in_loop: Boolean? = null,
)
