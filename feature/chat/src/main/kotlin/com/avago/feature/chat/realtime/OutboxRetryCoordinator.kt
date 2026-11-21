package com.avago.feature.chat.realtime

import com.avago.core.auth.IdentityManager
import com.avago.core.data.chat.ChatLocalStore
import com.avago.core.data.chat.ChatOutboxDrain
import com.avago.core.data.chat.ChatOutboxEntry
import com.avago.core.data.chat.ChatOutboxOperation
import com.avago.core.data.chat.ChatOutboxOutcome
import com.avago.core.data.chat.ChatOutboxQueue
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.sync.ApplicationScope
import com.avago.feature.chat.data.ChatMessageMapping
import com.avago.feature.chat.data.ChatMessageMapping.toEntity
import com.avago.feature.chat.data.ChatMessageMapping.withFallbackMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends chat writes through the ordered outbox of the user/account chat file (plan 4.1, L42).
 *
 * [send], [sendReply], [edit] and [delete] queue the write, show it optimistically, and start a
 * drain; the drain delivers rows strictly in causal order, with `client_ref` so an ambiguous
 * failure is re-sent safely. A row that must be retried backs off (2 s doubling to 30 s) and
 * blocks later rows; [flush], [startRetrying] and the foreground [startPeriodicFlush] resume
 * delivery, and queued rows survive process death because they live in the chat file.
 */
