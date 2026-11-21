package com.avago.core.data.v2

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A file copied into app storage under its UUIDv7 `file_id`, ready for an E12 upload. */
data class V2StagedFile(
    val fileId: String,
    val file: File,
    val sha256: String,
    val byteSize: Long,
    val mediaType: String,
    val originalName: String?,
) {
    fun toUpload(): V2NewUpload = V2NewUpload(fileId, file.absolutePath, sha256, byteSize, mediaType)
}

/**
 * Stages user-picked or captured content for upload. The staged copy is the device's own copy of
 * the file (it outlives the upload), so [localFile] resolves pictures the user added on this
 * device without a network round trip. Copies live in credential-encrypted app storage
 * (`filesDir/v2files`, excluded from backup and device transfer); a copy is evictable only after
 * its upload is finalized ([markUploaded], [sweep]).
 */
@Singleton
class V2FileStager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    suspend fun stage(uri: Uri, fallbackMediaType: String = "application/octet-stream"): V2StagedFile =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mediaType = resolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()))
                ?: fallbackMediaType
            val input = resolver.openInputStream(uri) ?: error("cannot read $uri")
            input.use { copy(it, mediaType, uri.lastPathSegment) }
        }

    suspend fun stage(file: File, mediaType: String): V2StagedFile = withContext(Dispatchers.IO) {
        file.inputStream().use { copy(it, mediaType, file.name) }
    }

    /** Stages content produced in memory (a drawn signature PNG). */
    suspend fun stage(bytes: ByteArray, mediaType: String, name: String?): V2StagedFile = withContext(Dispatchers.IO) {
        bytes.inputStream().use { copy(it, mediaType, name) }
    }

    /** The local copy of [fileId], if this device staged it. */
    fun localFile(fileId: String): File? = File(root, fileId).takeIf { it.isFile }

    /** The server holds the bytes at [localPath]: the copy becomes evictable (see [V2StagedMedia]). */
    fun markUploaded(localPath: String) = V2StagedMedia(root).markUploaded(localPath)

    /** Evicts uploaded copies down to [maxBytes]; unsent copies are always kept. Returns the bytes freed. */
    fun sweep(maxBytes: Long): Long = V2StagedMedia(root).sweep(maxBytes)

    /**
     * Copies [input] into app storage. The registered type is the sniffed kind of the bytes (the
     * server refuses a body that is not what was declared); [declaredType] is kept only for bytes
     * of no known kind, which the server will refuse with a clear `unsupported_media_type`.
     */
    private fun copy(input: InputStream, declaredType: String, name: String?): V2StagedFile {
        val fileId = V2Uuid.v7()
        val target = File(root, fileId)
        val digest = MessageDigest.getInstance("SHA-256")
        val head = ByteArray(V2MediaType.SNIFF_BYTES)
        var headSize = 0
        var size = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                out.write(buffer, 0, n)
                if (headSize < head.size) {
                    val take = minOf(n, head.size - headSize)
                    System.arraycopy(buffer, 0, head, headSize, take)
                    headSize += take
                }
                size += n
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val mediaType = V2MediaType.sniff(head.copyOf(headSize)) ?: declaredType
        return V2StagedFile(fileId, target, sha, size, mediaType, name)
    }

    private companion object {
        const val DIR = "v2files"
        const val BUFFER = 64 * 1024
    }
}
