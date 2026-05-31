package com.avago.app

import androidx.lifecycle.ViewModel
import com.avago.core.auth.IdentityManager
import com.avago.core.sync.ConnectivityMonitor
import com.avago.core.ui.AvagoToast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
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

    // ─────────────────────────────────────────────────────────────────────
    // Deep-link routing (push-notification taps)
    // ─────────────────────────────────────────────────────────────────────
    // AvagoFcmService builds a PendingIntent that launches MainActivity
    // with `thread_id` as an extra. MainActivity forwards it here on
    // create + onNewIntent; AvagoNavHost observes [pendingNavRoute] and
    // navigates, then clears it so a config change doesn't replay the
    // navigation. A Channel with `CONFLATED` capacity gives us "deliver
    // exactly once even if the consumer is slow", which matches the
    // tap-once-navigate-once intent semantics.
    // ─────────────────────────────────────────────────────────────────────
    private val pendingNavChannel = Channel<String>(capacity = Channel.CONFLATED)
    val pendingNavRoute = pendingNavChannel.consumeAsFlow()

    fun openChatThread(threadId: String) {
        // Use ChatRoute string format directly to avoid a circular
        // dependency on :feature:chat from :app/MainViewModel.
        pendingNavChannel.trySend("chat/thread/$threadId")
    }
}
