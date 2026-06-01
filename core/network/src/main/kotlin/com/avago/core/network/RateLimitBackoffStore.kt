package com.avago.core.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rateLimitBackoffDataStore by preferencesDataStore(name = "rate_limit_backoff")

@Singleton
class RateLimitBackoffStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.rateLimitBackoffDataStore

    suspend fun nextAllowedAtMs(endpoint: String): Long =
        dataStore.data.map { prefs -> prefs[key(endpoint)] ?: 0L }.first()

    suspend fun setNextAllowedAtMs(endpoint: String, value: Long) {
        dataStore.edit { prefs -> prefs[key(endpoint)] = value }
    }

    private fun key(endpoint: String) =
        longPreferencesKey("next_allowed_${endpoint.replace(Regex("[^A-Za-z0-9_]"), "_")}")
}
