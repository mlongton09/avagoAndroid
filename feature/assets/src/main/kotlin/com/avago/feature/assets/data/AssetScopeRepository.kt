package com.avago.feature.assets.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.feature.assets.model.AssetTypeCatalog
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the account's asset-type catalog; falls back to [AssetTypeCatalog.Default] offline. */
interface AssetScopeRepository {
    suspend fun catalog(): AssetTypeCatalog
}

@Singleton
class KtorAssetScopeRepository @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : AssetScopeRepository {
    private val cache = ConcurrentHashMap<String, AssetTypeCatalog>()

    override suspend fun catalog(): AssetTypeCatalog {
        val account = identity.getActiveAccountId() ?: return AssetTypeCatalog.Default
        cache[account]?.let { return it }
        return when (val r = api.get("/accounts/$account/asset-types")) {
            is ApiResult.Ok -> AssetTypeCatalog.from(r.value).also { cache[account] = it }
            is ApiResult.Err -> AssetTypeCatalog.Default
        }
    }
}
