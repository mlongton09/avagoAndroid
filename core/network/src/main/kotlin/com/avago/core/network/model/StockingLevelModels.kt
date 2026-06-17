package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class StockingLevelResponse(
    val stocking_level_id: String,
    val part_id: String,
    val location_id: String,
    val min_qty: Double? = null,
    val max_qty: Double? = null,
    val reorder_qty: Double? = null,
    val safety_stock: Double? = null,
    val safety_stock_quantity: Double? = null,
    val created_at: String = "",
    val updated_at: String = "",
    val server_version: Long = 0,
)

@Serializable
data class UpsertStockingLevelRequest(
    val part_id: String,
    val location_id: String,
    val min_qty: Double? = null,
    val max_qty: Double? = null,
    val reorder_qty: Double? = null,
    val safety_stock: Double? = null,
    val safety_stock_quantity: Double? = null,
)
