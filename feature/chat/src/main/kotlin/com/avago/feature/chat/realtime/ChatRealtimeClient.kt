package com.avago.feature.chat.realtime

import com.avago.core.network.model.ChatMessageResponse
import com.avago.core.sync.ApplicationScope
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Maintains a persistent Server-Sent Events (SSE) connection to a chat thread.
 *
 * The server endpoint GET /chat/threads/{threadId}/events returns a text/event-stream
 * response. Each SSE event has the format:
 *
 *   data: <json>\n\n
 *
 * We consume the channel line-by-line, accumulate a data block, and decode the JSON
 * payload as [ChatMessageResponse] on each double-newline flush.
 */
@Singleton
class ChatRealtimeClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var sseJob: Job? = null
    private var connectedThreadId: String? = null

    fun connect(threadId: String, onEvent: (ChatMessageResponse) -> Unit) {
        if (connectedThreadId == threadId && sseJob?.isActive == true) return
        disconnect()
        connectedThreadId = threadId
        sseJob = scope.launch {
            var backoffMs = 1_000L
            while (isActive) {
                try {
                    Timber.d("ChatSSE: connecting to thread $threadId")
                    httpClient.get("$baseUrl/chat/threads/$threadId/events") {
                        headers {
                            append("Accept", "text/event-stream")
                            append("Cache-Control", "no-cache")
                        }
                    }.run {
                        val channel = bodyAsChannel()
                        val dataBuffer = StringBuilder()
                        while (isActive) {
                            val line = channel.readUTF8Line() ?: break
                            when {
                                line.startsWith("data:") -> {
                                    dataBuffer.append(line.removePrefix("data:").trim())
                                }
                                line.isEmpty() && dataBuffer.isNotEmpty() -> {
                                    val raw = dataBuffer.toString().trim()
                                    dataBuffer.clear()
                                    if (raw == "[DONE]" || raw.isEmpty()) continue
                                    try {
                                        val msg = json.decodeFromString<ChatMessageResponse>(raw)
                                        onEvent(msg)
                                    } catch (e: Exception) {
                                        Timber.w(e, "ChatSSE: failed to parse event: $raw")
                                    }
                                }
                                // ignore comment lines (":") and other SSE fields
                            }
                        }
                    }
                    Timber.d("ChatSSE: stream ended for thread $threadId, reconnecting")
                    backoffMs = 1_000L
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "ChatSSE: error on thread $threadId, retrying in ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs = minOf(backoffMs * 2, 30_000L)
                }
            }
        }
    }

    fun disconnect() {
        sseJob?.cancel()
        sseJob = null
        connectedThreadId = null
    }
}
