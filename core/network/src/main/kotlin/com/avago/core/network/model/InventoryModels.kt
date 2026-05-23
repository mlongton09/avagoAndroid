package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class InventoryReceiveRequest(
    val quantity: Double,
    val location_id: String? = null,
    val notes: String? = null,
)

@Serializable
data class InventoryUseRequest(
    val quantity: Double,
    val notes: String? = null,
)

@Serializable
data class InventoryReceiveResponse(
    val inventory_id: String,
    val quantity_on_hand: Double,
)

@Serializable
data class CreatePurchaseOrderRequest(
    val vendor_id: String? = null,
    val expected_delivery: String? = null,
    val ship_to_location_id: String? = null,
    val notes: String? = null,
    val cost_approval: String? = null,
    val lines: List<PoLineRequest> = emptyList(),
)

@Serializable
data class PoLineRequest(
    val part_id: String? = null,
    val description: String? = null,
    val quantity: Double,
    val unit_cost: Double? = null,
    val currency: String? = null,
)

@Serializable
data class PurchaseOrderResponse(
    val po_id: String,
    val po_number: String? = null,
    val status: String,
)

@Serializable
data class CreateGrnRequest(
    val grn_number: String? = null,
    val received_at: Long? = null,
    val carrier: String? = null,
    val tracking_number: String? = null,
    val packing_slip_no: String? = null,
    val notes: String? = null,
    val has_discrepancy: Boolean = false,
    val lines: List<GrnLineRequest> = emptyList(),
)

@Serializable
data class GrnLineRequest(
    val po_line_id: String? = null,
    val part_id: String? = null,
    val quantity_received: Double,
)

@Serializable
data class GrnResponse(
    val grn_id: String,
    val grn_number: String? = null,
)

@Serializable
data class CreatePartIssueRequest(
    val issue_type: String,
    val location_id: String? = null,
    val from_location_id: String? = null,
    val to_location_id: String? = null,
    val reference_id: String? = null,
    val reference_type: String? = null,
    val notes: String? = null,
    val lines: List<PartIssueLineRequest> = emptyList(),
)

@Serializable
data class PartIssueLineRequest(
    val part_id: String,
    val inventory_id: String? = null,
    val quantity: Double,
    val unit_cost: Double? = null,
    val notes: String? = null,
)

@Serializable
data class PartIssueResponse(
    val issue_id: String,
)

@Serializable
data class CreateCycleCountRequest(
    val location_id: String,
    val scope_type: String? = null,
    val scope_value: String? = null,
)

@Serializable
data class CycleCountResponse(
    val cycle_count_id: String,
    val status: String,
)

@Serializable
data class CreateVendorPartRequest(
    val vendor_id: String,
    val part_id: String,
    val vendor_sku: String? = null,
    val unit_cost: Double? = null,
    val moq: Double? = null,
    val pack_size: Double? = null,
    val lead_days: Int? = null,
    val is_preferred: Boolean = false,
    val currency: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateVendorPartRequest(
    val vendor_sku: String? = null,
    val unit_cost: Double? = null,
    val moq: Double? = null,
    val pack_size: Double? = null,
    val lead_days: Int? = null,
    val is_preferred: Boolean? = null,
    val currency: String? = null,
    val notes: String? = null,
)

@Serializable
data class VendorPartResponse(
    val vendor_part_id: String,
)

@Serializable
data class ReorderSuggestionResponse(
    val suggestion_id: String,
    val part_id: String,
    val quantity_on_hand: Double,
    val reorder_qty: Double? = null,
    val suggested_qty: Double,
    val preferred_vendor_id: String? = null,
    val status: String,
    val reason: String? = null,
    val created_at: Long,
    val updated_at: Long,
)
