package com.avago.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.AccessDenialMonitor
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives [AccessDeniedDialog]: the typed `403` (P4-01b) recorded for the active account and the
 * ways out of it. Switching workspace happens on the account switcher; signing out and trying
 * again happen here.
 */
@HiltViewModel
class AccessDeniedViewModel @Inject constructor(
    private val monitor: AccessDenialMonitor,
    private val identityManager: IdentityManager,
    private val sync: SyncRepository,
) : ViewModel() {

    val denied: StateFlow<AccessDenialMonitor.Denied?> =
        monitor.denied.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Asks the server again (an admin may have restored access); a new denial shows the dialog again. */
    fun tryAgain() {
        monitor.clear()
        viewModelScope.launch { sync.syncNow() }
    }

    /** Signs out of the denied workspace, or of every account of a denied user. */
    fun signOut() {
        val current = denied.value ?: return
        viewModelScope.launch {
            try {
                if (current.denial.requiresSignOut) {
                    identityManager.signOutUserOf(current.accountId)
                } else {
                    identityManager.signOut(current.accountId)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "AccessDeniedViewModel: sign-out of ${current.accountId} failed")
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
