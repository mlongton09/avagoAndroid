package com.avago.core.sync.v2

import android.net.Uri
import com.avago.core.data.domain.FileUploads
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.v2.V2ApiUploader
import com.avago.core.data.v2.V2FileStager
import javax.inject.Inject
import javax.inject.Singleton

/** [FileUploads] for the signed-in account: stages the file, then runs the E12 handshake directly. */
@Singleton
class V2OnlineUploads @Inject constructor(
    private val coordinator: V2SyncCoordinator,
    private val stager: V2FileStager,
    private val blobs: V2HttpBlobTransport,
) : FileUploads {

    override suspend fun upload(file: LocalFileRef): String {
        val api = coordinator.api() ?: throw StoreNotReadyException()
        val staged = stager.stage(Uri.parse(file.uri), file.fallbackMediaType)
        V2ApiUploader(api, blobs, stager::markUploaded).process(staged.fileId, staged.file.absolutePath, staged.sha256, staged.byteSize, staged.mediaType)
        return staged.fileId
    }
}
