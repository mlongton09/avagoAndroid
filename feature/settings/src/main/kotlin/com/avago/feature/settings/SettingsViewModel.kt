package com.avago.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives [SettingsScreen].
 *
 * Owns all user-preference state (theme, distance unit) and delegates account
 * mutations to [IdentityManager].  All state is exposed as [StateFlow] so that
 * Compose can collect it safely across recompositions.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val identity: IdentityManager,
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

    val language: StateFlow<String> = prefs.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )

    // ── Identity state ────────────────────────────────────────────────────────

    /** Mirrors IdentityManager — null when no account is active. */
    val activeAccountId: StateFlow<String?> = identity.activeAccountId

    // ── Actions ───────────────────────────────────────────────────────────────

    fun setTheme(value: String) {
        viewModelScope.launch { prefs.setTheme(value) }
    }

    fun setDistanceUnit(value: String) {
        viewModelScope.launch { prefs.setDistanceUnit(value) }
    }

    fun setLanguage(value: String) {
        viewModelScope.launch { prefs.setLanguage(value) }
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

    fun deleteAccount() {
        viewModelScope.launch {
            val id = activeAccountId.value ?: return@launch
            try {
                // Stub: full delete-account API call deferred.
                Timber.w("SettingsViewModel: deleteAccount requested for $id — stub, signing out only")
                identity.signOut(id)
            } catch (e: Exception) {
                Timber.e(e, "SettingsViewModel: deleteAccount failed for $id")
            }
        }
    }
}
