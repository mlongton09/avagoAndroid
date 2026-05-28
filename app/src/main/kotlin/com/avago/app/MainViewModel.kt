package com.avago.app

import androidx.lifecycle.ViewModel
import com.avago.core.auth.IdentityManager
import com.avago.core.sync.ConnectivityMonitor
import com.avago.core.ui.AvagoToast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor,
    private val identityManager: IdentityManager,
    val toast: AvagoToast,
) : ViewModel() {

    val isOffline: StateFlow<Boolean> = connectivityMonitor.networkStatus
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Reflects [IdentityManager.activeAccountId]. Null means the user is not signed in.
     * Observed by [AvagoNavHost] to decide the start destination and to react to sign-out.
     */
    val activeAccountId: StateFlow<String?> = identityManager.activeAccountId
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.getActiveAccountId())

    /** True when the active account is an anonymous/guest session. */
    val activeAccountIsAnonymous: StateFlow<Boolean> = identityManager.activeAccountIsAnonymous
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
