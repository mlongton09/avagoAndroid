package com.avago.core.network.model

import kotlinx.serialization.Serializable

// Changes 93+94: category defaults and hierarchy

@Serializable
data class CategoryResponse(
    val category_id: String,
    val account_id: String,
    val name: String,
    val description: String? = null,
    val default_priority: String? = null,
    val default_sla_hours: Int? = null,
    val parent_category_id: String? = null,
    val path: List<String>? = null,
    val child_count: Int? = null,
    val created_at: String = "",
    val updated_at: String = "",
    val deleted_at: String? = null,
)
