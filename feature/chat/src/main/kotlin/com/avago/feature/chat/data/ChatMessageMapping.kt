package com.avago.feature.chat.data

import android.webkit.MimeTypeMap
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.network.model.ChatMessageResponse
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Maps chat REST payloads to the cached [ChatMessageEntity] (shared by the repository and the outbox worker). */
internal object ChatMessageMapping {
    private const val PREVIEW_LENGTH = 120

    val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "wav", "ogg", "opus", "flac")
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp")

    fun ChatMessageResponse.toEntity(accountId: String): ChatMessageEntity {
        val now = System.currentTimeMillis()
        val discoveredMedia = extractMediaFields(
            imageUrls = image_urls,
            responseAudioUrl = audio_url,
            responseAttachmentUrl = attachment_url,
            responseAttachmentName = attachment_name,
            responseAttachmentSize = attachment_size,
        )
        return ChatMessageEntity(
            messageId = message_id,
            threadId = thread_id,
            accountId = accountId,
            senderId = author_id ?: author?.id ?: "",
            senderName = author?.display_name,
            senderAvatarUrl = author?.avatar_url,
            bodyMd = body_md,
            bodyPreview = body_md.take(PREVIEW_LENGTH),
            editedAt = edited_at?.let(::epochMillisOrNull),
            linkPreviewTitle = link_preview?.title,
            linkPreviewDescription = link_preview?.description,
            linkPreviewImageUrl = link_preview?.image_url,
            linkPreviewUrl = link_preview?.url,
            linkPreviewSiteName = link_preview?.site_name,
            photoUrl = photo_url,
            imageUrls = buildJsonStringArray(discoveredMedia.imageUrls),
            audioUrl = discoveredMedia.audioUrl,
            attachmentUrl = discoveredMedia.attachmentUrl,
            attachmentName = discoveredMedia.attachmentName,
            attachmentSize = discoveredMedia.attachmentSize,
            mentionedUserIds = buildJsonStringArray(mentioned_user_ids),
            mentionKinds = buildJsonStringArray(mention_kinds),
            isSystem = is_system,
            systemKind = system_kind,
            systemPayload = system_payload?.toString(),
            replyCount = reply_count,
            latestReplyAt = latest_reply_at?.let(::epochMillisOrNull),
            deliveredByCount = delivered_by_count,
            readByCount = read_by_count,
            readByTotal = read_by_total,
            reactionCounts = buildReactionCountsJson(reaction_counts),
            myReactions = buildJsonStringArray(my_reactions),
            needsReply = needs_reply,
            clientRef = client_ref,
            outboxStatus = null,
            serverVersion = server_version,
            deletedAt = null,
            createdAt = epochMillisOrNull(created_at) ?: now,
            updatedAt = epochMillisOrNull(updated_at) ?: now,
            parentMessageId = parent_message_id,
            isPinned = is_pinned,
        )
    }

    /** Fills media the server echo omits from what the sender attached. */
    fun ChatMessageResponse.withFallbackMedia(
        imageUrls: List<String>,
        audioUrl: String?,
        attachmentUrl: String?,
        attachmentName: String?,
        attachmentSize: Long?,
    ): ChatMessageResponse = copy(
        image_urls = if (this.image_urls.isNotEmpty()) {
            this.image_urls.filter { urlExtension(it) in IMAGE_EXTENSIONS }
        } else {
            imageUrls
        },
        audio_url = this.audio_url ?: audioUrl,
        attachment_url = this.attachment_url ?: attachmentUrl,
        attachment_name = this.attachment_name ?: attachmentName ?: attachmentUrl?.substringAfterLast('/'),
        attachment_size = this.attachment_size ?: attachmentSize,
    )

    fun epochMillisOrNull(iso: String): Long? = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()

    fun buildReactionCountsJson(counts: Map<String, Int>): String? {
        if (counts.isEmpty()) return null
        return counts.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }
    }

    fun buildJsonStringArray(items: List<String>): String? {
        if (items.isEmpty()) return null
        return items.joinToString(",", "[", "]") { "\"$it\"" }
    }

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

    private data class MediaFields(
        val imageUrls: List<String>,
        val audioUrl: String?,
        val attachmentUrl: String?,
        val attachmentName: String?,
        val attachmentSize: Long?,
    )

    private fun extractMediaFields(
        imageUrls: List<String>,
        responseAudioUrl: String?,
        responseAttachmentUrl: String?,
        responseAttachmentName: String?,
        responseAttachmentSize: Long?,
    ): MediaFields {
        var audioUrl = responseAudioUrl?.takeIf { it.isNotBlank() }
        var attachmentUrl = responseAttachmentUrl?.takeIf { it.isNotBlank() }
        var attachmentName = responseAttachmentName?.takeIf { it.isNotBlank() }
        val normalizedImages = mutableListOf<String>()

        imageUrls.filter { it.isNotBlank() }.forEach { url ->
            val extension = urlExtension(url)
            when {
                audioUrl == null && extension in AUDIO_EXTENSIONS -> audioUrl = url
                attachmentUrl == null && extension !in IMAGE_EXTENSIONS && extension !in AUDIO_EXTENSIONS -> {
                    attachmentUrl = url
                    if (attachmentName == null) attachmentName = fileNameFromUrl(url)
                }
                else -> normalizedImages += url
            }
        }

        val resolvedAttachment = attachmentUrl
        if (resolvedAttachment != null && attachmentName == null) {
            attachmentName = fileNameFromUrl(resolvedAttachment)
        }

        return MediaFields(
            imageUrls = normalizedImages,
            audioUrl = audioUrl,
            attachmentUrl = attachmentUrl,
            attachmentName = attachmentName,
            attachmentSize = responseAttachmentSize,
        )
    }

    private fun fileNameFromUrl(url: String): String? {
        val raw = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
        if (raw.isBlank()) return null
        return runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        }.getOrDefault(raw)
    }
}
