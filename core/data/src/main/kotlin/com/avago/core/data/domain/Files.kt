package com.avago.core.data.domain

import java.io.File

/** Which stored bytes of a file: the original, or the server thumbnail (the original when none exists). */
enum class FileVariant { ORIGINAL, THUMBNAIL }

/**
 * The server cannot serve the file (unknown, deleted or its upload is not finalized: `404
 * file_not_found`) or the bytes could not be fetched (offline, signed URL refused).
 */
class FileUnavailableException(fileId: String, cause: Throwable? = null) :
    IllegalStateException("File $fileId is not available", cause)

/**
 * Bytes of synced `files` rows. The `files` projection carries metadata only; the bytes come from
 * `GET /v2/accounts/{a}/files/{id}/download` (signed URLs) and are kept in a device cache.
 */
interface FileRepository {
    /**
     * The device copy of [fileId], downloaded first when it is not cached yet. Throws
     * [FileUnavailableException], or [StoreNotReadyException] when nobody is signed in.
     */
    suspend fun fetch(fileId: String, variant: FileVariant = FileVariant.ORIGINAL): File

    /** Trims the download cache to [maxBytes], least recently used first; returns the bytes freed. */
    suspend fun trimCache(maxBytes: Long): Long
}

/**
 * Uploads a local file now (E12 register, PUT, finalize) for an online action whose REST request
 * names the file, such as a post charge billed at once on a closed order. Synced writes attach files
 * through their command's upload jobs instead. Returns the finalized file id; throws when offline,
 * or [StoreNotReadyException] when nobody is signed in.
 */
interface FileUploads {
    suspend fun upload(file: LocalFileRef): String
}
