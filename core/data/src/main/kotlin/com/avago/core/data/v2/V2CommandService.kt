package com.avago.core.data.v2

import com.avago.core.data.domain.ChangeQueueRepository
import com.avago.core.data.domain.ConflictResolution
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.FailedChange
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.domain.SyncConflict
import com.avago.core.data.v2.commands.SyncConflictStatus
import com.avago.core.data.v2.commands.SyncSyncConflictsResolve
import com.avago.core.data.v2.commands.toNewCommand
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Asks the sync layer (core/sync) to send queued commands soon. */
fun interface V2FlushRequester {
    fun requestFlush()
}

/**
 * The write entry point of the feature repositories: queues a typed command (with its optimistic
 * overlay) in the active account's outbox and nudges a flush. The outbox applies the overlay at
 * once, removes it when the command is rejected (rollback) and retires it once the committed
 * effect has arrived through sync.
 */
@Singleton
class V2CommandService @Inject constructor(
    private val active: V2ActiveStores,
    private val flusher: V2FlushRequester,
) {
    /** Queues [command]; returns its command id. Throws [StoreNotReadyException] before sign-in. */
    suspend fun submit(command: V2NewCommand): String {
        val stores = active.current.value ?: throw StoreNotReadyException()
        val id = V2CommandQueue(stores.business, stores.identity).enqueue(command)
        flusher.requestFlush()
        return id
    }

    /** A refused command of the active account (null when it is not refused / gone). */
    suspend fun rejected(id: String): V2RejectedCommand? {
        val stores = active.current.value ?: throw StoreNotReadyException()
        return V2CommandQueue(stores.business, stores.identity).rejected(id)
    }

    /** Drops a refused command (its overlay is already rolled back). */
    suspend fun dismiss(id: String) {
        val stores = active.current.value ?: throw StoreNotReadyException()
        V2CommandQueue(stores.business, stores.identity).dismissFailure(id)
    }

    /** The signed-in user's id in the active store (for `created_by`-style overlay fields). */
    fun currentUserId(): String? = active.current.value?.identity?.userId
}

@Singleton
class V2ChangeQueueRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val flusher: V2FlushRequester,
) : ChangeQueueRepository {

    override fun observePendingCount(): Flow<Int> = store.observe(setOf("pending_commands"), 0) { conn ->
        (
            conn.scalarLong(
                "SELECT COUNT(*) FROM pending_commands WHERE state IN (${PENDING_STATES.joinToString { "'$it'" }})",
            ) ?: 0L
            ).toInt()
    }

    override fun observeRecordState(entityType: String, entityKey: String): Flow<RecordSyncState> =
        store.observe(setOf("pending_commands"), RecordSyncState.SAVED) { conn ->
            val states = conn.query(
                "SELECT state FROM pending_commands WHERE entity_type = ? AND entity_key = ?",
                listOf(entityType, entityKey),
            ).map { it.str("state") }
            when {
                states.any { it == "rejected" } -> RecordSyncState.FAILED
                states.any { it in PENDING_STATES } -> RecordSyncState.PENDING
                else -> RecordSyncState.SAVED
            }
        }

    override fun observeFailures(): Flow<List<FailedChange>> = store.observe(setOf("pending_commands"), emptyList()) { conn ->
        conn.query(
            "SELECT id, operation, entity_type, entity_key, last_error, created_at FROM pending_commands " +
                "WHERE state = 'rejected' ORDER BY causal_order DESC",
        ).map { r ->
            val failure = V2CommandQueue.failure(r)
            FailedChange(
                commandId = failure.id,
                operation = failure.operation,
                entityType = failure.entityType,
                entityKey = failure.entityKey,
                code = failure.code,
                message = failure.message,
                conflictId = failure.conflictId,
                createdAt = failure.createdAt,
            )
        }
    }

    override fun observeOpenConflicts(): Flow<List<SyncConflict>> =
        store.observe(setOf("sync_conflicts", "local_overlays"), emptyList()) { conn ->
            V2OverlayMerge.query(conn, "sync_conflicts", "status = 'open'") { it["status"] == "open" }
                .sortedByDescending { it.str("id") }
                .map { r ->
                    SyncConflict(
                        id = r.str("id"),
                        commandId = r.str("command_id"),
                        entityType = r.str("entity_type"),
                        entityKey = r.str("entity_key"),
                        baseVersion = r.longOrNull("base_version"),
                        serverVersion = r.longOrNull("server_version"),
                        clientPayload = r.str("client_payload"),
                        serverSnapshot = r.strOrNull("server_snapshot"),
                        status = r.str("status"),
                        version = r.long("version"),
                    )
                }
        }

    override suspend fun dismiss(commandId: String) {
        val stores = store.current() ?: return
        V2CommandQueue(stores.business, stores.identity).dismissFailure(commandId)
    }

    override suspend fun resolveConflict(conflict: SyncConflict, failedCommandId: String?, resolution: ConflictResolution) {
        val stores = store.current() ?: throw StoreNotReadyException()
        val queue = V2CommandQueue(stores.business, stores.identity)
        when (resolution) {
            ConflictResolution.KEEP_MINE -> {
                requireNotNull(failedCommandId) { "re-applying an edit needs the refused command on this device" }
                val current = stores.business.read { conn -> currentVersion(conn, conflict.entityType, conflict.entityKey) }
                    ?: throw RecordNotFoundException(conflict.entityType, conflict.entityKey)
                queue.resendRejected(failedCommandId, current) { resent ->
                    listOf(resolveCommand(conflict, SyncConflictStatus.RESOLVED_CLIENT, stores.identity.userId, listOf(resent)))
                }
            }
            ConflictResolution.KEEP_SERVER, ConflictResolution.DISMISS -> {
                val status = if (resolution == ConflictResolution.KEEP_SERVER) {
                    SyncConflictStatus.RESOLVED_SERVER
                } else {
                    SyncConflictStatus.DISMISSED
                }
                queue.enqueueAll { listOf({ _ -> resolveCommand(conflict, status, stores.identity.userId, emptyList()) }) }
                failedCommandId?.let { queue.dismissFailure(it) }
            }
        }
        flusher.requestFlush()
    }

    internal companion object {
        val PENDING_STATES = listOf("queued", "leased", "sent_unknown")

        /** The resolve command; its overlay closes the conflict locally at once. */
        fun resolveCommand(conflict: SyncConflict, status: SyncConflictStatus, userId: String, dependsOn: List<String>): V2NewCommand =
            SyncSyncConflictsResolve(id = conflict.id, status = status).toNewCommand(
                baseVersion = conflict.version,
                overlay = V2OverlayPatch.of(
                    V2RowOp.upsert(
                        "sync_conflicts",
                        conflict.id,
                        mapOf(
                            "status" to status.wire,
                            "resolved_by_user_id" to userId,
                            "resolved_at" to System.currentTimeMillis(),
                        ),
                    ),
                ),
                dependsOn = dependsOn,
            )

        /** The synced version of the row an `entity_type` / `entity_key` names (null when it is gone). */
        fun currentVersion(conn: V2Connection, entityType: String, entityKey: String): Long? {
            val projection = V2GeneratedSchema.projectionsByType[entityType] ?: return null
            val table = projection.mobileTable ?: return null
            val parts = V2EntityKey.split(entityKey)
            if (parts.size != projection.keyColumns.size) return null
            return conn.scalarLong(
                "SELECT version FROM $table WHERE " + projection.keyColumns.joinToString(" AND ") { "$it = ?" },
                parts,
            )
        }
    }
}
