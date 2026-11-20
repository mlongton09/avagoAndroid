package com.avago.core.sync.v2

import com.avago.core.data.v2.V2Stores
import com.avago.core.data.v2.V2SyncResult

/**
 * Told about every successful v2 pull, after its rows are committed to [V2Stores]. Bound into a
 * Hilt set; [V2SyncCoordinator] calls each listener in turn and a failing listener does not
 * affect the sync or the other listeners.
 */
fun interface V2PullListener {
    suspend fun onPulled(stores: V2Stores, result: V2SyncResult.Synced)
}
