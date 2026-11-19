package com.avago.core.data.domain

/** On-demand sync for pull-to-refresh: pull server changes, then send queued changes. */
interface SyncRepository {
    /** Runs one cycle now; false when it could not complete (offline, server error, signed out). */
    suspend fun syncNow(): Boolean

    /**
     * Drops the local copy of the account's data and downloads it again. Changes not yet sent
     * are kept and sent afterwards. False when the download could not complete.
     */
    suspend fun resync(): Boolean
}
