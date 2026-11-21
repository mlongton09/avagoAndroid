package com.avago.core.data.v2

import java.io.File

/**
 * Space management of staged uploads under [root] (plan 4.3): a staged copy is the only copy of an
 * unsent photo, signature or document until its upload job is finalized, so it is never a
 * candidate for eviction. [markUploaded] records that the server holds the bytes; only those copies
 * may be deleted by [sweep] (the file stays readable through `files/{id}/download`).
 *
 * The marker lives beside the copies (`.uploaded/<file id>`), so the rule holds across every
 * account and user that shares the device-wide staging directory without opening their stores.
 */
class V2StagedMedia(private val root: File) {
    private val markers: File get() = File(root, MARKER_DIR)

    /** Records that the staged copy at [localPath] was uploaded and finalized. Ignores other paths. */
    fun markUploaded(localPath: String) {
        val file = File(localPath)
        if (!file.isFile || file.parentFile?.canonicalFile != root.canonicalFile) return
        markers.mkdirs()
        File(markers, file.name).createNewFile()
    }

    fun isUploaded(fileId: String): Boolean = File(markers, fileId).isFile

    /**
     * Deletes uploaded copies, oldest first, until the staged copies hold at most
     * [maxBytes]; returns the bytes freed. Copies that are not uploaded are kept even when they
     * alone exceed the budget.
     */
    fun sweep(maxBytes: Long): Long {
        val staged = root.listFiles { f -> f.isFile }.orEmpty().toList()
        var total = staged.sumOf { it.length() }
        var freed = 0L
        for (copy in staged.filter { isUploaded(it.name) }.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = copy.length()
            if (copy.delete()) {
                File(markers, copy.name).delete()
                total -= size
                freed += size
            }
        }
        markers.listFiles().orEmpty().filter { !File(root, it.name).exists() }.forEach { it.delete() }
        return freed
    }

    private companion object {
        const val MARKER_DIR = ".uploaded"
    }
}
