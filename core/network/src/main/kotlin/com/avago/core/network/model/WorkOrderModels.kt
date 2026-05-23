package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class EffortStatsResponse(
    val p10_hours: Double = 0.0,
    val p50_hours: Double = 0.0,
    val p90_hours: Double = 0.0,
    val sample_size: Int = 0,
)

@Serializable
data class AuditEventResponse(
    val event_id: String,
    val event_type: String,
    val actor_id: String? = null,
    val occurred_at: Long = 0,
    val meta: String? = null,
)

@Serializable
data class BudgetPillResponse(
    val total_cost: Double = 0.0,
    val labor_cost: Double = 0.0,
    val parts_cost: Double = 0.0,
    val currency: String = "USD",
)

@Serializable
data class VinDecodeResponse(
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val trim: String? = null,
    val engine: String? = null,
    val transmission: String? = null,
)

@Serializable
data class WorkOrderResponse(
    val work_order_id: String,
    val account_id: String,
    val title: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val asset_id: String? = null,
    val created_at: Long = 0,
    val updated_at: Long = 0,
)

@Serializable
data class RecurrenceResponse(
    val rrule: String? = null,
    val next_occurrence: String? = null,
    val enabled: Boolean = false,
)

@Serializable
data class RescheduleResponse(
    val scheduled_start: String? = null,
    val scheduled_end: String? = null,
    val reason: String? = null,
)

@Serializable
data class GeocodeRequest(
    val address_line1: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postal_code: String? = null,
    val country: String? = null,
)

@Serializable
data class GeocodeResponse(
    val lat: Double? = null,
    val lon: Double? = null,
    val formatted_address: String? = null,
)
