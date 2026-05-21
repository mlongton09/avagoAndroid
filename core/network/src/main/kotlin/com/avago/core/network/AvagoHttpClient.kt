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

interface TokenProvider {
    suspend fun accessToken(): String
    suspend fun refreshToken(): String
}

interface TokenStorage {
    suspend fun storeTokens(accessToken: String, refreshToken: String)
    suspend fun clearTokens()
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
        deviceId: String,
        isDebug: Boolean = false,
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
                            setBody(RefreshRequest(refresh_token = currentRefresh, device_id = deviceId))
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
