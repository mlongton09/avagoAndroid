package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

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
data class AuditListResponse(
    val entries: List<AuditEventResponse> = emptyList(),
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
    @SerialName("wo_id") val wo_id: String,
    val account_id: String,
    val title: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val asset_id: String? = null,
    val created_at: Long = 0,
    val updated_at: Long = 0,
    val assigned_group_id: String? = null,
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

@Serializable
data class WoChecklistItemResponse(
    val item_id: String,
    val checklist_id: String,
    val title: String,
    @SerialName("completed") val completed: Boolean = false,
    @SerialName("completed_by") val completed_by: String? = null,
    @SerialName("completed_at") val completed_at: String? = null,
    val sort_order: Int = 0,
    val row_type: String = "STEP",
    val description: String? = null,
    val urls: JsonArray? = null,
)

@Serializable
data class CreateChecklistItemRequest(
    val title: String,
    val sort_order: Int? = null,
    val row_type: String? = null,
    val description: String? = null,
)

// Change 72: skip occurrence request body
@Serializable
data class SkipOccurrenceRequest(
    val skip_reason_code: String? = null,
    val skip_reason: String? = null,
)

// New: Bulk reassign work orders
@Serializable
data class BulkReassignRequest(
    val work_order_ids: List<String>,
    val to_user_id: String,
    val unassign_others: Boolean = false,
)

@Serializable
data class BulkReassignResponse(
    val updated: Int,
    val work_order_ids: List<String>,
)

// New: Cost approval embedded in WO cost summary
@Serializable
data class CostApproval(
    val approved_by_id: String? = null,
    val approved_at: String? = null,
    val approval_state: String? = null,
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

@Serializable
data class CreateWorkPermitRequest(
    val permit_type: String,
    val hazards: List<String> = emptyList(),
    val precautions: List<String> = emptyList(),
)

// New: Asset history event (unified timeline)
@Serializable
data class AssetHistoryEvent(
    val type: String,
    val id: String,
    val title: String? = null,
    val status: String? = null,
    val occurred_at: String? = null,
)

// Change 3: PATCH work order — only changed fields are sent
@Serializable
data class WorkOrderPatch(
    val title: String? = null,
    val priority: String? = null,
    val status: String? = null,
    val assigned_to: String? = null,
    val due_date: String? = null,
    val category: String? = null,
    val description: String? = null,
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
