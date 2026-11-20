package com.avago.core.data.v2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Wire DTOs of sync-wire.md / openapi sync.yaml. seq, w, g, cursors, scope_epoch and
// result_watermark are decimal strings; feed_generation is an int64 integer.

/** Accepts a JSON string or number and keeps its text (seq values are strings; tolerate numbers). */
object V2DecimalStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("V2DecimalString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String =
        (decoder as? JsonDecoder)?.decodeJsonElement()?.let { (it as JsonPrimitive).content } ?: decoder.decodeString()
}

/** Accepts a JSON number or decimal string as a Long (feed_generation is an integer). */
object V2LenientLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("V2LenientLong", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder): Long =
        (decoder as? JsonDecoder)?.decodeJsonElement()?.let { (it as JsonPrimitive).content.toLong() } ?: decoder.decodeLong()
}

object V2Wire {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

/** Error body of every non-2xx v2 answer: `{"error","message","retry_after_ms"?}` + extras. */
@Serializable
data class V2ErrorBody(
    val error: String,
    val message: String? = null,
    @SerialName("retry_after_ms") val retryAfterMs: Long? = null,
    val reason: String? = null,
)

@Serializable
data class V2RegisterDeviceRequest(
    @SerialName("device_id") val deviceId: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("push_token") val pushToken: String? = null,
)

@Serializable
data class V2StreamPosition(
    val cursor: String,
    @SerialName("feed_generation") val feedGeneration: Long,
)

@Serializable
data class V2OpenSessionRequest(
    /** `incremental` or `bootstrap`. */
    val mode: String,
    val account: V2StreamPosition? = null,
    val global: V2StreamPosition? = null,
    @SerialName("scope_epoch") val scopeEpoch: String? = null,
    @SerialName("continuation_of") val continuationOf: String? = null,
)

@Serializable
data class V2AccountWindow(
    @Serializable(V2DecimalStringSerializer::class) val from: String,
    @Serializable(V2DecimalStringSerializer::class) val w: String,
    @SerialName("feed_generation") @Serializable(V2LenientLongSerializer::class) val feedGeneration: Long,
)

@Serializable
data class V2GlobalWindow(
    @Serializable(V2DecimalStringSerializer::class) val from: String,
    @Serializable(V2DecimalStringSerializer::class) val g: String,
    @SerialName("feed_generation") @Serializable(V2LenientLongSerializer::class) val feedGeneration: Long,
)

@Serializable
data class V2OpenSessionResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("session_token") val sessionToken: String,
    val kind: String,
    /** `open`, or `completed` when both streams are current (`first_page_token` null). */
    val state: String,
    val account: V2AccountWindow,
    val global: V2GlobalWindow,
    @SerialName("scope_epoch") @Serializable(V2DecimalStringSerializer::class) val scopeEpoch: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("first_page_token") val firstPageToken: String? = null,
)

@Serializable
data class V2PageItem(
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_key") val entityKey: String,
    @Serializable(V2DecimalStringSerializer::class) val seq: String,
    val version: JsonElement? = null,
    /** `upsert` or `delete` (a delete has no body). */
    val op: String,
    val body: JsonObject? = null,
)

@Serializable
data class V2PageResponse(
    @SerialName("session_id") val sessionId: String,
    val stream: String,
    @SerialName("page_no") val pageNo: Int,
    @Serializable(V2DecimalStringSerializer::class) val after: String,
    @SerialName("last_seq") @Serializable(V2DecimalStringSerializer::class) val lastSeq: String,
    val w: JsonElement? = null,
    val g: JsonElement? = null,
    val items: List<V2PageItem>,
    /** `sha256:<64 hex>` over the canonical JSON of `items`. */
    val checksum: String? = null,
    @SerialName("next_page_token") val nextPageToken: String? = null,
    @SerialName("is_final") val isFinal: Boolean,
)

@Serializable
data class V2AckRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("account_cursor") val accountCursor: String,
    @SerialName("global_cursor") val globalCursor: String,
    @SerialName("bootstrap_completed") val bootstrapCompleted: Boolean,
)

