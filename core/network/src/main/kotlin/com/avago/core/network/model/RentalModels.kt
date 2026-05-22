package com.avago.core.network.model

import kotlinx.serialization.Serializable

/** Wire models for the Rentals API. */

@Serializable
data class CreateRentalRequest(
    val asset_id: String,
    val start_at: String,
    val rate: Double,
    val rate_unit: String,  // "hourly", "daily", "weekly", "monthly"
    val currency: String = "USD",
    val customer_name: String? = null,
    val notes: String? = null,
)

@Serializable
data class RentalResponse(
    val rental_id: String,
    val asset_id: String,
    val start_at: String,
    val end_at: String? = null,
    val rate: Double,
    val rate_unit: String,
    val currency: String,
    val customer_name: String? = null,
    val notes: String? = null,
    val status: String,  // "active", "ended"
    val total_amount: Double? = null,
)
