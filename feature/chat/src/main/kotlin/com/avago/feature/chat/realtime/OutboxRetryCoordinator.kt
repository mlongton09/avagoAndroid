package com.avago.feature.chat.realtime

import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.ChatDatabaseFactory
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.sync.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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
    private val periodicFlushScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var periodicFlushJob: Job? = null
    private val isFlushRunning = AtomicBoolean(false)

    /**
     * Starts a 30-second periodic flush of the outbox while the app is in the foreground.
     * Calling this multiple times is safe — a second call while already running is a no-op.
     */
    fun startPeriodicFlush() {
        if (!isFlushRunning.compareAndSet(false, true)) return
        periodicFlushJob = periodicFlushScope.launch {
            Timber.d("OutboxRetryCoordinator: starting periodic flush (30 s interval)")
            while (isActive) {
                delay(30_000L)
                val accountId = identity.getActiveAccountId() ?: continue
                try {
                    flush(accountId)
                } catch (e: Exception) {
                    Timber.w(e, "OutboxRetryCoordinator: periodic flush error for $accountId")
                }
            }
        }
    }

    /**
     * Stops the periodic flush (call when the app goes to the background).
     */
    fun stopPeriodicFlush() {
        periodicFlushJob?.cancel()
        periodicFlushJob = null
        isFlushRunning.set(false)
        Timber.d("OutboxRetryCoordinator: stopped periodic flush")
    }

    /**
     * Triggers an immediate retry pass for all failed outbox messages for [accountId].
     */
    suspend fun flush(accountId: String) {
        val failedMessages = chatDbFactory.get(accountId)
            .chatMessageDao()
            .failedOutboxList()
        if (failedMessages.isEmpty()) return
        Timber.d("OutboxRetryCoordinator: flush found ${failedMessages.size} failed message(s) for $accountId")
        failedMessages.forEach { msg ->
            scope.launch { retryMessage(accountId, msg) }
        }
    }

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
        val senderId = identity.activeUserId.value ?: ""
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
                        senderId = serverMsg.author_id ?: serverMsg.author?.id ?: "",
                        senderName = serverMsg.author?.display_name,
                        senderAvatarUrl = serverMsg.author?.avatar_url,
                        bodyMd = serverMsg.body_md,
                        bodyPreview = serverMsg.body_md.take(120),
                        editedAt = serverMsg.edited_at?.toEpochMillisOrNull(),
                        linkPreviewTitle = serverMsg.link_preview?.title,
                        linkPreviewDescription = serverMsg.link_preview?.description,
                        linkPreviewImageUrl = serverMsg.link_preview?.image_url,
                        linkPreviewUrl = serverMsg.link_preview?.url,
                        linkPreviewSiteName = serverMsg.link_preview?.site_name,
                        photoUrl = serverMsg.photo_url,
                        isSystem = serverMsg.is_system,
                        systemKind = serverMsg.system_kind,
                        systemPayload = serverMsg.system_payload,
                        replyCount = serverMsg.reply_count,
                        latestReplyAt = serverMsg.latest_reply_at?.toEpochMillisOrNull(),
                        deliveredByCount = serverMsg.delivered_by_count,
                        readByCount = serverMsg.read_by_count,
                        readByTotal = serverMsg.read_by_total,
                        reactionCounts = serverMsg.reaction_counts.takeIf { it.isNotEmpty() }
                            ?.entries?.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" },
                        myReactions = serverMsg.my_reactions.takeIf { it.isNotEmpty() }
                            ?.joinToString(",", "[", "]") { "\"$it\"" },
                        needsReply = serverMsg.needs_reply,
                        clientRef = serverMsg.client_ref,
                        outboxStatus = null,
                        serverVersion = serverMsg.server_version,
                        deletedAt = null,
                        createdAt = serverMsg.created_at.toEpochMillisOrNull() ?: now,
                        updatedAt = serverMsg.updated_at.toEpochMillisOrNull() ?: now,
                        parentMessageId = serverMsg.parent_message_id,
                        isPinned = serverMsg.is_pinned,
                    )
                )
                if (serverMsg.message_id != messageId) {
                    db.chatMessageDao().deleteById(messageId)
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
                            senderId = serverMsg.author_id ?: serverMsg.author?.id ?: msg.senderId,
                            senderName = serverMsg.author?.display_name,
                            bodyMd = serverMsg.body_md,
                            bodyPreview = serverMsg.body_md.take(120),
                            outboxStatus = null,
                            serverVersion = serverMsg.server_version,
                            createdAt = serverMsg.created_at.toEpochMillisOrNull() ?: msg.createdAt,
                            updatedAt = serverMsg.updated_at.toEpochMillisOrNull() ?: msg.updatedAt,
                        )
                    )
                    if (serverMsg.message_id != msg.messageId) {
                        db.chatMessageDao().deleteById(msg.messageId)
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
} catch (_: Exception) {
    null
}
