package com.avago.feature.assets.data

import android.content.Context
import android.net.Uri
import android.os.Build
import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.map
import com.avago.core.network.json.str
import com.avago.feature.assets.model.AssetQr
import com.avago.feature.assets.model.QrLabelJob
import com.avago.feature.assets.model.QrLabelLayout
import com.avago.feature.assets.model.QrLabelTemplateChoice
import com.avago.feature.assets.model.QrScanPage
import com.avago.feature.assets.model.QrToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import java.time.Instant
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

    /** Records an in-app scan of the asset's label (append-only telemetry, never synced); returns the scan id. */
    suspend fun recordScan(assetId: String, scannedAt: Instant): ApiResult<String>

    /**
     * Renders one PDF label sheet for [assetIds] (rendered synchronously: the job comes back completed)
     * with the template [choice] names: a saved template, the account default or the built-in sheet.
     */
    suspend fun createLabelSheet(assetIds: List<String>, layout: QrLabelLayout, choice: QrLabelTemplateChoice): ApiResult<QrLabelJob>

    suspend fun labelJob(jobId: String): ApiResult<QrLabelJob>

    /** The PDF of a completed label job. */
    suspend fun downloadLabelSheet(jobId: String): ApiResult<ByteArray>

    companion object {
        const val PAGE_SIZE = 20
    }
}

@Singleton
class KtorAssetQrService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
    @ApplicationContext private val context: Context,
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

    override suspend fun recordScan(assetId: String, scannedAt: Instant): ApiResult<String> = withAccount { a ->
        val body = jsonBody(
            "scanned_at" to scannedAt.toString(),
            "device_info" to jsonBody(
                "platform" to "android",
                "os_version" to Build.VERSION.RELEASE,
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
            ),
            "source_app_version" to appVersion()?.take(MAX_APP_VERSION),
        )
        when (val r = api.post(assetPath(a, assetId) + "/qr-scans", body)) {
            is ApiResult.Ok -> r.value.str("id")?.let { ApiResult.Ok(it) }
                ?: ApiResult.Err(ApiError(500, "invalid_response", "No scan id in response"))
            is ApiResult.Err -> r
        }
    }

    override suspend fun createLabelSheet(assetIds: List<String>, layout: QrLabelLayout, choice: QrLabelTemplateChoice): ApiResult<QrLabelJob> =
        withAccount { a ->
            val body = AssetQr.labelSheetBody(assetIds, layout, choice)
            api.post("/accounts/$a/assets/qr-tokens/batch", body, timeoutMs = LABEL_TIMEOUT_MS).job()
        }

    override suspend fun labelJob(jobId: String): ApiResult<QrLabelJob> =
        withAccount { a -> api.get(batchPath(a, jobId)).job() }

    override suspend fun downloadLabelSheet(jobId: String): ApiResult<ByteArray> =
        withAccount { a -> api.download(batchPath(a, jobId) + "/download") }

    private fun ApiResult<JsonObject>.job(): ApiResult<QrLabelJob> = when (this) {
        is ApiResult.Ok -> AssetQr.labelJob(value)?.let { ApiResult.Ok(it) }
            ?: ApiResult.Err(ApiError(500, "invalid_response", "No label job in response"))
        is ApiResult.Err -> this
    }

    private fun appVersion(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    private fun batchPath(account: String, jobId: String) = "/accounts/$account/assets/qr-tokens/batch/${Uri.encode(jobId)}"

    private fun assetPath(account: String, assetId: String) = "/accounts/$account/assets/${Uri.encode(assetId)}"

    private suspend fun <T> withAccount(block: suspend (String) -> ApiResult<T>): ApiResult<T> {
        val a = identity.getActiveAccountId() ?: return ApiResult.Err(ApiError(-1, "no_account", "No active account"))
        return block(a)
    }

    private companion object {
        const val MAX_APP_VERSION = 64
        const val LABEL_TIMEOUT_MS = 120_000L
    }
}
