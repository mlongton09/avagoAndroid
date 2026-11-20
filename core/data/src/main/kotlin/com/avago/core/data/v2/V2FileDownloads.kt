package com.avago.core.data.v2

import com.avago.core.data.domain.FileUnavailableException
import com.avago.core.data.domain.FileVariant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Streams a presigned storage GET into a file (no service auth); returns the HTTP status. */
fun interface V2BlobDownloader {
    /** [target] is written only on a 2xx answer. */
    suspend fun download(url: String, target: File): Int
}

/**
 * Device cache of synced files' bytes under [root]`/{account}/`. A miss asks
 * `files/{id}/download` for a signed URL and streams it through a `.part` file, so a cached copy is
 * always complete. A file id's bytes never change (a new revision is a new file), so a copy stays
 * valid until [trim] evicts it; reads refresh its recency.
 */
class V2FileDownloads(
    private val root: File,
    private val blobs: V2BlobDownloader,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun fetch(api: V2Api, fileId: String, variant: FileVariant): File {
        require(FILE_ID.matches(fileId)) { "not a file id: $fileId" }
        require(ACCOUNT_ID.matches(api.accountId)) { "not an account id: ${api.accountId}" }
        val dir = File(root, api.accountId)
        val target = File(dir, if (variant == FileVariant.THUMBNAIL) "$fileId.thumb" else fileId)
        return locks.getOrPut(target.path) { Mutex() }.withLock {
            if (target.isFile && target.length() > 0) {
                target.setLastModified(clock())
                return@withLock target
            }
            dir.mkdirs()
            val link = try {
                api.fileDownload(fileId)
            } catch (e: V2ApiException) {
                throw FileUnavailableException(fileId, e)
            } catch (e: IOException) {
                throw FileUnavailableException(fileId, e)
            }
            val url = if (variant == FileVariant.THUMBNAIL) link.thumbnailUrl ?: link.url else link.url
            val part = File(dir, "${target.name}.part")
            val status = try {
                blobs.download(url, part)
            } catch (e: IOException) {
                part.delete()
                throw FileUnavailableException(fileId, e)
            }
            if (status !in 200..299 || !part.isFile || !part.renameTo(target)) {
                part.delete()
                throw FileUnavailableException(fileId)
            }
            target.setLastModified(clock())
            target
        }
    }

    /** Deletes least recently used copies until the cache holds at most [maxBytes]; returns the bytes freed. */
    fun trim(maxBytes: Long): Long {
        val files = root.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.sortedBy { it.lastModified() }.toList()
        var total = files.sumOf { it.length() }
        var freed = 0L
        for (f in files) {
            if (total <= maxBytes) break
            val size = f.length()
            if (f.delete()) {
                total -= size
                freed += size
            }
        }
        return freed
    }

    private companion object {
        val FILE_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val ACCOUNT_ID = FILE_ID
    }
}
