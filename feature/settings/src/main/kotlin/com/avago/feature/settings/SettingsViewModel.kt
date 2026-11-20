package com.avago.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.SyncRepository
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
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
 * Currency, units, quotes and the AI human-in-the-loop switch are the user's synced
 * preferences: [UserPreferencesRepository] queues `core.user_preferences.update` for them, so
 * web and the user's other devices pick the change up through sync.
 *
 * All state is exposed as [StateFlow] so Compose can collect it safely across
 * recompositions.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val identity: IdentityManager,
    private val serviceClient: AvagoServiceClient,
    private val sync: SyncRepository,
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

    val temperatureUnit: StateFlow<String> = prefs.temperatureUnitFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "F",
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
     * Persists the distance unit locally and as the user's synced preference.
     */
    fun setDistanceUnit(value: String) {
        viewModelScope.launch {
            prefs.setDistanceUnit(value)
        }
    }

    /**
     * Persists the currency locally and as the user's synced preference.
     */
    fun setCurrency(value: String) {
        viewModelScope.launch {
            prefs.setCurrency(value)
        }
    }

    /** Language is set via Android system locale settings; stored locally for quick read. */
    fun setLanguage(value: String) {
        viewModelScope.launch { prefs.setLanguage(value) }
    }

    /**
     * Persists the fuel volume unit locally and as the user's synced preference.
     */
    fun setFuelVolumeUnit(value: String) {
        viewModelScope.launch {
            prefs.setFuelVolumeUnit(value)
        }
    }

    fun setTemperatureUnit(value: String) {
        viewModelScope.launch {
            prefs.setTemperatureUnit(value)
        }
    }

    /**
     * Persists the "disable quotes" toggle locally and as the user's synced preference.
     */
    fun setDisableQuotes(value: Boolean) {
        viewModelScope.launch {
            prefs.setDisableQuotes(value)
        }
    }

    /**
     * Persists the AI Human-in-the-Loop toggle locally and as the user's synced preference.
     */
    fun setEnableHumanInLoop(value: Boolean) {
        viewModelScope.launch {
            prefs.setEnableHumanInLoop(value)
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
                        // The server data is gone; re-bootstrap the local v2 store from it.
                        sync.resync()
                    }
                    is NetworkResult.Error -> Timber.w("SettingsViewModel: deleteCustomerContent failed (${result.code}) — ${result.message}")
                    NetworkResult.Unauthorized -> Timber.w("SettingsViewModel: deleteCustomerContent unauthorized")
                }
            } catch (e: Exception) {
                Timber.e(e, "SettingsViewModel: deleteCustomerContent failed for $id")
            }
        }
    }
}
