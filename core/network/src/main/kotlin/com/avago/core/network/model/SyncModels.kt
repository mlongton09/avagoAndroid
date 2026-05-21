package com.avago.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SyncOperation(
    val entity_type: String,
    val entity_id: String,
    val operation: String,
    val payload: JsonObject? = null,
    val idempotency_key: String? = null,
)

@Serializable
data class SyncPushRequest(
    val operations: List<SyncOperation>,
)

@Serializable
data class SyncOperationResult(
    val entity_id: String,
    val success: Boolean,
    val data: JsonObject? = null,
    val error: String? = null,
    val conflict: Boolean = false,
)

@Serializable
data class SyncPushResponse(
    val results: List<SyncOperationResult>,
)

@Serializable
data class SyncPullResponse(
    val items: List<JsonObject>,
    val has_more: Boolean,
    val max_seq: Long,
)
