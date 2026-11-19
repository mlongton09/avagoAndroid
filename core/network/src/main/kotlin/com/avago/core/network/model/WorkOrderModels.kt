package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

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

// Change 72: skip occurrence request body
@Serializable
data class SkipOccurrenceRequest(
    val skip_reason_code: String? = null,
    val skip_reason: String? = null,
)

// New: Work permits
@Serializable
data class WorkPermit(
    val id: String,
    val work_order_id: String,
    val permit_type: String,
    val status: String,
    val hazards: List<String> = emptyList(),
    val precautions: List<String> = emptyList(),
    val created_at: String? = null,
)

// Change 80: KPI summary
@Serializable
data class OpenWoByPriority(
    val priority: String,
    val count: Int,
)

@Serializable
data class KpiSummaryResponse(
    val mttr: Double,
    val mtbf: Double,
    val pm_compliance_rate: Double,
    val open_wo_by_priority: List<OpenWoByPriority>,
    val generated_at: String,
)
