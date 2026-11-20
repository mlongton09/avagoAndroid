package com.avago.feature.rentalsops.data

import android.content.Context
import android.net.Uri
import com.avago.core.auth.IdentityManager
import com.avago.core.auth.PermissionStore
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.ChangeQueueRepository
import com.avago.core.data.domain.FailedChange
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalDispatchRepository
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.rental.LocalFleet
import com.avago.feature.rentalsops.domain.DeliveryStateMachine
import com.avago.feature.rentalsops.domain.DeliveryTask
import com.avago.feature.rentalsops.domain.OpsCounts
import com.avago.feature.rentalsops.domain.Proof
import com.avago.feature.rentalsops.domain.TaskAction
import com.avago.feature.rentalsops.domain.TaskStatus
import com.avago.feature.rentalsops.domain.Transfer
import com.avago.feature.rentalsops.domain.TransferStatus
import com.avago.feature.rentalsops.domain.TransitionError
import com.avago.feature.rentalsops.domain.TransitionResult
import com.avago.feature.rentalsops.domain.UnassignedLine
import com.avago.feature.rentalsops.domain.UnitAssignment
import com.avago.feature.rentalsops.domain.parseInstant
import com.avago.feature.rentalsops.domain.str
import com.avago.feature.rentalsops.domain.toAssignmentAllocation
import com.avago.feature.rentalsops.domain.toAssignmentUnit
import com.avago.feature.rentalsops.sync.RentalOpsSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.io.File
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local write state of one task / transfer / line: queued commands not yet acknowledged
 * ([rejected] false) or a command the server refused and the store rolled back ([rejected] true,
 * dismissable via [commandId]).
 */
data class PendingAction(
    val commandId: String?,
    val rejected: Boolean,
    val message: String?,
)

data class TaskView(
    val task: DeliveryTask,
    /** Status with this device's queued commands applied (the v2 store overlay). */
    val localStatus: TaskStatus,
    val pending: List<PendingAction>,
) {
    val rejected: PendingAction? get() = pending.firstOrNull { it.rejected }
}

data class LineView(
    val line: UnassignedLine,
    /** A refused assignment (rolled back, so the line needs a unit again). */
    val rejected: PendingAction?,
)

data class TransferView(
    val transfer: Transfer,
    val assetName: String?,
    val localStatus: TransferStatus,
    val pending: List<PendingAction>,
    /** Created on this device and not yet acknowledged by the server. */
    val localOnly: Boolean,
) {
    val rejected: PendingAction? get() = pending.firstOrNull { it.rejected }
}

/** Queued (waiting) and refused (rejected) changes for the hub banner. */
data class QueueSummary(val waiting: Int, val rejected: Int)

/** A proof photo or signature captured for a task, kept on the device until Complete stages it. */
data class ProofDraft(val id: String, val kind: String, val localPath: String)

data class CachedJson(val body: JsonObject, val fetchedAt: Long, val fresh: Boolean)

