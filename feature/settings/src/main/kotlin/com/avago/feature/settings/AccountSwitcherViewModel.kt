package com.avago.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.AccountManifest
import com.avago.core.auth.AccountRecord
import com.avago.core.auth.IdentityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the account-switcher panel in the side drawer.
 *
 * Exposes the list of all known accounts plus the currently active account ID.
 * All mutations are delegated to [IdentityManager] so that the single source of
 * truth stays in the auth layer.
 */
@HiltViewModel
class AccountSwitcherViewModel @Inject constructor(
    private val identity: IdentityManager,
    private val manifest: AccountManifest,
) : ViewModel() {

    // Backing state refreshed on every mutation so the UI stays in sync.
    private val _accounts = MutableStateFlow<List<AccountRecord>>(emptyList())
    val accounts: StateFlow<List<AccountRecord>> = _accounts.asStateFlow()

    /** Mirrors [IdentityManager.activeAccountId] directly. */
    val activeAccountId: StateFlow<String?> = identity.activeAccountId

    init {
        refreshAccounts()
        viewModelScope.launch {
            identity.activeAccountId.collect { refreshAccounts() }
        }
        viewModelScope.launch {
            identity.accountsChanged.collect { refreshAccounts() }
        }
    }

    // ---------------------------------------------------------------------------
    // Public actions
    // ---------------------------------------------------------------------------

    fun switchTo(accountId: String) {
        viewModelScope.launch {
            try {
                identity.switchAccount(accountId)
                refreshAccounts()
            } catch (e: Exception) {
                Timber.e(e, "AccountSwitcherViewModel: switchTo($accountId) failed")
            }
        }
    }

    fun signOut(accountId: String) {
        viewModelScope.launch {
            try {
                identity.signOut(accountId)
                refreshAccounts()
            } catch (e: Exception) {
                Timber.e(e, "AccountSwitcherViewModel: signOut($accountId) failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun refreshAccounts() {
        _accounts.value = manifest.allAccounts()
    }
}
