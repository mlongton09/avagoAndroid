package com.avago.feature.growth.data

import java.net.URI
import java.net.URLDecoder

/**
 * Renter portal magic link, `{web}/rent/{slug}/portal?token={token}` (AvagoSvc
 * `routes/rentals_api/portal.rs`). The token is single-use and exchanged for a session.
 */
data class PortalLink(val slug: String, val token: String) {
    companion object {
        private val SLUG_RE = Regex("^[A-Za-z0-9_-]{1,80}$")
        private val TOKEN_RE = Regex("^[A-Za-z0-9_.~-]{16,256}$")

        fun parse(raw: String?): PortalLink? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() != "https" && uri.scheme?.lowercase() != "avago") return null
            val segs = buildList {
                if (uri.scheme.equals("avago", true)) uri.host?.let(::add)
                addAll(uri.path.orEmpty().split('/').filter { it.isNotEmpty() })
            }
            val i = segs.indexOf("rent")
            if (i < 0 || segs.getOrNull(i + 2) != "portal") return null
            val slug = segs[i + 1].takeIf { SLUG_RE.matches(it) } ?: return null
            val token = uri.rawQuery.orEmpty().split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it[0] == "token" && it.size == 2 }
                ?.let { URLDecoder.decode(it[1], "UTF-8") }
                ?.takeIf { TOKEN_RE.matches(it) } ?: return null
            return PortalLink(slug, token)
        }
    }
}
