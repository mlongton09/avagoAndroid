package com.avago.core.data.v2

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A file a command needs uploaded and finalized before it may be sent (`upload_jobs`, E12). */
data class V2NewUpload(val localPath: String, val sha256: String, val byteSize: Long, val mediaType: String)

/** A local write to queue (sync-protocol §8). */
data class V2NewCommand(
    val operation: String,
    val payload: JsonElement,
    val payloadSchemaVersion: Int = 1,
    val entityType: String? = null,
    val entityKey: String? = null,
    /** The `version` the edit was based on; null for creates. */
    val baseVersion: Long? = null,
    /** Earlier command ids this one needs (sent as `depends_on`). */
    val dependsOn: List<String> = emptyList(),
    /** Optional optimistic patch shown over the base row until the command retires. */
    val overlayPatch: JsonObject? = null,
    val reducerVersion: Int = 1,
    /** Files that must reach `finalized` before the command is sent. */
    val uploads: List<V2NewUpload> = emptyList(),
)

enum class V2CommandState(val wire: String) {
    QUEUED("queued"),
    LEASED("leased"),
    SENT_UNKNOWN("sent_unknown"),
    ACKNOWLEDGED("acknowledged"),
    REJECTED("rejected"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromWire(raw: String?): V2CommandState? = entries.firstOrNull { it.wire == raw }
    }
}

data class V2PendingCommand(
    val id: String,
    val operation: String,
    val entityType: String?,
    val entityKey: String?,
    val state: V2CommandState,
    val attempts: Int,
    val causalOrder: Long,
    val resultWatermark: Long?,
    val nextAttemptAt: Long?,
    val createdAt: Long,
)

/** A final rejection kept for the UI until dismissed (sync-wire.md: "Surface `message`"). */
data class V2CommandFailure(
    val id: String,
    val operation: String,
    val entityType: String?,
    val entityKey: String?,
    val code: String,
    val message: String?,
    val conflictId: String?,
    val createdAt: Long,
)

data class V2FlushResult(
    val acknowledged: MutableList<String> = mutableListOf(),
    val rejected: MutableList<String> = mutableListOf(),
    /** Commands left for a later flush (backoff, dependencies, uploads, per-entity order). */
    var deferred: Int = 0,
    /** The flush stopped early because the network is down. */
    var offline: Boolean = false,
)

sealed class V2OutboxException(message: String) : Exception(message) {
    class UnsupportedOperation(operation: String) : V2OutboxException("operation $operation is not supported by the server")
    class InvalidCommand(message: String) : V2OutboxException(message)
    class UpgradeRequired : V2OutboxException("426 upgrade_required")
    class DeviceRevoked : V2OutboxException("401 device_revoked")
}

/** Upload seam for commands with `upload_jobs` (E12 endpoints are not on the server yet). */
fun interface V2Uploader {
    /** Moves the job forward; returns its new `upload_jobs.state`. */
    suspend fun process(jobId: String, localPath: String, sha256: String, byteSize: Long, mediaType: String): String
}

/**
 * Persistent command outbox over `pending_commands` (sync-protocol §8; port of the iOS
 * `V2CommandOutbox`):
 *
 * - [enqueue] stores the command with a UUIDv7 id (the idempotency key), a `causal_order`, the
 *   `request_hash`, its dependency edges, optional upload jobs and an optional optimistic overlay,
 *   in one transaction.
 * - [flush] replays commands in causal order, one at a time. A command waits while an earlier
 *   command for the same `(entity_type, entity_key)` is unresolved, while a dependency is
 *   unacknowledged, or while one of its uploads is not `finalized`. A replay always reuses the
 *   stored id and body, so the server's stored outcome makes it idempotent.
 * - Commands whose outcome is unknown (`sent_unknown`: network loss, 5xx, crash while leased) are
 *   resolved with `GET …/commands/{id}` first.
 * - `committed` → `acknowledged`, kept until an applied sync covers `result_watermark` (the sync
 *   engine retires it); `rejected` and final 4xx → `rejected` with the stored error, overlays
 *   dropped and queued dependents rejected with `dependency_rejected`.
 *
 * Not reentrant: the coordinator serialises [flush] with sync.
 */
