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
    /** Local server_version at time of push, for server-side optimistic concurrency. */
    val server_version: Long? = null,
    /** false = normal optimistic concurrency; true = force overwrite regardless of version. */
    val force: Boolean = false,
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
    /** Server-assigned version after successful push; used to update local server_version. */
    val server_version: Long? = null,
)

@Serializable
data class SyncPushResponse(
    val results: List<SyncOperationResult>,
)

@Serializable
data class SyncPullResponse(
    val items: List<JsonObject> = emptyList(),
    val has_more: Boolean = false,
    val max_seq: Long = 0,
)
