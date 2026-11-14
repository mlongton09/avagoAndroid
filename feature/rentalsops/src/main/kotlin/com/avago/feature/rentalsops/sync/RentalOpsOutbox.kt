package com.avago.feature.rentalsops.sync

import android.content.Context
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.PresignedUploadRequest
import com.avago.core.sync.ExternalOutbox
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.ApiResult
import com.avago.feature.rentalsops.data.CursorEntity
import com.avago.feature.rentalsops.data.RecordEntity
import com.avago.feature.rentalsops.data.RentalOpsApi
import com.avago.feature.rentalsops.data.RentalOpsDao
import com.avago.feature.rentalsops.data.RentalOpsDatabaseFactory
import com.avago.feature.rentalsops.data.RentalOpsEntities
import com.avago.feature.rentalsops.domain.isDeleted
import com.avago.feature.rentalsops.domain.parseInstant
import com.avago.feature.rentalsops.domain.str
import com.avago.feature.rentalsops.push.RentalOpsNotifier
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import com.avago.feature.rentalsops.widget.RentalOpsWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains queued rentals P2 actions (driver task events, unit assignments, transfers) and pulls
 * the P2 feeds into the module's local store. Invoked by SyncEngine via [ExternalOutbox].
 *
 * Guarantees:
 * - FIFO per subject: a transient failure stops later events of the same task/transfer in this
 *   cycle so the server sees them in order.
 * - Bounded: rows with ≥ [DrainOutcome.MAX_ATTEMPTS] transient failures are parked (still
 *   `error`) until the user taps Retry; pulls are capped at [MAX_PAGES] pages per feed.
 * - Business rejections become `conflict` rows (kept, visible, with Discard/Retry) plus one
 *   notification — never silently dropped or retried forever.
 * - Idempotent: every request carries the queue id as `Idempotency-Key`; transfers carry the
 *   local transfer id as `client_uuid`; replays of an already-applied step count as done.
 */
