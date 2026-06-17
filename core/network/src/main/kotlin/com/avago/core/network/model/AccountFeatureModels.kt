package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountFeaturesResponse(
    @SerialName("chat_enabled")                    val chatEnabled: Boolean = true,
    @SerialName("work_orders_enabled")             val workOrdersEnabled: Boolean = true,
    @SerialName("purchase_orders_enabled")         val purchaseOrdersEnabled: Boolean = true,
    @SerialName("permission_sets_enabled")         val permissionSetsEnabled: Boolean = true,
    @SerialName("team_work_order_routing_enabled") val teamWorkOrderRoutingEnabled: Boolean = true,
    @SerialName("multi_org_enabled")               val multiOrgEnabled: Boolean = true,
    @SerialName("chat_custom_status_enabled")      val chatCustomStatusEnabled: Boolean = true,
    @SerialName("chat_message_templates_enabled")  val chatMessageTemplatesEnabled: Boolean = true,
    @SerialName("ai_summaries_enabled")            val aiSummariesEnabled: Boolean = false,
    @SerialName("ai_transcription_enabled")        val aiTranscriptionEnabled: Boolean = false,
    @SerialName("asset_qr_bulk_generation_enabled") val assetQrBulkGenerationEnabled: Boolean = true,
)

@Serializable
data class PutAccountFeaturesRequest(
    @SerialName("chat_enabled")                    val chatEnabled: Boolean? = null,
    @SerialName("work_orders_enabled")             val workOrdersEnabled: Boolean? = null,
    @SerialName("purchase_orders_enabled")         val purchaseOrdersEnabled: Boolean? = null,
    @SerialName("permission_sets_enabled")         val permissionSetsEnabled: Boolean? = null,
    @SerialName("team_work_order_routing_enabled") val teamWorkOrderRoutingEnabled: Boolean? = null,
    @SerialName("multi_org_enabled")               val multiOrgEnabled: Boolean? = null,
    @SerialName("chat_custom_status_enabled")      val chatCustomStatusEnabled: Boolean? = null,
    @SerialName("chat_message_templates_enabled")  val chatMessageTemplatesEnabled: Boolean? = null,
    @SerialName("ai_summaries_enabled")            val aiSummariesEnabled: Boolean? = null,
    @SerialName("ai_transcription_enabled")        val aiTranscriptionEnabled: Boolean? = null,
    @SerialName("asset_qr_bulk_generation_enabled") val assetQrBulkGenerationEnabled: Boolean? = null,
)
