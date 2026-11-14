package com.avago.feature.rentalsops.data

import android.content.Context
import com.avago.core.auth.IdentityManager
import com.avago.core.auth.PermissionStore
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LocationEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.feature.rentalsops.domain.DeliveryStateMachine
import com.avago.feature.rentalsops.domain.DeliveryTask
import com.avago.feature.rentalsops.domain.FleetCard
import com.avago.feature.rentalsops.domain.OrderLine
import com.avago.feature.rentalsops.domain.OrderSummary
import com.avago.feature.rentalsops.domain.Proof
import com.avago.feature.rentalsops.domain.TaskAction
import com.avago.feature.rentalsops.domain.TaskStatus
import com.avago.feature.rentalsops.domain.Transfer
import com.avago.feature.rentalsops.domain.TransferStatus
import com.avago.feature.rentalsops.domain.TransitionResult
import com.avago.feature.rentalsops.domain.UnitAssignment
import com.avago.feature.rentalsops.domain.WidgetCounts
import com.avago.feature.rentalsops.domain.allocationFrom
import com.avago.feature.rentalsops.domain.parseInstant
import com.avago.feature.rentalsops.domain.str
import com.avago.feature.rentalsops.sync.AssignPayload
import com.avago.feature.rentalsops.sync.RentalOpsQueueTypes
import com.avago.feature.rentalsops.sync.RentalOpsSyncScheduler
import com.avago.feature.rentalsops.sync.TaskEventPayload
import com.avago.feature.rentalsops.sync.TransferPayload
import com.avago.feature.rentalsops.sync.outboxJson
import com.avago.feature.rentalsops.widget.RentalOpsWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A queued (not yet acknowledged) local action, for "waiting to sync" / "rejected" badges. */
data class PendingAction(
    val queueId: String,
    val status: String,
    val lastError: String?,
    val attempts: Long,
) {
    val rejected: Boolean get() = status == "conflict"
    val parked: Boolean get() = status == "error" && attempts >= com.avago.feature.rentalsops.sync.DrainOutcome.MAX_ATTEMPTS
}

data class TaskView(
    val task: DeliveryTask,
    /** Status after replaying queued driver events over the server status. */
    val localStatus: TaskStatus,
    val pending: List<PendingAction>,
) {
    val rejected: PendingAction? get() = pending.firstOrNull { it.rejected || it.parked }
}

data class LineView(
    val line: OrderLine,
    val order: OrderSummary?,
    val pendingAssetName: String?,
    val pending: PendingAction?,
)

data class TransferView(
    val transfer: Transfer,
    val assetName: String?,
    val localStatus: TransferStatus,
    val pending: List<PendingAction>,
    val localOnly: Boolean,
)

