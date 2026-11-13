package com.avago.app

import java.net.URI

/**
 * Turns an NFC tag payload (web asset URL, `avago://asset/{id}` or a bare asset id)
 * into an asset id. Pure so it can be unit tested without NDEF classes.
 */
object AssetTagParser {
    private val ID_RE = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val NON_ASSET_SEGMENTS = setOf("new", "add", "list")

    fun assetId(payload: String?): String? {
        val text = payload?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.contains("://")) return fromUri(text)
        return text.takeIf { ID_RE.matches(it) }
    }

    private fun fromUri(text: String): String? {
        val uri = try { URI(text) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase()
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val candidate = when (scheme) {
            "avago" -> if (uri.host == "asset" || uri.host == "assets") segments.firstOrNull() else null
            "http", "https" -> {
                val i = segments.indexOf("assets")
                if (i >= 0) segments.getOrNull(i + 1) else null
            }
            else -> null
        }
        return candidate?.takeIf { ID_RE.matches(it) && it.lowercase() !in NON_ASSET_SEGMENTS }
    }

    /** Decodes an NFC Forum RTD_TEXT payload (status byte, language code, text). */
    fun decodeRtdText(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val langLen = status and 0x3F
        if (1 + langLen > payload.size) return null
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        return String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
    }
}
