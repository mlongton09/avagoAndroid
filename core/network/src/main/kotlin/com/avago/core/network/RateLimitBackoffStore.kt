package com.avago.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rateLimitBackoffDataStore by preferencesDataStore(name = "rate_limit_backoff")

/**
 * Per-endpoint "not before" times set by 429 responses, persisted so a process restart still
 * honours the server's Retry-After.
 *
 * The HTTP interceptor consults this on every request, so reads are served from an in-memory
 * mirror that is loaded from DataStore once ([cachedNextAllowedAtMs] is non-blocking after
 * that). Writes update the mirror first and then persist.
 */
@Singleton
class RateLimitBackoffStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.rateLimitBackoffDataStore)

    private val gates = ConcurrentHashMap<String, Long>()
    private val loadLock = Mutex()

    @Volatile
    private var loaded = false

    /** The mirrored value without touching storage, or null until the mirror has been loaded. */
    fun cachedNextAllowedAtMs(endpoint: String): Long? =
        if (loaded) gates[keyName(endpoint)] ?: 0L else null

    suspend fun nextAllowedAtMs(endpoint: String): Long {
        ensureLoaded()
        return gates[keyName(endpoint)] ?: 0L
    }

    suspend fun setNextAllowedAtMs(endpoint: String, value: Long) {
        ensureLoaded()
        val name = keyName(endpoint)
        gates[name] = value
        dataStore.edit { prefs -> prefs[longPreferencesKey(name)] = value }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            dataStore.data.first().asMap().forEach { (key, value) ->
                if (value is Long) gates.putIfAbsent(key.name, value)
            }
            loaded = true
        }
    }

    private fun keyName(endpoint: String) = "next_allowed_${endpoint.replace(NON_KEY_CHARS, "_")}"

    private companion object {
        val NON_KEY_CHARS = Regex("[^A-Za-z0-9_]")
    }
}
