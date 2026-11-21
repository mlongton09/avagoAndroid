package com.avago.feature.chat.realtime

import com.avago.core.data.chat.ChatOutboxQueue
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.network.model.SendMessageRequest
import com.avago.feature.chat.data.ChatMessageMapping
import com.avago.feature.chat.data.ChatMessageMapping.withFallbackMedia
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** One uploaded file a message carries: its presign `storage_key` and its `/chat/media/{storage_key}` URL. */
data class ChatOutgoingMedia(
    val kind: Kind,
    val storageKey: String,
    val url: String,
    val name: String? = null,
    val size: Long? = null,
) {
    enum class Kind { IMAGE, AUDIO, DOCUMENT }
}

/**
 * The outbox payload of a message send and the request it is delivered as (AvagoSvc P5-05): an
 * image goes in `image_urls` as its `/chat/media/{key}` URL, a voice note as `audio_key` and a
 * document as `attachment_keys`; the payload also keeps the URLs the optimistic message shows.
 */
internal object ChatSendPayload {
    const val KEY_AUDIO_KEY = "audio_key"
    const val KEY_ATTACHMENT_KEYS = "attachment_keys"
    const val KEY_LOCAL_IMAGE_URLS = "local_image_urls"
    const val KEY_AUDIO_URL = "audio_url"
    const val KEY_ATTACHMENT_URL = "attachment_url"
    const val KEY_ATTACHMENT_NAME = "attachment_name"
    const val KEY_ATTACHMENT_SIZE = "attachment_size"

    fun payload(body: String, needsReply: Boolean, media: ChatOutgoingMedia?): JsonObject = buildJsonObject {
        val images = JsonArray(listOfNotNull(media?.url?.takeIf { media.kind == ChatOutgoingMedia.Kind.IMAGE })
            .map(::JsonPrimitive))
        put(ChatOutboxQueue.KEY_BODY, body)
        put(ChatOutboxQueue.KEY_IMAGE_URLS, images)
        put(ChatOutboxQueue.KEY_NEEDS_REPLY, needsReply)
        put(KEY_LOCAL_IMAGE_URLS, images)
        when (media?.kind) {
            ChatOutgoingMedia.Kind.AUDIO -> {
                put(KEY_AUDIO_KEY, media.storageKey)
                put(KEY_AUDIO_URL, media.url)
            }
            ChatOutgoingMedia.Kind.DOCUMENT -> {
                put(KEY_ATTACHMENT_KEYS, JsonArray(listOf(JsonPrimitive(media.storageKey))))
                put(KEY_ATTACHMENT_URL, media.url)
                media.name?.let { put(KEY_ATTACHMENT_NAME, it) }
                media.size?.let { put(KEY_ATTACHMENT_SIZE, it) }
            }
            ChatOutgoingMedia.Kind.IMAGE, null -> Unit
        }
    }

    fun request(payload: JsonObject, clientRef: String): SendMessageRequest = SendMessageRequest(
        body_md = payload.text(ChatOutboxQueue.KEY_BODY).orEmpty(),
        image_urls = payload.strings(ChatOutboxQueue.KEY_IMAGE_URLS),
        needs_reply = payload[ChatOutboxQueue.KEY_NEEDS_REPLY]?.jsonPrimitive?.booleanOrNull?.takeIf { it },
        client_ref = clientRef,
        audio_key = payload.text(KEY_AUDIO_KEY),
        attachmentKeys = payload.strings(KEY_ATTACHMENT_KEYS).ifEmpty { null },
    )

    /** [message] showing what [payload] sends (the optimistic copy). */
    fun showing(message: ChatMessageEntity, payload: JsonObject): ChatMessageEntity = message.copy(
        imageUrls = ChatMessageMapping.buildJsonStringArray(payload.strings(KEY_LOCAL_IMAGE_URLS)),
        audioUrl = payload.text(KEY_AUDIO_URL),
        attachmentUrl = payload.text(KEY_ATTACHMENT_URL),
        attachmentName = payload.text(KEY_ATTACHMENT_NAME),
        attachmentSize = payload[KEY_ATTACHMENT_SIZE]?.jsonPrimitive?.longOrNull,
    )

    /** Fills media the server echo of a send omits from what [payload] sent. */
    fun ChatMessageResponse.withPayloadMedia(payload: JsonObject): ChatMessageResponse = withFallbackMedia(
        imageUrls = payload.strings(KEY_LOCAL_IMAGE_URLS),
        audioUrl = payload.text(KEY_AUDIO_URL),
        attachmentUrl = payload.text(KEY_ATTACHMENT_URL),
        attachmentName = payload.text(KEY_ATTACHMENT_NAME),
        attachmentSize = payload[KEY_ATTACHMENT_SIZE]?.jsonPrimitive?.longOrNull,
    )

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
}
