package com.avago.feature.assets.model

import com.avago.core.network.json.double
import com.avago.core.network.json.int
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import kotlinx.serialization.json.JsonObject
import java.net.URI

/** An asset's active public QR token; [url] is what the printed label encodes. */
data class QrToken(val tokenId: String, val url: String)

/** One scan of an asset's public QR label (`masters.asset_qr_scans`). */
data class QrScan(
    val id: String,
    val scannedByUserId: String?,
    val scannedAt: String?,
    val deviceInfo: String?,
    val appVersion: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class QrScanPage(val scans: List<QrScan>, val total: Int, val page: Int, val pageSize: Int) {
    val hasMore: Boolean get() = page * pageSize < total
}

/** Sheet layouts the bulk label job renders (`page_size` of `POST .../qr-tokens/batch`). */
enum class QrLabelLayout(val wire: String) {
    LETTER("letter"),
    A4("a4"),
    AVERY_5160("avery_5160"),
    AVERY_5163("avery_5163"),
    AVERY_L7160("avery_l7160"),
    AVERY_L7163("avery_l7163"),
    THERMAL_2X1IN("thermal_2x1in"),
    THERMAL_50X25MM("thermal_50x25mm"),
}

/** A bulk QR label job (`masters.asset_qr_batch_jobs`); the PDF downloads once [completed]. */
data class QrLabelJob(
    val id: String,
    val status: String,
    val pageSize: String?,
    val assetCount: Int,
    val outputFileId: String?,
) {
    val completed: Boolean get() = status == "completed" && outputFileId != null
}

/** Wire shapes of the asset QR REST endpoints (`/accounts/{a}/assets/.../qr-token(s)`, `qr-scans`). */
object AssetQr {
    /** Most assets one label job accepts. */
    const val MAX_BATCH_ASSETS = 2000

    /** `POST .../qr-tokens/batch` and `GET .../qr-tokens/batch/{id}`. */
    fun labelJob(root: JsonObject): QrLabelJob? {
        val o = root.data()
        return QrLabelJob(
            id = o.str("id") ?: return null,
            status = o.str("status") ?: return null,
            pageSize = o.str("page_size"),
            assetCount = o.int("asset_count") ?: 0,
            outputFileId = o.str("output_file_id"),
        )
    }

    /** Public report link the server mints tokens under. */
    const val URL_BASE = "https://www.avagomate.com/a/"
    private const val HOST = "avagomate.com"
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun url(tokenId: String): String = URL_BASE + tokenId

    /** The token id a scanned label encodes (`https://[www.]avagomate.com/a/{token}`), else null. */
    fun tokenFromScan(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() !in setOf("https", "http") || (host != HOST && !host.endsWith(".$HOST"))) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return segments.takeIf { it.size == 2 && it[0] == "a" && UUID.matches(it[1]) }?.get(1)?.lowercase()
    }

    /** `GET .../qr-token` (`token_id` or null) and `POST .../qr-token` (`token_id`, `url`). */
    fun token(root: JsonObject): QrToken? {
        val o = root.data()
        return o.str("token_id")?.let { QrToken(it, o.str("url") ?: url(it)) }
    }

    /** `GET .../assets/qr-tokens`: active token id -> asset id. */
    fun tokenAssets(root: JsonObject): Map<String, String> =
        root.data().objs("tokens").mapNotNull { t ->
            val token = t.str("token_id") ?: return@mapNotNull null
            val asset = t.str("asset_id") ?: return@mapNotNull null
            token.lowercase() to asset
        }.toMap()

    fun scans(root: JsonObject): QrScanPage {
        val o = root.data()
        val scans = o.objs("scans").mapNotNull { s ->
            QrScan(
                id = s.str("id") ?: return@mapNotNull null,
                scannedByUserId = s.str("scanned_by_user_id"),
                scannedAt = s.str("scanned_at"),
                deviceInfo = s.str("device_info"),
                appVersion = s.str("source_app_version"),
                latitude = s.double("latitude"),
                longitude = s.double("longitude"),
            )
        }
        return QrScanPage(scans, o.int("total") ?: scans.size, o.int("page") ?: 1, o.int("page_size") ?: scans.size)
    }

    private fun JsonObject.data(): JsonObject = obj("data") ?: this
}
