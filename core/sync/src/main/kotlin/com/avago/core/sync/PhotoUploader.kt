package com.avago.core.sync

import android.content.Context
import com.avago.core.data.DatabaseFactory
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Sweeps for locally-captured photos that have not yet been uploaded (storage_key IS NULL,
 * local_path IS NOT NULL) and pushes them to the server via a presigned upload URL.
 *
 * Mirrors the iOS PhotoUploader service. Use [Provider] for [AvagoServiceClient] to avoid
 * potential Hilt dependency cycles.
 */
@Singleton
class PhotoUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseFactory: DatabaseFactory,
    private val clientProvider: Provider<AvagoServiceClient>,
) {
    private val sweepMutex = Mutex()

    /** Tracks photo IDs currently in-flight to prevent double-upload within the same sweep. */
    private val inFlight = mutableSetOf<String>()

    /**
     * Triggers a sweep for [accountId].  Safe to call from any coroutine — concurrent
     * invocations are serialised by [sweepMutex].
     */
    suspend fun sweep(accountId: String) {
        sweepMutex.withLock {
            runSweep(accountId)
        }
    }

    private suspend fun runSweep(accountId: String) = withContext(Dispatchers.IO) {
        val client = clientProvider.get()
        val db = databaseFactory.get(accountId)
        val pending = db.photoDao().pendingUpload(accountId)

        if (pending.isEmpty()) {
            Timber.d("[PhotoUploader] No pending uploads for $accountId")
            return@withContext
        }

        Timber.d("[PhotoUploader] Sweeping ${pending.size} pending photo(s) for $accountId")

        for (photo in pending) {
            val photoId = photo.photoId
            if (!inFlight.add(photoId)) {
                Timber.d("[PhotoUploader] Skipping $photoId — already in-flight")
                continue
            }

            try {
                val localPath = photo.localPath ?: continue
                val file = File(localPath)

                val bytes = try {
                    withContext(Dispatchers.IO) { file.readBytes() }
                } catch (e: FileNotFoundException) {
                    Timber.w("[PhotoUploader] Local file not found for $photoId: $localPath — clearing local_path")
                    db.photoDao().clearLocalPath(photoId, System.currentTimeMillis())
                    continue
                } catch (e: Exception) {
                    Timber.e(e, "[PhotoUploader] Failed to read local file for $photoId: $localPath")
                    continue
                }

                // 1. Request presigned upload URL from server
                val urlResult = client.getPhotoUploadUrl(
                    accountId = accountId,
                    photoId = photoId,
                    entityId = photo.entityId,
                    entityType = photo.entityType,
                )

                val urlResponse = when (urlResult) {
                    is NetworkResult.Success -> urlResult.data
                    is NetworkResult.Error -> {
                        Timber.w("[PhotoUploader] getPhotoUploadUrl failed for $photoId: ${urlResult.code} ${urlResult.message}")
                        continue
                    }
                    is NetworkResult.Unauthorized -> {
                        Timber.w("[PhotoUploader] Unauthorized getting upload URL for $photoId — aborting sweep")
                        return@withContext
                    }
                }

                // 2. PUT binary to presigned URL
                val uploadResult = client.uploadPhotoBinary(urlResponse.upload_url, bytes)
                when (uploadResult) {
                    is NetworkResult.Success -> {
                        // 3. Persist storage_key in Room
                        db.photoDao().updateStorageKey(
                            photoId = photoId,
                            storageKey = urlResponse.storage_key,
                            now = System.currentTimeMillis(),
                        )
                        Timber.d("[PhotoUploader] Uploaded $photoId → storage_key=${urlResponse.storage_key}")
                    }
                    is NetworkResult.Error -> {
                        Timber.w("[PhotoUploader] Binary upload failed for $photoId: ${uploadResult.code} ${uploadResult.message}")
                    }
                    is NetworkResult.Unauthorized -> {
                        Timber.w("[PhotoUploader] Unauthorized uploading binary for $photoId — aborting sweep")
                        return@withContext
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[PhotoUploader] Unexpected error uploading $photoId")
            } finally {
                inFlight.remove(photoId)
            }
        }

        Timber.d("[PhotoUploader] Sweep complete for $accountId")
    }
}
