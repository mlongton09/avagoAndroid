package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow

/** A local change the server refused; its optimistic effect has already been rolled back. */
data class FailedChange(
    val commandId: String,
    val operation: String,
    val entityType: String?,
    val entityKey: String?,
    val code: String,
    val message: String?,
    /** Set for `version_conflict`: the matching [SyncConflict] arrives with the next sync. */
    val conflictId: String?,
    val createdAt: Long,
) {
    val isConflict: Boolean get() = code == "version_conflict"
}

/** A server-recorded edit conflict (v2 `sync_conflicts`, visible only to its author). */
data class SyncConflict(
    val id: String,
    val commandId: String,
    val entityType: String,
    val entityKey: String,
    val baseVersion: Long?,
    val serverVersion: Long?,
    /** The rejected edit as sent (JSON). */
    val clientPayload: String,
    /** The row as the server had it (JSON), when available. */
    val serverSnapshot: String?,
    val status: String,
    /** Row version of the conflict itself (the resolve command's base version). */
    val version: Long,
)

/** How the user settles an edit conflict (`sync.sync_conflicts.resolve`). */
enum class ConflictResolution {
    /** Re-apply the refused edit on top of the current server row (`resolved_client`). */
    KEEP_MINE,

    /** Keep the server row and drop the edit (`resolved_server`). */
    KEEP_SERVER,

    /** Close the conflict without deciding (`dismissed`). */
    DISMISS,
}

/** Sync state of one record's local edits. */
enum class RecordSyncState { SAVED, PENDING, FAILED }

/** Local write queue state for the UI: pending count, failed changes and open conflicts. */
interface ChangeQueueRepository {
    fun observePendingCount(): Flow<Int>

    /**
     * Whether edits of one record (wire entity type, e.g. `activity_records`, and its key) are still
     * queued, were refused (and rolled back), or are all committed.
     */
    fun observeRecordState(entityType: String, entityKey: String): Flow<RecordSyncState>

    fun observeFailures(): Flow<List<FailedChange>>

    fun observeOpenConflicts(): Flow<List<SyncConflict>>

    /** Removes a failed change from the list (its effect is already rolled back). */
    suspend fun dismiss(commandId: String)

    /**
     * Settles [conflict] on the server and drops the local refusal [failedCommandId] (when the
     * refusal is still on this device). [ConflictResolution.KEEP_MINE] needs it: the refused edit
     * is queued again against the record's current version, with its optimistic effect, and the
     * conflict is resolved once that edit commits.
     */
    suspend fun resolveConflict(conflict: SyncConflict, failedCommandId: String?, resolution: ConflictResolution)
}

/** Thrown by write methods when nobody is signed in or the local store is not open yet. */
class StoreNotReadyException : IllegalStateException("The local store is not open yet")

/**
 * Thrown by update methods when an edit empties a field the server cannot clear (the catalog
 * marks which fields are clearable). Nothing is queued; [fields] are the wire field names.
 */
class FieldNotClearableException(val fields: List<String>) :
    IllegalArgumentException("These fields cannot be cleared: ${fields.joinToString()}")

/** Thrown when an edit is saved for a row that no longer exists locally. */
class RecordNotFoundException(what: String, id: String) : NoSuchElementException("$what $id not found")

class NoMeterException(assetId: String) : IllegalStateException("Asset $assetId has no meter to record a reading on")
