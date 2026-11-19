package com.avago.core.sync

import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.scalarText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open once the signed-in account's v2 store holds a completed bootstrap, so the UI does not show
 * empty screens before the first pull. Closes again while a re-bootstrap (reset, scope change,
 * [SyncEngine.resync]) rebuilds the store.
 */
@Singleton
class SyncGate @Inject constructor(
    store: V2StoreAccess,
    @ApplicationScope scope: CoroutineScope,
) {
    val isOpen: StateFlow<Boolean> = store.observe(setOf(SYNC_STATE), false) { conn ->
        conn.scalarText("SELECT bootstrap_state FROM sync_state WHERE stream = 'account'") == BOOTSTRAP_COMPLETE
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private companion object {
        const val SYNC_STATE = "sync_state"
        const val BOOTSTRAP_COMPLETE = "complete"
    }
}
