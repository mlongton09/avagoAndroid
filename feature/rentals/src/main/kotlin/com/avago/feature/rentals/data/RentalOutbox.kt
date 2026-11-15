package com.avago.feature.rentals.data

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.db.rental.RentalRecordEntity.Companion.STATE_CONFLICT
import com.avago.core.data.db.rental.RentalRecordEntity.Companion.STATE_SYNCED
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.sync.QueueRetryPolicy
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.PresignedUploadRequest
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.network.rentals.RentalsApiException
import com.avago.core.sync.ExternalOutbox
import com.avago.core.sync.PhotoUploader
import com.avago.feature.rentals.domain.ConflictMerge
import com.avago.feature.rentals.domain.Esign
import com.avago.feature.rentals.domain.HandoverGates
import com.avago.feature.rentals.domain.OutboxPlanner
import com.avago.feature.rentals.domain.Readiness
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.RentalJson
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.long
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Pushes queued rental actions (spec §15) in [OutboxPlanner] order. Called by
 * [com.avago.core.sync.SyncEngine] after `/sync/push` and by [RentalSyncWorker].
 *
 *  * success → server copy replaces the local draft (local→server id kept in `rental_id_map`),
 *    queue row deleted; a booking confirmed with an allocation conflict gets a notice;
 *  * 409 version `conflict` on a versioned edit (customer PATCH, order transition) → hybrid
 *    merge ([ConflictMerge]): disjoint field changes are rebased onto the server row and
 *    resent once; overlapping ones are parked as a conflict naming the clashing fields;
 *  * other 409s (allocation conflict / invalid transition / already checked out|in)
 *    and other 4xx → `conflict` with a user-facing notice; nothing later in the same order runs;
 *  * 404 / 405 from a route the client calls (route missing on the server) → `failed` with a
 *    visible error; the user can retry (e.g. after a server update) or discard;
 *  * 501 `not_implemented` → stays pending (not counted as an attempt);
 *  * 5xx / 429 / idempotency-key-in-flight → `error` (bounded by [QueueRetryPolicy.MAX_ATTEMPTS]);
 *  * no answer (offline / transport) → stays `pending` without using an attempt, and the run stops;
 *  * a malformed queued payload → `failed` with a visible error (never retried blindly).
 *  * server `warnings` on success are kept as a notice on the confirmed row.
 */
