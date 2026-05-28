package com.avago.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-request envelope sent to the server with every Scout query.
 *
 * Mirrors iOS ScreenContext.swift. The server's ScreenContextEnvelope requires [accountId];
 * the rest is forward-looking context the model uses to bind pronouns ("the asset"), default
 * fields, and filter results. Bounded to 8 KB per AIPlan §6.6.3.
 */
@Serializable
data class ScoutContext(
    /** REQUIRED — the account this request operates on. */
    @SerialName("account_id")
    val accountId: String = "",

    /** Stable identifier for the surface the request was launched from. */
    @SerialName("screen")
    val screen: String? = null,

    /** Currently-focused entity IDs. The model binds pronouns like "this asset" to these. */
    @SerialName("current_asset_id")
    val currentAssetId: String? = null,

    @SerialName("current_wo_id")
    val currentWoId: String? = null,

    @SerialName("current_part_id")
    val currentPartId: String? = null,

    /** Last 3 entities the user touched (most-recent-first). */
    @SerialName("recent_entities")
    val recentEntities: List<RecentEntity> = emptyList(),

    /** Filled at request-build time — callers should not set these. */
    @SerialName("now")
    val now: String? = null,

    @SerialName("locale")
    val locale: String? = null,

    @SerialName("tz")
    val tz: String? = null,

    /** True if the envelope had to be clipped to meet the 8 KB cap. */
    @SerialName("truncated")
    val truncated: Boolean = false,

    @SerialName("client")
    val client: ClientInfo = ClientInfo(),
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
