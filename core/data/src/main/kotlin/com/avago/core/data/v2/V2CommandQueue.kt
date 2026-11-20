package com.avago.core.data.v2

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The local half of the command outbox: queues commands (with dependencies, upload jobs and an
 * optimistic overlay) and answers queries over `pending_commands`. It needs no network, so feature
 * write paths use it directly; [V2CommandOutbox] adds the flush.
 */
class V2CommandQueue(
    private val store: V2Database,
    private val identity: V2StoreIdentity,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** `capabilities.commands`; when set, [enqueue] refuses other operations. */
    @Volatile var supportedOperations: Set<String>? = null

    suspend fun enqueue(command: V2NewCommand): String = store.write { tx -> insert(tx, command) }

    /**
     * Queues [build]'s commands in one transaction: either all of them are queued or none. [build]
     * gets the ids already queued in this batch, so later commands can depend on earlier ones.
     */
    suspend fun enqueueAll(build: (V2Connection) -> List<(List<String>) -> V2NewCommand>): List<String> = store.write { tx ->
        val ids = mutableListOf<String>()
        for (next in build(tx.connection)) ids += insert(tx, next(ids.toList()))
        ids
    }

    /**
     * Re-queues the refused command [id] as a new command based on [baseVersion] (the row's current
     * version), with the optimistic overlay it had when it was refused for a version conflict, then
     * queues [after] (which may depend on the resent command) and drops the refusal, all in one
     * transaction. Returns the new command ids (resent first).
     */
    suspend fun resendRejected(
        id: String,
        baseVersion: Long?,
        after: (resentId: String) -> List<V2NewCommand> = { emptyList() },
    ): List<String> = store.write { tx ->
        val row = tx.query(
            "SELECT operation, payload_schema_version, entity_type, entity_key, payload, last_error FROM pending_commands " +
                "WHERE id = ? AND state = 'rejected'",
            listOf(id.lowercase()),
        ).firstOrNull() ?: throw V2OutboxException.InvalidCommand("command $id is not refused")
        val error = (row["last_error"] as String?)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val resent = insert(
            tx,
            V2NewCommand(
                operation = row["operation"] as String,
                payload = Json.parseToJsonElement(row["payload"] as String).jsonObject,
                payloadSchemaVersion = (row["payload_schema_version"] as Number).toInt(),
                entityType = row["entity_type"] as String?,
                entityKey = row["entity_key"] as String?,
                baseVersion = baseVersion,
                overlayPatch = error?.get(ROLLED_BACK_OVERLAY) as? JsonObject,
                reducerVersion = V2OverlayPatch.REDUCER_VERSION,
            ),
        )
        val ids = listOf(resent) + after(resent).map { insert(tx, it) }
        tx.execute("DELETE FROM pending_commands WHERE id = ?", listOf(id.lowercase()))
        tx.markChanged("pending_commands")
        ids
    }

    private fun insert(tx: V2Transaction, command: V2NewCommand): String {
        supportedOperations?.let { if (command.operation !in it) throw V2OutboxException.UnsupportedOperation(command.operation) }
        if (!OPERATION.matches(command.operation)) throw V2OutboxException.InvalidCommand("bad operation ${command.operation}")
        val overlayEntity = command.overlayPatch?.let { overlayEntity(command, it) }
        val now = clock()
        val id = V2Uuid.v7(now)
        val hash = V2Canonical.requestHash(
            operation = command.operation,
            payloadSchemaVersion = command.payloadSchemaVersion,
            entityType = command.entityType,
            entityKey = command.entityKey,
            baseVersion = command.baseVersion?.toString(),
            payload = command.payload,
        )
        val order = tx.connection.scalarLong("SELECT COALESCE(MAX(causal_order), 0) + 1 FROM pending_commands") ?: 1L
        val epoch = tx.connection.scalarLong("SELECT scope_epoch FROM database_identity") ?: 0L
        tx.execute(
            "INSERT INTO pending_commands (id, user_id, account_id, scope_epoch, operation, payload_schema_version, " +
                "entity_type, entity_key, base_version, payload, request_hash, causal_order, state, attempts, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'queued', 0, ?)",
            listOf(
                id, identity.userId, identity.accountId, epoch, command.operation, command.payloadSchemaVersion,
                command.entityType, command.entityKey, command.baseVersion, V2Canonical.string(command.payload),
                hash, order, now,
            ),
        )
        for (dep in command.dependsOn.map { it.lowercase() }.distinct()) {
            if (!V2Uuid.isValid(dep)) throw V2OutboxException.InvalidCommand("bad dependency id $dep")
            // A retired dependency (acknowledged and applied) needs no local wait; the server still checks it.
            if (tx.connection.scalarLong("SELECT 1 FROM pending_commands WHERE id = ?", listOf(dep)) == null) continue
            tx.execute(
                "INSERT OR IGNORE INTO command_dependencies (command_id, depends_on_command_id) VALUES (?, ?)",
                listOf(id, dep),
            )
        }
        for (upload in command.uploads) {
            if (!V2Uuid.isValid(upload.fileId)) throw V2OutboxException.InvalidCommand("bad file id ${upload.fileId}")
            tx.execute(
                "INSERT INTO upload_jobs (id, command_id, local_path, sha256, byte_size, media_type, state, attempts, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'pending', 0, ?)",
                listOf(upload.fileId, id, upload.localPath, upload.sha256, upload.byteSize, upload.mediaType, now),
            )
        }
        val patch = command.overlayPatch
        if (patch != null) {
            tx.execute(
                "INSERT INTO local_overlays (id, entity_type, entity_key, kind, command_id, reducer_version, causal_order, " +
                    "patch, base_version, created_at, updated_at) VALUES (?, ?, ?, 'command', ?, ?, ?, ?, ?, ?, ?)",
                listOf(
                    V2Uuid.v7(now), overlayEntity!!.first, overlayEntity.second, id, command.reducerVersion, order,
                    V2Canonical.string(patch), command.baseVersion, now, now,
                ),
            )
            tx.markChanged("local_overlays")
        }
        tx.markChanged("pending_commands")
        return id
    }

    /**
     * The overlay's local entity: the command's own (`entity_type`, `entity_key`) or, for commands
     * that carry none (server-assigned ids, multi-row documents), the first row the patch touches.
     */
    private fun overlayEntity(command: V2NewCommand, patch: JsonObject): Pair<String, String> {
        if (command.entityType != null && command.entityKey != null) return command.entityType to command.entityKey
        val first = V2OverlayPatch.parse(patch)?.ops?.firstOrNull()
            ?: throw V2OutboxException.InvalidCommand("an overlay needs entity_type and entity_key or at least one row op")
        val key = first.key["id"]?.toString() ?: first.key.entries.joinToString("\u001f") { "${it.key}=${it.value}" }
        return first.table to key
    }

    // ---------------------------------------------------------------------------------------
    // Queries

    suspend fun pending(): List<V2PendingCommand> = store.read { conn ->
        conn.query(
            "SELECT id, operation, entity_type, entity_key, state, attempts, causal_order, result_watermark, " +
                "next_attempt_at, created_at FROM pending_commands " +
                "WHERE state IN ('queued', 'leased', 'sent_unknown', 'acknowledged') ORDER BY causal_order",
        ).mapNotNull(::pendingCommand)
    }

    suspend fun failures(): List<V2CommandFailure> = store.read { conn ->
        conn.query(
            "SELECT id, operation, entity_type, entity_key, last_error, created_at FROM pending_commands " +
                "WHERE state = 'rejected' ORDER BY causal_order",
        ).map(::failure)
    }

    /** A refused command with its payload (to resend it corrected). */
    suspend fun rejected(id: String): V2RejectedCommand? = store.read { conn ->
        conn.query(
            "SELECT id, operation, entity_type, entity_key, base_version, payload, last_error, created_at FROM pending_commands " +
                "WHERE id = ? AND state = 'rejected'",
            listOf(id.lowercase()),
        ).firstOrNull()?.let { r ->
            V2RejectedCommand(
                failure = failure(r),
                baseVersion = (r["base_version"] as Number?)?.toLong(),
                payload = Json.parseToJsonElement(r["payload"] as String).jsonObject,
            )
        }
    }

    /** Removes a surfaced rejection (and its overlays, by cascade). */
    suspend fun dismissFailure(id: String) = store.write { tx ->
        tx.execute("DELETE FROM pending_commands WHERE id = ? AND state IN ('rejected', 'cancelled')", listOf(id.lowercase()))
        tx.markChanged("pending_commands", "local_overlays")
    }

    /** Cancels a command that has never been sent. Returns false otherwise. */
    suspend fun cancel(id: String): Boolean = store.write { tx ->
        val lower = id.lowercase()
        val changed = tx.execute(
            "UPDATE pending_commands SET state = 'cancelled', next_attempt_at = NULL WHERE id = ? AND state = 'queued' AND attempts = 0",
            listOf(lower),
        )
        if (changed == 0L) return@write false
        tx.execute("DELETE FROM local_overlays WHERE command_id = ?", listOf(lower))
        rejectDependents(tx.connection, lower)
        tx.markChanged("pending_commands", "local_overlays")
        true
    }

    private fun pendingCommand(r: V2Row): V2PendingCommand? = V2PendingCommand(
        id = r["id"] as String,
        operation = r["operation"] as String,
        entityType = r["entity_type"] as String?,
        entityKey = r["entity_key"] as String?,
        state = V2CommandState.fromWire(r["state"] as String?) ?: return null,
        attempts = (r["attempts"] as Number).toInt(),
        causalOrder = (r["causal_order"] as Number).toLong(),
        resultWatermark = (r["result_watermark"] as Number?)?.toLong(),
        nextAttemptAt = (r["next_attempt_at"] as Number?)?.toLong(),
        createdAt = (r["created_at"] as Number).toLong(),
    )

    companion object {
        /** sync-wire.md: `operation` must match `^[a-z][a-z0-9_.]{2,80}$`. */
        val OPERATION = Regex("^[a-z][a-z0-9_.]{2,80}$")

        /**
         * `last_error` key holding the overlay a `version_conflict` refusal rolled back, so the user
         * can re-apply the edit ([resendRejected]) with its optimistic effect.
         */
        const val ROLLED_BACK_OVERLAY = "rolled_back_overlay"

        fun failure(r: V2Row): V2CommandFailure {
            val error = (r["last_error"] as String?)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            return V2CommandFailure(
                id = r["id"] as String,
                operation = r["operation"] as String,
                entityType = r["entity_type"] as String?,
                entityKey = r["entity_key"] as String?,
                code = error?.get("code")?.jsonPrimitive?.content ?: "rejected",
                message = error?.get("message")?.jsonPrimitive?.content,
                conflictId = error?.get("conflict_id")?.jsonPrimitive?.content,
                createdAt = (r["created_at"] as Number).toLong(),
            )
        }

        /** Unsent dependents can never succeed: reject them (transitively) locally. */
        internal fun rejectDependents(conn: V2Connection, id: String) {
            val error = V2Canonical.string(
                JsonObject(mapOf("code" to JsonPrimitive("dependency_rejected"), "message" to JsonPrimitive("Command $id was rejected"))),
            )
            val dependents = conn.query(
                "WITH RECURSIVE deps(id) AS (SELECT command_id FROM command_dependencies WHERE depends_on_command_id = ? " +
                    "UNION SELECT d.command_id FROM command_dependencies d JOIN deps ON d.depends_on_command_id = deps.id) " +
                    "SELECT c.id FROM pending_commands c JOIN deps ON deps.id = c.id WHERE c.state = 'queued'",
                listOf(id),
            ).map { it["id"] as String }
            for (dependent in dependents) {
                conn.execute(
                    "UPDATE pending_commands SET state = 'rejected', next_attempt_at = NULL, last_error = ? WHERE id = ?",
                    listOf(error, dependent),
                )
                conn.execute("DELETE FROM local_overlays WHERE command_id = ?", listOf(dependent))
            }
        }
    }
}

/** A refused command: why it was refused and what was sent. */
data class V2RejectedCommand(
    val failure: V2CommandFailure,
    val baseVersion: Long?,
    val payload: JsonObject,
)