@Singleton
class OutboxRetryCoordinator @Inject constructor(
    private val chatStores: ChatLocalStore,
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val periodicFlushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicFlushJob: Job? = null
    private val isFlushRunning = AtomicBoolean(false)
    private val drainLocks = ConcurrentHashMap<String, Mutex>()
    private val wakeups = ConcurrentHashMap<String, Job>()

    /**
     * Starts a 30-second periodic flush of the outbox while the app is in the foreground.
     * Calling this multiple times is safe — a second call while already running is a no-op.
     */
    fun startPeriodicFlush() {
        if (!isFlushRunning.compareAndSet(false, true)) return
        periodicFlushJob = periodicFlushScope.launch {
            Timber.d("OutboxRetryCoordinator: starting periodic flush (30 s interval)")
            while (isActive) {
                delay(PERIODIC_FLUSH_MS)
                val accountId = identity.getActiveAccountId() ?: continue
                try {
                    flush(accountId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "OutboxRetryCoordinator: periodic flush error for $accountId")
                }
            }
        }
    }

    /** Stops the periodic flush (call when the app goes to the background). */
    fun stopPeriodicFlush() {
        periodicFlushJob?.cancel()
        periodicFlushJob = null
        isFlushRunning.set(false)
        Timber.d("OutboxRetryCoordinator: stopped periodic flush")
    }

    /**
     * Delivers what is queued for [accountId] now, in order, until the queue is empty or its head
     * must back off (then a single delayed re-drain is scheduled).
     */
    suspend fun flush(accountId: String) {
        val store = chatStores.forAccount(accountId) ?: return
        val result = drainLocks.getOrPut(accountId) { Mutex() }.withLock {
            store.outbox.drain(System::currentTimeMillis) { entry -> deliver(accountId, entry) }
        }
        if (result is ChatOutboxDrain.WaitUntil) scheduleWakeup(accountId, result.atMillis)
    }

    /** Resumes delivery of anything left queued for [accountId] (e.g. when a thread opens). */
    fun startRetrying(accountId: String) {
        kick(accountId)
    }

    /** Queues a message and returns its client id (the optimistic message id and `client_ref`). */
    suspend fun send(
        accountId: String,
        threadId: String,
        body: String,
        imageUrls: List<String>? = null,
        needsReply: Boolean = false,
        audioUrl: String? = null,
        attachmentUrl: String? = null,
        attachmentName: String? = null,
        attachmentSize: Long? = null,
    ): String {
        val store = requireStore(accountId)
        val now = System.currentTimeMillis()
        val localImages = imageUrls.orEmpty().filter { it.isNotBlank() }
        val messageId = ChatOutboxQueue.newId()
        val optimistic = optimisticMessage(messageId, threadId, accountId, store.userId, body, now).copy(
            imageUrls = ChatMessageMapping.buildJsonStringArray(localImages),
            audioUrl = audioUrl,
            attachmentUrl = attachmentUrl,
            attachmentName = attachmentName,
            attachmentSize = attachmentSize,
            needsReply = needsReply,
        )
        val payload = buildJsonObject {
            put(ChatOutboxQueue.KEY_BODY, body)
            val imageUrls = serverImageUrls(localImages, audioUrl, attachmentUrl)
            put(ChatOutboxQueue.KEY_IMAGE_URLS, JsonArray(imageUrls.map(::JsonPrimitive)))
            put(ChatOutboxQueue.KEY_NEEDS_REPLY, needsReply)
            put(KEY_LOCAL_IMAGE_URLS, JsonArray(localImages.map(::JsonPrimitive)))
            audioUrl?.let { put(KEY_AUDIO_URL, it) }
            attachmentUrl?.let { put(KEY_ATTACHMENT_URL, it) }
            attachmentName?.let { put(KEY_ATTACHMENT_NAME, it) }
            attachmentSize?.let { put(KEY_ATTACHMENT_SIZE, it) }
        }
        store.outbox.enqueueSend(ChatOutboxOperation.SEND, optimistic, payload, now)
        kick(accountId)
        return messageId
    }

    /** Queues a subthread reply to [parentMessageId] and returns its client id. */
    suspend fun sendReply(accountId: String, threadId: String, parentMessageId: String, body: String): String {
        val store = requireStore(accountId)
        val now = System.currentTimeMillis()
        val messageId = ChatOutboxQueue.newId()
        val optimistic = optimisticMessage(messageId, threadId, accountId, store.userId, body, now)
            .copy(parentMessageId = parentMessageId)
        val payload = buildJsonObject {
            put(ChatOutboxQueue.KEY_BODY, body)
            put(ChatOutboxQueue.KEY_PARENT_MESSAGE_ID, parentMessageId)
        }
        store.outbox.enqueueSend(ChatOutboxOperation.REPLY, optimistic, payload, now)
        kick(accountId)
        return messageId
    }

    /** Queues an edit of [messageId]; it is applied after every write queued before it. */
    suspend fun edit(accountId: String, threadId: String, messageId: String, body: String) {
        requireStore(accountId).outbox.enqueueEdit(threadId, messageId, body, System.currentTimeMillis())
        kick(accountId)
    }

    /** Queues a delete of [messageId]; it is applied after every write queued before it. */
    suspend fun delete(accountId: String, threadId: String, messageId: String) {
        requireStore(accountId).outbox.enqueueDelete(threadId, messageId, System.currentTimeMillis())
        kick(accountId)
    }

    private suspend fun requireStore(accountId: String) =
        checkNotNull(chatStores.forAccount(accountId)) { "Chat is not available until the account's stores are open" }

    private fun kick(accountId: String) {
        scope.launch {
            try {
                flush(accountId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "OutboxRetryCoordinator: drain failed for $accountId")
            }
        }
    }

    private fun scheduleWakeup(accountId: String, atMillis: Long) {
        val job = scope.launch {
            delay((atMillis - System.currentTimeMillis()).coerceAtLeast(0))
            wakeups.remove(accountId, coroutineContext[Job])
            flush(accountId)
        }
        wakeups.put(accountId, job)?.takeIf { it !== job }?.cancel()
    }

    private suspend fun deliver(accountId: String, entry: ChatOutboxEntry): ChatOutboxOutcome {
        val body = entry.text(ChatOutboxQueue.KEY_BODY).orEmpty()
        return when (entry.operation) {
            ChatOutboxOperation.SEND -> client.sendMessage(
                threadId = entry.threadId,
                body = body,
                imageUrls = entry.strings(ChatOutboxQueue.KEY_IMAGE_URLS),
                needsReply = entry.payload[ChatOutboxQueue.KEY_NEEDS_REPLY]
                    ?.jsonPrimitive?.booleanOrNull?.takeIf { it },
                clientRef = entry.id,
            ).outcome(entry) { it.withPayloadMedia(entry).toEntity(accountId) }
            ChatOutboxOperation.REPLY -> client.sendReply(
                threadId = entry.threadId,
                messageId = entry.text(ChatOutboxQueue.KEY_PARENT_MESSAGE_ID).orEmpty(),
                body = body,
                clientRef = entry.id,
            ).outcome(entry) { it.toEntity(accountId) }
            ChatOutboxOperation.EDIT -> client.editMessage(entry.threadId, entry.targetMessageId.orEmpty(), body)
                .outcome(entry) { it.toEntity(accountId) }
            ChatOutboxOperation.DELETE -> {
                val result = client.deleteMessage(entry.threadId, entry.targetMessageId.orEmpty())
                // Already gone on the server: the delete has nothing left to do.
                if (result is NetworkResult.Error && result.code == HTTP_NOT_FOUND) {
                    ChatOutboxOutcome.Acknowledged(null)
                } else {
                    result.outcome(entry) { null }
                }
            }
        }
    }

    private fun <T> NetworkResult<T>.outcome(
        entry: ChatOutboxEntry,
        echo: (T) -> ChatMessageEntity?,
    ): ChatOutboxOutcome =
        when (this) {
            is NetworkResult.Success -> ChatOutboxOutcome.Acknowledged(echo(data))
            // 401 means the auth layer already tried to refresh and failed; retrying would storm it.
            is NetworkResult.Unauthorized -> ChatOutboxOutcome.Rejected("401 unauthorized")
            is NetworkResult.Error -> if (code in 400..499 && code != HTTP_TIMEOUT && code != HTTP_TOO_MANY) {
                Timber.e("OutboxRetry: permanent failure $code for ${entry.operation.wire} ${entry.id}: $message")
                ChatOutboxOutcome.Rejected("$code $message")
            } else {
                Timber.w(
                    "OutboxRetry: ${entry.operation.wire} ${entry.id} attempt ${entry.attempts + 1} " +
                        "failed ($code): $message",
                )
                ChatOutboxOutcome.Retry("$code $message")
            }
        }

    private fun ChatMessageResponse.withPayloadMedia(entry: ChatOutboxEntry): ChatMessageResponse = withFallbackMedia(
        imageUrls = entry.strings(KEY_LOCAL_IMAGE_URLS),
        audioUrl = entry.text(KEY_AUDIO_URL),
        attachmentUrl = entry.text(KEY_ATTACHMENT_URL),
        attachmentName = entry.text(KEY_ATTACHMENT_NAME),
        attachmentSize = entry.payload[KEY_ATTACHMENT_SIZE]?.jsonPrimitive?.longOrNull,
    )

    private fun ChatOutboxEntry.strings(key: String): List<String> =
        (payload[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

    private fun optimisticMessage(
        messageId: String,
        threadId: String,
        accountId: String,
        senderId: String,
        body: String,
        now: Long,
    ) = ChatMessageEntity(
        messageId = messageId,
        threadId = threadId,
        accountId = accountId,
        senderId = senderId,
        senderName = null,
        bodyMd = body,
        bodyPreview = body.take(PREVIEW_LENGTH),
        editedAt = null,
        linkPreviewTitle = null,
        linkPreviewDescription = null,
        linkPreviewImageUrl = null,
        linkPreviewUrl = null,
        photoUrl = null,
        clientRef = messageId,
        outboxStatus = ChatOutboxQueue.STATUS_SENDING,
        createdAt = now,
        updatedAt = now,
    )

    private fun serverImageUrls(
        imageUrls: List<String>,
        audioUrl: String?,
        attachmentUrl: String?,
    ): List<String> = buildList {
        addAll(imageUrls)
        audioUrl?.takeIf { it.isNotBlank() }?.let(::add)
        attachmentUrl?.takeIf { it.isNotBlank() }?.let(::add)
    }

    private companion object {
        const val PERIODIC_FLUSH_MS = 30_000L
        const val PREVIEW_LENGTH = 120
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TIMEOUT = 408
        const val HTTP_TOO_MANY = 429
        const val KEY_LOCAL_IMAGE_URLS = "local_image_urls"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_ATTACHMENT_URL = "attachment_url"
        const val KEY_ATTACHMENT_NAME = "attachment_name"
        const val KEY_ATTACHMENT_SIZE = "attachment_size"
    }
}
