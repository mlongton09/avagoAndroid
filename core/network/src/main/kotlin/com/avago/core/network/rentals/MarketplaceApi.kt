package com.avago.core.network.rentals

import com.avago.core.network.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Client for the public Avago Marketplace (`/public/marketplace/...`, AvagoSvc
 * docs/rentals-api-marketplace.md §3–§6).
 *
 * It deliberately uses its **own** HTTP client without the staff bearer `Auth` plugin: a renter
 * 401 must never trigger a staff token refresh (which signs staff out when the refresh fails).
 * Renter calls carry the session in `X-Marketplace-Token`; the header is redacted from debug logs.
 *
 * Responses are raw JSON (no envelope). Non-2xx answers throw [RentalsApiException] with the
 * server's stable `code`; transport failures propagate as IOExceptions.
 */
@Singleton
class MarketplaceApi @Inject constructor(
    @Named("baseUrl") private val baseUrl: String,
) {
    private val client: HttpClient by lazy { createClient(BuildConfig.DEBUG) }

    private fun p(path: String) = "$baseUrl/public/marketplace$path"

    // ---- renter auth (§3) ----------------------------------------------------------------

    /** POST /auth/magic-link `{email, name, locale, next}` → 202 `{sent}`. */
    suspend fun magicLink(body: JsonObject): JsonObject = call {
        client.post(p("/auth/magic-link")) { json(body) }.body()
    }

    /** POST /auth/session `{token}` → 201 `{token, expires_at, renter}`. */
    suspend fun createSession(body: JsonObject): JsonObject = call {
        client.post(p("/auth/session")) { json(body) }.body()
    }

    /** POST /auth/logout (revokes the session). */
    suspend fun logout(token: String): JsonObject = call {
        client.post(p("/auth/logout")) { renter(token); json(JsonObject(emptyMap())) }.body()
    }

    suspend fun me(token: String): JsonObject = call {
        client.get(p("/me")) { renter(token) }.body()
    }

    /** PATCH /me `{name?, phone?, locale?}` → renter. */
    suspend fun updateMe(token: String, body: JsonObject): JsonObject = call {
        client.patch(p("/me")) { renter(token); json(body) }.body()
    }

    // ---- public search and listing (§4, §5) -------------------------------------------------

    /** GET /places?q&country&limit → `{items, attribution}`. */
    suspend fun places(q: String, country: String?, limit: Int = 8): JsonObject = call {
        client.get(p("/places")) {
            parameter("q", q)
            country?.let { parameter("country", it) }
            parameter("limit", limit)
        }.body()
    }

    /** GET /search with the already-built query parameters (feature/rentals MarketplaceQuery). */
    suspend fun search(params: List<Pair<String, String>>): JsonObject = call {
        client.get(p("/search")) { params.forEach { (k, v) -> parameter(k, v) } }.body()
    }

    suspend fun listing(id: String, lat: Double?, lng: Double?, locationId: String?): JsonObject = call {
        client.get(p("/listings/$id")) {
            lat?.let { parameter("lat", it) }
            lng?.let { parameter("lng", it) }
            locationId?.let { parameter("location_id", it) }
        }.body()
    }

    /** GET /listings/{id}/availability?from&days(1–62)&location_id. */
    suspend fun availability(id: String, from: String, days: Int, locationId: String?): JsonObject = call {
        client.get(p("/listings/$id/availability")) {
            parameter("from", from)
            parameter("days", days)
            locationId?.let { parameter("location_id", it) }
        }.body()
    }

    /** GET /listings/{id}/meet-slots?from&days(1–31)&location_id. */
    suspend fun meetSlots(id: String, from: String?, days: Int, locationId: String?): JsonObject = call {
        client.get(p("/listings/$id/meet-slots")) {
            from?.let { parameter("from", it) }
            parameter("days", days)
            locationId?.let { parameter("location_id", it) }
        }.body()
    }

    // ---- renter actions (§6) ----------------------------------------------------------------

    /** POST /listings/{id}/reserve — 201 new order, 200 replay of the same `client_uuid`. */
    suspend fun reserve(token: String, id: String, body: JsonObject): JsonObject = call {
        client.post(p("/listings/$id/reserve")) { renter(token); json(body) }.body()
    }

    /** POST /listings/{id}/meetups → renter meet-up. */
    suspend fun requestMeetup(token: String, id: String, body: JsonObject): JsonObject = call {
        client.post(p("/listings/$id/meetups")) { renter(token); json(body) }.body()
    }

    /** GET /me/requests → `{meetups, bookings}`. */
    suspend fun myRequests(token: String): JsonObject = call {
        client.get(p("/me/requests")) { renter(token) }.body()
    }

    suspend fun cancelMeetup(token: String, id: String): JsonObject = call {
        client.post(p("/meetups/$id/cancel")) { renter(token); json(JsonObject(emptyMap())) }.body()
    }

    suspend fun acceptCounter(token: String, id: String): JsonObject = call {
        client.post(p("/meetups/$id/accept-counter")) { renter(token); json(JsonObject(emptyMap())) }.body()
    }

    // ---- plumbing --------------------------------------------------------------------------

    private fun HttpRequestBuilder.renter(token: String) {
        header(TOKEN_HEADER, token)
    }

    private fun HttpRequestBuilder.json(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        val text = runCatching { e.response.bodyAsText() }.getOrDefault("")
        throw RentalsApiException.from(e.response.status.value, text, e)
    }

    companion object {
        const val TOKEN_HEADER = "X-Marketplace-Token"

        private val codec = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

        private fun createClient(isDebug: Boolean): HttpClient = HttpClient(OkHttp) {
            expectSuccess = true
            install(ContentNegotiation) { json(codec) }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000L
                requestTimeoutMillis = 30_000L
                socketTimeoutMillis = 30_000L
            }
            install(DefaultRequest) {
                headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                headers.append(HttpHeaders.AcceptLanguage, Locale.getDefault().toLanguageTag())
            }
            if (isDebug) {
                install(Logging) {
                    level = LogLevel.HEADERS
                    logger = object : Logger {
                        override fun log(message: String) {
                            Timber.tag("AvagoMarketplace").d(message)
                        }
                    }
                    sanitizeHeader { it.equals(TOKEN_HEADER, ignoreCase = true) }
                }
            }
        }
    }
}
