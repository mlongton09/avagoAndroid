package com.avago.core.network.json

import com.avago.core.network.AvagoHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
        send(HttpMethod.Get, path, null, query, headers, null)

    suspend fun post(path: String, body: JsonObject = EMPTY, headers: Map<String, String> = emptyMap(), idempotencyKey: String = newKey()): ApiResult<JsonObject> =
        send(HttpMethod.Post, path, body, emptyMap(), headers, idempotencyKey)

    suspend fun put(path: String, body: JsonObject, headers: Map<String, String> = emptyMap()): ApiResult<JsonObject> =
        send(HttpMethod.Put, path, body, emptyMap(), headers, newKey())

    suspend fun patch(path: String, body: JsonObject): ApiResult<JsonObject> =
        send(HttpMethod.Patch, path, body, emptyMap(), emptyMap(), newKey())

    suspend fun delete(path: String): ApiResult<JsonObject> =
        send(HttpMethod.Delete, path, null, emptyMap(), emptyMap(), newKey())

    /** PUT raw bytes to a presigned upload URL with a client that sends no auth header. */
    suspend fun upload(url: String, bytes: ByteArray, contentType: String = "image/jpeg"): ApiResult<Unit> {
        val uploader = AvagoHttpClient.createUnauthenticatedClient()
        return try {
            val r = uploader.request(url) {
                method = HttpMethod.Put
                setBody(ByteArrayContent(bytes, ContentType.parse(contentType)))
            }
            if (r.status.isSuccess()) ApiResult.Ok(Unit) else ApiResult.Err(ApiError(r.status.value, "upload_failed", r.status.description))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            ApiResult.Err(ApiError(e.response.status.value, "upload_failed", e.response.status.description))
        } catch (e: Exception) {
            ApiResult.Err(ApiError(0, null, e.message ?: e.javaClass.simpleName))
        } finally {
            uploader.close()
        }
    }

    private suspend fun send(
        method: HttpMethod,
        path: String,
        body: JsonObject?,
        query: Map<String, Any?>,
        headers: Map<String, String>,
        idempotencyKey: String?,
    ): ApiResult<JsonObject> = try {
        val response = client.request(if (path.startsWith("http")) path else "$baseUrl$path") {
            this.method = method
            query.forEach { (k, v) -> if (v != null) parameter(k, v.toString()) }
            headers.forEach { (k, v) -> header(k, v) }
            idempotencyKey?.let { header("Idempotency-Key", it) }
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
