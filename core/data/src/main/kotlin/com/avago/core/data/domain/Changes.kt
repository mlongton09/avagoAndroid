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
)

/** Local write queue state for the UI: pending count, failed changes and open conflicts. */
interface ChangeQueueRepository {
    fun observePendingCount(): Flow<Int>

    fun observeFailures(): Flow<List<FailedChange>>

    fun observeOpenConflicts(): Flow<List<SyncConflict>>

    /** Removes a failed change from the list (its effect is already rolled back). */
    suspend fun dismiss(commandId: String)
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
