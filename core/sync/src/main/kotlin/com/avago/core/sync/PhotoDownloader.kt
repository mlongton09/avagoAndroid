package com.avago.core.sync

import com.avago.core.data.DatabaseFactory
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-side photo metadata refresher — Android twin of
 * `LogPictureDAO.shared.downloadIfNeeded(photo:)` on iOS.
 *
 * Background: the generic `/sync` pull stream brings down the `photos` row
 * (entity_id, storage_key, sort_order…) but NOT a presigned `download_url` —
 * those are short-lived S3 URLs that would expire long before the sync
 * payload is consumed. Without a download_url, any device that didn't
 * originally capture the photo would render an empty pager because the
 * header gates on `localPath != null || downloadUrl != null`.
 *
 * Flow, mirroring iOS:
 *   1. Caller (LogListViewModel) invokes [refreshForEntity] when the asset
 *      detail / log screen opens.
 *   2. GET `/accounts/{accountId}/entities/{entityId}/photos` returns each
 *      attached photo plus a presigned `download_url`.
 *   3. Stamp the URL onto the matching `PhotoEntity` so Coil can fetch the
 *      bytes lazily as the user pages through the carousel.
 *
 * Per-entity in-flight dedup keeps multiple concurrent screen entries (e.g. a
 * rapid back+forward) from hammering the endpoint.
 */
@Singleton
class PhotoDownloader @Inject constructor(
    private val databaseFactory: DatabaseFactory,
    private val client: AvagoServiceClient,
) {
    private val inFlightMutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    /**
     * Pull the latest photo metadata + presigned download URLs for [entityId]
     * and persist `download_url` on each matching local row. No-ops when a
     * fetch is already in flight for the same entity in this process.
     */
    suspend fun refreshForEntity(accountId: String, entityId: String) {
        val key = "$accountId|$entityId"
        inFlightMutex.withLock {
            if (key in inFlight) {
                Timber.d("[PhotoDownloader] Already refreshing $entityId — skipping")
                return
            }
            inFlight += key
        }
        try {
            withContext(Dispatchers.IO) { runRefresh(accountId, entityId) }
        } finally {
            inFlightMutex.withLock { inFlight -= key }
        }
    }

    private suspend fun runRefresh(accountId: String, entityId: String) {
        when (val result = client.getPhotosForEntity(accountId, entityId)) {
            is NetworkResult.Success -> {
                val rows = result.data
                if (rows.isEmpty()) {
                    Timber.d("[PhotoDownloader] No server photos for $entityId")
                    return
                }
                val db = databaseFactory.get(accountId)
                val dao = db.photoDao()
                var stamped = 0
                for (row in rows) {
                    val url = row.download_url ?: continue
                    if (url.isBlank()) continue
                    // Only update rows that actually exist locally — the
                    // generic /sync pull is the source of truth for row
                    // creation; we're strictly stamping a download URL.
                    val existing = dao.getById(row.photo_id) ?: continue
                    if (existing.downloadUrl == url) continue
                    dao.updateDownloadUrl(row.photo_id, url)
                    stamped++
                }
                Timber.d("[PhotoDownloader] Refreshed $stamped/${rows.size} download URL(s) for $entityId")
            }
            is NetworkResult.Error ->
                Timber.w("[PhotoDownloader] getPhotosForEntity failed for $entityId: ${result.code} ${result.message}")
            is NetworkResult.Unauthorized ->
                Timber.w("[PhotoDownloader] Unauthorized fetching photos for $entityId")
        }
    }
}
