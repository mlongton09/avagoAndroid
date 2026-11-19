package com.avago.core.data.v2

import com.avago.core.data.domain.ChangeQueueRepository
import com.avago.core.data.domain.FailedChange
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.domain.SyncConflict
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

    /** The signed-in user's id in the active store (for `created_by`-style overlay fields). */
    fun currentUserId(): String? = active.current.value?.identity?.userId
}

@Singleton
class V2ChangeQueueRepository @Inject constructor(
    private val store: V2StoreAccess,
) : ChangeQueueRepository {

    override fun observePendingCount(): Flow<Int> = store.observe(setOf("pending_commands"), 0) { conn ->
        (
            conn.scalarLong(
                "SELECT COUNT(*) FROM pending_commands WHERE state IN ('queued', 'leased', 'sent_unknown')",
            ) ?: 0L
            ).toInt()
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
        store.observe(setOf("sync_conflicts"), emptyList()) { conn ->
            conn.query("SELECT * FROM sync_conflicts WHERE status = 'open' ORDER BY id DESC").map { r ->
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
                )
            }
        }

    override suspend fun dismiss(commandId: String) {
        val stores = store.current() ?: return
        V2CommandQueue(stores.business, stores.identity).dismissFailure(commandId)
    }
}