data class CachedJson(val body: JsonObject, val fetchedAt: Long, val fresh: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RentalOpsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val identity: IdentityManager,
    private val permissions: PermissionStore,
    private val dbFactory: DatabaseFactory,
    private val opsDb: RentalOpsDatabaseFactory,
    private val api: RentalOpsApi,
    private val scheduler: RentalOpsSyncScheduler,
) {
    val activeAccountId = identity.activeAccountId
    fun userId(): String? = identity.getActiveUserId()
    fun can(key: String): Boolean = permissions.hasPermission(key)

    val access: Flow<RentalOpsAccess> =
        combine(permissions.permissions, permissions.isRoot) { p, root -> RentalOpsAccess.from(p, root) }

    fun requestSync() = scheduler.requestPush()

    /** Queue rows of this module that are not yet acknowledged (badge counts on the hub). */
    fun observeQueue(): Flow<List<PendingAction>> = queue(RentalOpsQueueTypes.ALL).map { rows -> rows.map { it.toPending() } }

    private fun <T> perAccount(block: suspend (String) -> Flow<T>, empty: T): Flow<T> =
        identity.activeAccountId.flatMapLatest { a -> if (a == null) flowOf(empty) else flow { emitAll(block(a)) } }

    private fun records(entity: String): Flow<List<JsonObject>> = perAccount({ a ->
        opsDb.dao(a).observe(entity).map { rows -> rows.mapNotNull { parse(it.json) } }
    }, emptyList())

    private fun queue(types: Set<String>): Flow<List<SyncQueueEntity>> = perAccount({ a ->
        // P1's rental DAO owns the typed sync_queue query (shared ExternalOutbox hook).
        dbFactory.get(a).rentalDao().observeQueueItemsOfTypes(types.toList())
    }, emptyList())

    private fun parse(s: String): JsonObject? = runCatching { outboxJson.parseToJsonElement(s) as JsonObject }.getOrNull()

    private fun SyncQueueEntity.toPending() = PendingAction(queueId, syncStatus, lastError, attempts)

    // ---- Driver tasks --------------------------------------------------------------------

    fun observeTasks(): Flow<List<TaskView>> =
        combine(records(RentalOpsEntities.TASK), queue(setOf(RentalOpsQueueTypes.TASK_EVENT))) { rows, q ->
            val byTask = q.groupBy { it.entityId }
            rows.mapNotNull { DeliveryTask.from(it) }.map { t -> view(t, byTask[t.id].orEmpty()) }
        }

    fun observeTask(id: String): Flow<TaskView?> = observeTasks().map { list -> list.firstOrNull { it.task.id == id } }

    private fun view(t: DeliveryTask, q: List<SyncQueueEntity>): TaskView {
        val live = q.filter { it.syncStatus != "conflict" }
        val events = live.mapNotNull { row ->
            val p = row.payload?.let { runCatching { outboxJson.decodeFromString(TaskEventPayload.serializer(), it) }.getOrNull() }
            val action = TaskAction.parse(p?.action) ?: return@mapNotNull null
            DeliveryStateMachine.QueuedEvent(row.queueId, action, Proof(photos = 1, signature = true, signedName = "queued"), p?.reason)
        }
        val fold = DeliveryStateMachine.fold(t.status, t.kind, t.driverUserId != null, events)
        return TaskView(t, fold.status, q.map { it.toPending() })
    }

    fun observeMedia(taskId: String): Flow<List<MediaEntity>> = perAccount({ a -> opsDb.dao(a).observeMedia(taskId) }, emptyList())

    /** New draft media file path for [taskId] (caller writes the bytes, then [addMedia]). */
    suspend fun newMediaFile(kind: String): Pair<String, File>? {
        val a = identity.getActiveAccountId() ?: return null
        val id = UUID.randomUUID().toString()
        // Always JPEG: the shared upload helper sends image/jpeg (signatures are flattened on white).
        return id to File(opsDb.mediaDir(a), "$id-$kind.jpg")
    }

    suspend fun addMedia(taskId: String, id: String, kind: String, file: File) {
        val a = identity.getActiveAccountId() ?: return
        opsDb.dao(a).insertMedia(MediaEntity(id, taskId, null, kind, file.path, createdAt = System.currentTimeMillis()))
    }

    suspend fun removeDraftMedia(id: String) {
        val a = identity.getActiveAccountId() ?: return
        val dao = opsDb.dao(a)
        val m = dao.media(id) ?: return
        if (m.eventQueueId != null) return // already part of a queued event
        File(m.localPath).delete()
        dao.deleteMedia(id)
    }

    /**
     * Validates [action] against the optimistic local status and queues it. Proof media drafts
     * of the task are attached to the COMPLETE event.
     */
    suspend fun submit(
        view: TaskView,
        action: TaskAction,
        reason: String?,
        signedName: String?,
        notes: String?,
        lat: Double?,
        lng: Double?,
    ): TransitionResult {
        val a = identity.getActiveAccountId() ?: return TransitionResult.Rejected(com.avago.feature.rentalsops.domain.TransitionError.Invalid(view.localStatus, action))
        val dao = opsDb.dao(a)
        val drafts = if (action == TaskAction.COMPLETE) dao.draftMedia(view.task.id) else emptyList()
        val proof = Proof(
            photos = drafts.count { it.kind == "photo" },
            signature = drafts.any { it.kind == "signature" },
            signedName = signedName,
            notes = notes,
        )
        val result = DeliveryStateMachine.transition(view.localStatus, action, view.task.kind, view.task.driverUserId != null, proof, reason)
        if (result !is TransitionResult.Ok) return result
        val queueId = UUID.randomUUID().toString()
        val payload = TaskEventPayload(
            taskId = view.task.id,
            action = action.wire,
            at = Instant.now().toString(),
            lat = lat,
            lng = lng,
            reason = reason?.trim()?.takeIf { it.isNotEmpty() },
            signedName = signedName?.trim()?.takeIf { it.isNotEmpty() },
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        )
        if (action == TaskAction.COMPLETE) dao.attachDrafts(view.task.id, queueId)
        enqueue(a, queueId, RentalOpsQueueTypes.TASK_EVENT, view.task.id, outboxJson.encodeToString(TaskEventPayload.serializer(), payload))
        return result
    }

    // ---- Queue management ----------------------------------------------------------------

    suspend fun retry(queueId: String) {
        val a = identity.getActiveAccountId() ?: return
        dbFactory.get(a).syncQueueDao().resetConflictToPending(queueId)
        scheduler.requestPush()
    }

    /** Drops a rejected action (and its proof media) — the server state stands. */
    suspend fun discard(queueId: String) {
        val a = identity.getActiveAccountId() ?: return
        val dao = opsDb.dao(a)
        dao.mediaForEvent(queueId).forEach { File(it.localPath).delete(); dao.deleteMedia(it.id) }
        val q = dbFactory.get(a).syncQueueDao()
        val row = q.getById(queueId)
        q.softDelete(queueId)
        // A discarded transfer create leaves no local-only row behind.
        if (row?.entityType == RentalOpsQueueTypes.TRANSFER) {
            val rec = dao.get(RentalOpsEntities.TRANSFER, row.entityId)?.let { parse(it.json) }
            if (rec?.get("_pending")?.let { (it as? JsonPrimitive)?.booleanOrNull } == true) dao.deleteRecord(RentalOpsEntities.TRANSFER, row.entityId)
        }
        RentalOpsWidgetUpdater.request(ctx)
    }

    private suspend fun enqueue(accountId: String, queueId: String, type: String, entityId: String, payload: String) {
        val now = System.currentTimeMillis()
        // Plain upsert (not enqueueWithDedup): each driver event is its own idempotent action.
        dbFactory.get(accountId).syncQueueDao().upsert(
            SyncQueueEntity(queueId, type, entityId, "update", null, payload, "pending", 0, null, now, now),
        )
        scheduler.requestPush()
        RentalOpsWidgetUpdater.request(ctx)
    }

    // ---- Fleet / widget ------------------------------------------------------------------

    fun observeFleet(): Flow<List<FleetCard>> = records(RentalOpsEntities.FLEET).map { rows -> rows.mapNotNull { FleetCard.from(it) } }

    fun observeCounts(): Flow<WidgetCounts.Counts> =
        observeFleet().map { cards -> WidgetCounts.compute(cards.map { it.toWidgetRow() }, Instant.now()) }

    // ---- Class → unit assignment ---------------------------------------------------------

    fun observeLinesNeedingUnit(): Flow<List<LineView>> = combine(
        records(RentalOpsEntities.ORDER_LINE),
        records(RentalOpsEntities.ORDER),
        observeFleet(),
        queue(setOf(RentalOpsQueueTypes.ASSIGN)),
    ) { lines, orders, fleet, q ->
        val ordersById = orders.mapNotNull { OrderSummary.from(it) }.associateBy { it.id }
        val names = fleet.associate { it.assetId to it.name }
        val pendingByLine = q.groupBy { it.entityId }.mapValues { it.value.last() }
        lines.mapNotNull { OrderLine.from(it) }
            .filter { it.needsUnit || pendingByLine.containsKey(it.id) }
            .filter { ordersById[it.orderId]?.status !in setOf("cancelled", "closed", "returned") }
            .map { l ->
                val p = pendingByLine[l.id]
                val payload = p?.payload?.let { runCatching { outboxJson.decodeFromString(AssignPayload.serializer(), it) }.getOrNull() }
                LineView(l, ordersById[l.orderId], payload?.let { it.assetName ?: names[it.assetId] ?: it.assetId }, p?.toPending())
            }
            .sortedBy { it.line.start ?: Instant.MAX }
    }

    fun observeLine(lineId: String): Flow<LineView?> = observeLinesNeedingUnit().map { l -> l.firstOrNull { it.line.id == lineId } }

    data class CandidateResult(val candidates: List<UnitAssignment.Candidate>, val confirmed: Boolean, val error: String?)

    /** Local candidates, upgraded with the server's availability search when reachable. */
    suspend fun candidates(line: OrderLine, location: String?): CandidateResult {
        val a = identity.getActiveAccountId() ?: return CandidateResult(emptyList(), false, null)
        val dao = opsDb.dao(a)
        val start = line.start ?: Instant.now()
        val end = line.end ?: start.plusSeconds(86_400)
        val classId = line.classId ?: return CandidateResult(emptyList(), false, null)
        val units = dao.all(RentalOpsEntities.FLEET).mapNotNull { parse(it.json)?.let(FleetCard::from) }.map { it.toUnit() }
        val allocs = dao.all(RentalOpsEntities.ALLOCATION).mapNotNull { parse(it.json)?.let(::allocationFrom) }
        val ul = UnitAssignment.Line(line.id, line.orderId, classId, location ?: line.locationId, start, end)
        val local = UnitAssignment.candidates(ul, units, allocs)
        return when (val r = api.availability(a, start.toString(), end.toString(), classId, ul.locationId)) {
            is ApiResult.Ok -> {
                val server = (r.body["units"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
                    val id = o.str("asset_id") ?: return@mapNotNull null
                    UnitAssignment.ServerUnit(
                        id, o.str("name"),
                        available = (o["available"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        ready = (o["ready"] as? JsonPrimitive)?.booleanOrNull,
                        nextFreeAt = parseInstant(o.str("next_free_at")),
                    )
                }
                CandidateResult(UnitAssignment.mergeServer(ul, local, server, units.associateBy { it.assetId }), true, null)
            }
            is ApiResult.Http -> CandidateResult(local, false, r.message)
            is ApiResult.Transport -> CandidateResult(local, false, null)
        }
    }

    suspend fun assign(line: OrderLine, unit: UnitAssignment.Unit) {
        val a = identity.getActiveAccountId() ?: return
        val payload = AssignPayload(line.orderId, line.id, unit.assetId, unit.name)
        enqueue(a, UUID.randomUUID().toString(), RentalOpsQueueTypes.ASSIGN, line.id, outboxJson.encodeToString(AssignPayload.serializer(), payload))
    }

    // ---- Transfers -----------------------------------------------------------------------

    fun observeTransfers(): Flow<List<TransferView>> = combine(
        records(RentalOpsEntities.TRANSFER),
        observeFleet(),
        queue(setOf(RentalOpsQueueTypes.TRANSFER)),
    ) { rows, fleet, q ->
        val names = fleet.associate { it.assetId to it.name }
        val byId = q.groupBy { it.entityId }
        rows.mapNotNull { o -> Transfer.from(o)?.let { it to o } }.map { (t, o) ->
            val pend = byId[t.id].orEmpty()
            var status = t.status
            pend.filter { it.syncStatus != "conflict" }.forEach { row ->
                val p = row.payload?.let { runCatching { outboxJson.decodeFromString(TransferPayload.serializer(), it) }.getOrNull() }
                status = when (p?.op) {
                    "ship" -> TransferStatus.IN_TRANSIT
                    "receive" -> TransferStatus.RECEIVED
                    else -> status
                }
            }
            TransferView(
                t,
                o.str("_asset_name") ?: t.assetId?.let { names[it] },
                status,
                pend.map { it.toPending() },
                localOnly = (o["_pending"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }.sortedWith(compareBy<TransferView> { it.localStatus == TransferStatus.RECEIVED || it.localStatus == TransferStatus.CANCELLED }.thenByDescending { it.transfer.shipAt ?: Instant.EPOCH })
    }

    fun observeLocations(): Flow<List<LocationEntity>> = perAccount({ a -> dbFactory.get(a).locationDao().observeAll(a) }, emptyList())

    data class ScannedAsset(val assetId: String, val name: String, val locationId: String?)

    /** Resolves a scanned tag/barcode/VIN to a unit using the shared asset cache. */
    suspend fun lookupAsset(code: String): ScannedAsset? {
        val a = identity.getActiveAccountId() ?: return null
        val raw = code.trim()
        if (raw.isEmpty()) return null
        val candidates = AssetCode.candidates(raw)
        val db = dbFactory.get(a)
        for (c in candidates) {
            val hit = db.assetDao().findByIdentifierCandidates(a, c).firstOrNull() ?: db.assetDao().getById(c)
            if (hit != null) return ScannedAsset(hit.assetId, hit.name, hit.locationId)
        }
        return null
    }

    /** Creates a transfer for [asset] to [toLocationId]; optionally ships it straight away. */
    suspend fun createTransfer(asset: ScannedAsset, toLocationId: String, arriveBy: Instant?, reason: String?, shipNow: Boolean): String? {
        val a = identity.getActiveAccountId() ?: return null
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val arrive = (arriveBy ?: Instant.now().plus(com.avago.feature.rentalsops.sync.RentalOpsOutbox.DEFAULT_TRANSIT)).toString()
        val local = JsonObject(
            buildMap {
                put("id", JsonPrimitive(id))
                put("asset_id", JsonPrimitive(asset.assetId))
                put("_asset_name", JsonPrimitive(asset.name))
                asset.locationId?.let { put("from_location_id", JsonPrimitive(it)) }
                put("to_location_id", JsonPrimitive(toLocationId))
                put("status", JsonPrimitive("requested"))
                put("ship_at", JsonPrimitive(now))
                put("arrive_by", JsonPrimitive(arrive))
                reason?.let { put("reason", JsonPrimitive(it)) }
                put("_pending", JsonPrimitive(true))
            },
        )
        opsDb.dao(a).upsertAll(listOf(RecordEntity(RentalOpsEntities.TRANSFER, id, local.toString(), null, false, System.currentTimeMillis())))
        val create = TransferPayload("create", id, asset.assetId, asset.name, asset.locationId, toLocationId, arrive, reason, now)
        enqueue(a, UUID.randomUUID().toString(), RentalOpsQueueTypes.TRANSFER, id, outboxJson.encodeToString(TransferPayload.serializer(), create))
        if (shipNow) transferStep(id, asset.assetId, "ship")
        return id
    }

    suspend fun transferStep(transferId: String, assetId: String, op: String) {
        val a = identity.getActiveAccountId() ?: return
        val p = TransferPayload(op, transferId, assetId, at = Instant.now().toString())
        enqueue(a, UUID.randomUUID().toString(), RentalOpsQueueTypes.TRANSFER, transferId, outboxJson.encodeToString(TransferPayload.serializer(), p))
    }

    // ---- Statements & AR aging (read-only; last good response cached) --------------------

    data class Customer(val id: String, val name: String)

    fun observeCustomers(): Flow<List<Customer>> = records(RentalOpsEntities.CUSTOMER).map { rows ->
        rows.mapNotNull { o -> o.str("rental_customer_id")?.let { Customer(it, o.str("name") ?: o.str("display_name") ?: it.take(8)) } }
            .sortedBy { it.name.lowercase() }
    }

    sealed class Fetch {
        data class Ok(val data: CachedJson) : Fetch()
        data class Failed(val status: Int?, val message: String, val cached: CachedJson?) : Fetch()
    }

    suspend fun statement(customerId: String): Fetch = fetchCached(STATEMENT_CACHE, customerId) { a -> api.statement(a, customerId, null, null) }

    suspend fun arAging(): Fetch = fetchCached(AGING_CACHE, "latest") { a -> api.arAging(a) }

    private suspend fun fetchCached(entity: String, key: String, call: suspend (String) -> ApiResult): Fetch {
        val a = identity.getActiveAccountId() ?: return Fetch.Failed(null, "signed out", null)
        val dao = opsDb.dao(a)
        val cached = dao.get(entity, key)?.let { r -> parse(r.json)?.let { CachedJson(it, r.updatedAt, fresh = false) } }
        return when (val r = call(a)) {
            is ApiResult.Ok -> {
                val now = System.currentTimeMillis()
                dao.upsertAll(listOf(RecordEntity(entity, key, r.body.toString(), null, false, now)))
                Fetch.Ok(CachedJson(r.body, now, fresh = true))
            }
            is ApiResult.Http -> {
                // No access → never show a cached copy.
                if (r.status == 403) dao.deleteRecord(entity, key)
                Fetch.Failed(r.status, r.message, cached.takeIf { r.status != 403 })
            }
            is ApiResult.Transport -> Fetch.Failed(null, r.message, cached)
        }
    }

    companion object {
        const val STATEMENT_CACHE = "rentalops_statement"
        const val AGING_CACHE = "rentalops_ar_aging"
    }
}

/** Minimal scanned-code normalisation (subset of app/AssetTagParser, which lives in :app). */
internal object AssetCode {
    fun candidates(raw: String): List<String> {
        val t = raw.trim()
        val out = linkedSetOf(t)
        // URLs from printed QR labels: …/assets/{id} or ?asset_id=…
        Regex("assets?/([A-Za-z0-9-]{4,})").find(t)?.groupValues?.get(1)?.let { out += it }
        Regex("[?&]asset(?:_id)?=([A-Za-z0-9-]{4,})").find(t)?.groupValues?.get(1)?.let { out += it }
        out += t.uppercase()
        return out.toList()
    }
}
