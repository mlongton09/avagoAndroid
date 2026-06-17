package com.avago.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FilterViewCreatedBy(val id: String, val name: String)

@Serializable
data class SavedFilterViewResponse(
    val id: String,
    val account_id: String,
    val user_id: String,
    val entity_type: String,
    val name: String,
    val filters: JsonObject = JsonObject(emptyMap()),
    val is_shared: Boolean = false,
    val created_at: String = "",
    val updated_at: String = "",
    val created_by: FilterViewCreatedBy? = null,
)

@Serializable
data class CreateFilterViewRequest(
    val entity_type: String,
    val name: String,
    val filters: JsonObject,
    val is_shared: Boolean = false,
)
