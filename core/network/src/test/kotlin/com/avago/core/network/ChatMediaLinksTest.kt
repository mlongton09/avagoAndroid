package com.avago.core.network

import com.avago.core.network.model.ChatMediaPresignResponse
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.SendMessageRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatMediaLinksTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val base = "https://api.example.com"

    @Test
    fun `decodes the presign answer the server sends`() {
        val presign = json.decodeFromString<ChatMediaPresignResponse>(
            """{"upload_url":"https://store/put?sig=1","storage_key":"accounts/a1/chat/u1.jpg",
               "cdn_url":"https://api.example.com/chat/media/accounts/a1/chat/u1.jpg",
               "max_bytes":10485760,"content_type":"image/jpeg"}""",
        )
        assertEquals("accounts/a1/chat/u1.jpg", presign.storageKey)
        assertEquals(presign.cdnUrl, ChatMediaLinks.url(base, presign.storageKey))
    }

    @Test
    fun `decodes the storage keys of a synced message`() {
        val message = json.decodeFromString<ChatMessageResponse>(
            """{"message_id":"m","thread_id":"t","body_md":"","created_at":"x","updated_at":"x",
               "image_keys":["accounts/a1/chat/i.png"],"attachment_keys":["accounts/a1/chat/d.pdf"],
               "audio_key":"accounts/a1/chat/v.m4a"}""",
        )
        assertEquals(listOf("accounts/a1/chat/i.png"), message.imageKeys)
        assertEquals(listOf("accounts/a1/chat/d.pdf"), message.attachment_keys)
        assertEquals("accounts/a1/chat/v.m4a", message.audio_key)
    }

    @Test
    fun `a send carries document keys and omits absent media`() {
        val withDocument = json.encodeToJsonElement(
            SendMessageRequest.serializer(),
            SendMessageRequest(body_md = "", attachmentKeys = listOf("accounts/a1/chat/d.pdf")),
        ).jsonObject
        val keys = withDocument.getValue("attachment_keys").jsonArray
        assertEquals("accounts/a1/chat/d.pdf", keys.single().jsonPrimitive.content)

        val textOnly = json.encodeToJsonElement(SendMessageRequest.serializer(), SendMessageRequest(body_md = "hi"))
            .jsonObject
        assertFalse("attachment_keys" in textOnly)
        assertFalse("audio_key" in textOnly)
    }

    @Test
    fun `builds the media URL and encodes each key segment`() {
        assertEquals(
            "https://api.example.com/chat/media/accounts/a1/chat/u1.jpg",
            ChatMediaLinks.url("$base/", "accounts/a1/chat/u1.jpg"),
        )
        assertEquals(
            "https://api.example.com/chat/media/accounts/a%201/chat/r%C3%A9sum%C3%A9%23.pdf",
            ChatMediaLinks.url(base, "accounts/a 1/chat/résumé#.pdf"),
        )
    }

    @Test
    fun `recognises chat media on this API only`() {
        assertTrue(ChatMediaLinks.isChatMedia(base, "$base/chat/media/accounts/a1/chat/u1.jpg"))
        assertFalse(ChatMediaLinks.isChatMedia(base, "https://cdn.other.com/chat/media/accounts/a1/chat/u1.jpg"))
        assertFalse(ChatMediaLinks.isChatMedia(base, "$base/files/abc"))
    }

    @Test
    fun `follows the redirect to the signed URL`() {
        val request = "$base/chat/media/accounts/a1/chat/u1.jpg"
        val signed = "https://store.example.com/accounts/a1/chat/u1.jpg?X-Amz-Signature=a%2Fb&X-Amz-Expires=300"
        assertEquals(signed, ChatMediaLinks.redirectTarget(request, 302, signed))
        assertEquals(
            "https://api.example.com/local/u1.jpg?sig=1",
            ChatMediaLinks.redirectTarget(request, 307, "/local/u1.jpg?sig=1"),
        )
        assertNull(ChatMediaLinks.redirectTarget(request, 200, signed))
        assertNull(ChatMediaLinks.redirectTarget(request, 302, null))
        assertNull(ChatMediaLinks.redirectTarget(request, 403, signed))
    }
}
