package com.avago.feature.assets.data

import android.net.Uri
import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.map
import com.avago.feature.assets.model.RecallList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server query for an asset's NHTSA safety recalls (computed from an external feed; not synced).
 * Marking a recall remedied has no v2 command yet (docs "Catalog requests").
 */
interface AssetExtrasService {
    suspend fun recalls(assetId: String, refresh: Boolean): ApiResult<RecallList>
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
}
