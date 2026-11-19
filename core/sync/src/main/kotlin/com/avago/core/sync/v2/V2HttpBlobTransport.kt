package com.avago.core.sync.v2

import com.avago.core.data.v2.V2BlobTransport
import com.avago.core.network.AvagoHttpClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Presigned PUTs go straight to storage, so they use a client without the service bearer. */
@Singleton
class V2HttpBlobTransport @Inject constructor() : V2BlobTransport {
    override suspend fun put(url: String, file: File, mediaType: String): Int {
        val client = AvagoHttpClient.createUnauthenticatedClient()
        return try {
            client.put(url) {
                setBody(FileContent(file, ContentType.parse(mediaType)))
            }.status.value
        } finally {
            client.close()
        }
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
