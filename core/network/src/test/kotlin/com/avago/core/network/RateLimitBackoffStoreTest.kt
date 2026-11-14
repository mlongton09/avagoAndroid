package com.avago.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RateLimitBackoffStoreTest {

    private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        val state = MutableStateFlow(initial)
        var reads = 0
        override val data: Flow<Preferences> = state.onEach { reads++ }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `persisted gate is loaded once and then served from memory`() = runTest {
        val persisted = mutablePreferencesOf(longPreferencesKey("next_allowed__api_jobs") to 5_000L)
        val dataStore = FakeDataStore(persisted)
        val store = RateLimitBackoffStore(dataStore)

        assertNull(store.cachedNextAllowedAtMs("/api/jobs"), "mirror is empty before the first load")
        assertEquals(5_000L, store.nextAllowedAtMs("/api/jobs"))
        assertEquals(0L, store.nextAllowedAtMs("/api/other"))
        assertEquals(5_000L, store.cachedNextAllowedAtMs("/api/jobs"))
        assertEquals(0L, store.cachedNextAllowedAtMs("/api/other"))
        assertEquals(1, dataStore.reads)
    }

    @Test
    fun `new gate is visible immediately and persisted under the existing key format`() = runTest {
        val dataStore = FakeDataStore()
        val store = RateLimitBackoffStore(dataStore)

        store.setNextAllowedAtMs("/api/work-orders", 9_000L)

        assertEquals(9_000L, store.cachedNextAllowedAtMs("/api/work-orders"))
        assertEquals(9_000L, dataStore.state.value[longPreferencesKey("next_allowed__api_work_orders")])
        // A fresh instance (process restart) still honours the gate.
        assertEquals(9_000L, RateLimitBackoffStore(dataStore).nextAllowedAtMs("/api/work-orders"))
    }
}
