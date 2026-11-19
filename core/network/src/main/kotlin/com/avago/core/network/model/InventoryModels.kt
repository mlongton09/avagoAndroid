package com.avago.core.network.model

import kotlinx.serialization.Serializable

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
