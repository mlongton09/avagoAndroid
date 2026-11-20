package com.avago.feature.assets.data

import android.net.Uri
import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.map
import com.avago.feature.assets.model.AssetQr
import com.avago.feature.assets.model.QrScanPage
import com.avago.feature.assets.model.QrToken
import javax.inject.Inject
import javax.inject.Singleton

/** Public QR labels of assets and their scan history: online actions (decisions L38, not synced). */
interface AssetQrService {
    suspend fun token(assetId: String): ApiResult<QrToken?>

    /** Mints a fresh token; the previous label stops resolving. */
    suspend fun mint(assetId: String): ApiResult<QrToken>

    suspend fun revoke(assetId: String): ApiResult<Unit>

    /** Active token id -> asset id, to resolve a scanned label. */
    suspend fun tokenAssets(): ApiResult<Map<String, String>>

    suspend fun scans(assetId: String, page: Int, pageSize: Int = PAGE_SIZE): ApiResult<QrScanPage>

    companion object {
        const val PAGE_SIZE = 20
    }
}

@Singleton
class KtorAssetQrService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : AssetQrService {
    override suspend fun token(assetId: String): ApiResult<QrToken?> =
        withAccount { a -> api.get(assetPath(a, assetId) + "/qr-token").map(AssetQr::token) }

    override suspend fun mint(assetId: String): ApiResult<QrToken> = withAccount { a ->
        when (val r = api.post(assetPath(a, assetId) + "/qr-token")) {
            is ApiResult.Ok -> AssetQr.token(r.value)?.let { ApiResult.Ok(it) }
                ?: ApiResult.Err(ApiError(500, "invalid_response", "No token in response"))
            is ApiResult.Err -> r
        }
    }

    override suspend fun revoke(assetId: String): ApiResult<Unit> =
        withAccount { a -> api.delete(assetPath(a, assetId) + "/qr-token").map { } }

    override suspend fun tokenAssets(): ApiResult<Map<String, String>> =
        withAccount { a -> api.get("/accounts/$a/assets/qr-tokens").map(AssetQr::tokenAssets) }

    override suspend fun scans(assetId: String, page: Int, pageSize: Int): ApiResult<QrScanPage> = withAccount { a ->
        api.get(assetPath(a, assetId) + "/qr-scans", mapOf("page" to page, "page_size" to pageSize)).map(AssetQr::scans)
    }

    private fun assetPath(account: String, assetId: String) = "/accounts/$account/assets/${Uri.encode(assetId)}"

    private suspend fun <T> withAccount(block: suspend (String) -> ApiResult<T>): ApiResult<T> {
        val a = identity.getActiveAccountId() ?: return ApiResult.Err(ApiError(-1, "no_account", "No active account"))
        return block(a)
    }
}
