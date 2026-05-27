package com.avago.feature.chat.realtime

import com.avago.core.auth.IdentityManager
import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.sync.ApplicationScope
import com.avago.feature.chat.data.ChatRepository
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
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connectedAccountId: String? = null

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
        wsJob = scope.launch {
            var backoffMs = 2_000L
            while (isActive) {
                val token = identity.getAccessToken(accountId)
                if (token == null) {
                    Timber.w("ChatWS: no token for $accountId, retrying in ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs = minOf(backoffMs * 2, 30_000L)
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
                        backoffMs = 2_000L
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
                    backoffMs = minOf(backoffMs * 2, 30_000L)
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
                backoffMs = minOf(backoffMs * 2, 30_000L)
            }
        }
    }

    fun disconnect() {
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
            "thread.unread_increment" -> {
                val threadId = payload?.get("thread_id")?.jsonPrimitive?.content ?: return
                val count = payload["unread_count"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
                scope.launch { repository.handleThreadUnreadIncrement(threadId, count) }
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
