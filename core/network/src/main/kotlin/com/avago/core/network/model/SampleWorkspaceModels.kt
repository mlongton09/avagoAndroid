package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One entry of GET /admin/sample-data/workspaces (SystemAdmin only).
 * [workspace] is the caller's saved demo account for this profile, or null when none exists yet.
 */
@Serializable
data class SampleWorkspaceProfile(
    val key: String,
    val label: String = "",
    val locale: String? = null,
    val available: Boolean = false,
    @SerialName("asset_count") val assetCount: Long = 0,
    val workspace: SampleWorkspace? = null,
)

@Serializable
data class SampleWorkspace(
    @SerialName("account_id") val accountId: String,
    @SerialName("account_name") val accountName: String? = null,
    val active: Boolean = false,
    @SerialName("asset_count") val assetCount: Long = 0,
    val available: Boolean = false,
)

@Serializable
data class SampleWorkspacesResponse(
    val profiles: List<SampleWorkspaceProfile> = emptyList(),
)

@Serializable
data class SwitchSampleWorkspaceRequest(
    @SerialName("profile_key") val profileKey: String,
)

/** POST /admin/sample-data/workspaces/switch result. The current account is NOT changed server-side. */
@Serializable
data class SwitchSampleWorkspaceResponse(
    @SerialName("account_id") val accountId: String,
    val action: String? = null,
    @SerialName("analytics_status") val analyticsStatus: String? = null,
)

/**
 * Decodes the sample-workspace admin bodies. The server currently returns the payload bare,
 * but an `{ "success": …, "data": { … } }` envelope is accepted too so either shape parses.
 */
object SampleWorkspaceJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decodeProfiles(body: JsonElement): SampleWorkspacesResponse =
        json.decodeFromJsonElement(SampleWorkspacesResponse.serializer(), unwrap(body, "profiles"))

    fun decodeSwitch(body: JsonElement): SwitchSampleWorkspaceResponse =
        json.decodeFromJsonElement(SwitchSampleWorkspaceResponse.serializer(), unwrap(body, "account_id"))

    private fun unwrap(body: JsonElement, requiredKey: String): JsonElement {
        val obj = body as? JsonObject ?: return body
        if (obj.containsKey(requiredKey)) return obj
        return (obj["data"] as? JsonObject) ?: obj
    }
}
