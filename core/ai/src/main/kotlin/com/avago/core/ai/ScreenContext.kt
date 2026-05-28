package com.avago.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-request envelope sent to the server with every AI Scout query.
 * Mirrors iOS ScreenContext.swift. Bounded to 8 KB per AIPlan §6.6.3.
 */
@Serializable
data class ScreenContext(
    @SerialName("account_id") val accountId: String = "",
    @SerialName("screen") val screen: String? = null,
    @SerialName("current_asset_id") val currentAssetId: String? = null,
    @SerialName("current_wo_id") val currentWoId: String? = null,
    @SerialName("current_part_id") val currentPartId: String? = null,
    @SerialName("recent_entities") val recentEntities: List<RecentEntity> = emptyList(),
    @SerialName("now") val now: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("tz") val tz: String? = null,
    @SerialName("truncated") val truncated: Boolean = false,
    @SerialName("client") val client: ClientInfo = ClientInfo(),
) {
    @Serializable
    data class ClientInfo(
        @SerialName("platform") val platform: String = "android",
        @SerialName("app_version") val appVersion: String? = null,
    )
}

/** One row in the recent-entities ring. */
@Serializable
data class RecentEntity(
    @SerialName("kind") val kind: String,
    @SerialName("id") val id: String,
    @SerialName("label") val label: String? = null,
)

/** Server response from POST /accounts/:id/ai/scout. */
@Serializable
data class ScoutResponse(
    val targetScreen: String? = null,
    val skillName: String? = null,
    val fields: Map<String, String?> = emptyMap(),
    val envelopeId: String,
    val actionCard: ActionCard? = null,
)
