package com.avago.feature.chat.realtime

import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.ChatDatabaseFactory
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.sync.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles optimistic local insertion and retry of failed outbox messages.
 *
 * Call [send] to insert a message locally and attempt immediate network delivery.
 * Call [startRetrying] once per account (e.g. on sign-in or app foreground) to
 * continuously retry any messages stuck in `outbox_status = 'failed'`.
 */
@Singleton
class OutboxRetryCoordinator @Inject constructor(
    private val chatDbFactory: ChatDatabaseFactory,
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /**
     * Begins watching failed outbox messages for [accountId] and retrying them
     * with exponential back-off per individual message.
     */
    fun startRetrying(accountId: String) {
        scope.launch {
            chatDbFactory.get(accountId)
                .chatMessageDao()
                .observeFailedOutbox()
                .distinctUntilChanged()
                .collect { failedMessages ->
                    failedMessages.forEach { msg ->
                        launch { retryMessage(accountId, msg) }
                    }
                }
        }
    }

    /**
     * Inserts a message locally with `outbox_status = "sending"`, then attempts
     * to deliver it to the server. On failure, marks it `"failed"` for retry.
     * Returns the locally-generated [messageId].
     */
    suspend fun send(accountId: String, threadId: String, body: String): String {
        val messageId = UUID.randomUUID().toString()
        val senderId = identity.activeAccountId.value ?: ""
        val now = System.currentTimeMillis()

        val db = chatDbFactory.get(accountId)
        db.chatMessageDao().upsert(
            ChatMessageEntity(
                messageId = messageId,
                threadId = threadId,
                accountId = accountId,
                senderId = senderId,
                senderName = null,
                bodyMd = body,
                bodyPreview = body.take(100),
                editedAt = null,
                linkPreviewTitle = null,
                linkPreviewDescription = null,
                linkPreviewImageUrl = null,
                linkPreviewUrl = null,
                photoUrl = null,
                reactions = null,
                outboxStatus = "sending",
                serverVersion = 0,
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
            )
        )

        when (val result = client.sendMessage(threadId, body)) {
            is NetworkResult.Success -> {
                val serverMsg = result.data
                db.chatMessageDao().upsert(
                    ChatMessageEntity(
                        messageId = serverMsg.message_id,
                        threadId = serverMsg.thread_id,
                        accountId = accountId,
                        senderId = serverMsg.sender_id,
                        senderName = serverMsg.sender_name,
                        bodyMd = serverMsg.body_md,
                        bodyPreview = serverMsg.body_preview,
                        editedAt = serverMsg.edited_at?.toEpochMillisOrNull(),
                        linkPreviewTitle = serverMsg.link_preview_title,
                        linkPreviewDescription = serverMsg.link_preview_description,
                        linkPreviewImageUrl = serverMsg.link_preview_image_url,
                        linkPreviewUrl = serverMsg.link_preview_url,
                        photoUrl = serverMsg.photo_url,
                        reactions = serverMsg.reactions,
                        outboxStatus = null,
                        serverVersion = serverMsg.server_version,
                        deletedAt = null,
                        createdAt = serverMsg.created_at.toEpochMillisOrNull() ?: now,
                        updatedAt = serverMsg.updated_at.toEpochMillisOrNull() ?: now,
                    )
                )
                if (serverMsg.message_id != messageId) {
                    db.chatMessageDao().updateOutboxStatus(messageId, null)
                }
            }
            else -> {
                Timber.w("OutboxRetry: initial send failed for $messageId, marked 'failed'")
                db.chatMessageDao().updateOutboxStatus(messageId, "failed")
            }
        }

        return messageId
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private suspend fun retryMessage(accountId: String, msg: ChatMessageEntity) {
        var backoffMs = 2_000L
        val db = chatDbFactory.get(accountId)
        db.chatMessageDao().updateOutboxStatus(msg.messageId, "sending")

        repeat(5) { attempt ->
            when (val result = client.sendMessage(msg.threadId, msg.bodyMd)) {
                is NetworkResult.Success -> {
                    val serverMsg = result.data
                    db.chatMessageDao().upsert(
                        msg.copy(
                            messageId = serverMsg.message_id,
                            senderId = serverMsg.sender_id,
                            senderName = serverMsg.sender_name,
                            bodyMd = serverMsg.body_md,
                            bodyPreview = serverMsg.body_preview,
                            outboxStatus = null,
                            serverVersion = serverMsg.server_version,
                            createdAt = serverMsg.created_at.toEpochMillisOrNull() ?: msg.createdAt,
                            updatedAt = serverMsg.updated_at.toEpochMillisOrNull() ?: msg.updatedAt,
                        )
                    )
                    if (serverMsg.message_id != msg.messageId) {
                        db.chatMessageDao().updateOutboxStatus(msg.messageId, null)
                    }
                    Timber.d("OutboxRetry: succeeded for ${msg.messageId} on attempt $attempt")
                    return
                }
                is NetworkResult.Unauthorized -> {
                    Timber.w("OutboxRetry: unauthorized, aborting retry for ${msg.messageId}")
                    db.chatMessageDao().updateOutboxStatus(msg.messageId, "failed")
                    return
                }
                is NetworkResult.Error -> {
                    Timber.w("OutboxRetry: attempt $attempt failed for ${msg.messageId}")
                    if (attempt < 4) {
                        delay(backoffMs)
                        backoffMs = minOf(backoffMs * 2, 30_000L)
                    }
                }
            }
        }

        db.chatMessageDao().updateOutboxStatus(msg.messageId, "failed")
        Timber.e("OutboxRetry: giving up on ${msg.messageId} after 5 attempts")
    }
}

/** Parse an ISO-8601 string to epoch milliseconds, returning null on parse failure. */
private fun String.toEpochMillisOrNull(): Long? = try {
    java.time.Instant.parse(this).toEpochMilli()
} catch (e: Exception) {
    null
}
