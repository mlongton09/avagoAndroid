package com.avago.core.sync

sealed class SyncState {
    object Idle : SyncState()
    object Pushing : SyncState()
    object Pulling : SyncState()
    data class Error(val message: String) : SyncState()
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Partial(val pushedCount: Int, val pulledCount: Int) : SyncResult()
    data class Failed(val error: Throwable) : SyncResult()
}
