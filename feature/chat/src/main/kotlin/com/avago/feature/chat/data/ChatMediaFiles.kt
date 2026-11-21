package com.avago.feature.chat.data

import android.webkit.MimeTypeMap
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** File-type and file-name rules for chat media URLs and uploads. */
internal object ChatMediaFiles {
    val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "wav", "ogg", "opus", "flac")
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp")

    fun defaultUploadFileName(contentType: String): String {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType)
            ?.takeIf { it.isNotBlank() }
            ?: "bin"
        return "chat_upload_${System.currentTimeMillis()}.$extension"
    }

    fun isImageMedia(url: String, contentType: String, fileName: String?): Boolean =
        contentType.startsWith("image/") || urlExtension(fileName ?: url) in IMAGE_EXTENSIONS

    fun isAudioMedia(url: String, contentType: String, fileName: String?): Boolean =
        contentType.startsWith("audio/") || urlExtension(fileName ?: url) in AUDIO_EXTENSIONS

    fun urlExtension(url: String): String =
        url.substringAfterLast('.').substringBefore('?').substringBefore('#').lowercase()

    fun fileNameFromUrl(url: String): String? {
        val raw = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
        if (raw.isBlank()) return null
        return runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        }.getOrDefault(raw)
    }
}
