package com.avago.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avago.core.data.domain.AccountPreferencesRepository
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.domain.UserPreferenceChange
import com.avago.core.data.domain.UserPreferenceOverrides
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 * Currency, distance unit, fuel volume unit, disable_quotes and enable_human_in_loop are also the
 * signed-in user's synced `user_preferences` ([AccountPreferencesRepository]): a value stored there
 * wins, then the account default (synced `account_preferences`), then the device value. Setting one
 * also queues `core.user_preferences.update`, so web and the user's other devices follow. Signed
 * out, the device value is used alone.
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
    private val account: AccountPreferencesRepository,
) {

    private val dataStore = context.userPreferencesDataStore

    // ── Flows ─────────────────────────────────────────────────────────────────

    val themeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: "system"
    }

    val distanceUnitFlow: Flow<String> = synced({ it[DISTANCE_UNIT_KEY] ?: "mi" }) { it.distanceUnit?.let(::deviceDistanceUnit) }

    val languageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: ""
    }

    val currencyFlow: Flow<String> = synced({ it[CURRENCY_KEY] ?: "USD" }) { it.currency }

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
    val fuelVolumeUnitFlow: Flow<String> = synced({ it[FUEL_VOLUME_UNIT_KEY] ?: "gallon" }) { it.fuelVolumeUnit }

    /**
     * Temperature unit — "F" (default) or "C".
     * Mirrors iOS AVDefaultsKeyTemperatureUnit.
     */
    val temperatureUnitFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[TEMPERATURE_UNIT_KEY] ?: "F"
    }

    /**
     * When `true` the "daily quotes" banner is hidden.
     * Mirrors iOS AVDefaultsKeyDisableQuotes.
     */
    val disableQuotesFlow: Flow<Boolean> = synced({ it[DISABLE_QUOTES_KEY] ?: false }) { it.disableQuotes }

    /**
     * AI Human-in-the-Loop — when `true` Scout populates a form for the user to
     * review before committing (default).  When `false` Scout acts directly.
     * Mirrors iOS AVDefaultsKeyEnableHumanInLoop.
     */
    val enableHumanInLoopFlow: Flow<Boolean> = synced({ it[ENABLE_HUMAN_IN_LOOP_KEY] ?: true }) { it.enableHumanInLoop }

    /** Forces service clients to read only cached/local data and block network calls. */
    val forceOfflineFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FORCE_OFFLINE_KEY] ?: false
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    /** High-contrast "sunlight mode" for outdoor inspections. */
    val sunlightModeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SUNLIGHT_MODE_KEY] ?: false
    }

    suspend fun setSunlightMode(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SUNLIGHT_MODE_KEY] = enabled }
    }

    suspend fun setTheme(value: String) {
        dataStore.edit { prefs -> prefs[THEME_KEY] = value }
    }

    suspend fun setDistanceUnit(value: String) {
        dataStore.edit { prefs -> prefs[DISTANCE_UNIT_KEY] = value }
        share(UserPreferenceChange.DistanceUnit(serverDistanceUnit(value)))
    }

    suspend fun setLanguage(value: String) {
        dataStore.edit { prefs -> prefs[LANGUAGE_KEY] = value }
    }

    suspend fun setCurrency(value: String) {
        dataStore.edit { prefs -> prefs[CURRENCY_KEY] = value }
        share(UserPreferenceChange.Currency(value))
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
        share(UserPreferenceChange.FuelVolumeUnit(value))
    }

    suspend fun setTemperatureUnit(value: String) {
        dataStore.edit { prefs -> prefs[TEMPERATURE_UNIT_KEY] = value }
    }

    suspend fun setDisableQuotes(value: Boolean) {
        dataStore.edit { prefs -> prefs[DISABLE_QUOTES_KEY] = value }
        share(UserPreferenceChange.DisableQuotes(value))
    }

    suspend fun setEnableHumanInLoop(value: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLE_HUMAN_IN_LOOP_KEY] = value }
        share(UserPreferenceChange.EnableHumanInLoop(value))
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

    /** Removes every chat composer draft; other preferences are untouched (v2 legacy reset). */
    suspend fun clearChatDrafts() {
        dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(CHAT_DRAFT_PREFIX) }
                .forEach { prefs.remove(it) }
        }
    }

    private fun <T : Any> synced(device: (Preferences) -> T, synced: (UserPreferenceOverrides) -> T?): Flow<T> =
        combine(dataStore.data.map(device), account.observeMine(), account.observeAccountDefaults()) { local, mine, defaults ->
            mine?.let(synced) ?: defaults?.let(synced) ?: local
        }

    /** Queues the change for the signed-in user; signed out it stays on this device only. */
    private suspend fun share(change: UserPreferenceChange) {
        try {
            account.update(listOf(change))
        } catch (_: StoreNotReadyException) {
            // Nobody signed in (or the v2 store is still opening): the device value is the preference.
        }
    }

    // ── Keys ──────────────────────────────────────────────────────────────────

    companion object {
        const val CHAT_DRAFT_PREFIX = "chat_draft_"
        val THEME_KEY = stringPreferencesKey("theme_preference")
        val SUNLIGHT_MODE_KEY = booleanPreferencesKey("sunlight_mode")
        val DISTANCE_UNIT_KEY = stringPreferencesKey("distance_unit")
        val LANGUAGE_KEY = stringPreferencesKey("language_override")
        val CURRENCY_KEY = stringPreferencesKey("currency_preference")
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val FRE_DISMISSED_KEY = booleanPreferencesKey("fre_dismissed")
        val FRE_COMPLETED_KEY = booleanPreferencesKey("fre_completed")
        val FUEL_VOLUME_UNIT_KEY = stringPreferencesKey("fuel_volume_unit")
        val TEMPERATURE_UNIT_KEY = stringPreferencesKey("temperature_unit")
        val DISABLE_QUOTES_KEY = booleanPreferencesKey("disable_quotes")
        const val RUNTIME_FLAGS_PREFS = "avago_runtime_flags"
        const val FORCE_OFFLINE_PREF_KEY = "force_offline"
        val ENABLE_HUMAN_IN_LOOP_KEY = booleanPreferencesKey("enable_human_in_loop")
        val FORCE_OFFLINE_KEY = booleanPreferencesKey(FORCE_OFFLINE_PREF_KEY)

        /** Device code (`mi` / `km`) of a synced `distance_unit` (`miles` / `km`). */
        fun deviceDistanceUnit(server: String): String = if (server == "miles") "mi" else server

        /** Synced `distance_unit` code of a device value. */
        fun serverDistanceUnit(device: String): String = if (device == "mi") "miles" else device
    }
}
