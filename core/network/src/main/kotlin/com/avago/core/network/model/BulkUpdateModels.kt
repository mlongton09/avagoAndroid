package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class BulkUpdateWorkOrderRequest(
    val ids: List<String>,
    val priority: String? = null,
    val assigned_user_id: String? = null,
    val due_date: String? = null,
    val status: String? = null,
    val category_id: String? = null,
)

@Serializable
data class BulkUpdateResult(
    val id: String,
    val ok: Boolean,
    val error: String? = null,
)

@Serializable
data class BulkUpdateResponse(
    val updated_count: Int,
    val failed_count: Int,
    val results: List<BulkUpdateResult>,
)