@Singleton
class RentalOpsOutbox @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dbFactory: DatabaseFactory,
    private val opsDb: RentalOpsDatabaseFactory,
    private val api: RentalOpsApi,
    private val client: AvagoServiceClient,
    private val notifier: RentalOpsNotifier,
) : ExternalOutbox {

    override val entityTypes: Set<String> = RentalOpsQueueTypes.ALL

    override suspend fun drain(accountId: String, items: List<SyncQueueEntity>): Int {
        val queue = dbFactory.get(accountId).syncQueueDao()
        val dao = opsDb.dao(accountId)
        val blockedSubjects = mutableSetOf<String>()
        var ok = 0
        for (item in items.sortedBy { it.createdAt }) {
            if (item.syncStatus == "conflict" || item.attempts >= DrainOutcome.MAX_ATTEMPTS) {
                blockedSubjects += subject(item)
                continue
            }
            if (subject(item) in blockedSubjects) continue
            val outcome = try {
                when (item.entityType) {
                    RentalOpsQueueTypes.TASK_EVENT -> drainTaskEvent(accountId, dao, item)
                    RentalOpsQueueTypes.ASSIGN -> drainAssign(accountId, dao, item)
                    RentalOpsQueueTypes.TRANSFER -> drainTransfer(accountId, dao, item)
                    else -> DrainOutcome.Retry("unknown type")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[RentalOps] drain ${item.entityType} failed")
                DrainOutcome.Retry(e.message ?: e.javaClass.simpleName)
            }
            when (outcome) {
                DrainOutcome.Done -> {
                    queue.markSuccess(item.queueId)
                    ok++
                }
                is DrainOutcome.Rejected -> {
                    queue.markError(item.queueId, outcome.reason) // records last_error
                    queue.markConflict(item.queueId)
                    blockedSubjects += subject(item)
                    notifier.postRejected(item.queueId, describe(item), outcome.reason, routeFor(item))
                }
                is DrainOutcome.Retry -> {
                    queue.markError(item.queueId, outcome.reason)
                    blockedSubjects += subject(item)
                }
            }
        }
        if (ok > 0) RentalOpsWidgetUpdater.request(ctx)
        return ok
    }

    /** Ordering key: events of one task / one transfer must reach the server in order. */
    private fun subject(item: SyncQueueEntity) = "${item.entityType}:${item.entityId}"

    private fun describe(item: SyncQueueEntity): String = ctx.getString(
        when (item.entityType) {
            RentalOpsQueueTypes.TASK_EVENT -> R.string.rentalops_kind_task_update
            RentalOpsQueueTypes.ASSIGN -> R.string.rentalops_kind_assignment
            else -> R.string.rentalops_kind_transfer
        },
    )

    private fun routeFor(item: SyncQueueEntity): String = when (item.entityType) {
        RentalOpsQueueTypes.TASK_EVENT -> RentalOpsRoutes.task(item.entityId)
        RentalOpsQueueTypes.ASSIGN -> RentalOpsRoutes.ASSIGN
        else -> RentalOpsRoutes.TRANSFERS
    }

    // ---- task events ------------------------------------------------------------------

    private suspend fun drainTaskEvent(accountId: String, dao: RentalOpsDao, item: SyncQueueEntity): DrainOutcome {
        val p = outboxJson.decodeFromString(TaskEventPayload.serializer(), item.payload ?: return DrainOutcome.Rejected("empty payload"))
        val media = dao.mediaForEvent(item.queueId)
        val remote = mutableMapOf<String, String>()
        for (m in media) {
            if (m.uploaded && m.remoteId != null) {
                remote[m.id] = m.remoteId
                continue
            }
            val file = File(m.localPath)
            if (!file.exists()) return DrainOutcome.Rejected(ctx.getString(R.string.rentalops_err_media_missing))
            when (val up = uploadProof(accountId, p.taskId, m.id, file)) {
                is UploadResult.Ok -> {
                    dao.markUploaded(m.id, up.fileUploadId)
                    remote[m.id] = up.fileUploadId
                }
                is UploadResult.Failed -> return up.outcome
            }
        }
        val photos = media.filter { it.kind == "photo" }.mapNotNull { remote[it.id] }
        val signature = media.firstOrNull { it.kind == "signature" }?.let { remote[it.id] }
        val body = JsonObject(
            buildMap {
                put("at", JsonPrimitive(p.at))
                p.lat?.let { put("lat", JsonPrimitive(it)) }
                p.lng?.let { put("lng", JsonPrimitive(it)) }
                p.reason?.let { put("reason", JsonPrimitive(it)) }
                if (photos.isNotEmpty()) put("photo_file_ids", JsonArray(photos.map(::JsonPrimitive)))
                signature?.let { put("signature_file_id", JsonPrimitive(it)) }
                p.signedName?.let { put("signed_name", JsonPrimitive(it)) }
                p.notes?.let { put("notes", JsonPrimitive(it)) }
            },
        )
        val r = api.taskAction(accountId, p.taskId, p.action, body, item.queueId)
        if (r is ApiResult.Ok) cacheRow(dao, RentalOpsEntities.TASK, r.body)
        if (alreadyApplied(r, TASK_REACHED[p.action].orEmpty())) return DrainOutcome.Done
        return DrainOutcome.classify(r)
    }

    private sealed class UploadResult {
        data class Ok(val fileUploadId: String) : UploadResult()
        data class Failed(val outcome: DrainOutcome) : UploadResult()
    }

    /** presigned-url → PUT bytes → finalize (server-issued `file_upload_id`, cited by the action). */
    private suspend fun uploadProof(accountId: String, taskId: String, mediaId: String, file: File): UploadResult {
        val reserve = client.getPresignedUploadUrl(
            accountId,
            PresignedUploadRequest(entity_id = taskId, entity_type = RentalOpsEntities.TASK, filename = "$mediaId.jpg", content_type = "image/jpeg"),
        )
        val slot = when (reserve) {
            is NetworkResult.Success -> reserve.data
            is NetworkResult.Error -> return UploadResult.Failed(networkOutcome(reserve.code, reserve.message, "proof reserve"))
            else -> return UploadResult.Failed(DrainOutcome.Retry("proof reserve unauthorized"))
        }
        when (val put = client.uploadPhotoBinary(slot.upload_url, file.readBytes())) {
            is NetworkResult.Success -> Unit
            is NetworkResult.Error -> return UploadResult.Failed(DrainOutcome.Retry("proof upload ${put.code}"))
            else -> return UploadResult.Failed(DrainOutcome.Retry("proof upload"))
        }
        return when (val fin = client.finalizeFileUpload(accountId, slot.file_upload_id, file.length())) {
            is NetworkResult.Success -> UploadResult.Ok(slot.file_upload_id)
            is NetworkResult.Error -> UploadResult.Failed(networkOutcome(fin.code, fin.message, "proof finalize"))
            else -> UploadResult.Failed(DrainOutcome.Retry("proof finalize"))
        }
    }

    private fun networkOutcome(code: Int, message: String, what: String): DrainOutcome =
        DrainOutcome.classify(if (code <= 0) ApiResult.Transport(message) else ApiResult.Http(code, null, "$what: $message"))

    // ---- unit assignment ---------------------------------------------------------------

    private suspend fun drainAssign(accountId: String, dao: RentalOpsDao, item: SyncQueueEntity): DrainOutcome {
        val p = outboxJson.decodeFromString(AssignPayload.serializer(), item.payload ?: return DrainOutcome.Rejected("empty payload"))
        var r = api.assignUnitC(accountId, p.orderId, p.lineId, p.assetId, item.queueId)
        if (r is ApiResult.Http && r.status in NOT_DEPLOYED) {
            // Stage C class booking not routed: stage A line PATCH gives the line its unit.
            r = api.assignUnit(accountId, p.orderId, p.lineId, p.assetId, item.queueId)
        }
        if (r is ApiResult.Ok) {
            // Both return the order (with lines) or the line; cache whichever line we find.
            val line = (r.body["lines"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { it.str("id") == p.lineId }
                ?: r.body.takeIf { it.str("id") == p.lineId }
            if (line != null) cacheRow(dao, RentalOpsEntities.ORDER_LINE, line)
        }
        return DrainOutcome.classify(r)
    }

    // ---- transfers ---------------------------------------------------------------------

    private suspend fun drainTransfer(accountId: String, dao: RentalOpsDao, item: SyncQueueEntity): DrainOutcome {
        val p = outboxJson.decodeFromString(TransferPayload.serializer(), item.payload ?: return DrainOutcome.Rejected("empty payload"))
        val local = dao.get(RentalOpsEntities.TRANSFER, p.transferId)?.let { runCatching { outboxJson.parseToJsonElement(it.json) as JsonObject }.getOrNull() }
        if (local?.get("_fallback_moved")?.let { (it as? JsonPrimitive)?.booleanOrNull } == true && p.op != "create") {
            return DrainOutcome.Done // completed by the immediate-move fallback already
        }
        return when (p.op) {
            "create" -> {
                val shipAt = parseInstant(p.at) ?: Instant.now()
                val arriveBy = parseInstant(p.arriveBy)?.takeIf { it.isAfter(shipAt) } ?: shipAt.plus(DEFAULT_TRANSIT)
                val body = JsonObject(
                    buildMap {
                        // The local id doubles as client_uuid: replays return the same server row.
                        put("client_uuid", JsonPrimitive(p.transferId))
                        put("asset_id", JsonPrimitive(p.assetId))
                        p.toLocationId?.let { put("to_location_id", JsonPrimitive(it)) }
                        put("ship_at", JsonPrimitive(shipAt.toString()))
                        put("arrive_by", JsonPrimitive(arriveBy.toString()))
                        p.reason?.let { put("reason", JsonPrimitive(it)) }
                    },
                )
                val r = api.createTransfer(accountId, body, item.queueId)
                if (r is ApiResult.Http && r.status in NOT_DEPLOYED) {
                    return fallbackMove(accountId, dao, item, p, local)
                }
                if (r is ApiResult.Ok) {
                    val id = r.body.str("id")
                    if (id != null && id != p.transferId) {
                        // Hide the local row but keep it as an alias so queued ship/receive find the server id.
                        val alias = JsonObject((local ?: JsonObject(emptyMap())) + mapOf("_server_id" to JsonPrimitive(id), "_pending" to JsonPrimitive(false)))
                        dao.upsertAll(listOf(RecordEntity(RentalOpsEntities.TRANSFER, p.transferId, alias.toString(), null, true, System.currentTimeMillis())))
                    }
                    cacheRow(dao, RentalOpsEntities.TRANSFER, r.body)
                }
                DrainOutcome.classify(r)
            }
            "ship", "receive" -> {
                val serverId = local?.str("_server_id") ?: p.transferId
                val body = JsonObject(mapOf("at" to JsonPrimitive(p.at)))
                val r = api.transferAction(accountId, serverId, p.op, body, item.queueId)
                if (r is ApiResult.Ok) cacheRow(dao, RentalOpsEntities.TRANSFER, r.body)
                if (alreadyApplied(r, TRANSFER_REACHED[p.op].orEmpty())) return DrainOutcome.Done
                DrainOutcome.classify(r)
            }
            else -> DrainOutcome.Rejected("unknown transfer op ${p.op}")
        }
    }

    /**
     * Stage C transfer routes not deployed: move the unit with the existing
     * `POST /assets/{id}/transfer` (immediate relocation, location history kept server-side).
     * A replay after a lost ack answers 400 "already at the destination" → treat as done.
     */
    private suspend fun fallbackMove(
        accountId: String,
        dao: RentalOpsDao,
        item: SyncQueueEntity,
        p: TransferPayload,
        local: JsonObject?,
    ): DrainOutcome {
        val to = p.toLocationId ?: return DrainOutcome.Rejected("missing destination")
        val r = api.moveAsset(accountId, p.assetId, to, p.reason, item.queueId)
        val alreadyThere = r is ApiResult.Http && r.status == 400 && r.message.contains("already at the destination", ignoreCase = true)
        val outcome = if (alreadyThere) DrainOutcome.Done else DrainOutcome.classify(r)
        if (outcome == DrainOutcome.Done) {
            val moved = JsonObject(
                (local ?: JsonObject(emptyMap())) + mapOf(
                    "status" to JsonPrimitive("received"),
                    "received_at" to JsonPrimitive(p.at),
                    "_fallback_moved" to JsonPrimitive(true),
                    "_pending" to JsonPrimitive(false),
                ),
            )
            dao.upsertAll(listOf(RecordEntity(RentalOpsEntities.TRANSFER, p.transferId, moved.toString(), null, false, System.currentTimeMillis())))
        }
        return outcome
    }

    private suspend fun cacheRow(dao: RentalOpsDao, entity: String, row: JsonObject) {
        val id = row.str(RentalOpsEntities.pkField(entity)) ?: return
        dao.upsertAll(listOf(RecordEntity(entity, id, row.toString(), (row["seq"] as? JsonPrimitive)?.longOrNull, row.isDeleted(), System.currentTimeMillis())))
    }

    // ---- pull ----------------------------------------------------------------------------

    private enum class Pull { OK, FORBIDDEN, UNSUPPORTED, FAILED }

    override suspend fun pull(accountId: String) {
        val dao = opsDb.dao(accountId)
        for (entity in RentalOpsEntities.PULLED) {
            try {
                if (entity == RentalOpsEntities.TASK) pullTasks(accountId, dao) else pullFeed(dao, entity, entity) { c -> api.syncFeed(accountId, entity, c) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[RentalOps] pull $entity failed")
            }
        }
        RentalOpsWidgetUpdater.request(ctx)
    }

    /**
     * Dispatchers/viewers (`rental.view`) get every task through the sync feed; drivers with only
     * `rental.drive` get `dispatch/my-tasks`. The server decides: a 403 on one source switches to
     * the other and starts that feed from scratch (rows of the old source are cleared).
     */
    private suspend fun pullTasks(accountId: String, dao: RentalOpsDao) {
        val source = dao.cursor(TASK_SOURCE)?.cursor ?: SOURCE_ALL
        val result = runTaskSource(accountId, dao, source)
        if (result == Pull.FORBIDDEN) {
            val other = if (source == SOURCE_ALL) SOURCE_MINE else SOURCE_ALL
            dao.clearEntity(RentalOpsEntities.TASK)
            dao.upsertCursor(CursorEntity(TASK_SOURCE, other, System.currentTimeMillis()))
            dao.upsertCursor(CursorEntity(RentalOpsEntities.TASK, null, null))
            if (runTaskSource(accountId, dao, other) == Pull.FORBIDDEN) dao.clearEntity(RentalOpsEntities.TASK)
        }
    }

    private suspend fun runTaskSource(accountId: String, dao: RentalOpsDao, source: String): Pull =
        if (source == SOURCE_MINE) {
            pullFeed(dao, RentalOpsEntities.TASK, RentalOpsEntities.TASK, clearOnForbidden = false) { c -> api.myTasksFeed(accountId, c) }
        } else {
            pullFeed(dao, RentalOpsEntities.TASK, RentalOpsEntities.TASK, clearOnForbidden = false) { c -> api.syncFeed(accountId, RentalOpsEntities.TASK, c) }
        }

    private suspend fun pullFeed(
        dao: RentalOpsDao,
        entity: String,
        cursorKey: String,
        clearOnForbidden: Boolean = true,
        fetch: suspend (String?) -> ApiResult,
    ): Pull {
        val now = System.currentTimeMillis()
        val state = dao.cursor(cursorKey)
        // Feed not deployed yet: re-probe at most every UNSUPPORTED_REPROBE_MS (bounded calls).
        if (state?.unsupported == true && now - (state.lastSyncedAt ?: 0L) < UNSUPPORTED_REPROBE_MS) return Pull.UNSUPPORTED
        var cursor = state?.cursor
        repeat(MAX_PAGES) {
            when (val r = fetch(cursor)) {
                is ApiResult.Ok -> {
                    val items = (r.body["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                    val pk = RentalOpsEntities.pkField(entity)
                    val rows = items.mapNotNull { o ->
                        val id = o.str(pk) ?: o.str("id") ?: return@mapNotNull null
                        RecordEntity(entity, id, o.toString(), (o["seq"] as? JsonPrimitive)?.longOrNull, o.isDeleted(), now)
                    }
                    if (rows.isNotEmpty()) dao.upsertAll(rows)
                    val next = r.body.str("next_cursor") ?: cursor
                    cursor = next
                    dao.upsertCursor(CursorEntity(cursorKey, next, now, unsupported = false))
                    val more = (r.body["has_more"] as? JsonPrimitive)?.booleanOrNull == true
                    if (!more || items.isEmpty()) return Pull.OK
                }
                is ApiResult.Http -> {
                    return when (r.status) {
                        in NOT_DEPLOYED -> {
                            // Keep last-known-good rows; just remember the feed is unavailable.
                            dao.upsertCursor(CursorEntity(cursorKey, cursor, now, unsupported = true))
                            Pull.UNSUPPORTED
                        }
                        403 -> {
                            // Access revoked: never keep showing cached rows.
                            if (clearOnForbidden) {
                                dao.clearEntity(entity)
                                dao.upsertCursor(CursorEntity(cursorKey, null, now, unsupported = false))
                            }
                            Pull.FORBIDDEN
                        }
                        else -> {
                            Timber.d("[RentalOps] pull $entity HTTP ${r.status} ${r.code}")
                            Pull.FAILED
                        }
                    }
                }
                is ApiResult.Transport -> return Pull.FAILED
            }
        }
        return Pull.OK
    }

    companion object {
        const val MAX_PAGES = 50
        const val UNSUPPORTED_REPROBE_MS = 6 * 60 * 60 * 1000L
        val NOT_DEPLOYED = setOf(404, 405, 501)
        /** Default transit window when the user did not pick an arrival time. */
        val DEFAULT_TRANSIT: Duration = Duration.ofHours(24)

        private const val TASK_SOURCE = "rental_delivery_task#source"
        private const val SOURCE_ALL = "all"
        private const val SOURCE_MINE = "mine"

        /** Statuses at which a replayed action has already taken effect (lost ack). */
        private val TASK_REACHED = mapOf(
            "start" to setOf("en_route", "arrived", "completed"),
            "arrive" to setOf("arrived", "completed"),
            "complete" to setOf("completed"),
            "fail" to setOf("failed"),
        )
        private val TRANSFER_REACHED = mapOf(
            "ship" to setOf("in_transit", "received"),
            "receive" to setOf("received"),
        )

        /** 409 `invalid_transition` whose `details.from` shows the step already happened. */
        internal fun alreadyApplied(r: ApiResult, reached: Set<String>): Boolean =
            r is ApiResult.Http && r.status == 409 && r.code == "invalid_transition" &&
                (r.details?.get("from") as? JsonPrimitive)?.content in reached
    }
}
