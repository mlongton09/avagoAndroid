package com.avago.feature.chat.realtime

import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.network.ChatMediaLinks
import com.avago.core.network.model.ChatMessageResponse
import com.avago.feature.chat.data.ChatMessageMapping.toEntity
import com.avago.feature.chat.realtime.ChatSendPayload.withPayloadMedia
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatSendPayloadTest {
    private val mediaUrl = { key: String -> ChatMediaLinks.url(BASE, key) }

    @Test
    fun `an image is sent as its chat media URL`() {
        val media = media(ChatOutgoingMedia.Kind.IMAGE, "accounts/a1/chat/p.jpg")
        val request = ChatSendPayload.request(ChatSendPayload.payload("", false, media), "ref-1")

        assertEquals(listOf("$BASE/chat/media/accounts/a1/chat/p.jpg"), request.image_urls)
        assertNull(request.audio_key)
        assertNull(request.attachmentKeys)
        assertEquals("ref-1", request.client_ref)
        assertNull(request.needs_reply)
    }

    @Test
    fun `a voice note is sent as its audio key`() {
        val media = media(ChatOutgoingMedia.Kind.AUDIO, "accounts/a1/chat/v.m4a")
        val request = ChatSendPayload.request(ChatSendPayload.payload("", true, media), "ref-2")

        assertEquals("accounts/a1/chat/v.m4a", request.audio_key)
        assertEquals(emptyList<String>(), request.image_urls)
        assertNull(request.attachmentKeys)
        assertEquals(true, request.needs_reply)
    }

    @Test
    fun `a document is sent as its attachment key`() {
        val media = media(ChatOutgoingMedia.Kind.DOCUMENT, "accounts/a1/chat/d.pdf", name = "Quote.pdf", size = 2048)
        val request = ChatSendPayload.request(ChatSendPayload.payload("see attached", false, media), "ref-3")

        assertEquals(listOf("accounts/a1/chat/d.pdf"), request.attachmentKeys)
        assertEquals(emptyList<String>(), request.image_urls)
        assertNull(request.audio_key)
        assertEquals("see attached", request.body_md)
    }

    @Test
    fun `the optimistic message shows the document under its own name`() {
        val media = media(ChatOutgoingMedia.Kind.DOCUMENT, "accounts/a1/chat/d.pdf", name = "Quote.pdf", size = 2048)
        val shown = ChatSendPayload.showing(message(), ChatSendPayload.payload("", false, media))

        assertEquals("$BASE/chat/media/accounts/a1/chat/d.pdf", shown.attachmentUrl)
        assertEquals("Quote.pdf", shown.attachmentName)
        assertEquals(2048L, shown.attachmentSize)
        assertNull(shown.imageUrls)
    }

    @Test
    fun `a synced message shows its stored media through chat media URLs`() {
        val entity = response(
            imageKeys = listOf("accounts/a1/chat/p.jpg"),
            audioKey = "accounts/a1/chat/v.m4a",
            attachmentKeys = listOf("accounts/a1/chat/d.pdf"),
        ).toEntity("a1", mediaUrl)

        assertEquals("[\"$BASE/chat/media/accounts/a1/chat/p.jpg\"]", entity.imageUrls)
        assertEquals("$BASE/chat/media/accounts/a1/chat/v.m4a", entity.audioUrl)
        assertEquals("$BASE/chat/media/accounts/a1/chat/d.pdf", entity.attachmentUrl)
        assertEquals("d.pdf", entity.attachmentName)
    }

    @Test
    fun `the echo of an image send shows the image once`() {
        val payload = ChatSendPayload.payload("", false, media(ChatOutgoingMedia.Kind.IMAGE, "accounts/a1/chat/p.jpg"))

        val entity = response(imageKeys = listOf("accounts/a1/chat/p.jpg"))
            .withPayloadMedia(payload)
            .toEntity("a1", mediaUrl)

        assertEquals("[\"$BASE/chat/media/accounts/a1/chat/p.jpg\"]", entity.imageUrls)
    }

    @Test
    fun `the echo of a document send keeps the sender's file name`() {
        val media = media(ChatOutgoingMedia.Kind.DOCUMENT, "accounts/a1/chat/d.pdf", name = "Quote.pdf", size = 2048)
        val entity = response(attachmentKeys = listOf("accounts/a1/chat/d.pdf"))
            .withPayloadMedia(ChatSendPayload.payload("", false, media))
            .toEntity("a1", mediaUrl)

        assertEquals("$BASE/chat/media/accounts/a1/chat/d.pdf", entity.attachmentUrl)
        assertEquals("Quote.pdf", entity.attachmentName)
        assertEquals(2048L, entity.attachmentSize)
        assertNull(entity.imageUrls)
    }

    private fun media(kind: ChatOutgoingMedia.Kind, key: String, name: String? = null, size: Long? = null) =
        ChatOutgoingMedia(kind, key, mediaUrl(key), name, size)

    private fun response(
        imageKeys: List<String> = emptyList(),
        audioKey: String? = null,
        attachmentKeys: List<String> = emptyList(),
    ) = ChatMessageResponse(
        message_id = "m1",
        thread_id = "t1",
        body_md = "",
        imageKeys = imageKeys,
        audio_key = audioKey,
        attachment_keys = attachmentKeys,
        created_at = "2025-01-01T00:00:00Z",
        updated_at = "2025-01-01T00:00:00Z",
    )

    private fun message() = ChatMessageEntity(
        messageId = "m1",
        threadId = "t1",
        accountId = "a1",
        senderId = "u1",
        senderName = null,
        bodyMd = "",
        bodyPreview = "",
        editedAt = null,
        linkPreviewTitle = null,
        linkPreviewDescription = null,
        linkPreviewImageUrl = null,
        linkPreviewUrl = null,
        photoUrl = null,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        const val BASE = "https://api.example.com"
    }
}
