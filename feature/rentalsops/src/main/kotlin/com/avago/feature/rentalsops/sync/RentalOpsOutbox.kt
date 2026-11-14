package com.avago.feature.rentalsops.sync

import com.avago.core.data.sync.QueueRetryPolicy
import android.content.Context
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult

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
 * - Bounded: rows with ≥ [DrainOutcome.MAX_ATTEMPTS] answered transient failures are parked (still
 *   `error`) until the user taps Retry; pulls are capped at [MAX_PAGES] pages per feed.
 * - Business rejections become `conflict` rows (kept, visible, with Discard/Retry) plus one
 *   notification — never silently dropped or retried forever.
 * - Idempotent: every request carries the queue id as `Idempotency-Key`; transfers carry the
 *   local transfer id as `client_uuid`, proof uploads the media id; replays of an
 *   already-applied step count as done.
 * - `403 not_assigned` (task reassigned while offline) is never retried: the action and the
 *   later queued events of that task are dropped with their media, the driver is told, and
 *   `my-tasks` is re-pulled (it delivers the tombstone).
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
        val dropped = mutableSetOf<String>()
        var reassigned = false
        var ok = 0
        val ordered = items.sortedBy { it.createdAt }
        for (item in ordered) {
            if (item.queueId in dropped) continue
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
            } catch (e: java.io.IOException) {
                DrainOutcome.Offline(e.message ?: e.javaClass.simpleName)
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
                is DrainOutcome.Reassigned -> {
                    // Drop this action and every queued event of the same task (incl. parked or
                    // rejected ones): none can succeed any more.
                    val same = ordered.filter { subject(it) == subject(item) }
                    for (row in same) {
                        dao.mediaForEvent(row.queueId).forEach { File(it.localPath).delete(); dao.deleteMedia(it.id) }
                        queue.softDelete(row.queueId)
                        dropped += row.queueId
                    }
                    if (item.entityType == RentalOpsQueueTypes.TASK_EVENT) {
                        dao.draftMedia(item.entityId).forEach { File(it.localPath).delete(); dao.deleteMedia(it.id) }
                    }
                    blockedSubjects += subject(item)
                    reassigned = true
                    notifier.postReassigned(item.entityId, taskLabel(dao, item.entityId))
                }
                is DrainOutcome.Retry -> {
                    queue.markError(item.queueId, outcome.reason)
                    blockedSubjects += subject(item)
                }
                is DrainOutcome.Offline -> {
                    // Never reached the server: keep it pending without using an attempt and
                    // stop; the remaining rows would fail the same way.
                    queue.markOfflineRetry(item.queueId, "offline: ${outcome.reason}".take(500))
                    break
                }
            }
        }
        if (reassigned) {
            try {
                pullTasks(accountId, dao)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[RentalOps] re-pull after not_assigned failed")
            }
        }
        return ok
    }

    /** "R-1042 · Builder Ltd" for the reassignment notice (null when the task is not cached). */
    private suspend fun taskLabel(dao: RentalOpsDao, taskId: String): String? {
        val o = dao.get(RentalOpsEntities.TASK, taskId)?.let { runCatching { outboxJson.parseToJsonElement(it.json) as JsonObject }.getOrNull() }
            ?: return null
        return listOfNotNull(o.str("order_number"), o.str("customer_display_name") ?: o.str("customer_name"))
            .joinToString(" · ").ifEmpty { null }
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
            when (val up = uploadProof(accountId, p.taskId, m.id, m.kind, file)) {
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

    /**
     * `POST …/proof-uploads` response. A replay of an already uploaded file has
     * `upload_status: "uploaded"` and `upload_url: null` (nothing to PUT or finalize); a replay
     * of a pending reservation carries a fresh `upload_url`.
     */
    data class ProofReservation(val fileUploadId: String, val uploadUrl: String?, val uploadStatus: String?) {
        val alreadyUploaded: Boolean get() = uploadStatus == "uploaded" && uploadUrl == null

        companion object {
            fun from(body: JsonObject): ProofReservation? {
                val id = body.str("file_upload_id") ?: return null
                return ProofReservation(id, body.str("upload_url"), body.str("upload_status"))
            }
        }
    }

    private sealed class UploadResult {
        data class Ok(val fileUploadId: String) : UploadResult()
        data class Failed(val outcome: DrainOutcome) : UploadResult()
    }

    /**
     * Stage C proof upload: reserve (`client_uuid` = local media id, so a retry after a lost
     * response gets the same `file_upload_id`) → PUT bytes (skipped when the replay says the
     * bytes are already there: `upload_url` null) → finalize → id cited by `complete`.
     */
    private suspend fun uploadProof(accountId: String, taskId: String, mediaId: String, kind: String, file: File): UploadResult {
        val proofKind = if (kind == "signature") "signature" else "photo"
        // Both kinds are stored as JPEG (signatures are flattened on white before saving).
        val reserve = api.proofUpload(accountId, taskId, mediaId, proofKind, "$mediaId.jpg", "image/jpeg")
        if (reserve !is ApiResult.Ok) return UploadResult.Failed(DrainOutcome.classify(reserve))
        val res = ProofReservation.from(reserve.body)
            ?: return UploadResult.Failed(DrainOutcome.Retry("proof upload: no file_upload_id"))
        val fileId = res.fileUploadId
        val uploadUrl = res.uploadUrl
        if (uploadUrl != null) {
            when (val put = client.uploadPhotoBinary(uploadUrl, file.readBytes())) {
                is NetworkResult.Success -> Unit
                // An expired upload URL (403) re-reserves on the next run, so answered errors are retries.
                is NetworkResult.Error -> return UploadResult.Failed(
                    if (QueueRetryPolicy.isNoResponse(put.code)) DrainOutcome.Offline("proof upload: ${put.message}")
                    else DrainOutcome.Retry("proof upload ${put.code}"),
                )
                else -> return UploadResult.Failed(DrainOutcome.Retry("proof upload"))
            }
        }
        if (res.alreadyUploaded) return UploadResult.Ok(fileId)
        return when (val fin = client.finalizeFileUpload(accountId, fileId, file.length())) {
            is NetworkResult.Success -> UploadResult.Ok(fileId)
            is NetworkResult.Error -> UploadResult.Failed(networkOutcome(fin.code, fin.message, "proof finalize"))
            else -> UploadResult.Failed(DrainOutcome.Retry("proof finalize"))
        }
    }

    private fun networkOutcome(code: Int, message: String, what: String): DrainOutcome =
        DrainOutcome.classify(
            if (QueueRetryPolicy.isNoResponse(code)) ApiResult.Transport("$what: $message", offline = true)
            else ApiResult.Http(code, null, "$what: $message"),
        )

    // ---- unit assignment ---------------------------------------------------------------

    private suspend fun drainAssign(accountId: String, dao: RentalOpsDao, item: SyncQueueEntity): DrainOutcome {
        val p = outboxJson.decodeFromString(AssignPayload.serializer(), item.payload ?: return DrainOutcome.Rejected("empty payload"))
        // Latest order_version this device knows (our own earlier assigns on the same order bump
        // it, see below); falls back to the version captured when the unit was picked.
        val cached = dao.get(RentalOpsEntities.UNASSIGNED_LINE, p.lineId)?.let { runCatching { outboxJson.parseToJsonElement(it.json) as JsonObject }.getOrNull() }
        val version = (cached?.get("order_version") as? JsonPrimitive)?.longOrNull ?: p.orderVersion
        val r = api.assignUnit(accountId, p.orderId, p.lineId, p.assetId, version, item.queueId)
        if (r is ApiResult.Ok) {
            dao.deleteRecord(RentalOpsEntities.UNASSIGNED_LINE, p.lineId)
            // assign-unit returns the stage A order: carry its new version to the order's other
            // waiting lines so a second queued assign on the same order does not 409 on our own edit.
            val newVersion = (r.body["version"] as? JsonPrimitive)?.longOrNull
            if (newVersion != null) {
                val now = System.currentTimeMillis()
                val siblings = dao.all(RentalOpsEntities.UNASSIGNED_LINE).mapNotNull { rec ->
                    val o = runCatching { outboxJson.parseToJsonElement(rec.json) as JsonObject }.getOrNull() ?: return@mapNotNull null
                    if (o.str("order_id") != p.orderId) return@mapNotNull null
                    val bumped = JsonObject(o + ("order_version" to JsonPrimitive(newVersion)))
                    RecordEntity(RentalOpsEntities.UNASSIGNED_LINE, rec.id, bumped.toString(), null, false, now)
                }
                if (siblings.isNotEmpty()) dao.upsertAll(siblings)
            }
        }
        return DrainOutcome.classify(r)
    }
    // ---- transfers ---------------------------------------------------------------------

    private suspend fun drainTransfer(accountId: String, dao: RentalOpsDao, item: SyncQueueEntity): DrainOutcome {
        val p = outboxJson.decodeFromString(TransferPayload.serializer(), item.payload ?: return DrainOutcome.Rejected("empty payload"))
        val local = dao.get(RentalOpsEntities.TRANSFER, p.transferId)?.let { runCatching { outboxJson.parseToJsonElement(it.json) as JsonObject }.getOrNull() }
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

    private suspend fun cacheRow(dao: RentalOpsDao, entity: String, row: JsonObject) {
        val id = row.str(RentalOpsEntities.pkField(entity)) ?: return
        dao.upsertAll(listOf(RecordEntity(entity, id, row.toString(), (row["seq"] as? JsonPrimitive)?.longOrNull, row.isDeleted(), System.currentTimeMillis())))
    }

    // ---- pull ----------------------------------------------------------------------------

    private enum class Pull { OK, FORBIDDEN, FAILED }

    /**
     * P2-only data: tasks, transfers and the unassigned-lines work queue. Fleet, orders, lines,
     * allocations and customers are pulled by P1 (feature:rentals) into the shared store.
     */
    override suspend fun pull(accountId: String) {
        val dao = opsDb.dao(accountId)
        val steps: List<Pair<String, suspend () -> Unit>> = listOf(
            RentalOpsEntities.TASK to { pullTasks(accountId, dao) },
            RentalOpsEntities.TRANSFER to {
                pullFeed(dao, RentalOpsEntities.TRANSFER, RentalOpsEntities.TRANSFER) { c -> api.syncFeed(accountId, RentalOpsEntities.TRANSFER, c) }
            },
            RentalOpsEntities.UNASSIGNED_LINE to { pullUnassigned(accountId, dao) },
        )
        for ((name, step) in steps) {
            try {
                step()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[RentalOps] pull $name failed")
            }
        }
    }

    /**
     * `GET …/unassigned-lines` is a snapshot (no cursor): replace the cache on success, clear
     * it on 403 (no `rental.view`), keep last-known-good rows on any other failure.
     */
    private suspend fun pullUnassigned(accountId: String, dao: RentalOpsDao) {
        when (val r = api.unassignedLines(accountId, limit = UNASSIGNED_LIMIT)) {
            is ApiResult.Ok -> {
                val now = System.currentTimeMillis()
                val rows = (r.body["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
                    val id = o.str("line_id") ?: return@mapNotNull null
                    RecordEntity(RentalOpsEntities.UNASSIGNED_LINE, id, o.toString(), null, false, now)
                }
                dao.replaceEntity(RentalOpsEntities.UNASSIGNED_LINE, rows)
            }
            is ApiResult.Http -> if (r.status == 403) dao.clearEntity(RentalOpsEntities.UNASSIGNED_LINE)
                else Timber.d("[RentalOps] unassigned-lines HTTP ${r.status} ${r.code}")
            is ApiResult.Transport -> Unit
        }
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
        var cursor = state?.cursor
        repeat(MAX_PAGES) {
            when (val r = fetch(cursor)) {
                is ApiResult.Ok -> {
                    val items = (r.body["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                    val rows = feedRows(entity, items, now)
                    if (rows.isNotEmpty()) dao.upsertAll(rows)
                    val next = r.body.str("next_cursor") ?: cursor
                    cursor = next
                    dao.upsertCursor(CursorEntity(cursorKey, next, now, unsupported = false))
                    val more = (r.body["has_more"] as? JsonPrimitive)?.booleanOrNull == true
                    if (!more || items.isEmpty()) return Pull.OK
                }
                is ApiResult.Http -> {
                    return when (r.status) {
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
        /**
         * One sync-feed page (`{entity, items, next_cursor, has_more}`) → local rows. Tombstones
         * (`{id, _deleted: true}`, also sent by `my-tasks` on reassignment) are kept as deleted
         * rows so the UI drops them and later replays cannot resurrect them.
         */
        fun feedRows(entity: String, items: List<JsonObject>, now: Long): List<RecordEntity> {
            val pk = RentalOpsEntities.pkField(entity)
            return items.mapNotNull { o ->
                val id = o.str(pk) ?: o.str("id") ?: return@mapNotNull null
                RecordEntity(entity, id, o.toString(), (o["seq"] as? JsonPrimitive)?.longOrNull, o.isDeleted(), now)
            }
        }

        const val MAX_PAGES = 50
        /** Server default is 200, max 1000; one dispatcher work queue rarely exceeds this. */
        const val UNASSIGNED_LIMIT = 500
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
