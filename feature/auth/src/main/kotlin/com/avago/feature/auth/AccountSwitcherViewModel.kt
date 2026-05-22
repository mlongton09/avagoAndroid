package com.avago.feature.auth

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

@HiltViewModel
class AccountSwitcherViewModel @Inject constructor(
    private val identityManager: IdentityManager,
    private val accountManifest: AccountManifest,
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<AccountRecord>>(emptyList())
    val accounts: StateFlow<List<AccountRecord>> = _accounts.asStateFlow()

    val activeAccountId: StateFlow<String?> = identityManager.activeAccountId

    init {
        _accounts.value = accountManifest.allAccounts()
    }

    fun switchTo(accountId: String) {
        viewModelScope.launch {
            try {
                identityManager.switchAccount(accountId)
                _accounts.value = accountManifest.allAccounts()
            } catch (e: Exception) {
                Timber.e(e, "AccountSwitcherViewModel: failed to switch to $accountId")
            }
        }
    }
}