@Singleton
class RentalOutbox @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val api: RentalsApi,
    private val photoUploader: Provider<PhotoUploader>,
    private val sync: Provider<RentalSync>,
    private val files: AvagoServiceClient,
    private val online: Provider<RentalsOnline>,
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
        val rowsById = rows.associateBy { it.queueId }
        val failedGroups = HashSet<String>()
        val failedUuids = HashSet<String>()
        var ok = 0
        for (item in plan.runnable) {
            if (item.group in failedGroups || item.dependsOn.any { it in failedUuids }) continue
            val row = rowsById.getValue(item.queueId)
            val result = try {
                execute(accountId, row, payloads.getValue(row.queueId))
                Res.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e !is RentalsApiException) Timber.w(e, "[RentalOutbox] ${row.entityType} failed")
                classify(row.entityType, e)
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
                is Res.Offline -> {
                    // Never reached the server: keep it pending (no attempt used) and stop, as the
                    // rest would fail the same way. WorkManager retries once connected.
                    queue.markOfflineRetry(row.queueId, "offline: ${result.message}".take(500))
                    return@withLock ok
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
        /** No HTTP answer (offline / transport): keep pending without using an attempt. */
        data class Offline(val message: String) : Res
        /** Endpoint not available yet (501 `not_implemented`) — keep pending without burning attempts. */
        data object Later : Res
        /** The route is missing (404/405) — stop and show the error; the user may retry. */
        data class Failed(val message: String) : Res
        data class Conflict(val notice: String) : Res
        data object Unauthorized : Res
    }

    private fun classify(type: String, e: Exception): Res {
        if (e is FieldConflict) return Res.Conflict(e.notice)
        val api = e as? RentalsApiException
        val detail = e.message ?: api?.let { "HTTP ${it.status}" } ?: e.javaClass.simpleName
        return when (OutboxPlanner.dispositionOf(type, e)) {
            OutboxPlanner.Disposition.UNAUTHORIZED -> Res.Unauthorized
            OutboxPlanner.Disposition.LATER -> Res.Later
            OutboxPlanner.Disposition.OFFLINE -> Res.Offline(detail)
            OutboxPlanner.Disposition.FAILED ->
                Res.Failed(api?.let { "HTTP ${it.status}: ${it.message ?: it.code ?: "route not found"}" } ?: detail)
            OutboxPlanner.Disposition.RETRY -> Res.Retry(detail)
            OutboxPlanner.Disposition.CONFLICT -> Res.Conflict(
                api?.takeIf { HandoverGates.isGate(it.code) }?.let { HandoverGates.notice(it.code!!, it.message, it.body?.obj("details")) }
                    ?: api?.let(::conflictNotice) ?: detail,
            )
        }
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

    private suspend fun serverId(accountId: String, id: String?): String? = id?.let { mappedId(accountId, it) }

    private suspend fun mappedId(accountId: String, id: String): String {
        val m = dbFactory.get(accountId).rentalDao().get(RentalEntity.ID_MAP, id)
        return m?.obj?.str("server_id") ?: id
    }

    /** The server id for the required payload [field] (a malformed row fails visibly). */
    private suspend fun requiredServerId(accountId: String, p: JsonObject, field: String): String =
        mappedId(accountId, p.required(field))

    private fun JsonObject.required(field: String): String =
        str(field) ?: throw OutboxPlanner.MalformedItem("queued item without $field")

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
                    val cid = requiredServerId(accountId, p, "customer_id")
                    sendRebasing(accountId, row, p, body, RentalEntity.CUSTOMER, cid) { b -> api.updateCustomer(accountId, cid, b, key) }
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
                val cid = requiredServerId(accountId, p, "customer_id")
                // G2 counter photo: reserve → PUT → finalize once, then send it as `file_ids`.
                val fileId = p.str("photo_path")?.let { path ->
                    uploadOnce(accountId, row, p, "photo_file_id", path) { cu ->
                        val r = api.customerDocumentUpload(
                            accountId, cid,
                            buildJsonObject { put("client_uuid", cu); put("filename", "document.jpg"); put("content_type", "image/jpeg") },
                            cu,
                        )
                        Reservation(r.str("file_upload_id") ?: throw IllegalStateException("document upload without file_upload_id"), r.str("upload_url"))
                    }
                }
                val docBody = if (fileId != null) JsonObject(body + ("file_ids" to JsonArray(listOf(JsonPrimitive(fileId))))) else body
                val resp = api.createCustomerChild(accountId, cid, "documents", docBody, key)
                val doc = resp.obj("document") ?: resp
                replaceLocal(accountId, RentalEntity.CUSTOMER_DOCUMENT, p.str("local_id"), doc)
            }
            OutboxPlanner.TYPE_ORDER -> {
                val resp = api.createOrder(accountId, body, key)
                applyOrder(accountId, p.str("local_id"), resp, confirmedDraft = true)
            }
            OutboxPlanner.TYPE_ORDER_ACTION -> {
                val oid = requiredServerId(accountId, p, "order_id")
                val action = p.required("action")
                val resp = sendRebasing(accountId, row, p, body, RentalEntity.ORDER, oid) { b -> api.orderAction(accountId, oid, action, b, key) }
                applyOrder(accountId, null, resp, confirmedDraft = p.str("action") == "extend")
            }
            OutboxPlanner.TYPE_ASSET -> {
                val assetId = p.required("asset_id")
                if (p.bool("enabled") == true) api.enableAsset(accountId, assetId, body, key)
                else api.disableAsset(accountId, assetId, key)
                dao.get(RentalEntity.FLEET, assetId)?.let {
                    if (p.bool("enabled") == true) dao.setState(it.entity, it.id, STATE_SYNCED, null)
                    else dao.delete(it.entity, it.id)
                }
            }
            OutboxPlanner.TYPE_SIGNATURE -> {
                val oid = requiredServerId(accountId, p, "order_id")
                val resp = api.createSignature(accountId, oid, body, key)
                val s = resp.obj("signature") ?: resp
                val sid = s.str("id")
                val localId = p.str("local_id")
                if (localId != null && sid != null) mapId(accountId, localId, sid, RentalEntity.SIGNATURE)
                localId?.let { dao.setState(RentalEntity.SIGNATURE, it, STATE_SYNCED, null) }
            }
            OutboxPlanner.TYPE_HANDOVER -> {
                val oid = requiredServerId(accountId, p, "order_id")
                val lid = requiredServerId(accountId, p, "line_id")
                val photos = p.strings("photo_ids").mapNotNull { id ->
                    dbFactory.get(accountId).photoDao().getById(id)?.storageKey?.let { k ->
                        buildJsonObject { put("file_id", id); put("storage_key", k) }
                    }
                }
                val full0 = JsonObject(body + ("photos" to JsonArray(photos)))
                // Wave 2 meter photo: an account file upload, sent as `meter_photo_file_id`.
                val meterPhoto = p.str("meter_photo_path")?.let { path ->
                    uploadOnce(accountId, row, p, "meter_photo_file_id", path) { cu ->
                        val r = files.getPresignedUploadUrl(
                            accountId,
                            PresignedUploadRequest(entity_id = body.str("asset_id"), entity_type = "asset", filename = "meter_$cu.jpg", content_type = "image/jpeg"),
                        )
                        when (r) {
                            is NetworkResult.Success -> Reservation(r.data.file_upload_id, r.data.upload_url)
                            is NetworkResult.Error -> throw uploadError(r.code, r.message, "meter photo reserve")
                            else -> throw IllegalStateException("meter photo reserve")
                        }
                    }
                }
                val full = if (meterPhoto != null) JsonObject(full0 + ("meter_photo_file_id" to JsonPrimitive(meterPhoto))) else full0
                val kind = p.str("kind") ?: RentalsApi.HANDOVER_CHECKOUT
                val resp = api.lineHandover(accountId, oid, lid, kind, full, key)
                val localId = p.str("local_id")
                val server = resp.objs("handovers").firstOrNull { it.str("order_line_id") == lid }
                    ?: resp.objs("handovers").firstOrNull()
                if (server != null) replaceLocal(accountId, RentalEntity.HANDOVER, localId, server, warningNotice(resp))
                else localId?.let { dao.setState(RentalEntity.HANDOVER, it, STATE_SYNCED, warningNotice(resp)) }
                resp.obj("order")?.let { applyOrder(accountId, null, it, confirmedDraft = false) }
                if (kind == RentalsApi.HANDOVER_CHECKIN && localId != null) {
                    settleReadinessPlaceholders(accountId, localId, body.str("asset_id") ?: server?.str("asset_id"))
                }
            }
            OutboxPlanner.TYPE_READINESS -> executeReadiness(accountId, key, p)
            OutboxPlanner.TYPE_LINE_SWAP -> {
                val oid = requiredServerId(accountId, p, "order_id")
                val lid = requiredServerId(accountId, p, "line_id")
                val resp = api.lineSwap(accountId, oid, lid, body, key)
                val notice = warningNotice(resp)
                // The tentative line / allocation give way to the server's (applyOrder drops
                // unsynced allocations of the order when the server sends its set).
                val localNewLine = p.str("new_line_id")
                val serverNewLine = resp.str("new_line_id")
                if (localNewLine != null) {
                    dao.delete(RentalEntity.ORDER_LINE, localNewLine)
                    if (serverNewLine != null) mapId(accountId, localNewLine, serverNewLine, RentalEntity.ORDER_LINE)
                }
                p.str("new_alloc_id")?.let { dao.delete(RentalEntity.ALLOCATION, it) }
                resp.objs("handovers").forEach { h -> RentalRecords.record(RentalEntity.HANDOVER, h, notice = notice)?.let { dao.upsert(it) } }
                resp.obj("order")?.let { applyOrder(accountId, null, it, confirmedDraft = false) }
                // The returned unit gets a readiness check on the server: swap the placeholder for it.
                settleReadinessPlaceholders(accountId, key, p.str("old_asset_id"))
                if (notice != null) dao.setState(RentalEntity.ORDER, oid, STATE_SYNCED, notice)
            }
            OutboxPlanner.TYPE_LEDGER -> {
                val oid = requiredServerId(accountId, p, "order_id")
                val resp = api.createLedgerEntry(accountId, oid, body, key)
                val localId = p.str("local_id")
                val entry = resp.obj("entry")
                if (entry != null) replaceLocal(accountId, RentalEntity.LEDGER, localId, entry, warningNotice(resp))
                else localId?.let { dao.setState(RentalEntity.LEDGER, it, STATE_SYNCED, warningNotice(resp)) }
                resp.obj("order")?.let { applyOrder(accountId, null, it, confirmedDraft = false) }
            }
            OutboxPlanner.TYPE_INVOICE -> {
                val oid = requiredServerId(accountId, p, "order_id")
                val resp = api.createInvoice(accountId, oid, body, key)
                replaceLocal(accountId, RentalEntity.INVOICE, p.str("local_id"), resp.obj("invoice") ?: resp, warningNotice(resp))
            }
            OutboxPlanner.TYPE_ESIGN -> {
                val requestId = p.required("request_id")
                val oid = p.str("order_id")?.let { mappedId(accountId, it) }
                try {
                    api.signSignatureRequest(accountId, requestId, body, key)
                } catch (e: RentalsApiException) {
                    // 409/410: signed elsewhere, voided or expired — drop the capture and refresh.
                    if (!Esign.dropOnConflict(e.status) || e.code == "duplicate_client_uuid") throw e
                    Timber.i("[RentalOutbox] e-sign capture dropped: ${e.code}")
                    oid?.let { dao.get(RentalEntity.ORDER, it) }?.let {
                        dao.setState(it.entity, it.id, it.localState, NOTICE_ESIGN_DROPPED_PREFIX + (e.code ?: e.status.toString()))
                    }
                }
                oid?.let { online.get().refreshSignatures(accountId, it) }
            }
            OutboxPlanner.TYPE_BULK_RETURN -> {
                val oid = requiredServerId(accountId, p, "order_id")
                val lid = requiredServerId(accountId, p, "line_id")
                val resp = api.bulkReturn(accountId, oid, lid, body, key)
                val ret = resp.obj("return")
                if (ret != null) replaceLocal(accountId, RentalEntity.BULK_RETURN, p.str("local_id"), ret, warningNotice(resp))
                else p.str("local_id")?.let { dao.setState(RentalEntity.BULK_RETURN, it, STATE_SYNCED, warningNotice(resp)) }
                resp.obj("order")?.let { applyOrder(accountId, null, it, confirmedDraft = false) }
                warningNotice(resp)?.let { n -> dao.get(RentalEntity.ORDER, oid)?.let { dao.setState(it.entity, it.id, it.localState, n) } }
            }
            else -> throw OutboxPlanner.MalformedItem("unknown rental outbox type ${row.entityType}")
        }
    }

    private class Reservation(val fileId: String, val uploadUrl: String?)

    /**
     * Uploads the local JPEG at [path] once per queued item: the reserved file id is stored in the
     * payload ([idKey]) before the PUT and finalize, so a retry after a lost answer never reserves a
     * second file. Returns the file id to reference. A missing local file fails visibly.
     */
    private suspend fun uploadOnce(
        accountId: String,
        row: SyncQueueEntity,
        p: JsonObject,
        idKey: String,
        path: String,
        reserve: suspend (clientUuid: String) -> Reservation,
    ): String {
        p.str("${idKey}_done")?.let { return it }
        val file = java.io.File(path)
        if (!file.exists() || file.length() == 0L) throw OutboxPlanner.MalformedItem("photo file missing: ${file.name}")
        val dao = dbFactory.get(accountId).rentalDao()
        val cu = p.str("${idKey}_client_uuid") ?: UUID.randomUUID().toString().also {
            dao.updateQueuePayload(row.queueId, encode(JsonObject(p + ("${idKey}_client_uuid" to JsonPrimitive(it)))))
        }
        val res = reserve(cu)
        if (res.uploadUrl != null) {
            when (val put = files.uploadPhotoBinary(res.uploadUrl, file.readBytes())) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Error -> throw uploadError(put.code, put.message, "photo upload")
                else -> throw IllegalStateException("photo upload")
            }
            when (val fin = files.finalizeFileUpload(accountId, res.fileId, file.length())) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Error -> throw uploadError(fin.code, fin.message, "photo finalize")
                else -> throw IllegalStateException("photo finalize")
            }
        }
        val latest = dao.queueItemsOfTypes(listOf(row.entityType)).firstOrNull { it.queueId == row.queueId }
            ?.let { parseObj(it.payload) } ?: p
        dao.updateQueuePayload(
            row.queueId,
            encode(JsonObject(latest + mapOf("${idKey}_done" to JsonPrimitive(res.fileId), "${idKey}_client_uuid" to JsonPrimitive(cu)))),
        )
        return res.fileId
    }

    /** No answer → offline (no attempt used); an answered upload error is retried (bounded). */
    private fun uploadError(code: Int, message: String, what: String): Exception =
        if (QueueRetryPolicy.isNoResponse(code)) java.io.IOException("$what: $message") else IllegalStateException("$what failed: HTTP $code")

    /**
     * Sends a versioned edit; a 409 version `conflict` goes through the hybrid policy
     * ([ConflictMerge]): disjoint changes are rebased onto `current` and resent once with the
     * same idempotency key (the server caches only 2xx answers), anything else is parked as a
     * conflict naming the clashing fields. Either way the server's non-clashing changes are
     * applied to the local copy of [entity] [id].
     *
     * After a conflict the payload's base becomes `current` (and the rebase marker is cleared),
     * so the user's Retry re-applies their changes on top of the latest server row.
     */
    private suspend fun sendRebasing(
        accountId: String,
        row: SyncQueueEntity,
        p: JsonObject,
        body: JsonObject,
        entity: String,
        id: String,
        send: suspend (JsonObject) -> JsonObject,
    ): JsonObject {
        val dao = dbFactory.get(accountId).rentalDao()
        var payload = p
        var request = body
        // At most two sends: the rebase marker is set before the resend, so a second 409 is final.
        while (true) {
            val e = try {
                return send(request)
            } catch (e: RentalsApiException) {
                if (!e.isVersionConflict()) throw e
                e
            }
            val versionField = if (entity == RentalEntity.ORDER) "version" else "server_version"
            val raw = e.body?.obj("current")
            val current = if (entity == RentalEntity.ORDER) raw?.let { RentalRecords.splitOrder(it)?.order ?: it } else raw
            val decision = ConflictMerge.resolve(
                base = payload.obj(ConflictMerge.KEY_BASE),
                changes = payload.obj(ConflictMerge.KEY_CHANGES) ?: JsonObject(emptyMap()),
                current = current,
                body = request,
                versionField = versionField,
                alreadyRebased = payload.bool(ConflictMerge.KEY_REBASED) == true,
            )
            val clashing = (decision as? ConflictMerge.Decision.Conflict)?.fields.orEmpty()
            if (current != null) applyServerFields(accountId, entity, id, current, decision.serverChanged - clashing)
            when (decision) {
                is ConflictMerge.Decision.Resend -> {
                    // Persist first: a later 409 (even after a crash) must not rebase again.
                    payload = JsonObject(payload + mapOf("body" to decision.body, ConflictMerge.KEY_REBASED to JsonPrimitive(true)))
                    dao.updateQueuePayload(row.queueId, encode(payload))
                    Timber.i("[RentalOutbox] ${row.entityType} rebased onto the server row (server changed ${decision.serverChanged})")
                    request = decision.body
                }
                is ConflictMerge.Decision.Conflict -> {
                    if (current != null) {
                        val (changes, retryBody) = ConflictMerge.forRetry(
                            payload.obj(ConflictMerge.KEY_BASE), payload.obj(ConflictMerge.KEY_CHANGES) ?: JsonObject(emptyMap()),
                            current, request, versionField,
                        )
                        val next = payload + mapOf(
                            "body" to retryBody,
                            ConflictMerge.KEY_BASE to current,
                            ConflictMerge.KEY_CHANGES to changes,
                            ConflictMerge.KEY_REBASED to JsonPrimitive(false),
                        )
                        dao.updateQueuePayload(row.queueId, encode(JsonObject(next)))
                    }
                    val notice = if (clashing.isNotEmpty()) NOTICE_FIELD_CONFLICT_PREFIX + clashing.joinToString(",") else conflictNotice(e)
                    throw FieldConflict(notice, e)
                }
            }
        }
    }

    private fun RentalsApiException.isVersionConflict() = status == 409 && code == "conflict"

    /** A version conflict the user must resolve; [notice] is shown on the record. */
    private class FieldConflict(val notice: String, cause: Throwable) : Exception(notice, cause)

    /** The server's (non-clashing) changes to [fields] applied to the local copy, keeping its state. */
    private suspend fun applyServerFields(accountId: String, entity: String, id: String, current: JsonObject, fields: Set<String>) {
        if (fields.isEmpty()) return
        val dao = dbFactory.get(accountId).rentalDao()
        val rec = dao.get(entity, id) ?: return
        val merged = ConflictMerge.applyServerFields(rec.obj, current, fields)
        RentalRecords.record(entity, merged, rec.localState, rec.clientUuid, rec.notice)?.copy(id = rec.id)?.let { dao.upsert(it) }
    }

    private fun encode(o: JsonObject) = RentalJson.encodeToString(JsonObject.serializer(), o)

    /**
     * Readiness decision (stage B §3). The check to PATCH is: the server id the local row was
     * already mapped to; the row itself when it came from the server (`mode = patch`); for a
     * placeholder written on an offline check-in, the open check the server opened at check-in
     * (else a new one); for a check started on the device, a new one (`POST`, replay-safe by
     * `client_uuid`). A replayed PATCH answers 409 (`invalid_transition` / version `conflict`);
     * when the current row already has the target status the decision counts as applied.
     */
    private suspend fun executeReadiness(accountId: String, key: String, p: JsonObject) {
        val dao = dbFactory.get(accountId).rentalDao()
        val localId = p.required("local_id")
        val assetId = p.required("asset_id")
        val target = p.required("status")
        val mapped = mappedId(accountId, localId)
        val createBody = { JsonObject(RentalRequests.readinessCreate(assetId, null, null) + ("client_uuid" to JsonPrimitive(key))) }
        var fresh: JsonObject? = null
        val checkId: String = when {
            mapped != localId || p.str("mode") == "patch" -> mapped
            p.str("mode") == "resolve" -> {
                fresh = api.readinessChecks(accountId, assetId).objs("readiness_checks")
                    .firstOrNull { it.str("status") in LocalFleet.READINESS_OPEN }
                    ?: api.createReadinessCheck(accountId, createBody(), key)
                fresh.str("id") ?: throw IllegalStateException("readiness check without id")
            }
            else -> {
                fresh = api.createReadinessCheck(accountId, createBody(), key)
                fresh.str("id") ?: throw IllegalStateException("readiness check without id")
            }
        }
        val local = dao.get(RentalEntity.READINESS_CHECK, checkId)?.takeIf { it.localState == STATE_SYNCED }
        val version = fresh?.long("server_version") ?: local?.obj?.long("server_version") ?: p.long("server_version")
        val photoDao = dbFactory.get(accountId).photoDao()
        val items = Readiness.parseItems(p["items"] as? JsonArray)
        val keys = items.flatMap { it.photoIds }.associateWith { photoDao.getById(it)?.storageKey }
        val body = RentalRequests.readinessUpdate(target, Readiness.serverItems(items) { keys[it] }, p.str("notes"), version)
        // A distinct key: the POST above already used [key] as its Idempotency-Key.
        val patchKey = UUID.nameUUIDFromBytes("$key:patch".toByteArray()).toString()
        val row = try {
            api.updateReadinessCheck(accountId, checkId, body, patchKey)
        } catch (e: RentalsApiException) {
            if (e.status != 409) throw e
            val current = e.body?.obj("current") ?: runCatching { api.readinessCheck(accountId, checkId) }.getOrNull()
            if (current != null && Readiness.alreadyApplied(current, target)) current else throw e
        }
        replaceLocal(accountId, RentalEntity.READINESS_CHECK, localId, row)
    }

    /**
     * After a check-in (or swap) [handoverKey] synced: the server opened the unit's checklist, so
     * the device placeholder is replaced by the server's open checks. A placeholder the user
     * already decided on stays (its queued decision resolves to the server check).
     */
    private suspend fun settleReadinessPlaceholders(accountId: String, handoverKey: String, assetId: String?) {
        assetId ?: return
        val dao = dbFactory.get(accountId).rentalDao()
        val placeholders = dao.all(RentalEntity.READINESS_CHECK).filter { r ->
            val o = r.obj
            o.bool(Readiness.KEY_PLACEHOLDER) == true && o.str(Readiness.KEY_HANDOVER_LOCAL_ID) == handoverKey &&
                o.bool(Readiness.KEY_QUEUED) != true
        }
        if (placeholders.isEmpty()) return
        try {
            val open = api.readinessChecks(accountId, assetId).objs("readiness_checks")
            dao.upsertAll(open.mapNotNull { c ->
                RentalRecords.record(RentalEntity.READINESS_CHECK, c)
                    ?.takeIf { dao.get(it.entity, it.id)?.localState.let { s -> s == null || s == STATE_SYNCED } }
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The feed brings the server check on the next pull.
            Timber.d(e, "[RentalOutbox] readiness refresh after check-in failed")
        }
        placeholders.forEach { dao.delete(it.entity, it.id) }
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
            OutboxPlanner.TYPE_READINESS -> RentalEntity.READINESS_CHECK to p.str("local_id")
            OutboxPlanner.TYPE_LINE_SWAP -> RentalEntity.ORDER_LINE to p.str("line_id")
            OutboxPlanner.TYPE_BULK_RETURN -> RentalEntity.BULK_RETURN to p.str("local_id")
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
        /** `field_conflict:<field,field>` — a version conflict on these fields (changed here and on the server). */
        const val NOTICE_FIELD_CONFLICT_PREFIX = "field_conflict:"
        /** `esign_dropped:<code>` — a queued in-person signature was refused (signed elsewhere / voided / expired). */
        const val NOTICE_ESIGN_DROPPED_PREFIX = "esign_dropped:"
    }
}
