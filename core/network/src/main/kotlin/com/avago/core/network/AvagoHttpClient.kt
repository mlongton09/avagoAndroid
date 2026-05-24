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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import timber.log.Timber
import com.avago.core.network.model.RefreshRequest
import com.avago.core.network.model.AuthResponse

interface TokenProvider {
    suspend fun accessToken(): String
    suspend fun refreshToken(): String
    suspend fun deviceId(): String
}

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
    ): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 30_000L
            requestTimeoutMillis = 60_000L
            socketTimeoutMillis = 60_000L
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
                    val access = tokenProvider.accessToken()
                    if (access.isBlank() || isTokenNearExpiry(access)) {
                        val refresh = tokenProvider.refreshToken()
                        if (refresh.isNotBlank()) {
                            val unauthClient = createUnauthenticatedClient(isDebug)
                            try {
                                val response = unauthClient.post("$baseUrl/auth/refresh") {
                                    contentType(ContentType.Application.Json)
                                    setBody(RefreshRequest(refresh_token = refresh, device_id = tokenProvider.deviceId()))
                                }.body<AuthResponse>()
                                tokenStorage.storeTokens(response.access_token, response.refresh_token)
                                return@loadTokens BearerTokens(
                                    accessToken = response.access_token,
                                    refreshToken = response.refresh_token,
                                )
                            } catch (e: Exception) {
                                Timber.w(e, "Proactive token refresh failed, proceeding with existing token")
                            } finally {
                                unauthClient.close()
                            }
                        }
                    }
                    BearerTokens(
                        accessToken = access,
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

    private fun isTokenNearExpiry(token: String): Boolean {
        return try {
            val payload = token.split(".").getOrNull(1) ?: return true
            val decoded = String(android.util.Base64.decode(
                payload.replace('-', '+').replace('_', '/'),
                android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            ))
            val exp = kotlinx.serialization.json.Json.parseToJsonElement(decoded)
                .jsonObject["exp"]?.jsonPrimitive?.long ?: return true
            val nowSeconds = System.currentTimeMillis() / 1000
            (exp - nowSeconds) < 60L
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Creates a bare client with no auth — used only for the /auth/refresh call
     * so we don't create a circular auth loop.
     */
    fun createUnauthenticatedClient(isDebug: Boolean = false): HttpClient = HttpClient(OkHttp) {
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
