package com.avago.feature.assets.data

import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.map
import com.avago.feature.assets.model.AssetTaxonomy
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetTaxonomyRepository @Inject constructor(private val api: JsonApi) {
    suspend fun load(): ApiResult<AssetTaxonomy> = api.get("/asset-taxonomy").map(AssetTaxonomy::parse)
    suspend fun suggest(account: String, name: String, type: String, make: String = "", model: String = "") =
        api.post("/accounts/$account/asset-taxonomy/suggest", buildJsonObject {
            put("name", name); put("asset_type", type)
            if (make.isNotBlank()) put("make", make)
            if (model.isNotBlank()) put("model", model)
        }, timeoutMs = 110_000)
}
