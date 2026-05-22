package com.avago.core.sync

import android.content.Context
import com.avago.core.data.DatabaseFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU eviction sweeper for locally-captured photos that have already been uploaded to the server
 * (storage_key IS NOT NULL, local_path IS NOT NULL).
 *
 * Enforces a 500 MB soft cap on local photo storage, deleting the oldest files first and
 * nulling out their [local_path] in Room so the image renderer knows to re-download from
 * [download_url].
 *
 * Mirrors the iOS post-foreground cache sweeper (deferred 30 s).
 */
@Singleton
class PhotoCacheSweeper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseFactory: DatabaseFactory,
) {
    @Volatile private var lastRunMs = 0L

    private val MIN_INTERVAL_MS = 30 * 60 * 1000L  // 30 minutes between runs
    private val SOFT_CAP_BYTES = 500L * 1024 * 1024 // 500 MB

    /**
     * Runs the cache sweep only if at least [MIN_INTERVAL_MS] have passed since the last run.
     */
    suspend fun runIfNeeded(accountId: String) {
        val now = System.currentTimeMillis()
        if (now - lastRunMs < MIN_INTERVAL_MS) {
            Timber.d("[PhotoCacheSweeper] Skipping — ran ${now - lastRunMs} ms ago")
            return
        }
        lastRunMs = now
        run(accountId)
    }

    private suspend fun run(accountId: String) = withContext(Dispatchers.IO) {
        val db = databaseFactory.get(accountId)
        val evictable = db.photoDao().uploadedWithLocalPath(accountId)

        if (evictable.isEmpty()) {
            Timber.d("[PhotoCacheSweeper] No evictable photos for $accountId")
            return@withContext
        }

        // Pair each photo with its File so we can sort by last-modified and calculate size
        data class PhotoFile(val photoId: String, val file: File)

        val photoFiles = evictable.mapNotNull { photo ->
            val path = photo.localPath ?: return@mapNotNull null
            val file = File(path)
            if (file.exists()) PhotoFile(photo.photoId, file) else null
        }

        val totalBytes = photoFiles.sumOf { it.file.length() }

        Timber.d("[PhotoCacheSweeper] Local photo cache: ${totalBytes / (1024 * 1024)} MB / ${SOFT_CAP_BYTES / (1024 * 1024)} MB cap (${photoFiles.size} files)")

        if (totalBytes <= SOFT_CAP_BYTES) {
            Timber.d("[PhotoCacheSweeper] Under cap — nothing to evict")
            return@withContext
        }

        // Sort oldest-first by last-modified time
        val sorted = photoFiles.sortedBy { it.file.lastModified() }

        var remaining = totalBytes
        val now = System.currentTimeMillis()

        for (pf in sorted) {
            if (remaining <= SOFT_CAP_BYTES) break

            val fileSize = pf.file.length()

            try {
                val deleted = pf.file.delete()
                if (deleted) {
                    db.photoDao().clearLocalPath(pf.photoId, now)
                    remaining -= fileSize
                    Timber.d("[PhotoCacheSweeper] Evicted ${pf.file.name} (${fileSize / 1024} KB) for photo ${pf.photoId}")
                } else {
                    Timber.w("[PhotoCacheSweeper] Failed to delete ${pf.file.absolutePath}")
                }
            } catch (e: Exception) {
                Timber.e(e, "[PhotoCacheSweeper] Error evicting ${pf.file.absolutePath}")
            }
        }

        Timber.d("[PhotoCacheSweeper] Sweep complete for $accountId — cache now ~${remaining / (1024 * 1024)} MB")
    }
}
