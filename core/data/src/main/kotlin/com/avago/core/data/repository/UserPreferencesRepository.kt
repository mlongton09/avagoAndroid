package com.avago.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_prefs")

/**
 * Persists user-facing preferences (theme, distance unit, fuel unit, currency,
 * language, disable_quotes, enable_human_in_loop, force_offline) via Jetpack DataStore.
 *
 * All flows emit the current value immediately and on every subsequent change,
 * making them safe to collect inside [androidx.lifecycle.ViewModel.viewModelScope].
 *
 * Mirrors the preference keys used by the iOS app:
 *   - AVDefaultsKeyThemeOverride
 *   - AVDefaultsKeyDefaultOdometerUnit
 *   - AVDefaultsKeyFuelVolumeUnit
 *   - AVDefaultsKeyCurrency
 *   - AVDefaultsKeyDisableQuotes
 *   - AVDefaultsKeyEnableHumanInLoop
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

    val currencyFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[CURRENCY_KEY] ?: "USD"
    }

    val notificationsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_ENABLED_KEY] ?: true
    }

    val freDismissedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FRE_DISMISSED_KEY] ?: false
    }

    val freCompletedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FRE_COMPLETED_KEY] ?: false
    }

    /**
     * Fuel volume unit — "gallon" (default) or "liter".
     * Mirrors iOS AVDefaultsKeyFuelVolumeUnit.
     */
    val fuelVolumeUnitFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[FUEL_VOLUME_UNIT_KEY] ?: "gallon"
    }

    /**
     * When `true` the "daily quotes" banner is hidden.
     * Mirrors iOS AVDefaultsKeyDisableQuotes.
     */
    val disableQuotesFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DISABLE_QUOTES_KEY] ?: false
    }

    /**
     * AI Human-in-the-Loop — when `true` Scout populates a form for the user to
     * review before committing (default).  When `false` Scout acts directly.
     * Mirrors iOS AVDefaultsKeyEnableHumanInLoop.
     */
    val enableHumanInLoopFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ENABLE_HUMAN_IN_LOOP_KEY] ?: true
    }

    /** Forces service clients to read only cached/local data and block network calls. */
    val forceOfflineFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FORCE_OFFLINE_KEY] ?: false
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

    suspend fun setCurrency(value: String) {
        dataStore.edit { prefs -> prefs[CURRENCY_KEY] = value }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[NOTIFICATIONS_ENABLED_KEY] = value }
    }

    suspend fun setFreDismissed() {
        dataStore.edit { prefs -> prefs[FRE_DISMISSED_KEY] = true }
    }

    suspend fun setFreCompleted() {
        dataStore.edit { prefs -> prefs[FRE_COMPLETED_KEY] = true }
    }

    suspend fun setFuelVolumeUnit(value: String) {
        dataStore.edit { prefs -> prefs[FUEL_VOLUME_UNIT_KEY] = value }
    }

    suspend fun setDisableQuotes(value: Boolean) {
        dataStore.edit { prefs -> prefs[DISABLE_QUOTES_KEY] = value }
    }

    suspend fun setEnableHumanInLoop(value: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLE_HUMAN_IN_LOOP_KEY] = value }
    }

    suspend fun setForceOffline(value: Boolean) {
        dataStore.edit { prefs -> prefs[FORCE_OFFLINE_KEY] = value }
        context.getSharedPreferences(RUNTIME_FLAGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(FORCE_OFFLINE_PREF_KEY, value)
            .apply()
    }

    /**
     * Returns the saved draft body for a specific thread, or empty string if none.
     * Used to restore the composer on cold start (matches iOS UserDefaults draft persistence).
     */
    suspend fun getChatDraft(threadId: String): String {
        return dataStore.data.map { prefs ->
            prefs[stringPreferencesKey("chat_draft_$threadId")] ?: ""
        }.first()
    }

    /** Persists the current composer draft for [threadId]. Pass empty string to clear. */
    suspend fun setChatDraft(threadId: String, text: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("chat_draft_$threadId")] = text
        }
    }

    // ── Keys ──────────────────────────────────────────────────────────────────

    companion object {
        val THEME_KEY = stringPreferencesKey("theme_preference")
        val DISTANCE_UNIT_KEY = stringPreferencesKey("distance_unit")
        val LANGUAGE_KEY = stringPreferencesKey("language_override")
        val CURRENCY_KEY = stringPreferencesKey("currency_preference")
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val FRE_DISMISSED_KEY = booleanPreferencesKey("fre_dismissed")
        val FRE_COMPLETED_KEY = booleanPreferencesKey("fre_completed")
        val FUEL_VOLUME_UNIT_KEY = stringPreferencesKey("fuel_volume_unit")
        val DISABLE_QUOTES_KEY = booleanPreferencesKey("disable_quotes")
        const val RUNTIME_FLAGS_PREFS = "avago_runtime_flags"
        const val FORCE_OFFLINE_PREF_KEY = "force_offline"
        val ENABLE_HUMAN_IN_LOOP_KEY = booleanPreferencesKey("enable_human_in_loop")
        val FORCE_OFFLINE_KEY = booleanPreferencesKey(FORCE_OFFLINE_PREF_KEY)
    }
}
