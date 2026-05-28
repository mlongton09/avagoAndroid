package com.avago.core.auth

import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.UpdatePreferencesRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-device preference synchronisation.
 *
 * The web app and iOS both edit the same preference set. This class keeps Android
 * in sync by:
 *
 *   1. **Pull** — [refreshFromServer] fetches GET /accounts/:id/preferences/me and
 *      mirrors the result into [prefs]. Called at app launch and after each sync cycle.
 *
 *   2. **Push** — [save] writes a partial patch to the server and on success updates
 *      [prefs] and emits [prefsChanged] so UI observes the change immediately.
 *
 * "Last write wins" — no CRDT / version column. Acceptable for user settings.
 *
 * Mirrors iOS PreferencesSync.
 */
@Singleton
class AccountPreferencesSync @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) {
    data class UserPrefs(
        val distanceUnit: String = "miles",
        val currency: String = "USD",
        val locale: String? = null,
        val localeIsUserOverride: Boolean = false,
        val disableQuotes: Boolean = false,
        val enableHumanInLoop: Boolean = true,
        val fuelVolumeUnit: String = "gallon",
    )

    private val _prefs = MutableStateFlow(UserPrefs())
    val prefs: StateFlow<UserPrefs> = _prefs.asStateFlow()

    private val _prefsChanged = MutableStateFlow(0L)
    val prefsChanged: StateFlow<Long> = _prefsChanged.asStateFlow()

    /**
     * Pull effective preferences from the server and update [prefs].
     * No-op if no active account.
     *
     * @return true on success, false on network/server error.
     */
    suspend fun refreshFromServer(): Boolean {
        val accountId = identityManager.activeAccountId.value ?: return false
        return when (val result = serviceClient.getMyPreferences(accountId)) {
            is NetworkResult.Success -> {
                val r = result.data
                _prefs.value = UserPrefs(
                    distanceUnit = r.distance_unit ?: _prefs.value.distanceUnit,
                    currency = r.currency ?: _prefs.value.currency,
                    locale = r.locale ?: _prefs.value.locale,
                    disableQuotes = r.disable_quotes ?: _prefs.value.disableQuotes,
                    enableHumanInLoop = _prefs.value.enableHumanInLoop,
                    fuelVolumeUnit = _prefs.value.fuelVolumeUnit,
                )
                _prefsChanged.value = System.currentTimeMillis()
                Timber.d("[AccountPreferencesSync] refreshed")
                true
            }
            is NetworkResult.Error -> {
                Timber.w("[AccountPreferencesSync] refresh failed HTTP ${result.code}: ${result.message}")
                false
            }
            is NetworkResult.Unauthorized -> false
        }
    }

    /**
     * Push a partial preference patch to the server.
     * On success, [prefs] is updated and [prefsChanged] emits.
     *
     * @return true if the server accepted the change.
     */
    suspend fun save(patch: UpdatePreferencesRequest): Boolean {
        val accountId = identityManager.activeAccountId.value ?: return false
        return when (val result = serviceClient.updateMyPreferences(accountId, patch)) {
            is NetworkResult.Success -> {
                // Server returns Unit — apply patch values directly to local state.
                _prefs.value = _prefs.value.copy(
                    distanceUnit = patch.distance_unit ?: _prefs.value.distanceUnit,
                    currency = patch.currency ?: _prefs.value.currency,
                    locale = patch.locale ?: _prefs.value.locale,
                    disableQuotes = patch.disable_quotes ?: _prefs.value.disableQuotes,
                    enableHumanInLoop = patch.enable_human_in_loop ?: _prefs.value.enableHumanInLoop,
                    fuelVolumeUnit = patch.fuel_volume_unit ?: _prefs.value.fuelVolumeUnit,
                )
                _prefsChanged.value = System.currentTimeMillis()
                true
            }
            is NetworkResult.Error -> {
                Timber.w("[AccountPreferencesSync] save failed HTTP ${result.code}: ${result.message}")
                false
            }
            is NetworkResult.Unauthorized -> false
        }
    }
}
