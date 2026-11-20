package com.avago.core.data.v2

import java.io.File

/** The storage answer to a PUT: its HTTP status and, on an error, the body's error code and message. */
data class V2BlobPutResult(val status: Int, val code: String? = null, val message: String? = null) {
    val isSuccess: Boolean get() = status in 200..299
}

/** PUTs bytes to a presigned storage URL (no service auth). Throws only when no answer was received. */
fun interface V2BlobTransport {
    suspend fun put(url: String, file: File, mediaType: String): V2BlobPutResult
}

/**
 * The server will never take this file (P3-20): its type is not allowed on the route (`400
 * unsupported_media_type` at register, `415 unsupported_media_type` at the PUT) or its bytes are
 * not the type it was registered as (`415 media_type_mismatch`). Retrying cannot help.
 */
class V2UploadRefusedException(val code: String, message: String?) : Exception(message ?: code)

/**
 * E12 upload handshake for one `upload_jobs` row (sync-protocol §9): register → PUT → finalize.
 * Each step is idempotent per `file_id`, so a retry after any failure simply starts over; the
 * outbox records the error and retries on the next flush. A refused file throws
 * [V2UploadRefusedException].
 */
class V2ApiUploader(
    private val api: V2Api,
    private val blobs: V2BlobTransport,
) : V2Uploader {

    override suspend fun process(jobId: String, localPath: String, sha256: String, byteSize: Long, mediaType: String): String {
        val file = File(localPath)
        val url = try {
            api.registerUpload(jobId, sha256, byteSize, mediaType, originalName = file.name)
        } catch (e: V2ApiException.Status) {
            if (e.status == 400 && e.code == UNSUPPORTED_MEDIA_TYPE) throw V2UploadRefusedException(UNSUPPORTED_MEDIA_TYPE, e.body?.message)
            throw e
        } ?: return FINALIZED
        check(file.isFile) { "upload source missing: $localPath" }
        val put = blobs.put(url, file, mediaType)
        if (put.status == 415) throw V2UploadRefusedException(put.code ?: UNSUPPORTED_MEDIA_TYPE, put.message)
        check(put.isSuccess) { "upload PUT failed with HTTP ${put.status}" }
        api.finalizeUpload(jobId, sha256, byteSize)
        return FINALIZED
    }

    companion object {
        const val UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type"
        const val MEDIA_TYPE_MISMATCH = "media_type_mismatch"
        private const val FINALIZED = "finalized"
    }
}
