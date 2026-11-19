package com.avago.core.data.v2

import java.io.File

/** PUTs bytes to a presigned storage URL (no service auth); returns the HTTP status. */
fun interface V2BlobTransport {
    suspend fun put(url: String, file: File, mediaType: String): Int
}

/**
 * E12 upload handshake for one `upload_jobs` row (sync-protocol §9): register → PUT → finalize.
 * Each step is idempotent per `file_id`, so a retry after any failure simply starts over; the
 * outbox records the error and retries on the next flush.
 */
class V2ApiUploader(
    private val api: V2Api,
    private val blobs: V2BlobTransport,
) : V2Uploader {

    override suspend fun process(jobId: String, localPath: String, sha256: String, byteSize: Long, mediaType: String): String {
        val file = File(localPath)
        val url = api.registerUpload(jobId, sha256, byteSize, mediaType, originalName = file.name)
            ?: return FINALIZED
        check(file.isFile) { "upload source missing: $localPath" }
        val status = blobs.put(url, file, mediaType)
        check(status in 200..299) { "upload PUT failed with HTTP $status" }
        api.finalizeUpload(jobId, sha256, byteSize)
        return FINALIZED
    }

    private companion object {
        const val FINALIZED = "finalized"
    }
}