/**
 * Rentals operations (dispatch, drivers, transfers, class → unit assignment, statements) on the
 * schema v2 store. Reads are flows over core/data repositories; every write is a rental catalog
 * command (optimistic overlay, rolled back when the server refuses it). Only reports and the
 * driver route are fetched over REST (read-only), with the last good answer cached on disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RentalOpsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val identity: IdentityManager,
    private val permissions: PermissionStore,
    private val dispatch: RentalDispatchRepository,
    private val rentals: RentalRepository,
    private val changes: ChangeQueueRepository,
    private val assets: AssetRepository,
    private val locationRepository: LocationRepository,
    private val api: RentalOpsApi,
    private val scheduler: RentalOpsSyncScheduler,
) {
    val activeAccountId = identity.activeAccountId
    fun userId(): String? = identity.getActiveUserId()
    fun can(key: String): Boolean = permissions.hasPermission(key)

    val access: Flow<RentalOpsAccess> =
        combine(permissions.permissions, permissions.isRoot) { p, root -> RentalOpsAccess.from(p, root) }

    /** Runs a sync cycle (send queued commands, pull changes) as soon as the device is online. */
    fun requestSync() = scheduler.requestPush()

    fun observeQueue(): Flow<QueueSummary> = combine(changes.observePendingCount(), opsFailures()) { waiting, failed ->
        QueueSummary(waiting, failed.size)
    }

    /** Removes a refused change from view (its effect was already rolled back). */
    suspend fun dismiss(commandId: String) = changes.dismiss(commandId)

    private fun opsFailures(): Flow<List<FailedChange>> =
        changes.observeFailures().map { all -> all.filter { it.entityType in OPS_ENTITIES } }

    private fun pending(sync: RecordSyncState, failures: List<FailedChange>): List<PendingAction> = buildList {
        if (sync == RecordSyncState.PENDING) add(PendingAction(null, rejected = false, message = null))
        failures.forEach { add(PendingAction(it.commandId, rejected = true, message = it.message ?: it.code)) }
    }

    // ---- Driver tasks --------------------------------------------------------------------

    fun observeTasks(): Flow<List<TaskView>> = combine(dispatch.observeTasks(), opsFailures()) { tasks, failures ->
        val byTask = failures.filter { it.entityType == TASKS }.groupBy { it.entityKey }
        tasks.map { t ->
            val task = DeliveryTask.of(t)
            TaskView(task, task.status, pending(t.sync, byTask[t.id].orEmpty()))
        }
    }

    fun observeTask(id: String): Flow<TaskView?> = observeTasks().map { list -> list.firstOrNull { it.task.id == id } }

    /** Units on the task: the transfer's unit, else the units assigned on the order's lines. */
    fun observeTaskUnits(task: DeliveryTask): Flow<List<String>> = when {
        task.transferId != null -> dispatch.observeTransfers().map { list ->
            list.filter { it.id == task.transferId }.map { it.assetName ?: it.assetId.take(8) }
        }
        task.orderId != null -> combine(rentals.observeLines(task.orderId), observeAssetNames()) { lines, names ->
            lines.filter { it.assetId != null && it.status != "cancelled" }
                .map { l -> names[l.assetId] ?: l.description.ifBlank { l.assetId!!.take(8) } }
                .distinct()
        }
        else -> kotlinx.coroutines.flow.flowOf(emptyList())
    }

    private val draftsChanged = MutableStateFlow(0L)

    fun observeDrafts(taskId: String): Flow<List<ProofDraft>> = draftsChanged.map { drafts(taskId) }

    private suspend fun draftDir(taskId: String): File? {
        val a = identity.getActiveAccountId() ?: return null
        return File(ctx.filesDir, "accounts/$a/rentalops_media/${taskId.filter { it.isLetterOrDigit() || it == '-' }}")
    }

    private suspend fun drafts(taskId: String): List<ProofDraft> = withContext(Dispatchers.IO) {
        draftDir(taskId)?.listFiles().orEmpty()
            .filter { it.isFile && it.length() > 0 }
            .sortedBy { it.lastModified() }
            .mapNotNull { f ->
                val name = f.nameWithoutExtension
                val kind = name.substringAfterLast('-', "")
                if (kind != KIND_PHOTO && kind != KIND_SIGNATURE) null else ProofDraft(name.substringBeforeLast('-'), kind, f.path)
            }
    }

    /** New draft file for [taskId] (the caller writes the bytes, then calls [draftsUpdated]). */
    suspend fun newDraftFile(taskId: String, kind: String): File? = withContext(Dispatchers.IO) {
        val dir = draftDir(taskId)?.apply { mkdirs() } ?: return@withContext null
        val ext = if (kind == KIND_SIGNATURE) "png" else "jpg"
        File(dir, "${UUID.randomUUID()}-$kind.$ext")
    }

    fun draftsUpdated() {
        draftsChanged.value = System.nanoTime()
    }

    suspend fun removeDraft(taskId: String, id: String) {
        withContext(Dispatchers.IO) { drafts(taskId).filter { it.id == id }.forEach { File(it.localPath).delete() } }
        draftsUpdated()
    }

    /**
     * Validates [action] against the task's current (overlaid) status and queues the matching
     * dispatch command. Complete carries the task's proof drafts: photos and the PNG signature
     * are staged for upload with the command, then the drafts are removed.
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
        val taskId = view.task.id
        val drafts = if (action == TaskAction.COMPLETE) drafts(taskId) else emptyList()
        val photos = drafts.filter { it.kind == KIND_PHOTO }
        val signature = drafts.firstOrNull { it.kind == KIND_SIGNATURE }
        val name = signedName?.trim()?.takeIf { it.isNotEmpty() }
        val note = notes?.trim()?.takeIf { it.isNotEmpty() }
        val proof = Proof(photos = photos.size, signature = signature != null, signedName = name, notes = note)
        val hasDriver = view.task.driverUserId != null
        val result = DeliveryStateMachine.transition(view.localStatus, action, view.task.kind, hasDriver, proof, reason)
        if (result !is TransitionResult.Ok) return result
        val at = Instant.now()
        val la = lat?.let(BigDecimal::valueOf)
        val ln = lng?.let(BigDecimal::valueOf)
        try {
            when (action) {
                TaskAction.START -> dispatch.start(taskId, at, la, ln)
                TaskAction.ARRIVE -> dispatch.arrive(taskId, at, la, ln)
                TaskAction.FAIL -> dispatch.fail(taskId, reason!!.trim(), at, note, la, ln)
                TaskAction.COMPLETE -> {
                    val png = signature?.let { withContext(Dispatchers.IO) { File(it.localPath).readBytes() } }
                    dispatch.complete(
                        taskId, at,
                        photos.map { LocalFileRef(Uri.fromFile(File(it.localPath)).toString(), "image/jpeg") },
                        png, name, note, la, ln,
                    )
                    withContext(Dispatchers.IO) { draftDir(taskId)?.deleteRecursively() }
                    draftsUpdated()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[RentalOps] %s on task %s not queued", action.wire, taskId)
            return TransitionResult.Rejected(TransitionError.Invalid(view.localStatus, action))
        }
        return result
    }

    // ---- Fleet ----------------------------------------------------------------------------

    /** asset_id → display name for the rental fleet. */
    fun observeAssetNames(): Flow<Map<String, String>> = rentals.observeFleet().map { f ->
        f.units.associate { it.assetId to it.name.ifBlank { it.assetId.take(8) } }
    }

    /** Out / due today / overdue — the same LocalFleet computation as the home-screen widget. */
    fun observeCounts(): Flow<OpsCounts> =
        rentals.observeFleet().map { OpsCounts.of(LocalFleet.compute(it, Instant.now()).values) }

    // ---- Class → unit assignment ---------------------------------------------------------

    /** Class-booked lines of live orders still waiting for a unit, with refused assignments. */
    fun observeLinesNeedingUnit(): Flow<List<LineView>> = combine(
        rentals.observeOrders(),
        rentals.observeLines(),
        rentals.observeClasses(),
        opsFailures(),
    ) { orders, lines, classes, failures ->
        val refused = failures.filter { it.entityType == LINES }.associateBy { it.entityKey }
        UnassignedLine.of(orders, lines, classes.associate { it.id to it.name })
            .map { l -> LineView(l, refused[l.lineId]?.let { PendingAction(it.commandId, true, it.message ?: it.code) }) }
            .sortedWith(compareBy<LineView> { it.line.start ?: Instant.MAX }.thenBy { it.line.orderNumber })
    }

    fun observeLine(lineId: String): Flow<LineView?> = observeLinesNeedingUnit().map { l -> l.firstOrNull { it.line.lineId == lineId } }

    data class CandidateResult(val candidates: List<UnitAssignment.Candidate>, val confirmed: Boolean, val error: String?)

    /** Local candidates (fleet + allocations in the store), upgraded with the server's availability search when reachable. */
    suspend fun candidates(line: UnassignedLine): CandidateResult {
        val a = identity.getActiveAccountId() ?: return CandidateResult(emptyList(), false, null)
        val start = line.start ?: Instant.now()
        val end = line.end ?: start.plusSeconds(86_400)
        val classId = line.classId ?: return CandidateResult(emptyList(), false, null)
        val fleet = rentals.observeFleet().first()
        val units = fleet.units.map { it.toAssignmentUnit() }
        val allocs = fleet.allocations.map { it.toAssignmentAllocation() }
        val ul = UnitAssignment.Line(line.lineId, line.orderId, classId, line.locationId, start, end)
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

    /** Queues `assign_unit` for [line]; false when it could not be queued. */
    suspend fun assign(line: UnassignedLine, unit: UnitAssignment.Unit): Boolean = guarded("assign unit") {
        rentals.assignUnit(line.lineId, unit.assetId)
    }

    // ---- Transfers -----------------------------------------------------------------------

    fun observeTransfers(): Flow<List<TransferView>> = combine(dispatch.observeTransfers(), opsFailures()) { rows, failures ->
        val byId = failures.filter { it.entityType == TRANSFERS }.groupBy { it.entityKey }
        rows.map { t ->
            val transfer = Transfer.of(t)
            TransferView(
                transfer,
                t.assetName,
                transfer.status,
                pending(t.sync, byId[t.id].orEmpty()),
                localOnly = t.sync == RecordSyncState.PENDING && t.version == 0L,
            )
        }.sortedWith(
            compareBy<TransferView> { it.localStatus == TransferStatus.RECEIVED || it.localStatus == TransferStatus.CANCELLED }
                .thenByDescending { it.transfer.shipAt ?: Instant.EPOCH },
        )
    }

    fun observeLocations(): Flow<List<Location>> = locationRepository.observeLocations()

    data class ScannedAsset(val assetId: String, val name: String, val locationId: String?)

    /** Resolves a scanned tag/barcode/VIN/link to a unit in the store. */
    suspend fun lookupAsset(code: String): ScannedAsset? {
        val raw = code.trim()
        if (raw.isEmpty()) return null
        for (c in AssetCode.candidates(raw)) {
            val hit = assets.findByCode(c) ?: assets.asset(c)
            if (hit != null) return ScannedAsset(hit.id, hit.name, hit.locationId)
        }
        return null
    }

    /** Creates a transfer for [asset] to [toLocationId]; optionally ships it straight away. */
    suspend fun createTransfer(asset: ScannedAsset, toLocationId: String, arriveBy: Instant?, reason: String?, shipNow: Boolean): String? = try {
        val id = dispatch.createTransfer(asset.assetId, toLocationId, arriveBy ?: Instant.now().plus(DEFAULT_TRANSIT), reason, null)
        if (shipNow) dispatch.shipTransfer(id, Instant.now())
        id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "[RentalOps] transfer not queued")
        null
    }

    suspend fun shipTransfer(id: String): Boolean = guarded("ship transfer") { dispatch.shipTransfer(id, Instant.now()) }

    suspend fun receiveTransfer(id: String): Boolean = guarded("receive transfer") { dispatch.receiveTransfer(id, Instant.now()) }

    suspend fun cancelTransfer(id: String): Boolean = guarded("cancel transfer") { dispatch.cancelTransfer(id, null) }

    private suspend fun guarded(what: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "[RentalOps] %s not queued", what)
        false
    }

    // ---- Statements & AR aging (read-only; last good response cached) --------------------

    data class Customer(val id: String, val name: String)

    /** Customers known to the store (statements are per customer party). */
    fun observeCustomers(): Flow<List<Customer>> = rentals.observeCustomers().map { rows ->
        rows.filter { !it.archived && it.sync != RecordSyncState.PENDING }
            .map { Customer(it.partyId, it.company ?: it.name) }
            .sortedBy { it.name.lowercase() }
    }

    sealed class Fetch {
        data class Ok(val data: CachedJson) : Fetch()
        data class Failed(val status: Int?, val message: String, val cached: CachedJson?) : Fetch()
    }

    suspend fun statement(customerId: String): Fetch = fetchCached(STATEMENT_CACHE, customerId) { a -> api.statement(a, customerId, null, null) }

    suspend fun arAging(): Fetch = fetchCached(AGING_CACHE, "latest") { a -> api.arAging(a) }

    /** `routing/my-route` for today (server day in the routing timezone), cached for offline. */
    suspend fun myRoute(): Fetch = fetchCached(ROUTE_CACHE, "today") { a -> api.myRoute(a, null) }

    sealed class PdfResult {
        data class Pdf(val file: File) : PdfResult()
        data class Failed(val status: Int?, val message: String) : PdfResult()
    }

    /** `GET …/statement/pdf` (`rental.reports`) into the app cache, ready to share. */
    suspend fun statementPdf(customerId: String): PdfResult {
        val a = identity.getActiveAccountId() ?: return PdfResult.Failed(null, "signed out")
        val dir = File(ctx.cacheDir, "rentalops_statements").apply { mkdirs() }
        // One file per customer; overwritten on every share so the cache never grows.
        val dest = File(dir, "statement-${safe(customerId)}.pdf")
        return when (val r = api.statementPdf(a, customerId, dest)) {
            is ApiResult.Ok -> PdfResult.Pdf(dest)
            is ApiResult.Http -> { dest.delete(); PdfResult.Failed(r.status, r.message) }
            is ApiResult.Transport -> { dest.delete(); PdfResult.Failed(null, r.message) }
        }
    }

    sealed class EmailResult {
        data class Emailed(val to: String?, val replayed: Boolean) : EmailResult()
        /** 400 `invalid_value`: neither the request nor the customer has an e-mail address. */
        data object NoRecipient : EmailResult()
        data class Failed(val status: Int?, val message: String) : EmailResult()
    }

    /**
     * `POST …/statement/email` (online action, decision L38). [clientUuid] stays stable for one
     * user intent so a retry replays instead of mailing twice; the stored statement document
     * reaches the store through the next sync.
     */
    suspend fun emailStatement(customerId: String, clientUuid: String, recipient: String?): EmailResult {
        val a = identity.getActiveAccountId() ?: return EmailResult.Failed(null, "signed out")
        val to = recipient?.trim()?.takeIf { it.isNotEmpty() }?.let { listOf(it) }.orEmpty()
        return when (val r = api.statementEmail(a, customerId, clientUuid, to, null)) {
            is ApiResult.Ok -> {
                requestSync()
                EmailResult.Emailed(
                    (r.body["email"] as? JsonPrimitive)?.contentOrNull ?: to.firstOrNull(),
                    (r.body["replayed"] as? JsonPrimitive)?.booleanOrNull == true,
                )
            }
            is ApiResult.Http ->
                if (r.status == 400 && r.code == "invalid_value") EmailResult.NoRecipient else EmailResult.Failed(r.status, r.message)
            is ApiResult.Transport -> EmailResult.Failed(null, r.message)
        }
    }

    private suspend fun fetchCached(entity: String, key: String, call: suspend (String) -> ApiResult): Fetch {
        val a = identity.getActiveAccountId() ?: return Fetch.Failed(null, "signed out", null)
        val file = File(ctx.filesDir, "accounts/$a/rentalops_cache/$entity-${safe(key)}.json")
        val cached = withContext(Dispatchers.IO) {
            file.takeIf { it.isFile }?.let { f ->
                runCatching { json.parseToJsonElement(f.readText()) as? JsonObject }.getOrNull()?.let { CachedJson(it, f.lastModified(), fresh = false) }
            }
        }
        return when (val r = call(a)) {
            is ApiResult.Ok -> {
                withContext(Dispatchers.IO) {
                    file.parentFile?.mkdirs()
                    runCatching { file.writeText(r.body.toString()) }.onFailure { Timber.w(it, "[RentalOps] cache write failed") }
                }
                Fetch.Ok(CachedJson(r.body, System.currentTimeMillis(), fresh = true))
            }
            is ApiResult.Http -> {
                // No access → never show a cached copy.
                if (r.status == 403) withContext(Dispatchers.IO) { file.delete() }
                Fetch.Failed(r.status, r.message, cached.takeIf { r.status != 403 })
            }
            is ApiResult.Transport -> Fetch.Failed(null, r.message, cached)
        }
    }

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' }.take(64)

    companion object {
        const val KIND_PHOTO = "photo"
        const val KIND_SIGNATURE = "signature"
        const val STATEMENT_CACHE = "statement"
        const val AGING_CACHE = "ar_aging"
        const val ROUTE_CACHE = "my_route"

        /** Default planned transit when the dispatcher gives no arrive-by time. */
        val DEFAULT_TRANSIT: Duration = Duration.ofHours(24)

        private const val TASKS = "rental_delivery_tasks"
        private const val TRANSFERS = "rental_transfers"
        private const val LINES = "rental_order_lines"
        private val OPS_ENTITIES = setOf(TASKS, TRANSFERS, LINES)

        private val json = Json { ignoreUnknownKeys = true }
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
