package com.avago.core.sync.v2

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.sync.QueueRetryPolicy
import com.avago.core.data.v2.V2ActiveStores
import com.avago.core.data.v2.V2BridgePlan
import com.avago.core.data.v2.V2Database
import com.avago.core.data.v2.V2LegacyProjector
import com.avago.core.data.v2.V2LegacyWritePlanner
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.sync.SyncPayloadBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P3-12b bridge between the legacy Room feature layer and the v2 server while the v2 flag is on:
 *
 * - [push] drains the legacy `sync_queue` through the REST routes the v2 server keeps
 *   ([V2LegacyWritePlanner]); the legacy `/sync/push` endpoint answers 426 on v2.
 * - [mirror] projects the v2 store into Room ([V2LegacyProjector]) so the ~700 existing DAO call
 *   sites read v2 data without being rewritten. Rows with a queued local change are protected by
 *   the pull upsert's pending-push guard; Room rows the server no longer has are pruned.
 */
@Singleton
class V2LegacyBridge @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val payloads: SyncPayloadBuilder,
    private val client: AvagoServiceClient,
    private val active: V2ActiveStores,
) {
    private val mutex = Mutex()
    private val planner = V2LegacyWritePlanner()
    private val projector = V2LegacyProjector()

    /** "$accountId|$entityType" → entity id → hash of the JSON last applied to Room. */
    private val applied = mutableMapOf<String, MutableMap<String, Int>>()

    /** Sends queued legacy writes; returns how many REST calls succeeded. */
    suspend fun push(accountId: String): Int = mutex.withLock {
        val business = active.forAccount(accountId)?.business ?: return@withLock 0
        val queue = dbFactory.get(accountId).syncQueueDao()
        val rows = queue.pendingItemsList().filter {
            it.syncStatus != "error" || it.attempts < QueueRetryPolicy.MAX_ATTEMPTS
        }
        var sent = 0
        val sentThisRun = mutableMapOf<String, JsonObject>()
        for (row in rows) {
            val key = "${row.entityType}|${row.entityId}"
            val outcome = try {
                sendRow(accountId, business, row, sentThisRun[key])
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "V2LegacyBridge: ${row.entityType}/${row.entityId} failed")
                RowOutcome.Failed(e.message ?: e.javaClass.simpleName)
            }
            when (outcome) {
                is RowOutcome.Done -> {
                    queue.markSuccess(row.queueId)
                    sent += outcome.calls
                    outcome.payload?.let { sentThisRun[key] = it }
                    invalidate(accountId, row.entityType, row.entityId)
                }
                is RowOutcome.Failed -> queue.markError(row.queueId, outcome.reason)
                is RowOutcome.Offline -> {
                    queue.markOfflineRetry(row.queueId, outcome.reason)
                    break
                }
            }
        }
        sent
    }

    private sealed interface RowOutcome {
        data class Done(val calls: Int, val payload: JsonObject?) : RowOutcome
        data class Failed(val reason: String) : RowOutcome
        data class Offline(val reason: String) : RowOutcome
    }

    private suspend fun sendRow(accountId: String, business: V2Database, row: SyncQueueEntity, sentBefore: JsonObject?): RowOutcome {
        val type = row.entityType
        if (type !in V2LegacyWritePlanner.SUPPORTED_TYPES) {
            return RowOutcome.Failed("$UNSUPPORTED: $type writes are not available on the v2 server yet")
        }
        val payload = payloads.buildPayload(accountId, type, row.entityId)
        val current = business.read { projector.projectOne(it, type, row.entityId) } ?: sentBefore
        val plan = planner.plan(accountId, type, row.entityId, row.operation, payload, current)
        val calls = when (plan) {
            is V2BridgePlan.Unsupported -> return RowOutcome.Failed("$UNSUPPORTED: ${plan.reason}")
            is V2BridgePlan.Send -> plan.calls
        }
        for (call in calls) {
            when (val result = client.sendJson(call.method, call.path, call.body)) {
                is NetworkResult.Success -> Unit
                NetworkResult.Unauthorized -> return RowOutcome.Offline("unauthorized")
                is NetworkResult.Error -> return when {
                    result.code == -1 || result.code == 408 || result.code == 429 || result.code >= 500 ->
                        RowOutcome.Offline("HTTP ${result.code}: ${result.message.take(200)}")
                    // Deleting something the server no longer has is done.
                    result.code == 404 && call.method == "DELETE" -> RowOutcome.Done(0, null)
                    else -> RowOutcome.Failed("HTTP ${result.code} ${call.method} ${call.path}: ${result.message.take(300)}")
                }
            }
        }
        Timber.d("V2LegacyBridge: $type/${row.entityId} → ${calls.size} call(s)")
        return RowOutcome.Done(calls.size, payload?.let { JsonObject(it + ("server_version" to JsonPrimitive(0))) })
    }

    /**
     * Mirrors every projected entity type into Room. [apply] is the legacy pull upsert;
     * [roomIds] lists the Room primary keys of a type; [delete] removes Room rows by id.
     */
    suspend fun mirror(
        accountId: String,
        apply: suspend (entityType: String, item: JsonObject) -> Unit,
        roomIds: suspend (table: String, pk: String) -> List<String>,
        delete: suspend (table: String, pk: String, ids: Collection<String>) -> Unit,
    ): Int = mutex.withLock {
        val business = active.forAccount(accountId)?.business ?: return@withLock 0
        val queue = dbFactory.get(accountId).syncQueueDao()
        var changed = 0
        val keepByType = mutableMapOf<String, Set<String>>()
        for (spec in V2LegacyProjector.SPECS) {
            val rows = try {
                business.read { projector.project(it, spec.entityType) }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "V2LegacyBridge: projecting ${spec.entityType} failed")
                continue
            }
            val seen = applied.getOrPut("$accountId|${spec.entityType}") { mutableMapOf() }
            val ids = HashSet<String>(rows.size)
            for (item in rows) {
                val id = (item[spec.idKey] as? JsonPrimitive)?.content ?: continue
                ids += id
                val hash = item.hashCode()
                if (seen[id] == hash) continue
                try {
                    apply(spec.entityType, item)
                    seen[id] = hash
                    changed++
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Timber.w(e, "V2LegacyBridge: mirroring ${spec.entityType}/$id failed")
                }
            }
            seen.keys.retainAll(ids)
            keepByType[spec.entityType] = ids
        }
        // Children first, so nothing references a pruned parent mid-way.
        for (spec in V2LegacyProjector.SPECS.asReversed()) {
            val (table, pk) = ROOM_TABLES[spec.entityType] ?: continue
            val keep = keepByType[spec.entityType] ?: continue
            try {
                val pending = queue.unsyncedEntityIds(spec.entityType).toSet()
                val stale = roomIds(table, pk).filter { it !in keep && it !in pending }
                if (stale.isNotEmpty()) {
                    delete(table, pk, stale)
                    changed += stale.size
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "V2LegacyBridge: pruning ${spec.entityType} failed")
            }
        }
        if (changed > 0) Timber.d("V2LegacyBridge: mirrored $changed Room change(s)")
        changed
    }

    /** Forces the next [mirror] to re-apply the entity (its Room row may hold a local edit). */
    private fun invalidate(accountId: String, entityType: String, entityId: String) {
        applied["$accountId|$entityType"]?.remove(entityId)
    }

    companion object {
        const val UNSUPPORTED = "v2_unsupported"

        /** Room table + primary-key column per mirrored entity type (prune targets). */
        val ROOM_TABLES: Map<String, Pair<String, String>> = mapOf(
            "location" to ("locations" to "location_id"),
            "bin" to ("bins" to "bin_id"),
            "category" to ("categories" to "category_id"),
            "tech_profile" to ("tech_profiles" to "tech_id"),
            "asset" to ("assets" to "asset_id"),
            "wo_template" to ("wo_templates" to "template_id"),
            "work_order" to ("work_orders" to "wo_id"),
            "work_order_asset" to ("work_order_assets" to "wo_asset_id"),
            "wo_assignment" to ("wo_assignments" to "assignment_id"),
            "wo_checklist_item" to ("wo_checklist_items" to "item_id"),
            "wo_comment" to ("wo_comments" to "comment_id"),
            "log" to ("log" to "log_id"),
            "log_cost_line" to ("log_cost_lines" to "line_id"),
            "part" to ("parts" to "part_id"),
            "inventory" to ("inventory" to "inventory_id"),
            "stocking_level" to ("stocking_levels" to "stocking_level_id"),
            "pm_plan" to ("pm_plans" to "pm_plan_id"),
            "meter_reading" to ("meter_readings" to "meter_reading_id"),
        )
    }
}