class V2CommandOutbox(
    private val store: V2Database,
    private val api: V2Api,
    private val identity: V2StoreIdentity,
    private val clock: () -> Long = System::currentTimeMillis,
    private val leaseOwner: String = V2Uuid.v7(),
    private val uploader: V2Uploader? = null,
) {
    /** `capabilities.commands`; when set, [enqueue] refuses other operations. */
    @Volatile var supportedOperations: Set<String>? = null
    var leaseDurationMs: Long = 60_000
    var maxBackoffMs: Long = 300_000
    var receiptRetryMs: Long = 2_000

    // ---------------------------------------------------------------------------------------
    // Enqueue

    suspend fun enqueue(command: V2NewCommand): String {
        supportedOperations?.let { if (command.operation !in it) throw V2OutboxException.UnsupportedOperation(command.operation) }
        if (!OPERATION.matches(command.operation)) throw V2OutboxException.InvalidCommand("bad operation ${command.operation}")
        if (command.overlayPatch != null && (command.entityType == null || command.entityKey == null)) {
            throw V2OutboxException.InvalidCommand("an overlay needs entity_type and entity_key")
        }
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
        store.write { tx ->
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
                tx.execute(
                    "INSERT INTO upload_jobs (id, command_id, local_path, sha256, byte_size, media_type, state, attempts, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'pending', 0, ?)",
                    listOf(V2Uuid.v7(now), id, upload.localPath, upload.sha256, upload.byteSize, upload.mediaType, now),
                )
            }
            val patch = command.overlayPatch
            if (patch != null) {
                tx.execute(
                    "INSERT INTO local_overlays (id, entity_type, entity_key, kind, command_id, reducer_version, causal_order, " +
                        "patch, base_version, created_at, updated_at) VALUES (?, ?, ?, 'command', ?, ?, ?, ?, ?, ?, ?)",
                    listOf(
                        V2Uuid.v7(now), command.entityType, command.entityKey, id, command.reducerVersion, order,
                        V2Canonical.string(patch), command.baseVersion, now, now,
                    ),
                )
                tx.markChanged("local_overlays")
            }
            tx.markChanged("pending_commands")
        }
        return id
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

    // ---------------------------------------------------------------------------------------
    // Flush

    private data class Row(
        val id: String,
        val operation: String,
        val payloadSchemaVersion: Int,
        val entityType: String?,
        val entityKey: String?,
        val baseVersion: Long?,
        val payload: String,
        val state: V2CommandState,
        val attempts: Int,
        val nextAttemptAt: Long?,
        val createdAt: Long,
    ) {
        val entity: String? get() = if (entityType != null && entityKey != null) "$entityType\u001f$entityKey" else null
    }

    private enum class Step { DONE, DEFERRED, OFFLINE }

    private sealed interface Gate {
        data object Ready : Gate
        data object Waiting : Gate
        data class Rejected(val dependencyId: String) : Gate
    }

    /**
     * Sends every ready command once. Throws [V2OutboxException.UpgradeRequired] /
     * [V2OutboxException.DeviceRevoked]; other failures are recorded on the commands.
     */
    suspend fun flush(): V2FlushResult {
        recoverStaleLeases()
        val result = V2FlushResult()
        val blocked = mutableSetOf<String>()
        for (row in loadActive()) {
            val entity = row.entity
            if (entity != null && entity in blocked) {
                result.deferred++
                continue
            }
            val step = if ((row.nextAttemptAt ?: 0L) > clock()) {
                Step.DEFERRED
            } else {
                when (val gate = gate(row.id)) {
                    is Gate.Rejected -> {
                        reject(row, "dependency_rejected", "Command ${gate.dependencyId} was rejected", null)
                        result.rejected += row.id
                        continue
                    }
                    Gate.Waiting -> Step.DEFERRED
                    Gate.Ready -> send(row, result)
                }
            }
            when (step) {
                Step.DONE -> Unit
                Step.DEFERRED -> {
                    result.deferred++
                    if (entity != null) blocked += entity
                }
                Step.OFFLINE -> {
                    result.offline = true
                    return result
                }
            }
        }
        return result
    }

    /** Dependencies (acknowledged) and uploads (finalized) gate a command. */
    private suspend fun gate(id: String): Gate {
        val (deps, uploads) = store.read { conn ->
            conn.query(
                "SELECT c.id, c.state FROM command_dependencies d JOIN pending_commands c ON c.id = d.depends_on_command_id " +
                    "WHERE d.command_id = ?",
                listOf(id),
            ) to conn.query(
                "SELECT id, local_path, sha256, byte_size, media_type, state FROM upload_jobs WHERE command_id = ? AND state <> 'finalized'",
                listOf(id),
            )
        }
        var waiting = false
        for (dep in deps) {
            when (dep["state"]) {
                "rejected", "cancelled" -> return Gate.Rejected(dep["id"] as String)
                "acknowledged" -> Unit
                else -> waiting = true
            }
        }
        if (waiting) return Gate.Waiting
        if (uploads.isEmpty()) return Gate.Ready
        val up = uploader ?: return Gate.Waiting
        var allFinal = true
        for (job in uploads) {
            val jobId = job["id"] as String
            val state = try {
                up.process(jobId, job["local_path"] as String, job["sha256"] as String, (job["byte_size"] as Number).toLong(), job["media_type"] as String)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.write { tx ->
                    tx.execute(
                        "UPDATE upload_jobs SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                        listOf(e.message ?: e.toString(), jobId),
                    )
                }
                "failed"
            }
            if (state != "finalized") allFinal = false
            store.write { tx -> tx.execute("UPDATE upload_jobs SET state = ? WHERE id = ?", listOf(state, jobId)) }
        }
        return if (allFinal) Gate.Ready else Gate.Waiting
    }

    private suspend fun send(row: Row, result: V2FlushResult): Step {
        val previous = row.state
        lease(row)
        // Outcome unknown: ask before re-sending (a resend is safe too, but the receipt avoids a
        // 409 idempotency_in_progress while the first attempt is still running).
        if (previous == V2CommandState.SENT_UNKNOWN || previous == V2CommandState.LEASED) {
            try {
                val receipt = api.commandReceipt(row.id)
                if (receipt.state == "processing") {
                    reschedule(row, V2CommandState.SENT_UNKNOWN, receiptRetryMs, null)
                    return Step.DEFERRED
                }
                return handle(receipt, row, result)
            } catch (e: V2ApiException.Status) {
                if (e.status != 404) return handleError(e, row, sentBefore = true, result = result)
                // 404: never reached the server; send it.
            } catch (e: V2ApiException) {
                return handleError(e, row, sentBefore = true, result = result)
            }
        }
        val request = try {
            makeRequest(row)
        } catch (e: Exception) {
            reject(row, "invalid_payload", e.message, null)
            result.rejected += row.id
            return Step.DONE
        }
        return try {
            handle(api.submitCommand(request), row, result)
        } catch (e: V2ApiException) {
            handleError(e, row, sentBefore = false, result = result)
        }
    }

    private suspend fun makeRequest(row: Row): V2CommandRequest {
        val payload = Json.parseToJsonElement(row.payload)
        val deps = store.read { conn ->
            conn.query(
                "SELECT depends_on_command_id FROM command_dependencies WHERE command_id = ? ORDER BY depends_on_command_id",
                listOf(row.id),
            ).map { it["depends_on_command_id"] as String }
        }
        return V2CommandRequest(
            commandId = row.id,
            operation = row.operation,
            payloadSchemaVersion = row.payloadSchemaVersion,
            entityType = row.entityType,
            entityKey = row.entityKey,
            baseVersion = row.baseVersion?.toString(),
            dependsOn = deps,
            payload = payload,
        )
    }

    private suspend fun handle(outcome: V2CommandOutcome, row: Row, result: V2FlushResult): Step = when (outcome.state) {
        "committed" -> {
            val watermark = outcome.watermark?.toLongOrNull()
            val resultText = outcome.result?.let(V2Canonical::string)
            store.write { tx ->
                val applied = tx.connection.scalarLong("SELECT cursor FROM sync_state WHERE stream = 'account'") ?: 0L
                if (watermark == null || watermark <= applied) {
                    // Already visible in the base (or nothing changed): retire now.
                    tx.execute("DELETE FROM pending_commands WHERE id = ?", listOf(row.id))
                } else {
                    tx.execute(
                        "UPDATE pending_commands SET state = 'acknowledged', result_watermark = ?, result = ?, lease_owner = NULL, " +
                            "lease_expires_at = NULL, next_attempt_at = NULL, last_error = NULL WHERE id = ?",
                        listOf(watermark, resultText, row.id),
                    )
                }
                tx.markChanged("pending_commands", "local_overlays")
            }
            result.acknowledged += row.id
            Step.DONE
        }
        "rejected" -> {
            reject(row, outcome.rejectionCode ?: "rejected", outcome.message, outcome.conflictId)
            result.rejected += row.id
            Step.DONE
        }
        else -> {
            reschedule(row, V2CommandState.SENT_UNKNOWN, receiptRetryMs, null)
            Step.DEFERRED
        }
    }

    private suspend fun handleError(e: V2ApiException, row: Row, sentBefore: Boolean, result: V2FlushResult): Step {
        val unsent = if (sentBefore) V2CommandState.SENT_UNKNOWN else V2CommandState.QUEUED
        return when (e) {
            is V2ApiException.UpgradeRequired -> {
                reschedule(row, unsent, null, null, countAttempt = false)
                throw V2OutboxException.UpgradeRequired()
            }
            is V2ApiException.Network -> {
                // The request may have reached the server.
                reschedule(row, V2CommandState.SENT_UNKNOWN, backoffMs(row), e.message)
                Step.OFFLINE
            }
            is V2ApiException.Decoding -> {
                // A 2xx we could not read: the outcome is stored server-side; fetch the receipt later.
                reschedule(row, V2CommandState.SENT_UNKNOWN, receiptRetryMs, e.message)
                Step.DEFERRED
            }
            is V2ApiException.Status -> {
                val code = e.code
                when {
                    e.status == 401 && code == "device_revoked" -> {
                        reschedule(row, unsent, null, null, countAttempt = false)
                        throw V2OutboxException.DeviceRevoked()
                    }
                    e.status == 401 -> {
                        reschedule(row, unsent, null, null, countAttempt = false)
                        Step.OFFLINE
                    }
                    e.status == 409 && code == "dependency_pending" -> {
                        reschedule(row, unsent, e.retryAfterMs ?: 1_000, code)
                        Step.DEFERRED
                    }
                    e.status == 409 && code == "idempotency_in_progress" -> {
                        reschedule(row, V2CommandState.SENT_UNKNOWN, e.retryAfterMs ?: 1_000, code)
                        Step.DEFERRED
                    }
                    e.status == 409 && code == "device_not_registered" -> {
                        // The sync engine re-registers; try again after the next sync.
                        store.write { tx -> V2Database.metaPut(tx.connection, V2SyncEngine.META_DEVICE_REGISTERED, null) }
                        reschedule(row, unsent, null, code, countAttempt = false)
                        Step.OFFLINE
                    }
                    e.status == 429 || e.status == 503 || code == "overloaded" -> {
                        reschedule(row, unsent, maxOf(e.retryAfterMs ?: 0, backoffMs(row)), code)
                        Step.OFFLINE
                    }
                    e.status >= 500 -> {
                        // May have committed before failing: resolve with the receipt next time.
                        reschedule(row, V2CommandState.SENT_UNKNOWN, maxOf(e.retryAfterMs ?: 0, backoffMs(row)), code)
                        Step.DEFERRED
                    }
                    else -> {
                        // 409 idempotency_conflict, 400 validation_failed, 403, 404, 422…: final client bugs.
                        reject(row, code ?: "http_${e.status}", e.body?.message, null)
                        result.rejected += row.id
                        Step.DONE
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // State changes

    private suspend fun lease(row: Row) = store.write { tx ->
        tx.execute(
            "UPDATE pending_commands SET state = 'leased', lease_owner = ?, lease_expires_at = ?, attempts = attempts + 1 WHERE id = ?",
            listOf(leaseOwner, clock() + leaseDurationMs, row.id),
        )
    }

    private suspend fun reschedule(row: Row, state: V2CommandState, afterMs: Long?, error: String?, countAttempt: Boolean = true) =
        store.write { tx ->
            tx.execute(
                "UPDATE pending_commands SET state = ?, lease_owner = NULL, lease_expires_at = NULL, next_attempt_at = ?, " +
                    "last_error = COALESCE(?, last_error), attempts = CASE WHEN ? THEN attempts ELSE MAX(attempts - 1, 0) END " +
                    "WHERE id = ?",
                listOf(state.wire, afterMs?.let { clock() + it }, error, if (countAttempt) 1L else 0L, row.id),
            )
            tx.markChanged("pending_commands")
        }

    private suspend fun reject(row: Row, code: String, message: String?, conflictId: String?) = store.write { tx ->
        val error = buildMap<String, JsonElement> {
            put("code", JsonPrimitive(code))
            message?.let { put("message", JsonPrimitive(it)) }
            conflictId?.let { put("conflict_id", JsonPrimitive(it)) }
        }
        tx.execute(
            "UPDATE pending_commands SET state = 'rejected', lease_owner = NULL, lease_expires_at = NULL, next_attempt_at = NULL, " +
                "last_error = ? WHERE id = ?",
            listOf(V2Canonical.string(JsonObject(error)), row.id),
        )
        tx.execute("DELETE FROM local_overlays WHERE command_id = ?", listOf(row.id))
        rejectDependents(tx.connection, row.id)
        tx.markChanged("pending_commands", "local_overlays")
    }

    /** Unsent dependents can never succeed: reject them (transitively) locally. */
    private fun rejectDependents(conn: V2Connection, id: String) {
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

    /** A lease left by a crashed process (or expired): the request may have been sent. */
    private suspend fun recoverStaleLeases() = store.write { tx ->
        tx.execute(
            "UPDATE pending_commands SET state = 'sent_unknown', lease_owner = NULL, lease_expires_at = NULL " +
                "WHERE state = 'leased' AND (lease_owner IS NULL OR lease_owner <> ? OR lease_expires_at < ?)",
            listOf(leaseOwner, clock()),
        )
    }

    /** Exponential backoff: 1 s · 2^(attempts-1), capped at [maxBackoffMs]. */
    private fun backoffMs(row: Row): Long {
        val attempts = maxOf(1, row.attempts + 1)
        return minOf(maxBackoffMs, 1_000L shl minOf(attempts - 1, 20))
    }

    private suspend fun loadActive(): List<Row> = store.read { conn ->
        conn.query(
            "SELECT id, operation, payload_schema_version, entity_type, entity_key, base_version, payload, state, attempts, " +
                "next_attempt_at, created_at FROM pending_commands WHERE state IN ('queued', 'leased', 'sent_unknown') " +
                "ORDER BY causal_order",
        ).mapNotNull { r ->
            Row(
                id = r["id"] as String,
                operation = r["operation"] as String,
                payloadSchemaVersion = (r["payload_schema_version"] as Number).toInt(),
                entityType = r["entity_type"] as String?,
                entityKey = r["entity_key"] as String?,
                baseVersion = (r["base_version"] as Number?)?.toLong(),
                payload = r["payload"] as String,
                state = V2CommandState.fromWire(r["state"] as String?) ?: return@mapNotNull null,
                attempts = (r["attempts"] as Number).toInt(),
                nextAttemptAt = (r["next_attempt_at"] as Number?)?.toLong(),
                createdAt = (r["created_at"] as Number).toLong(),
            )
        }
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

    private fun failure(r: V2Row): V2CommandFailure {
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

    private companion object {
        /** sync-wire.md: `operation` must match `^[a-z][a-z0-9_.]{2,80}$`. */
        val OPERATION = Regex("^[a-z][a-z0-9_.]{2,80}$")
    }
}
