package com.avago.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber
import com.avago.core.network.model.RefreshRequest
import com.avago.core.network.model.AuthResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

interface TokenProvider {
    suspend fun accessToken(): String
    suspend fun refreshToken(): String
    suspend fun deviceId(): String
}

private fun synthetic429(request: okhttp3.Request, message: String): Response =
    Response.Builder()
        .request(request)
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(429)
        .message("Too Many Requests")
        .body(message.toResponseBody("text/plain".toMediaType()))
        .build()

private fun retryAfterSeconds(value: String?): Long? {
    value ?: return null
    value.toLongOrNull()?.let { return it }
    return runCatching {
        val instant = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        ((instant.toEpochMilli() - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }.getOrNull()
}

private fun parseWaitForSeconds(value: String): Long? =
    Regex("""Wait for (\d+)s""").find(value)?.groupValues?.getOrNull(1)?.toLongOrNull()

interface TokenStorage {
    suspend fun storeTokens(accessToken: String, refreshToken: String)
    suspend fun clearTokens()
}

interface RefreshFailedHandler {
    suspend fun onRefreshFailed()
}

object AvagoHttpClient {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        // Tripwire: a null-valued field is omitted from the wire rather than serialized as JSON null.
        // This matches Rust serde defaults (Vec<T>, #[serde(default)], Option<T> with skip_serializing_if).
        // If a future PATCH endpoint needs explicit-null semantics ("clear this field"), build that
        // request body as a hand-crafted JsonObject instead of relying on the shared DTO config.
        explicitNulls = false
    }

    /**
     * Creates the main authenticated HttpClient that uses the bearer auth plugin
     * with automatic token refresh.
     */
    fun create(
        baseUrl: String,
        tokenProvider: TokenProvider,
        tokenStorage: TokenStorage,
        isDebug: Boolean = false,
        refreshFailedHandler: RefreshFailedHandler? = null,
        rateLimitBackoffStore: RateLimitBackoffStore? = null,
    ): HttpClient = HttpClient(OkHttp) {
        // Throw ResponseException for any non-2xx before body deserialization (mirrors iOS status-before-parse)
        expectSuccess = true

        install(ContentNegotiation) {
            json(jsonConfig)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 30_000L
            requestTimeoutMillis = 60_000L
            socketTimeoutMillis = 60_000L
        }

        rateLimitBackoffStore?.let { store ->
            engine {
                addInterceptor { chain ->
                    val request = chain.request()
                    val endpoint = request.url.encodedPath
                    if (!endpoint.startsWith("/auth/")) {
                        val now = System.currentTimeMillis()
                        val nextAllowed = runBlocking { store.nextAllowedAtMs(endpoint) }
                        if (nextAllowed > now) {
                            val wait = ((nextAllowed - now) / 1000L).coerceAtLeast(1L)
                            return@addInterceptor synthetic429(request, "Rate limited. Wait for ${wait}s")
                        }
                    }

                    val response = chain.proceed(request)
                    if (response.code == 429 && !endpoint.startsWith("/auth/")) {
                        val waitSeconds = retryAfterSeconds(response.header("Retry-After"))
                            ?: parseWaitForSeconds(response.peekBody(1024).string())
                            ?: 900L
                        runBlocking {
                            store.setNextAllowedAtMs(
                                endpoint,
                                System.currentTimeMillis() + waitSeconds * 1_000L,
                            )
                        }
                    }
                    response
                }
            }
        }

        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
            headers.append("Accept", ContentType.Application.Json.toString())
        }

        if (isDebug) {
            install(Logging) {
                level = LogLevel.BODY
                logger = object : Logger {
                    override fun log(message: String) {
                        Timber.tag("AvagoHttp").d(message)
                    }

                }
            }
        }

        install(Auth) {
            bearer {
                loadTokens {
                    // Return the cached tokens as-is. If the token is expired the
                    // request will receive a 401 and Ktor will call refreshTokens,
                    // which is serialized — only one refresh fires even with many
                    // parallel requests. Proactive refresh here was a thundering-herd
                    // source: every concurrent request fired POST /auth/refresh
                    // simultaneously, exhausting the server rate-limit bucket.
                    BearerTokens(
                        accessToken = tokenProvider.accessToken(),
                        refreshToken = tokenProvider.refreshToken(),
                    )
                }

                refreshTokens {
                    val currentRefresh = tokenProvider.refreshToken()
                    val unauthClient = createUnauthenticatedClient(isDebug)
                    try {
                        val response = unauthClient.post("$baseUrl/auth/refresh") {
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refresh_token = currentRefresh, device_id = tokenProvider.deviceId()))
                            markAsRefreshTokenRequest()
                        }.body<AuthResponse>()
                        tokenStorage.storeTokens(response.access_token, response.refresh_token)
                        BearerTokens(
                            accessToken = response.access_token,
                            refreshToken = response.refresh_token,
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Token refresh failed")
                        tokenStorage.clearTokens()
                        refreshFailedHandler?.onRefreshFailed()
                        null
                    } finally {
                        unauthClient.close()
                    }
                }
            }
        }
    }

    /**
     * Creates a bare client with no auth — used only for the /auth/refresh call
     * so we don't create a circular auth loop.
     */
    fun createUnauthenticatedClient(isDebug: Boolean = false): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(jsonConfig)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 30_000L
            requestTimeoutMillis = 60_000L
        }

        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
            headers.append("Accept", ContentType.Application.Json.toString())
        }

        if (isDebug) {
            install(Logging) {
                level = LogLevel.HEADERS
                logger = object : Logger {
                    override fun log(message: String) {
                        Timber.tag("AvagoHttpUnauth").d(message)
                    }
                }
            }
        }
    }
}
