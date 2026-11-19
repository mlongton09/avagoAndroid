package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncConflict(
    val id: String,
    val account_id: String,
    val entity_type: String,
    val entity_id: String,
    val server_payload: kotlinx.serialization.json.JsonObject,
    val client_payload: kotlinx.serialization.json.JsonObject,
    val status: String = "OPEN",
    val resolved_by_user_id: String? = null,
    val resolved_at: String? = null,
    val created_at: String = "",
)
