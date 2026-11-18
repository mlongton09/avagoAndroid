package com.avago.core.network.v2

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Outcome of probing `GET /v2/capabilities` (contract design E1). */
enum class V2ProbeResult {
    /** The server's contract range includes the client version. */
    COMPATIBLE,

    /** `426 upgrade_required`, or the client version is outside the advertised range. */
    INCOMPATIBLE,

    /** No `/v2` API on this server (404/405): pre-v2 or rolled back. */
    LEGACY,

    /** Network error, 5xx, or an unparseable answer: keep the previously stored state. */
    UNREACHABLE,
}

/**
 * Probes the server's v2 contract range. Sends `X-Contract-Version` as every v2 request does.
 *
 * The response body shape is not final server-side; [classify] accepts either
 * `{"contract_version":{"min":1,"max":1}}` or `{"min_contract_version":1,"max_contract_version":1}`.
 */
@Singleton
class V2CapabilitiesClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
) {
    suspend fun probe(clientContractVersion: Int): V2ProbeResult = try {
        val response = httpClient.get("${baseUrl.trimEnd('/')}/v2/capabilities") {
            expectSuccess = false
            header(CONTRACT_VERSION_HEADER, clientContractVersion.toString())
        }
        classify(response.status.value, response.bodyAsText(), clientContractVersion)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Timber.w(e, "V2CapabilitiesClient: probe failed")
        V2ProbeResult.UNREACHABLE
    }

    companion object {
        const val CONTRACT_VERSION_HEADER = "X-Contract-Version"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private const val HTTP_UPGRADE_REQUIRED = 426
        private val LEGACY_STATUSES = setOf(404, 405)
        private val SUCCESS_STATUSES = 200..299

        fun classify(status: Int, body: String, clientContractVersion: Int): V2ProbeResult = when (status) {
            HTTP_UPGRADE_REQUIRED -> V2ProbeResult.INCOMPATIBLE
            in LEGACY_STATUSES -> V2ProbeResult.LEGACY
            in SUCCESS_STATUSES -> classifyBody(body, clientContractVersion)
            else -> V2ProbeResult.UNREACHABLE
        }

        private fun classifyBody(body: String, clientContractVersion: Int): V2ProbeResult {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            val range = root?.get("contract_version") as? JsonObject
            val min = (range?.get("min") ?: root?.get("min_contract_version"))?.intOrNullSafe()
            val max = (range?.get("max") ?: root?.get("max_contract_version"))?.intOrNullSafe()
            return when {
                min == null || max == null -> V2ProbeResult.UNREACHABLE
                clientContractVersion in min..max -> V2ProbeResult.COMPATIBLE
                else -> V2ProbeResult.INCOMPATIBLE
            }
        }

        private fun kotlinx.serialization.json.JsonElement.intOrNullSafe(): Int? =
            runCatching { jsonPrimitive.intOrNull }.getOrNull()
    }
}
