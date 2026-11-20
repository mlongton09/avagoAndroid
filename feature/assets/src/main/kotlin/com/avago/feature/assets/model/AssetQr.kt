package com.avago.feature.assets.model

import com.avago.core.data.domain.AssetLabelTemplate
import com.avago.core.network.json.double
import com.avago.core.network.json.int
import com.avago.core.network.json.jsonBody
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

/**
 * Sheet layouts the bulk label job renders (`page_size` of `POST .../qr-tokens/batch`).
 * [accepts] mirrors the server's sheet rules (maintenance.md "QR label templates"): the built-in
 * sheet prints on [LETTER], [A4] and the stock layouts; template labels print as a grid on
 * [LETTER] / [A4] when they fit inside the margins, one per page on [THERMAL], or one per cell of a
 * stock layout they fit.
 */
enum class QrLabelLayout(
    val wire: String,
    private val kind: Kind,
    private val widthMm: Double = 0.0,
    private val heightMm: Double = 0.0,
) {
    LETTER("letter", Kind.PAGE, 215.9, 279.4),
    A4("a4", Kind.PAGE, 210.0, 297.0),
    THERMAL("thermal", Kind.LABEL_SIZED),
    AVERY_5160("avery_5160", Kind.CELL, 66.675, 25.4),
    AVERY_5163("avery_5163", Kind.CELL, 101.6, 50.8),
    AVERY_L7160("avery_l7160", Kind.CELL, 63.5, 38.1),
    AVERY_L7163("avery_l7163", Kind.CELL, 99.1, 38.1),
    THERMAL_2X1IN("thermal_2x1in", Kind.CELL, 50.8, 25.4),
    THERMAL_50X25MM("thermal_50x25mm", Kind.CELL, 50.0, 25.0),
    ;

    private enum class Kind { PAGE, LABEL_SIZED, CELL }

    /** Whether labels of [template] print on this layout; null prints the built-in sheet. */
    fun accepts(template: AssetLabelTemplate?): Boolean {
        if (template == null) return kind != Kind.LABEL_SIZED
        val w = template.widthMm.toDouble()
        val h = template.heightMm.toDouble()
        return when (kind) {
            Kind.PAGE -> w <= widthMm - 2 * SHEET_MARGIN_MM + FIT_EPSILON_MM && h <= heightMm - 2 * SHEET_MARGIN_MM + FIT_EPSILON_MM
            Kind.LABEL_SIZED -> true
            Kind.CELL -> w <= widthMm + CELL_TOLERANCE_MM && h <= heightMm + CELL_TOLERANCE_MM
        }
    }

    private companion object {
        const val SHEET_MARGIN_MM = 6.35
        const val FIT_EPSILON_MM = 0.001

        /** The server lets a label exceed a stock cell by half a point. */
        const val CELL_TOLERANCE_MM = 0.5 * 25.4 / 72
    }
}

/** What a bulk label job prints with (`label_template` of `POST .../qr-tokens/batch`). */
sealed interface QrLabelTemplateChoice {
    /** The account default template, or the built-in sheet when the account has none. */
    data object AccountDefault : QrLabelTemplateChoice

    /** The built-in sheet even when the account has a default template. */
    data object BuiltIn : QrLabelTemplateChoice

    /** A saved, non-default template. */
    data class Saved(val templateId: String) : QrLabelTemplateChoice
}

/**
 * The label template a bulk job prints with for [choice]: the chosen saved template, else the
 * account default; null prints the built-in sheet (chosen, or no template applies).
 */
fun effectiveLabelTemplate(templates: List<AssetLabelTemplate>, choice: QrLabelTemplateChoice): AssetLabelTemplate? =
    when (choice) {
        QrLabelTemplateChoice.BuiltIn -> null
        QrLabelTemplateChoice.AccountDefault -> templates.firstOrNull { it.isDefault }
        is QrLabelTemplateChoice.Saved ->
            templates.firstOrNull { it.id == choice.templateId } ?: templates.firstOrNull { it.isDefault }
    }

/** A bulk QR label job (`masters.asset_qr_batch_jobs`); the PDF downloads once [completed]. */
data class QrLabelJob(
    val id: String,
    val status: String,
    val pageSize: String?,
    val assetCount: Int,
    val outputFileId: String?,
    /** The name of the template the job printed with; null for the built-in sheet or an unnamed template. */
    val templateName: String?,
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
            templateName = o.obj("label_template")?.str("name"),
        )
    }

    /**
     * `POST .../qr-tokens/batch` body. A saved template is sent as `{"template_id"}` and the
     * built-in sheet as `{"builtin": true}` (the server picks the sheet from `page_size`); for the
     * account default `label_template` is omitted, so the server applies the default template (or
     * the built-in sheet when there is none).
     */
    fun labelSheetBody(assetIds: List<String>, layout: QrLabelLayout, choice: QrLabelTemplateChoice): JsonObject = jsonBody(
        "asset_ids" to assetIds,
        "format" to "pdf",
        "page_size" to layout.wire,
        "label_template" to when (choice) {
            QrLabelTemplateChoice.AccountDefault -> null
            QrLabelTemplateChoice.BuiltIn -> jsonBody("builtin" to true)
            is QrLabelTemplateChoice.Saved -> jsonBody("template_id" to choice.templateId)
        },
    )

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
