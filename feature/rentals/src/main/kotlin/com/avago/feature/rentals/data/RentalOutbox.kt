package com.avago.feature.rentals.data

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.db.rental.RentalRecordEntity.Companion.STATE_CONFLICT
import com.avago.core.data.db.rental.RentalRecordEntity.Companion.STATE_SYNCED
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.network.rentals.RentalsApiException
import com.avago.core.sync.ExternalOutbox
import com.avago.core.sync.PhotoUploader
import com.avago.feature.rentals.domain.OutboxPlanner
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.RentalJson
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.objs
import com.avago.feature.rentals.domain.parseObj
import com.avago.feature.rentals.domain.str
import com.avago.feature.rentals.domain.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Pushes queued rental actions (spec §15) in [OutboxPlanner] order. Called by
 * [com.avago.core.sync.SyncEngine] after `/sync/push` and by [RentalSyncWorker].
 *
 *  * success → server copy replaces the local draft (local→server id kept in `rental_id_map`),
 *    queue row deleted; a booking confirmed with an allocation conflict gets a notice;
 *  * 409 (stale version / allocation conflict / invalid transition / already checked out|in)
 *    and other 4xx → `conflict` with a user-facing notice; nothing later in the same order runs;
 *  * 404 / 405 from a route the client calls (route missing on the server) → `failed` with a
 *    visible error; the user can retry (e.g. after a server update) or discard;
 *  * 501 `not_implemented` → stays pending (not counted as an attempt);
 *  * network / 5xx / 429 / idempotency-key-in-flight → `error` (bounded by attempts).
 *  * server `warnings` on success are kept as a notice on the confirmed row.
 */
