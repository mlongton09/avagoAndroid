package com.avago.core.network

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * How chat messages reference uploaded media (AvagoSvc P5-05): `{api}/chat/media/{storage_key}`.
 * The route needs the bearer token and answers with a redirect to a short-lived signed URL; sends
 * store the full key after `/chat/media/`.
 */
object ChatMediaLinks {
    const val PATH = "/chat/media/"

    /** The API URL of [storageKey]; each path segment is percent-encoded, the slashes are kept. */
    fun url(baseUrl: String, storageKey: String): String =
        baseUrl.trimEnd('/') + PATH + storageKey.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }

    /** True when [url] is a chat media reference on the API at [baseUrl]. */
    fun isChatMedia(baseUrl: String, url: String): Boolean = url.startsWith(baseUrl.trimEnd('/') + PATH)

    /**
     * The absolute signed URL a `/chat/media` answer redirects to (an absolute Location is kept byte for byte,
     * so its signature stays intact), or null when the answer is not a redirect.
     */
    fun redirectTarget(requestUrl: String, status: Int, location: String?): String? =
        if (status in FIRST_REDIRECT..LAST_REDIRECT && !location.isNullOrBlank()) {
            if (location.contains("://")) location else URI(requestUrl).resolve(location).toString()
        } else {
            null
        }

    private const val FIRST_REDIRECT = 300
    private const val LAST_REDIRECT = 399
}
