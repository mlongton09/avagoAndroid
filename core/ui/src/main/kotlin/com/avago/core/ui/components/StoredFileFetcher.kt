package com.avago.core.ui.components

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.avago.core.data.domain.FileRepository
import com.avago.core.data.domain.FileVariant
import com.avago.core.data.domain.StoredFile
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/** Coil model for the bytes of a synced `files` row; resolved through [FileRepository]. */
data class StoredFileRequest(val fileId: String, val variant: FileVariant = FileVariant.ORIGINAL)

/**
 * What to hand an image loader for this file: this device's copy when present, else the verified
 * server copy; null for non-images and files whose bytes the server cannot serve yet.
 */
fun StoredFile.imageModel(
    variant: FileVariant = FileVariant.ORIGINAL,
    localExists: (File) -> Boolean = File::exists,
): Any? {
    val local = localFile
    return when {
        !isImage -> null
        local != null && localExists(local) -> local
        isDownloadable -> StoredFileRequest(id, variant)
        else -> null
    }
}

/** Loads [StoredFileRequest]s from the device file cache, downloading on a miss. */
class StoredFileFetcher(
    private val request: StoredFileRequest,
    private val files: FileRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val file = files.fetch(request.fileId, request.variant)
        return SourceFetchResult(
            source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    /** [files] is resolved per request so the image loader can be built before injection completes. */
    class Factory(private val files: () -> FileRepository) : Fetcher.Factory<StoredFileRequest> {
        override fun create(data: StoredFileRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            StoredFileFetcher(data, files())
    }
}

/** Memory-cache key: a file id's bytes never change, so the id and variant identify the image. */
class StoredFileKeyer : Keyer<StoredFileRequest> {
    override fun key(data: StoredFileRequest, options: Options): String = "v2file:${data.fileId}:${data.variant}"
}