@Singleton
class RentalOutbox @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val api: RentalsApi,
    private val photoUploader: Provider<PhotoUploader>,
    private val sync: Provider<RentalSync>,
) : ExternalOutbox {

    private val mutex = Mutex()

    override val entityTypes: Set<String> = OutboxPlanner.TYPES

    override suspend fun pull(accountId: String) {
        sync.get().pull(accountId)
    }

    override suspend fun drain(accountId: String, items: List<SyncQueueEntity>): Int = drainAll(accountId)

    /** Drains every runnable rental row; returns how many were confirmed. */
    suspend fun drainAll(accountId: String): Int = mutex.withLock {
        val db = dbFactory.get(accountId)
        val queue = db.syncQueueDao()
        val rows = db.rentalDao().queueItemsOfTypes(OutboxPlanner.TYPES.toList())
        if (rows.isEmpty()) return@withLock 0
        val payloads = rows.associate { it.queueId to (parseObj(it.payload) ?: JsonObject(emptyMap())) }
        if (rows.any { payloads[it.queueId]?.strings("photo_ids")?.isNotEmpty() == true }) {
            runCatching { photoUploader.get().sweep(accountId) }
                .onFailure { if (it is CancellationException) throw it; Timber.w(it, "[RentalOutbox] photo sweep failed") }
        }
        val items = rows.map { r ->
            val p = payloads.getValue(r.queueId)
            OutboxPlanner.Item(
                queueId = r.queueId,
                type = r.entityType,
                clientUuid = r.entityId,
                group = p.str("group") ?: r.entityId,
                createdAt = r.createdAt,
                dependsOn = p.strings("depends_on"),
                photoIds = p.strings("photo_ids"),
                logId = p.str("log_id"),
                status = r.syncStatus,
                attempts = r.attempts,
            )
        }
        val photoKeys = items.flatMap { it.photoIds }.distinct()
            .associateWith { db.photoDao().getById(it)?.storageKey }
        val logsPending = items.mapNotNull { it.logId }.distinct()
            .filterTo(HashSet()) { queue.hasPendingPush("log", it) != null }
        val plan = OutboxPlanner.plan(
            items,
            photoUploaded = { id -> photoKeys[id] != null },
            logSynced = { id -> id !in logsPending },
        )
        val failedGroups = HashSet<String>()
        val failedUuids = HashSet<String>()
        var ok = 0
        for (item in plan.runnable) {
            if (item.group in failedGroups || item.dependsOn.any { it in failedUuids }) continue
            val row = rows.first { it.queueId == item.queueId }
            val result = try {
                execute(accountId, row, payloads.getValue(row.queueId))
                Res.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (e: RentalsApiException) {
                classify(row.entityType, e)
            } catch (e: IOException) {
                Res.Retry(e.message ?: "network")
            } catch (e: Exception) {
                Timber.w(e, "[RentalOutbox] ${row.entityType} failed")
                Res.Retry(e.message ?: e.javaClass.simpleName)
            }
            when (result) {
                Res.Ok -> {
                    queue.markSuccess(row.queueId)
                    ok++
                    settleGroup(accountId, item.group, payloads.getValue(row.queueId))
                }
                is Res.Retry -> {
                    queue.markError(row.queueId, result.message.take(500))
                    failedGroups += item.group; failedUuids += item.clientUuid
                }
                Res.Later -> {
                    failedGroups += item.group; failedUuids += item.clientUuid
                }
                is Res.Failed -> {
                    dbFactory.get(accountId).rentalDao().markQueueFailed(row.queueId, result.message.take(500))
                    markConflict(accountId, row, payloads.getValue(row.queueId), NOTICE_SYNC_FAILED_PREFIX + result.message.take(300))
                    failedGroups += item.group; failedUuids += item.clientUuid
                }
                is Res.Conflict -> {
                    queue.markConflict(row.queueId)
                    markConflict(accountId, row, payloads.getValue(row.queueId), result.notice)
                    failedGroups += item.group; failedUuids += item.clientUuid
                }
                Res.Unauthorized -> return@withLock ok
            }
        }
        ok
    }

    private sealed interface Res {
        data object Ok : Res
        data class Retry(val message: String) : Res
        /** Endpoint not available yet (501 `not_implemented`) — keep pending without burning attempts. */
        data object Later : Res
        /** The route is missing (404/405) — stop and show the error; the user may retry. */
        data class Failed(val message: String) : Res
        data class Conflict(val notice: String) : Res
        data object Unauthorized : Res
    }

    private fun classify(type: String, e: RentalsApiException): Res =
        when (OutboxPlanner.disposition(type, e.status, e.code, e.message)) {
            OutboxPlanner.Disposition.UNAUTHORIZED -> Res.Unauthorized
            OutboxPlanner.Disposition.LATER -> Res.Later
            OutboxPlanner.Disposition.FAILED -> Res.Failed("HTTP ${e.status}: ${e.message ?: e.code ?: "route not found"}")
            OutboxPlanner.Disposition.RETRY -> Res.Retry(e.message ?: "HTTP ${e.status}")
            OutboxPlanner.Disposition.CONFLICT -> Res.Conflict(conflictNotice(e))
        }

    /** Server `warnings:[{code, message?}]` of a successful write as a notice (null when none). */
    private fun warningNotice(resp: JsonObject): String? = OutboxPlanner.warningsText(resp)?.let { NOTICE_WARNINGS_PREFIX + it }

    /** User-facing reason; allocation conflicts list the clashing order numbers. */
    private fun conflictNotice(e: RentalsApiException): String {
        val body = e.body
        val clashes = body?.objs("conflicts").orEmpty().mapNotNull { it.str("order_number") ?: it.str("kind") }
        val base = e.message ?: body?.str("error") ?: "HTTP ${e.status}"
        return if (clashes.isEmpty()) base else "$base (${clashes.distinct().joinToString()})"
    }

    // ── id map ──────────────────────────────────────────────────────────────────

    private suspend fun serverId(accountId: String, id: String?): String? {
        id ?: return null
        val m = dbFactory.get(accountId).rentalDao().get(RentalEntity.ID_MAP, id)
        return m?.obj?.str("server_id") ?: id
    }

    private suspend fun mapId(accountId: String, localId: String, serverId: String, entity: String) {
        if (localId == serverId) return
        dbFactory.get(accountId).rentalDao().upsert(
            RentalRecordEntity(
                entity = RentalEntity.ID_MAP,
                id = localId,
                json = RentalJson.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject { put("server_id", serverId); put("entity", entity) },
                ),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Replaces local ids in [body] fields that reference other offline-created rows. */
    private suspend fun resolve(accountId: String, body: JsonObject): JsonObject {
        val keys = listOf("customer_id", "order_id", "order_line_id", "customer_signature_id", "contact_id", "job_site_id")
        var out = body
        for (k in keys) {
            val v = body.str(k) ?: continue
            val s = serverId(accountId, v)
            if (s != v) out = JsonObject(out + (k to JsonPrimitive(s)))
        }
        return out
    }

    // ── execution ───────────────────────────────────────────────────────────────

    private suspend fun execute(accountId: String, row: SyncQueueEntity, p: JsonObject) {
        val key = row.entityId
        val body = resolve(accountId, p.obj("body") ?: JsonObject(emptyMap()))
        val dao = dbFactory.get(accountId).rentalDao()
        when (row.entityType) {
            OutboxPlanner.TYPE_CUSTOMER -> {
                val resp = if (p.bool("update") == true) {
                    api.updateCustomer(accountId, serverId(accountId, p.str("customer_id"))!!, body, key)
                } else {
                    api.createCustomer(accountId, body, key)
                }
                val c = resp.obj("customer") ?: resp
                val localId = p.str("local_id") ?: p.str("customer_id")
                val sid = c.str("rental_customer_id") ?: c.str("id")
                if (localId != null && sid != null) {
                    if (localId != sid) dao.delete(RentalEntity.CUSTOMER, localId)
                    mapId(accountId, localId, sid, RentalEntity.CUSTOMER)
                    RentalRecords.record(RentalEntity.CUSTOMER, c)?.let { dao.upsert(it) }
                }
            }
            OutboxPlanner.TYPE_CUSTOMER_DOCUMENT -> {
                val cid = serverId(accountId, p.str("customer_id"))!!
                val resp = api.createCustomerChild(accountId, cid, "documents", body, key)
                val doc = resp.obj("document") ?: resp
                replaceLocal(accountId, RentalEntity.CUSTOMER_DOCUMENT, p.str("local_id"), doc)
            }
            OutboxPlanner.TYPE_ORDER -> {
                val resp = api.createOrder(accountId, body, key)
                applyOrder(accountId, p.str("local_id"), resp, confirmedDraft = true)
            }
            OutboxPlanner.TYPE_ORDER_ACTION -> {
                val oid = serverId(accountId, p.str("order_id"))!!
                val resp = api.orderAction(accountId, oid, p.str("action")!!, body, key)
                applyOrder(accountId, null, resp, confirmedDraft = p.str("action") == "extend")
            }
            OutboxPlanner.TYPE_ASSET -> {
                val assetId = p.str("asset_id")!!
                if (p.bool("enabled") == true) api.enableAsset(accountId, assetId, body, key)
                else api.disableAsset(accountId, assetId, key)
                dao.get(RentalEntity.FLEET, assetId)?.let {
                    if (p.bool("enabled") == true) dao.setState(it.entity, it.id, STATE_SYNCED, null)
                    else dao.delete(it.entity, it.id)
                }
            }
            OutboxPlanner.TYPE_SIGNATURE -> {
                val oid = serverId(accountId, p.str("order_id"))
                    ?: throw IllegalStateException("signature without order_id")
                val resp = api.createSignature(accountId, oid, body, key)
                val s = resp.obj("signature") ?: resp
                val sid = s.str("id")
                val localId = p.str("local_id")
                if (localId != null && sid != null) mapId(accountId, localId, sid, RentalEntity.SIGNATURE)
                localId?.let { dao.setState(RentalEntity.SIGNATURE, it, STATE_SYNCED, null) }
            }
            OutboxPlanner.TYPE_HANDOVER -> {
                val oid = serverId(accountId, p.str("order_id"))!!
                val lid = serverId(accountId, p.str("line_id"))
                    ?: throw IllegalStateException("handover without line_id")
                val photos = p.strings("photo_ids").mapNotNull { id ->
                    dbFactory.get(accountId).photoDao().getById(id)?.storageKey?.let { k ->
                        buildJsonObject { put("file_id", id); put("storage_key", k) }
                    }
                }
                val full = JsonObject(body + ("photos" to JsonArray(photos)))
                val kind = p.str("kind") ?: RentalsApi.HANDOVER_CHECKOUT
                val resp = api.lineHandover(accountId, oid, lid, kind, full, key)
                val localId = p.str("local_id")
                val server = resp.objs("handovers").firstOrNull { it.str("order_line_id") == lid }
                    ?: resp.objs("handovers").firstOrNull()
                if (server != null) replaceLocal(accountId, RentalEntity.HANDOVER, localId, server, warningNotice(resp))
                else localId?.let { dao.setState(RentalEntity.HANDOVER, it, STATE_SYNCED, warningNotice(resp)) }
                resp.obj("order")?.let { applyOrder(accountId, null, it, confirmedDraft = false) }
            }
            OutboxPlanner.TYPE_LEDGER -> {
                val oid = serverId(accountId, p.str("order_id"))!!
                val resp = api.createLedgerEntry(accountId, oid, body, key)
                val localId = p.str("local_id")
                val entry = resp.obj("entry")
                if (entry != null) replaceLocal(accountId, RentalEntity.LEDGER, localId, entry, warningNotice(resp))
                else localId?.let { dao.setState(RentalEntity.LEDGER, it, STATE_SYNCED, warningNotice(resp)) }
                resp.obj("order")?.let { applyOrder(accountId, null, it, confirmedDraft = false) }
            }
            OutboxPlanner.TYPE_INVOICE -> {
                val oid = serverId(accountId, p.str("order_id"))!!
                val resp = api.createInvoice(accountId, oid, body, key)
                replaceLocal(accountId, RentalEntity.INVOICE, p.str("local_id"), resp.obj("invoice") ?: resp, warningNotice(resp))
            }
            else -> error("unknown rental outbox type ${row.entityType}")
        }
    }

    private suspend fun replaceLocal(accountId: String, entity: String, localId: String?, server: JsonObject, notice: String? = null) {
        val dao = dbFactory.get(accountId).rentalDao()
        val rec = RentalRecords.record(entity, server, notice = notice) ?: run {
            localId?.let { dao.setState(entity, it, STATE_SYNCED, notice) }
            return
        }
        if (localId != null && localId != rec.id) {
            dao.delete(entity, localId)
            mapId(accountId, localId, rec.id, entity)
        }
        dao.upsert(rec)
    }

    /**
     * Stores the server order (and its embedded lines/allocations) in place of the local draft.
     * A draft confirmed with an allocation flagged `conflict` keeps a notice for the user.
     */
    private suspend fun applyOrder(accountId: String, localId: String?, resp: JsonObject, confirmedDraft: Boolean) {
        val split = RentalRecords.splitOrder(resp) ?: return
        val sid = split.orderId
        val dao = dbFactory.get(accountId).rentalDao()
        if (localId != null && localId != sid) {
            dao.byOrder(RentalEntity.ORDER_LINE, localId).forEach { dao.delete(it.entity, it.id) }
            dao.byOrder(RentalEntity.ALLOCATION, localId).forEach { dao.delete(it.entity, it.id) }
            dao.delete(RentalEntity.ORDER, localId)
            mapId(accountId, localId, sid, RentalEntity.ORDER)
        }
        val allocations = split.allocations
        val conflicted = allocations.any { it.bool("conflict") == true }
        val notice = if (confirmedDraft && conflicted) NOTICE_CONFIRMED_WITH_CONFLICT else null
        val records = mutableListOf<RentalRecordEntity>()
        RentalRecords.record(RentalEntity.ORDER, split.order, notice = notice)?.let { records += it }
        split.lines.forEach { l -> RentalRecords.record(RentalEntity.ORDER_LINE, l)?.let { records += it } }
        // Lines created offline carry their local id as client_uuid; later items (handovers) reference it.
        split.lineIdsByClientUuid.forEach { (cu, lineId) -> mapId(accountId, cu, lineId, RentalEntity.ORDER_LINE) }
        allocations.forEach { a -> RentalRecords.record(RentalEntity.ALLOCATION, a)?.let { records += it } }
        if (allocations.isNotEmpty()) {
            // Optimistic local allocations of this order are superseded by the server set.
            dao.byOrder(RentalEntity.ALLOCATION, sid).filter { it.localState != STATE_SYNCED }
                .forEach { dao.delete(it.entity, it.id) }
        }
        dao.upsertAll(records)
    }

    /** After a success: once nothing else is queued for the group its rows become `synced`. */
    private suspend fun settleGroup(accountId: String, group: String, p: JsonObject) {
        val remaining = dbFactory.get(accountId).rentalDao().queueItemsOfTypes(OutboxPlanner.TYPES.toList()).any {
            parseObj(it.payload)?.str("group") == group
        }
        if (remaining) return
        val dao = dbFactory.get(accountId).rentalDao()
        val orderId = serverId(accountId, p.str("order_id") ?: p.str("local_id").takeIf { group.startsWith("order:") })
        if (group.startsWith("order:") && orderId != null) {
            dao.get(RentalEntity.ORDER, orderId)?.takeIf { it.localState != STATE_SYNCED && it.localState != STATE_CONFLICT }
                ?.let { dao.setState(it.entity, it.id, STATE_SYNCED, it.notice) }
            dao.byOrder(RentalEntity.ALLOCATION, orderId).filter { it.localState != STATE_SYNCED }
                .forEach { dao.setState(it.entity, it.id, STATE_SYNCED, null) }
        }
        if (group.startsWith("customer:")) {
            val cid = serverId(accountId, group.removePrefix("customer:"))
            cid?.let { dao.get(RentalEntity.CUSTOMER, it) }?.takeIf { it.localState != STATE_SYNCED }
                ?.let { dao.setState(it.entity, it.id, STATE_SYNCED, null) }
        }
    }

    private suspend fun markConflict(accountId: String, row: SyncQueueEntity, p: JsonObject, notice: String) {
        val dao = dbFactory.get(accountId).rentalDao()
        val (entity, id) = when (row.entityType) {
            OutboxPlanner.TYPE_CUSTOMER -> RentalEntity.CUSTOMER to (p.str("local_id") ?: p.str("customer_id"))
            OutboxPlanner.TYPE_CUSTOMER_DOCUMENT -> RentalEntity.CUSTOMER_DOCUMENT to p.str("local_id")
            OutboxPlanner.TYPE_ASSET -> RentalEntity.FLEET to p.str("asset_id")
            OutboxPlanner.TYPE_HANDOVER -> RentalEntity.HANDOVER to p.str("local_id")
            OutboxPlanner.TYPE_LEDGER -> RentalEntity.LEDGER to p.str("local_id")
            OutboxPlanner.TYPE_INVOICE -> RentalEntity.INVOICE to p.str("local_id")
            OutboxPlanner.TYPE_SIGNATURE -> RentalEntity.SIGNATURE to p.str("local_id")
            else -> RentalEntity.ORDER to (p.str("local_id") ?: p.str("order_id"))
        }
        id ?: return
        dao.setState(entity, serverId(accountId, id) ?: id, STATE_CONFLICT, notice)
        // Orders also surface conflicts of their child actions.
        if (entity != RentalEntity.ORDER) {
            p.str("order_id")?.let { oid -> dao.setState(RentalEntity.ORDER, serverId(accountId, oid) ?: oid, STATE_CONFLICT, notice) }
        }
    }

    companion object {
        /** Marker stored in `notice` — the UI shows a localised message for it. */
        const val NOTICE_CONFIRMED_WITH_CONFLICT = "confirmed_with_conflict"
        /** `sync_failed:<detail>` — the UI shows a localised "Sync failed" message with the detail. */
        const val NOTICE_SYNC_FAILED_PREFIX = "sync_failed:"
        /** `server_warnings:<codes>` — confirmed with server warnings (e.g. needs_review). */
        const val NOTICE_WARNINGS_PREFIX = "server_warnings:"
    }
}
