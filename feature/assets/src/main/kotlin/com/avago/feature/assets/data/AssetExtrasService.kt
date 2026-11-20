package com.avago.feature.assets.data

import android.net.Uri
import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.map
import com.avago.feature.assets.model.RecallList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An asset's NHTSA safety recalls: a server query over an external feed with online remedy actions
 * (`masters.asset_recalls` is not synced; decisions L38).
 */
interface AssetExtrasService {
    suspend fun recalls(assetId: String, refresh: Boolean): ApiResult<RecallList>

    /** Marks [campaign] remedied (with an optional [note]) or reopens it; returns the updated list. */
    suspend fun setRemedied(assetId: String, campaign: String, remedied: Boolean, note: String? = null): ApiResult<RecallList>
}

@Singleton
class KtorAssetExtrasService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : AssetExtrasService {
    override suspend fun recalls(assetId: String, refresh: Boolean): ApiResult<RecallList> {
        val a = identity.getActiveAccountId()
            ?: return ApiResult.Err(ApiError(-1, "no_account", "No active account"))
        val path = "/accounts/$a/assets/${Uri.encode(assetId)}/recalls"
        return api.get(path, if (refresh) mapOf("refresh" to true) else emptyMap()).map(RecallList::from)
    }

    override suspend fun setRemedied(assetId: String, campaign: String, remedied: Boolean, note: String?): ApiResult<RecallList> {
        val a = identity.getActiveAccountId()
            ?: return ApiResult.Err(ApiError(-1, "no_account", "No active account"))
        val path = "/accounts/$a/assets/${Uri.encode(assetId)}/recalls/${Uri.encode(campaign)}/remedy"
        val result = if (remedied) {
            api.post(path, jsonBody("note" to note?.trim()?.ifEmpty { null }))
        } else {
            api.delete(path)
        }
        return result.map(RecallList::from)
    }
}
