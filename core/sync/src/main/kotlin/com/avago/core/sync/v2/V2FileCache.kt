package com.avago.core.sync.v2

import android.content.Context
import com.avago.core.data.domain.FileRepository
import com.avago.core.data.domain.FileVariant
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.v2.V2FileDownloads
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** [FileRepository] for the signed-in account: [V2FileDownloads] under `cacheDir/v2files`. */
@Singleton
class V2FileCache @Inject constructor(
    @ApplicationContext context: Context,
    private val coordinator: V2SyncCoordinator,
    blobs: V2HttpBlobTransport,
) : FileRepository {
    private val downloads = V2FileDownloads(File(context.cacheDir, "v2files"), blobs)

    override suspend fun fetch(fileId: String, variant: FileVariant): File = withContext(Dispatchers.IO) {
        val api = coordinator.api() ?: throw StoreNotReadyException()
        downloads.fetch(api, fileId, variant)
    }

    override suspend fun trimCache(maxBytes: Long): Long = withContext(Dispatchers.IO) { downloads.trim(maxBytes) }
}
