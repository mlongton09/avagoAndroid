package com.avago.feature.chat.realtime

import com.avago.core.auth.IdentityManager
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.sync.ApplicationScope
import com.avago.feature.chat.data.ChatRepository
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class TypingChangedEvent(val threadId: String, val typingUserIds: List<String>)

/**
 * Singleton WebSocket connection to the account-level chat realtime endpoint.
 *
 * Connects to `wss://api.avagomate.com/accounts/{accountId}/chat/ws?token={jwt}`.
 * Automatically manages connection lifecycle: connects on sign-in, disconnects on sign-out.
 * Dispatches server-pushed events to [ChatRepository] (for DB writes) and [typingChangedFlow]
 * (for ViewModels to observe).
 */
@Singleton
class ChatRealtimeClient @Inject constructor(
    private val identity: IdentityManager,
    @Named("baseUrl") private val baseUrl: String,
    private val repository: ChatRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val backgroundSync: BackgroundSyncCoordinator,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val okHttp = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _typingChanged = MutableSharedFlow<TypingChangedEvent>(
        replay = 0, extraBufferCapacity = 64
    )
    val typingChangedFlow: SharedFlow<TypingChangedEvent> = _typingChanged.asSharedFlow()

    private var wsJob: Job? = null
    private var ackJob: Job? = null
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connectedAccountId: String? = null

    // ── Delivery ack batching ─────────────────────────────────────────────────

    private val pendingAckIds = mutableSetOf<String>()
    private val ackLock = Any()

    init {
        scope.launch {
            identity.activeAccountId.collect { accountId ->
                if (accountId != null) {
                    connect(accountId)
                } else {
                    disconnect()
                }
            }
        }
    }

    fun connect(accountId: String) {
        if (connectedAccountId == accountId && wsJob?.isActive == true) return
        disconnect()
        connectedAccountId = accountId

        // Start delivery-ack flush job (iOS pattern: flush every 2s).
        ackJob = scope.launch {
            while (isActive) {
                delay(2_000)
                val ids = synchronized(ackLock) {
                    if (pendingAckIds.isEmpty()) return@synchronized emptyList()
                    val copy = pendingAckIds.toList()
                    pendingAckIds.clear()
                    copy
                }
                if (ids.isNotEmpty()) {
                    scope.launch { repository.markDelivered(ids) }
                }
            }
        }

        wsJob = scope.launch {
            var backoffMs = 1_000L
            while (isActive) {
                val token = identity.getAccessToken(accountId)
                if (token == null) {
                    Timber.w("ChatWS: no token for $accountId, retrying in ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs = nextBackoffMs(backoffMs)
                    continue
                }
                val wsUrl = buildWsUrl(accountId, token)
                val request = Request.Builder().url(wsUrl).build()
                var connected = false
                val listener = object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        Timber.d("ChatWS: connected for $accountId")
                        webSocket = ws
                        connected = true
                        backoffMs = 1_000L
                        // Trigger delta sync on reconnect (iOS pattern).
                        scope.launch { backgroundSync.runDelta() }
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        handleMessage(text)
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        Timber.w(t, "ChatWS: failure for $accountId")
                        webSocket = null
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        Timber.d("ChatWS: closed for $accountId code=$code reason=$reason")
                        webSocket = null
                    }
                }
                val ws = okHttp.newWebSocket(request, listener)
                // Wait for the connection to be established or fail with a short poll.
                // OkHttp delivers events on its own thread; we just wait here before retrying.
                var waited = 0
                while (isActive && webSocket == null && waited < 10_000) {
                    delay(200)
                    waited += 200
                }
                if (!isActive) {
                    ws.cancel()
                    break
                }
                if (webSocket == null) {
                    // Connection never opened or was immediately closed; retry with backoff.
                    Timber.w("ChatWS: connect timed out for $accountId, retrying in ${backoffMs}ms")
                    ws.cancel()
                    delay(backoffMs)
                    backoffMs = nextBackoffMs(backoffMs)
                    continue
                }
                // Stay connected until the socket closes or job is cancelled.
                while (isActive && webSocket != null) {
                    delay(1_000)
                }
                if (!isActive) break
                // Socket dropped — reconnect after backoff.
                Timber.d("ChatWS: socket dropped for $accountId, reconnecting in ${backoffMs}ms")
                delay(backoffMs)
                backoffMs = nextBackoffMs(backoffMs)
            }
        }
    }

    fun disconnect() {
        ackJob?.cancel()
        ackJob = null
        wsJob?.cancel()
        wsJob = null
        webSocket?.close(1000, "signed out")
        webSocket = null
        connectedAccountId = null
    }

    /** Send a typing indicator to the server for the current thread. */
    fun sendTyping(threadId: String, isTyping: Boolean) {
        webSocket?.send("""{"type":"typing","thread_id":"$threadId","is_typing":$isTyping}""")
    }

    /** Notify the server that the user has read up to [messageId] in [threadId]. */
    fun sendRead(threadId: String, messageId: String) {
        webSocket?.send("""{"type":"read","thread_id":"$threadId","message_id":"$messageId"}""")
    }

    // ── Reconnect backoff with ±25% jitter (iOS pattern) ─────────────────────

    private fun nextBackoffMs(current: Long): Long {
        val next = minOf(current * 2, 30_000L)
        val jitter = (next * 0.25 * (Random.nextDouble() * 2 - 1)).toLong()
        return next + jitter
    }

    // ── Internal event dispatch ───────────────────────────────────────────────

    private fun handleMessage(raw: String) {
        val envelope = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val type = envelope["type"]?.jsonPrimitive?.content ?: return
        val payload = envelope["payload"]?.jsonObject

        when (type) {
            "message.created", "message.updated" -> {
                val msgJson = payload?.get("message")?.jsonObject ?: return
                val msg = runCatching {
                    json.decodeFromJsonElement<ChatMessageResponse>(msgJson)
                }.getOrNull() ?: return
                // Queue delivery ack for messages from other users.
                if (type == "message.created" && msg.author_id != identity.getActiveUserId()) {
                    synchronized(ackLock) { pendingAckIds.add(msg.message_id) }
                }
                scope.launch { repository.handleRealtimeMessage(msg) }
            }
            "message.deleted" -> {
                val messageId = payload?.get("message_id")?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeMessageDeleted(messageId) }
            }
            "message.pinned" -> {
                val messageId = payload?.get("message_id")?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeMessagePinned(messageId, true) }
            }
            "message.unpinned" -> {
                val messageId = payload?.get("message_id")?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeMessagePinned(messageId, false) }
            }
            "message.reaction_added" -> {
                val messageId = payload?.get("message_id")?.jsonPrimitive?.content ?: return
                val emoji = payload["emoji"]?.jsonPrimitive?.content ?: return
                val userId = payload["user_id"]?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeReaction(messageId, emoji, userId, added = true) }
            }
            "message.reaction_removed" -> {
                val messageId = payload?.get("message_id")?.jsonPrimitive?.content ?: return
                val emoji = payload["emoji"]?.jsonPrimitive?.content ?: return
                val userId = payload["user_id"]?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeReaction(messageId, emoji, userId, added = false) }
            }
            "thread.created" -> {
                val threadId = payload?.get("thread_id")?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeThreadCreated(threadId) }
            }
            "thread.archived" -> {
                val threadId = payload?.get("thread_id")?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeThreadArchived(threadId) }
            }
            "thread.renamed" -> {
                val threadId = payload?.get("thread_id")?.jsonPrimitive?.content ?: return
                val newName = payload["name"]?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeThreadRenamed(threadId, newName) }
            }
            "thread.members_added", "thread.members_removed", "thread.left" -> {
                val threadId = payload?.get("thread_id")?.jsonPrimitive?.content ?: return
                scope.launch { repository.syncThreadMembers(threadId) }
            }
            "thread.notification_pref_updated" -> {
                val threadId = payload?.get("thread_id")?.jsonPrimitive?.content ?: return
                val pref = payload["notification_pref"]?.jsonPrimitive?.content ?: return
                scope.launch { repository.handleRealtimeNotifPrefChanged(threadId, pref) }
            }
            "thread.unread_increment" -> {
                val threadId = payload?.get("thread_id")?.jsonPrimitive?.content ?: return
                val count = payload["unread_count"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
                scope.launch { repository.handleThreadUnreadIncrement(threadId, count) }
            }
            "presence.online" -> {
                val userId = payload?.get("user_id")?.jsonPrimitive?.content ?: return
                scope.launch { repository.updatePresence(userId, "online") }
            }
            "presence.offline" -> {
                val userId = payload?.get("user_id")?.jsonPrimitive?.content ?: return
                scope.launch { repository.updatePresence(userId, "offline") }
            }
            "typing.changed" -> {
                val threadId = payload?.get("thread_id")?.jsonPrimitive?.content ?: return
                val userIds = payload["typing_user_ids"]
                    ?.let { json.decodeFromJsonElement<List<String>>(it) }
                    ?: emptyList()
                scope.launch { _typingChanged.emit(TypingChangedEvent(threadId, userIds)) }
            }
            "pong" -> { /* heartbeat, no action */ }
            else -> Timber.d("ChatWS: unknown event type=$type")
        }
    }

    private fun buildWsUrl(accountId: String, token: String): String {
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        return "$wsBase/accounts/$accountId/chat/ws?token=$token"
    }
}
