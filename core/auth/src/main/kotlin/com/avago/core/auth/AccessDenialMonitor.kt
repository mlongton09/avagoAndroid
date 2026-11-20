package com.avago.core.auth

import com.avago.core.network.AccessDenial
import com.avago.core.network.AccessDenialReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the typed `403` (P4-01b) the server last gave the active account, from any REST or
 * `/v2` call ([AccessDenialReporter], fed by the authenticated client) and from sync. While the
 * active account is denied, sync and background work stand down instead of retrying, and the app
 * asks the user to switch workspace or sign out. Cleared when the user acts (switch, sign-in,
 * sign-out, [clear] to try again).
 */
@Singleton
class AccessDenialMonitor @Inject constructor(
    private val activeAccountId: StateFlow<String?>,
) : AccessDenialReporter {

    data class Denied(val accountId: String, val denial: AccessDenial)

    private val _denied = MutableStateFlow<Denied?>(null)

    /** The denial of the active account, or null while it may be used. */
    val denied: Flow<Denied?> = combine(_denied, activeAccountId) { denied, active -> denied?.takeIf { it.accountId == active } }
        .distinctUntilChanged()

    /**
     * Records [denial] against the active account. A membership denial for another account (one
     * named in the request path) says nothing about the active one and is ignored; a user denial
     * always applies, since the token's user is the active user.
     */
    override fun onAccessDenied(denial: AccessDenial, accountId: String?) {
        val active = activeAccountId.value ?: return
        if (!denial.requiresSignOut && accountId != null && accountId != active) return
        _denied.value = Denied(active, denial)
    }

    /** The denial recorded for [accountId], if it is the one last denied. */
    fun deniedFor(accountId: String?): AccessDenial? = _denied.value?.takeIf { accountId != null && it.accountId == accountId }?.denial

    fun clear() {
        _denied.value = null
    }
}
