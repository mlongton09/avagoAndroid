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

/** One `platform.audit_events` row of a work order (`GET .../work-orders/{id}/audit`). */
@Serializable
data class WorkOrderAuditEntry(
    val audit_id: String,
    val created_at: String,
    val actor_type: String? = null,
    val actor_id: String? = null,
    val action_type: String? = null,
    val description: String? = null,
)

@Serializable
data class WorkOrderAuditResponse(val entries: List<WorkOrderAuditEntry> = emptyList())

/** An active budget covering a work order (`GET .../work-orders/{id}/budget-pill`). */
@Serializable
data class WorkOrderBudget(
    val budget_id: String,
    val name: String,
    val amount: Double,
    val actual: Double,
    val remaining: Double,
    val currency: String,
    val percent_used: Double,
    val period_start: String? = null,
    val period_end: String? = null,
)

@Serializable
data class WorkOrderBudgetsResponse(val budgets: List<WorkOrderBudget> = emptyList())
