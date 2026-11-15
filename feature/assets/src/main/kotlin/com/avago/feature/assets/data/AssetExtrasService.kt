package com.avago.feature.assets.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.map
import com.avago.feature.assets.model.RecallList
import com.avago.feature.assets.model.ToolDetails
import com.avago.feature.assets.model.VehicleDetails
import kotlinx.serialization.json.JsonObject
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/** Feature I per-asset endpoints: tool details, vehicle details and recalls. */
interface AssetExtrasService {
    suspend fun toolDetails(assetId: String): ApiResult<ToolDetails>
    suspend fun saveToolDetails(assetId: String, body: JsonObject): ApiResult<ToolDetails>
    suspend fun vehicleDetails(assetId: String): ApiResult<VehicleDetails>
    suspend fun saveVehicleDetails(assetId: String, body: JsonObject): ApiResult<VehicleDetails>
    suspend fun recalls(assetId: String, refresh: Boolean): ApiResult<RecallList>
    suspend fun setRemedied(assetId: String, campaign: String, remedied: Boolean, note: String?): ApiResult<RecallList>
}

@Singleton
class KtorAssetExtrasService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : AssetExtrasService {
    private inline fun <T> withAsset(assetId: String, block: (String) -> ApiResult<T>): ApiResult<T> {
        val a = identity.getActiveAccountId()
            ?: return ApiResult.Err(ApiError(-1, "no_account", "No active account"))
        return block("/accounts/$a/assets/${Uri.encode(assetId)}")
    }

    override suspend fun toolDetails(assetId: String) =
        withAsset(assetId) { api.get("$it/tool-details").map(ToolDetails::from) }

    override suspend fun saveToolDetails(assetId: String, body: JsonObject) =
        withAsset(assetId) { api.put("$it/tool-details", body).map(ToolDetails::from) }

    override suspend fun vehicleDetails(assetId: String) =
        withAsset(assetId) { api.get("$it/vehicle-details").map(VehicleDetails::from) }

    override suspend fun saveVehicleDetails(assetId: String, body: JsonObject) =
        withAsset(assetId) { api.put("$it/vehicle-details", body).map(VehicleDetails::from) }

    override suspend fun recalls(assetId: String, refresh: Boolean) = withAsset(assetId) {
        api.get("$it/recalls", if (refresh) mapOf("refresh" to true) else emptyMap()).map(RecallList::from)
    }

    override suspend fun setRemedied(assetId: String, campaign: String, remedied: Boolean, note: String?) =
        withAsset(assetId) {
            val path = "$it/recalls/${Uri.encode(campaign)}/remedy"
            if (remedied) {
                api.post(path, jsonBody("note" to note?.trim()?.ifEmpty { null })).map(RecallList::from)
            } else {
                api.delete(path).map(RecallList::from)
            }
        }
}
