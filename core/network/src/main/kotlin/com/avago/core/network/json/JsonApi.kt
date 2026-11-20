package com.avago.core.network.json

import com.avago.core.network.AvagoHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Server error `{error, code, message, details}`; [status] 0 means no response (offline, DNS, timeout). */
data class ApiError(
    val status: Int,
    val code: String?,
    val message: String,
    val details: JsonObject? = null,
) {
    val offline: Boolean get() = status == 0
}

/** Outcome of a [JsonApi] call. Only cancellation is thrown. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val error: ApiError) : ApiResult<Nothing>

    fun valueOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): ApiError? = (this as? Err)?.error
}

/** Maps the success value; a parser exception becomes a `parse_error` failure instead of a crash. */
inline fun <T, R> ApiResult<T>.map(f: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> try {
        ApiResult.Ok(f(value))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Err(ApiError(-1, "parse_error", e.message ?: "parse error"))
    }
    is ApiResult.Err -> this
}

/**
 * Thin JSON client for the feature endpoints (crew, trust, growth, payouts, passport, scope…).
 * Bodies are bare JSON objects; a lone `{data: …}` envelope is unwrapped. Mutations carry an
 * `Idempotency-Key` so a retried tap does not apply twice.
 */
@Singleton
class JsonApi @Inject constructor(
    private val client: HttpClient,
    @Named("baseUrl") val baseUrl: String,
) {
    suspend fun get(path: String, query: Map<String, Any?> = emptyMap(), headers: Map<String, String> = emptyMap()): ApiResult<JsonObject> =
        send(HttpMethod.Get, path, null, RequestOptions(query = query, headers = headers))

    suspend fun post(path: String, body: JsonObject = EMPTY, headers: Map<String, String> = emptyMap(), idempotencyKey: String = newKey(), timeoutMs: Long? = null): ApiResult<JsonObject> =
        send(HttpMethod.Post, path, body, RequestOptions(idempotencyKey, timeoutMs, headers = headers))

    suspend fun put(path: String, body: JsonObject, headers: Map<String, String> = emptyMap()): ApiResult<JsonObject> =
        send(HttpMethod.Put, path, body, RequestOptions(newKey(), headers = headers))

    suspend fun patch(path: String, body: JsonObject): ApiResult<JsonObject> =
        send(HttpMethod.Patch, path, body, RequestOptions(newKey()))

    suspend fun delete(path: String): ApiResult<JsonObject> =
        send(HttpMethod.Delete, path, null, RequestOptions(newKey()))

    /** GET a binary body (a rendered PDF, an image) as bytes; errors carry the JSON error shape. */
    suspend fun download(path: String, accept: String = "application/pdf"): ApiResult<ByteArray> = try {
        val response = client.request(if (path.startsWith("http")) path else "$baseUrl$path") {
            method = HttpMethod.Get
            header(HttpHeaders.Accept, accept)
        }
        if (response.status.isSuccess()) {
            ApiResult.Ok(response.bodyAsBytes())
        } else {
            ApiResult.Err(parseError(response.status.value, response.bodyAsText()))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        ApiResult.Err(parseError(e.response.status.value, runCatching { e.response.bodyAsText() }.getOrDefault("")))
    } catch (e: Exception) {
        ApiResult.Err(ApiError(if (e is java.io.IOException) 0 else -1, null, e.message ?: e.javaClass.simpleName))
    }

    /**
     * PUT raw bytes to a presigned upload URL with a client that sends no auth header. [contentType]
     * must be the type the upload was registered with: storage refuses other bytes (`415`).
     */
    suspend fun upload(url: String, bytes: ByteArray, contentType: String): ApiResult<Unit> {
        val uploader = AvagoHttpClient.createUnauthenticatedClient()
        return try {
            val r = uploader.request(url) {
                method = HttpMethod.Put
                setBody(ByteArrayContent(bytes, ContentType.parse(contentType)))
            }
            if (r.status.isSuccess()) ApiResult.Ok(Unit) else ApiResult.Err(parseError(r.status.value, runCatching { r.bodyAsText() }.getOrDefault("")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            ApiResult.Err(parseError(e.response.status.value, runCatching { e.response.bodyAsText() }.getOrDefault("")))
        } catch (e: Exception) {
            ApiResult.Err(ApiError(0, null, e.message ?: e.javaClass.simpleName))
        } finally {
            uploader.close()
        }
    }

    private data class RequestOptions(
        val idempotencyKey: String? = null,
        val timeoutMs: Long? = null,
        val query: Map<String, Any?> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
    )

    private suspend fun send(
        method: HttpMethod,
        path: String,
        body: JsonObject?,
        options: RequestOptions,
    ): ApiResult<JsonObject> = try {
        val response = client.request(if (path.startsWith("http")) path else "$baseUrl$path") {
            this.method = method
            options.timeoutMs?.let { timeout { requestTimeoutMillis = it; socketTimeoutMillis = it } }
            options.query.forEach { (k, v) -> if (v != null) parameter(k, v.toString()) }
            options.headers.forEach { (k, v) -> header(k, v) }
            options.idempotencyKey?.let { header("Idempotency-Key", it) }
            if (body != null) setBody(TextContent(body.toString(), ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        if (response.status.isSuccess()) ApiResult.Ok(parseBody(text)) else ApiResult.Err(parseError(response.status.value, text))
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        ApiResult.Err(parseError(e.response.status.value, runCatching { e.response.bodyAsText() }.getOrDefault("")))
    } catch (e: Exception) {
        ApiResult.Err(ApiError(if (e is java.io.IOException) 0 else -1, null, e.message ?: e.javaClass.simpleName))
    }

    companion object {
        val EMPTY = JsonObject(emptyMap())
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun newKey(): String = UUID.randomUUID().toString()

        /** Bare object, or a lone `{data: {...}}` envelope unwrapped; a top-level array becomes `{items: [...]}`. */
        fun parseBody(text: String): JsonObject {
            if (text.isBlank()) return EMPTY
            val obj = when (val el = json.parseToJsonElement(text)) {
                is JsonObject -> el
                is JsonArray -> JsonObject(mapOf("items" to el))
                else -> JsonObject(mapOf("value" to el))
            }
            val data = obj["data"]
            return if (obj.size == 1 && data is JsonObject) data else obj
        }

        /** `{error, code, message, details}`; `error` may also be a nested object of the same shape. */
        fun parseError(status: Int, text: String): ApiError {
            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: return ApiError(status, null, text.take(200).ifBlank { "HTTP $status" })
            val src = obj.obj("error") ?: obj
            val errorText = (obj["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val code = src.str("code") ?: errorText?.takeIf { it.matches(CODE) }
            val message = src.str("message") ?: errorText ?: "HTTP $status"
            return ApiError(status, code, message, src.obj("details"))
        }

        private val CODE = Regex("^[a-z0-9_.]+$")
    }
}
