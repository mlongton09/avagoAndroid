package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One of the caller's server inspection drafts (`maint.inspection_drafts`, not synced). */
@Serializable
data class InspectionDraftRow(
    @SerialName("draft_id") val draftId: String,
    @SerialName("asset_id") val assetId: String,
    @SerialName("log_id") val logId: String? = null,
    @SerialName("config_id") val configId: String? = null,
    /** Opaque to the server: the client's draft document. */
    val payload: JsonObject = JsonObject(emptyMap()),
    @SerialName("updated_at") val updatedAt: String,
)

/** `PUT /accounts/{a}/inspection-drafts`: upserts the caller's draft for (asset, log). */
@Serializable
data class PutInspectionDraftRequest(
    @SerialName("asset_id") val assetId: String,
    @SerialName("log_id") val logId: String? = null,
    @SerialName("config_id") val configId: String? = null,
    val payload: JsonObject,
)
