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
    val scheduled_date: String? = null,
    val completed_date: String? = null,
)

// Change 91: cycle count line response with variance fields
@Serializable
data class CycleCountLineResponse(
    val line_id: String,
    val cycle_count_id: String,
    val inventory_id: String,
    val part_id: String? = null,
    val expected_qty: Double? = null,
    val counted_qty: Double? = null,
    val variance: Double? = null,
    val unit_cost: Double? = null,
    val variance_quantity: Double? = null,
    val variance_value: Double? = null,
    val is_counted: Boolean = false,
    val counted_at: Long? = null,
    val counted_by: String? = null,
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
data class InventoryTransactionResponse(
    val transaction_id: String,
    val inventory_id: String,
    val part_id: String? = null,
    val transaction_type: String,
    val quantity: Double,
    val quantity_before: Double? = null,
    val quantity_after: Double? = null,
    val location_id: String? = null,
    val reference_id: String? = null,
    val reference_type: String? = null,
    val reference_number: String? = null,
    val notes: String? = null,
    val actor_id: String? = null,
    val created_at: Long = 0,
)

// Change 73: inventory transaction reason code
@Serializable
data class CreateInventoryTransactionRequest(
    val transaction_type: String,
    val quantity: Double,
    val location_id: String? = null,
    val notes: String? = null,
    val reference_id: String? = null,
    val reference_type: String? = null,
    val reason_code: String? = null,
)

// Change 74: reverse inventory transaction
@Serializable
data class ReverseTransactionRequest(
    val reason: String? = null,
)

// Change 75: reorder suggestions to PO conversion
@Serializable
data class ConvertReorderToPORequest(
    val suggestion_ids: List<String>,
    val vendor_id: String? = null,
    val group_by_vendor: Boolean = true,
    val draft_only: Boolean = true,
)

@Serializable
data class ConvertedPOSummary(
    val po_id: String,
    val vendor_id: String?,
    val status: String,
    val line_count: Int,
)

@Serializable
data class ConvertReorderToPOResponse(
    val purchase_orders: List<ConvertedPOSummary>,
)

// Change 76: PATCH purchase order
@Serializable
data class PatchPurchaseOrderRequest(
    val notes: String? = null,
    val expected_delivery: String? = null,
    val vendor_invoice_no: String? = null,
    val ship_to_location_id: String? = null,
)

// Change 77: list of purchase orders (for WO-linked POs endpoint)
@Serializable
data class PurchaseOrdersResponse(
    val purchase_orders: List<PurchaseOrderResponse>,
)

// Change 108 — abc_class on parts
// NOTE: No PartResponse data class exists in this network module yet (parts sync via generic JSON pull).
// When a typed PartResponse is added here, include: val abc_class: String? = null

// New: Bin contents
@Serializable
data class BinContent(
    val bin_id: String,
    val part_id: String,
    val quantity: Int,
    val part_name: String? = null,
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
    val economic_order_qty: Double? = null,
    val suggested_vendor_id: String? = null,
    val vendor_lead_time_days: Int? = null,
)
