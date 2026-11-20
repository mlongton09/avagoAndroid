package com.avago.core.sync.v2

import com.avago.core.data.v2.V2BlobDownloader
import com.avago.core.data.v2.V2BlobPutResult
import com.avago.core.data.v2.V2BlobTransport
import com.avago.core.network.AvagoHttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.writeFully
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Presigned PUTs and GETs go straight to storage, so they use a client without the service bearer. */
@Singleton
class V2HttpBlobTransport @Inject constructor() : V2BlobTransport, V2BlobDownloader {
    override suspend fun put(url: String, file: File, mediaType: String): V2BlobPutResult {
        val client = AvagoHttpClient.createUnauthenticatedClient()
        return try {
            val response = client.put(url) {
                expectSuccess = false
                setBody(FileContent(file, ContentType.parse(mediaType)))
            }
            if (response.status.isSuccess()) {
                V2BlobPutResult(response.status.value)
            } else {
                blobPutError(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
            }
        } finally {
            client.close()
        }
    }

    override suspend fun download(url: String, target: File): Int {
        val client = AvagoHttpClient.createUnauthenticatedClient()
        return try {
            client.prepareGet(url).execute { response ->
                if (response.status.isSuccess()) {
                    response.bodyAsChannel().toInputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                }
                response.status.value
            }
        } finally {
            client.close()
        }
    }
}

/**
 * The storage proxy answers errors with the REST body `{"error": message, "code": code}` (P3-20
 * `415 media_type_mismatch` / `unsupported_media_type`); a bare `{"error": code}` is read too.
 */
internal fun blobPutError(status: Int, body: String?): V2BlobPutResult {
    val json = body?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val code = json?.get("code")?.jsonPrimitive?.contentOrNull
    val error = json?.get("error")?.jsonPrimitive?.contentOrNull
    val message = json?.get("message")?.jsonPrimitive?.contentOrNull
    return if (code != null) {
        V2BlobPutResult(status, code, message ?: error)
    } else {
        V2BlobPutResult(status, error, message)
    }
}

/** Streams [file] as the request body without loading it into memory. */
private class FileContent(private val file: File, override val contentType: ContentType) : OutgoingContent.WriteChannelContent() {
    override val contentLength: Long get() = file.length()

    override suspend fun writeTo(channel: ByteWriteChannel) {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                channel.writeFully(buffer, 0, n)
            }
        }
    }
}
