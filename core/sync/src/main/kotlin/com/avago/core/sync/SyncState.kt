package com.avago.core.sync

sealed class SyncState {
    object Idle : SyncState()
    object Pulling : SyncState()
    data class Error(val message: String) : SyncState()
}

sealed class SyncResult {
    /** The pull completed (queued commands were flushed after it). */
    object Success : SyncResult()

    /** Nothing to retry: v2 inactive, inside a server retry window, or a terminal server answer. */
    data class Partial(val pushedCount: Int, val pulledCount: Int) : SyncResult()

    /** Retryable failure (offline, server error, local apply failure). */
    data class Failed(val error: Throwable) : SyncResult()
}