@Serializable
data class V2CommandRequest(
    @SerialName("command_id") val commandId: String,
    val operation: String,
    @SerialName("payload_schema_version") val payloadSchemaVersion: Int,
    @SerialName("entity_type") val entityType: String?,
    @SerialName("entity_key") val entityKey: String?,
    @SerialName("base_version") val baseVersion: String?,
    @SerialName("depends_on") val dependsOn: List<String>,
    val payload: JsonElement,
) {
    /** Envelope with explicit nulls (sync.yaml: `base_version` is `string | null`). */
    fun toJson(): JsonObject = buildJsonObject {
        put("command_id", commandId)
        put("operation", operation)
        put("payload_schema_version", payloadSchemaVersion)
        put("entity_type", entityType?.let(::JsonPrimitive) ?: JsonNull)
        put("entity_key", entityKey?.let(::JsonPrimitive) ?: JsonNull)
        put("base_version", baseVersion?.let(::JsonPrimitive) ?: JsonNull)
        put("depends_on", kotlinx.serialization.json.JsonArray(dependsOn.map(::JsonPrimitive)))
        put("payload", payload)
    }
}

/** `200` answer of `POST commands` / `GET commands/{id}`: a stored outcome (or `processing`). */
@Serializable
data class V2CommandOutcome(
    @SerialName("command_id") val commandId: String? = null,
    /** `committed`, `rejected`, or `processing` (receipt endpoint only). */
    val state: String,
    @SerialName("result_watermark") val resultWatermark: JsonElement? = null,
    val result: JsonElement? = null,
    @SerialName("rejection_code") val rejectionCode: String? = null,
    val message: String? = null,
    @SerialName("conflict_id") val conflictId: String? = null,
) {
    val watermark: String? get() = (resultWatermark as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}

/** Text of a JSON string/number value; null for JSON null or absent. */
internal fun JsonElement?.decimalText(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

// ---------------------------------------------------------------------------------------------
// Transport

/** One HTTP call relative to the service base URL. */
data class V2Request(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class V2Response(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap()) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/**
 * Network seam. The app implementation (core/sync `V2HttpTransport`) adds the base URL and
 * `Authorization`; tests use a scripted mock. Throws only when no HTTP response was received.
 */
fun interface V2Transport {
    suspend fun send(request: V2Request): V2Response
}

sealed class V2ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No HTTP response (offline, timeout, TLS…). Safe to retry with the same request. */
    class Network(message: String, cause: Throwable? = null) : V2ApiException(message, cause)

    /** `426 upgrade_required` (any endpoint). */
    class UpgradeRequired : V2ApiException("426 upgrade_required")

    /** Any other non-2xx answer. */
    class Status(val status: Int, val body: V2ErrorBody?, val retryAfterMs: Long?) :
        V2ApiException("$status ${body?.error.orEmpty()} ${body?.message.orEmpty()}".trim()) {
        val code: String? get() = body?.error
    }

    class Decoding(message: String, cause: Throwable? = null) : V2ApiException(message, cause)
}

/** `GET /v2/accounts/{a}/files/{id}/download`: short-lived signed URLs, never stored. */
@Serializable
data class V2FileDownload(
    @SerialName("file_id") val fileId: String,
    val url: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("original_name") val originalName: String? = null,
)

/** Typed v2 calls (sync-wire.md). Adds `X-Contract-Version` and `X-Device-Id` to every request. */
class V2Api(
    private val transport: V2Transport,
    val accountId: String,
    var deviceId: String,
    private val contractVersion: Int = V2Contract.CLIENT_CONTRACT_VERSION,
) {
    private val accountPath get() = "/v2/accounts/$accountId"

    suspend fun registerDevice(body: V2RegisterDeviceRequest) {
        raw("POST", "/v2/devices", body = V2Wire.json.encodeToString(V2RegisterDeviceRequest.serializer(), body))
    }

    suspend fun openSession(body: V2OpenSessionRequest): V2OpenSessionResponse =
        decode(
            V2OpenSessionResponse.serializer(),
            raw("POST", "$accountPath/sync/sessions", body = V2Wire.json.encodeToString(V2OpenSessionRequest.serializer(), body)),
        )

    suspend fun page(sessionId: String, sessionToken: String, pageToken: String): V2PageResponse = decode(
        V2PageResponse.serializer(),
        raw(
            "GET",
            "$accountPath/sync/sessions/$sessionId/pages",
            query = mapOf("page_token" to pageToken),
            headers = mapOf(SESSION_HEADER to sessionToken),
        ),
    )

    suspend fun ack(body: V2AckRequest) {
        raw("POST", "$accountPath/sync/acks", body = V2Wire.json.encodeToString(V2AckRequest.serializer(), body))
    }

    suspend fun closeSession(sessionId: String, sessionToken: String, reason: String) {
        raw(
            "POST",
            "$accountPath/sync/sessions/$sessionId/close",
            headers = mapOf(SESSION_HEADER to sessionToken),
            body = """{"reason":"$reason"}""",
        )
    }

    suspend fun submitCommand(body: V2CommandRequest): V2CommandOutcome =
        decode(V2CommandOutcome.serializer(), raw("POST", "$accountPath/commands", body = body.toJson().toString()))

    suspend fun commandReceipt(commandId: String): V2CommandOutcome =
        decode(V2CommandOutcome.serializer(), raw("GET", "$accountPath/commands/$commandId"))

    /**
     * E12 register (sync-protocol ?9): idempotent per [fileId]. Returns the presigned `upload_url`
     * for a pending upload, or null when the file is already finalized.
     */
    suspend fun registerUpload(fileId: String, sha256: String, byteSize: Long, mediaType: String, originalName: String?): String? {
        val body = buildJsonObject {
            put("file_id", JsonPrimitive(fileId))
            put("sha256", JsonPrimitive(sha256))
            put("byte_size", JsonPrimitive(byteSize))
            put("media_type", JsonPrimitive(mediaType))
            if (originalName != null) put("original_name", JsonPrimitive(originalName))
        }
        val answer = decode(JsonObject.serializer(), raw("POST", "$accountPath/uploads", body = body.toString()))
        if (answer["state"]?.decimalText() == "finalized") return null
        return answer["upload_url"]?.decimalText() ?: throw V2ApiException.Decoding("upload register: no upload_url")
    }

    /** Signed GET URLs for a finalized file's bytes (and thumbnail); `404 file_not_found` otherwise. */
    suspend fun fileDownload(fileId: String): V2FileDownload =
        decode(V2FileDownload.serializer(), raw("GET", "$accountPath/files/$fileId/download"))

    /** E12 finalize: the server hashes the stored bytes; a mismatch is `422`. */
    suspend fun finalizeUpload(fileId: String, sha256: String, byteSize: Long) {
        val body = buildJsonObject {
            put("sha256", JsonPrimitive(sha256))
            put("byte_size", JsonPrimitive(byteSize))
        }
        raw("POST", "$accountPath/uploads/$fileId/finalize", body = body.toString())
    }

    private suspend fun raw(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): String {
        val all = headers + mapOf(
            CONTRACT_HEADER to contractVersion.toString(),
            DEVICE_HEADER to deviceId,
            "Accept" to "application/json",
        ) + (if (body != null) mapOf("Content-Type" to "application/json") else emptyMap())
        val response = try {
            transport.send(V2Request(method, path, query, all, body))
        } catch (e: V2ApiException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw V2ApiException.Network(e.message ?: e.toString(), e)
        }
        if (response.status in 200..299) return response.body
        if (response.status == 426) throw V2ApiException.UpgradeRequired()
        val error = runCatching { V2Wire.json.decodeFromString(V2ErrorBody.serializer(), response.body) }.getOrNull()
        val retryAfter = error?.retryAfterMs ?: response.header("Retry-After")?.toLongOrNull()?.times(1000)
        throw V2ApiException.Status(response.status, error, retryAfter)
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, body: String): T = try {
        V2Wire.json.decodeFromString(serializer, body)
    } catch (e: Exception) {
        throw V2ApiException.Decoding(e.message ?: "decode failed", e)
    }

    companion object {
        const val CONTRACT_HEADER = "X-Contract-Version"
        const val DEVICE_HEADER = "X-Device-Id"
        const val SESSION_HEADER = "X-Sync-Session"
    }
}
