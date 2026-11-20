package com.avago.core.sync.v2

import com.avago.core.data.v2.V2Request
import com.avago.core.data.v2.V2Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync telemetry (the server's `POST /metrics/client`, which accepts `sync_*` / `push_delta_*`
 * counters with `entity_type` and `outcome` labels). Counters accumulate in memory and are sent
 * at most every [FLUSH_INTERVAL_MS]; a failed send keeps them for the next try.
 */
@Singleton
class V2ClientMetrics internal constructor(
    private val transport: V2Transport,
    private val clock: () -> Long,
) {
    @Inject constructor(transport: V2HttpTransport) : this(transport, System::currentTimeMillis)

    private data class Key(val name: String, val entityType: String, val outcome: String)

    private val mutex = Mutex()
    private val counters = LinkedHashMap<Key, Long>()
    private var lastFlushMs = 0L

    suspend fun count(name: String, entityType: String, outcome: String) = mutex.withLock {
        require(name.startsWith("sync_")) { "client metric $name is outside the server's sync_ allowlist" }
        val key = Key(name, entityType, outcome)
        counters[key] = (counters[key] ?: 0L) + 1
    }

    /** Sends the counters when [FLUSH_INTERVAL_MS] has passed since the last send. */
    suspend fun flushIfDue() {
        val batch = mutex.withLock {
            if (counters.isEmpty() || clock() - lastFlushMs < FLUSH_INTERVAL_MS) return
            lastFlushMs = clock()
            counters.toMap().also { counters.clear() }
        }
        val body = buildJsonObject {
            putJsonArray("counters") {
                batch.forEach { (key, value) ->
                    addJsonObject {
                        put("name", key.name)
                        put("value", value)
                        put("entity_type", key.entityType)
                        put("outcome", key.outcome)
                    }
                }
            }
        }
        val sent = try {
            transport.send(V2Request("POST", PATH, body = body.toString())).status in 200..299
        } catch (e: CancellationException) {
            restore(batch)
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.d("V2ClientMetrics: not sent (${e.message})")
            false
        }
        if (!sent) restore(batch)
    }

    private suspend fun restore(batch: Map<Key, Long>) = mutex.withLock {
        batch.forEach { (key, value) -> counters[key] = (counters[key] ?: 0L) + value }
    }

    companion object {
        const val PATH = "/metrics/client"
        const val FLUSH_INTERVAL_MS = 5 * 60 * 1000L
    }
}
