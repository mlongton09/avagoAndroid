package com.avago.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_prefs")

/**
 * Persists user-facing preferences (theme, distance unit, language) via Jetpack DataStore.
 *
 * All flows emit the current value immediately and on every subsequent change,
 * making them safe to collect inside [androidx.lifecycle.ViewModel.viewModelScope].
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore = context.userPreferencesDataStore

    // ── Flows ─────────────────────────────────────────────────────────────────

    val themeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: "system"
    }

    val distanceUnitFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[DISTANCE_UNIT_KEY] ?: "mi"
    }

    val languageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: ""
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    suspend fun setTheme(value: String) {
        dataStore.edit { prefs -> prefs[THEME_KEY] = value }
    }

    suspend fun setDistanceUnit(value: String) {
        dataStore.edit { prefs -> prefs[DISTANCE_UNIT_KEY] = value }
    }

    suspend fun setLanguage(value: String) {
        dataStore.edit { prefs -> prefs[LANGUAGE_KEY] = value }
    }

    // ── Keys ──────────────────────────────────────────────────────────────────

    companion object {
        val THEME_KEY = stringPreferencesKey("theme_preference")
        val DISTANCE_UNIT_KEY = stringPreferencesKey("distance_unit")
        val LANGUAGE_KEY = stringPreferencesKey("language_override")
    }
}
