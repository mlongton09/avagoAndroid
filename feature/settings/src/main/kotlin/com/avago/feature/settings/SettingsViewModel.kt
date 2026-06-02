package com.avago.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.UpdatePreferencesRequest
import com.avago.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives [SettingsScreen].
 *
 * Owns all user-preference state (theme, distance unit, fuel unit, currency,
 * language, AI HITL toggle) and delegates account mutations to [IdentityManager].
 *
 * Mirrors the iOS SettingsViewController behaviour of persisting preference changes
 * both locally (DataStore) and to the server (PUT /accounts/{id}/preferences/me),
 * so that web + other devices pick up the change on their next sync.
 *
 * All state is exposed as [StateFlow] so Compose can collect it safely across
 * recompositions.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val identity: IdentityManager,
    private val serviceClient: AvagoServiceClient,
    private val databaseFactory: DatabaseFactory,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    // ── Preference state ──────────────────────────────────────────────────────

    val theme: StateFlow<String> = prefs.themeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "system",
    )

    val distanceUnit: StateFlow<String> = prefs.distanceUnitFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "mi",
    )

    val currency: StateFlow<String> = prefs.currencyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "USD",
    )

    val language: StateFlow<String> = prefs.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )

    val fuelVolumeUnit: StateFlow<String> = prefs.fuelVolumeUnitFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "gallon",
    )

    val disableQuotes: StateFlow<Boolean> = prefs.disableQuotesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    /**
     * AI Human-in-the-Loop toggle.  When `true` the Scout AI populates a form for
     * the user to review before saving (default).  When `false` Scout commits
     * directly without showing the form.
     *
     * Mirrors iOS AVDefaultsKeyEnableHumanInLoop / "enable_human_in_loop".
     */
    val enableHumanInLoop: StateFlow<Boolean> = prefs.enableHumanInLoopFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    val forceOffline: StateFlow<Boolean> = prefs.forceOfflineFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    // ── Identity state ────────────────────────────────────────────────────────

    /** Mirrors IdentityManager — null when no account is active. */
    val activeAccountId: StateFlow<String?> = identity.activeAccountId

    val effectiveRole: StateFlow<String?> = identity.activeAccountId.map { identity.getEffectiveRole() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = identity.getEffectiveRole(),
    )

    // ── Actions ───────────────────────────────────────────────────────────────

    /** Theme is device-local only (no server-side pref for theme). */
    fun setTheme(value: String) {
        viewModelScope.launch { prefs.setTheme(value) }
    }

    /**
     * Persists the distance unit locally and mirrors it to the server so that
     * web + other devices pick up the change on their next sync.
     *
     * Mirrors iOS distanceUnitSegmentChanged → PreferencesSync.shared.save(patch:).
     */
    fun setDistanceUnit(value: String) {
        viewModelScope.launch {
            prefs.setDistanceUnit(value)
            syncPreferences(UpdatePreferencesRequest(distance_unit = value))
        }
    }

    /**
     * Persists the currency locally and mirrors it to the server.
     *
     * Mirrors iOS showCurrencyPicker → PreferencesSync.shared.save(patch:).
     */
    fun setCurrency(value: String) {
        viewModelScope.launch {
            prefs.setCurrency(value)
            syncPreferences(UpdatePreferencesRequest(currency = value))
        }
    }

    /** Language is set via Android system locale settings; stored locally for quick read. */
    fun setLanguage(value: String) {
        viewModelScope.launch { prefs.setLanguage(value) }
    }

    /**
     * Persists the fuel volume unit locally and mirrors it to the server.
     *
     * Mirrors iOS fuelUnitSegmentChanged → PreferencesSync.shared.save(patch:).
     */
    fun setFuelVolumeUnit(value: String) {
        viewModelScope.launch {
            prefs.setFuelVolumeUnit(value)
            syncPreferences(UpdatePreferencesRequest(fuel_volume_unit = value))
        }
    }

    /**
     * Persists the "disable quotes" toggle locally and mirrors it to the server.
     *
     * Mirrors iOS quotesToggleChanged → PreferencesSync.shared.save(patch:).
     */
    fun setDisableQuotes(value: Boolean) {
        viewModelScope.launch {
            prefs.setDisableQuotes(value)
            syncPreferences(UpdatePreferencesRequest(disable_quotes = value))
        }
    }

    /**
     * Persists the AI Human-in-the-Loop toggle locally and mirrors it to the server.
     *
     * Mirrors iOS humanInLoopToggleChanged → PreferencesSync.shared.save(patch:).
     */
    fun setEnableHumanInLoop(value: Boolean) {
        viewModelScope.launch {
            prefs.setEnableHumanInLoop(value)
            syncPreferences(UpdatePreferencesRequest(enable_human_in_loop = value))
        }
    }

    fun setForceOffline(value: Boolean) {
        viewModelScope.launch { prefs.setForceOffline(value) }
    }

    fun signOut() {
        viewModelScope.launch {
            val id = activeAccountId.value ?: return@launch
            try {
                identity.signOut(id)
            } catch (e: Exception) {
                Timber.e(e, "SettingsViewModel: signOut failed for $id")
            }
        }
    }

    fun deleteAccount(hard: Boolean) {
        viewModelScope.launch {
            val id = activeAccountId.value ?: return@launch
            try {
                when (val result = serviceClient.deleteAccount(id, hard = hard)) {
                    is NetworkResult.Success -> identity.signOut(id)
                    is NetworkResult.Error -> Timber.w("SettingsViewModel: deleteAccount failed (${result.code}) — ${result.message}")
                    NetworkResult.Unauthorized -> Timber.w("SettingsViewModel: deleteAccount unauthorized")
                }
            } catch (e: Exception) {
                Timber.e(e, "SettingsViewModel: deleteAccount failed for $id")
            }
        }
    }

    fun deleteCustomerContent() {
        viewModelScope.launch {
            val id = activeAccountId.value ?: return@launch
            try {
                when (val result = serviceClient.deleteCustomerContent(id)) {
                    is NetworkResult.Success -> {
                        runCatching { databaseFactory.deleteDatabase(id) }
                            .onFailure { Timber.w(it, "SettingsViewModel: local customer-content wipe failed for $id") }
                        syncEngine.sync()
                    }
                    is NetworkResult.Error -> Timber.w("SettingsViewModel: deleteCustomerContent failed (${result.code}) — ${result.message}")
                    NetworkResult.Unauthorized -> Timber.w("SettingsViewModel: deleteCustomerContent unauthorized")
                }
            } catch (e: Exception) {
                Timber.e(e, "SettingsViewModel: deleteCustomerContent failed for $id")
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Fire-and-forget server sync for a preference patch.  Failures are logged but
     * do not surface to the UI — the local DataStore value is already committed.
     */
    private suspend fun syncPreferences(request: UpdatePreferencesRequest) {
        val accountId = activeAccountId.value ?: return
        when (val result = serviceClient.updateMyPreferences(accountId, request)) {
            is NetworkResult.Success -> Timber.d("SettingsViewModel: prefs synced to server")
            is NetworkResult.Error ->
                Timber.w("SettingsViewModel: pref sync failed (${result.code}) — ${result.message}")
            NetworkResult.Unauthorized ->
                Timber.w("SettingsViewModel: pref sync unauthorized")
        }
    }
}
