package com.avago.core.data.v2.repo

import androidx.core.net.toUri
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalDeliveryTask
import com.avago.core.data.domain.RentalDispatchRepository
import com.avago.core.data.domain.RentalTaskAsset
import com.avago.core.data.domain.RentalTransfer
import com.avago.core.data.domain.RouteCheckInStatus
import com.avago.core.data.domain.StopFix
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2FileStager
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.commands.RentalCommands
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val TASK_TABLES = setOf("rental_delivery_tasks", "rental_orders", "parties", "locations", "pending_commands")
private val TRANSFER_TABLES = setOf("rental_transfers", "assets", "pending_commands")

/** Delivery / pick-up tasks and branch transfers on the v2 store, written as rental catalog commands. */
@Singleton
class V2RentalDispatchRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
    private val stager: V2FileStager,
) : RentalDispatchRepository {

    override fun observeTasks(): Flow<List<RentalDeliveryTask>> =
        store.observe(TASK_TABLES, emptyList()) { conn -> tasks(conn, null) }

    override fun observeTask(id: String): Flow<RentalDeliveryTask?> =
        store.observe(TASK_TABLES, null) { conn -> tasks(conn, id).firstOrNull() }

    override fun observeTransfers(): Flow<List<RentalTransfer>> =
        store.observe(TRANSFER_TABLES, emptyList()) { conn -> transfers(conn, null) }

    override fun observeTaskAssets(taskId: String): Flow<List<RentalTaskAsset>> =
        store.observe(setOf("rental_delivery_task_assets", "assets"), emptyList()) { conn ->
            val ids = V2OverlayMerge.query(conn, "rental_delivery_task_assets", "task_id = ?", listOf(taskId)) { it["task_id"] == taskId }
                .sortedWith(compareBy<V2Row> { it.longOrNull("created_at") ?: 0L }.thenBy { it.str("asset_id") })
                .map { it.str("asset_id") }
                .distinct()
            if (ids.isEmpty()) return@observe emptyList()
            val names = V2OverlayMerge.query(conn, "assets").filter { it["id"] in ids }.associate { it.str("id") to it.strOrNull("name") }
            ids.map { RentalTaskAsset(it, names[it]) }
        }

    override suspend fun assign(taskId: String, driverUserId: String?) {
        commands.submit(RentalCommands.assignTask(task(taskId), driverUserId, System.currentTimeMillis()))
    }

    override suspend fun checkIn(
        taskId: String,
        status: RouteCheckInStatus,
        at: Instant,
        fix: StopFix?,
        reason: String?,
        notes: String?,
        photos: List<LocalFileRef>,
        signaturePng: ByteArray?,
        signedName: String?,
    ) {
        task(taskId)
        val staged = photos.map { stager.stage(it.uri.toUri(), it.fallbackMediaType) }
        val signature = signaturePng?.let { stager.stage(it, "image/png", "proof-signature.png") }
        val now = System.currentTimeMillis()
        val proof = RentalCommands.StopProof(reason, notes, staged, signature, signedName)
        commands.submit(RentalCommands.checkIn(taskId, V2Uuid.v7(now), status, at, fix, proof, now, commands.currentUserId()))
    }

    override suspend fun cancel(taskId: String, reason: String?) {
        task(taskId)
        commands.submit(RentalCommands.cancelTask(taskId, reason, System.currentTimeMillis()))
    }

    override suspend fun reopen(taskId: String, reason: String?) {
        commands.submit(RentalCommands.reopenTask(task(taskId), reason, System.currentTimeMillis()))
    }

    override suspend fun createTransfer(assetId: String, toLocationId: String, arriveBy: Instant, reason: String?, orderId: String?): String {
        val from = store.read(null to false) { conn ->
            val asset = V2OverlayMerge.query(conn, "assets", "id = ?", listOf(assetId)) { it["id"] == assetId }.firstOrNull()
            asset?.strOrNull("location_id") to (asset != null)
        }
        if (!from.second) throw RecordNotFoundException("asset", assetId)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(
            RentalCommands.createTransfer(id, assetId, from.first, toLocationId, arriveBy, reason, orderId, now, commands.currentUserId()),
        )
        return id
    }

    override suspend fun shipTransfer(id: String, at: Instant) {
        commands.submit(RentalCommands.shipTransfer(transfer(id), at, System.currentTimeMillis()))
    }

    override suspend fun receiveTransfer(id: String, at: Instant) {
        commands.submit(RentalCommands.receiveTransfer(transfer(id), at, System.currentTimeMillis()))
    }

    override suspend fun cancelTransfer(id: String, reason: String?) {
        commands.submit(RentalCommands.cancelTransfer(transfer(id), reason, System.currentTimeMillis()))
    }

    private suspend fun task(id: String): RentalDeliveryTask =
        store.read(null) { conn -> tasks(conn, id).firstOrNull() } ?: throw RecordNotFoundException("delivery task", id)

    private suspend fun transfer(id: String): RentalTransfer =
        store.read(null) { conn -> transfers(conn, id).firstOrNull() } ?: throw RecordNotFoundException("transfer", id)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun tasks(conn: V2Connection, id: String?): List<RentalDeliveryTask> {
            val rows = if (id == null) {
                V2OverlayMerge.query(conn, "rental_delivery_tasks")
            } else {
                V2OverlayMerge.query(conn, "rental_delivery_tasks", "id = ?", listOf(id)) { it["id"] == id }
            }
            if (rows.isEmpty()) return emptyList()
            val orderIds = rows.mapNotNull { it.strOrNull("order_id") }.toSet()
            val orders = if (orderIds.isEmpty()) emptyMap() else V2OverlayMerge.query(conn, "rental_orders")
                .filter { it["id"] in orderIds }.associateBy { it.str("id") }
            val names = V2OverlayMerge.query(conn, "parties").associate { it.str("id") to it.strOrNull("display_name") }
            val locations = V2LocationRepository.locations(conn, includeArchived = true).associateBy { it.id }
            val sync = V2RentalRepository.syncStates(conn, "rental_delivery_tasks")
            return rows.map { r ->
                val taskId = r.str("id")
                val order = r.strOrNull("order_id")?.let(orders::get)
                val destination = r.strOrNull("destination_location_id")?.let(locations::get)
                val snapshot = snapshot(r.strOrNull("destination_snapshot"))
                val snapLat = snapshot?.let { coordinate(it, "lat") }
                val snapLng = snapshot?.let { coordinate(it, "lng") }
                val hasSnapPoint = snapLat != null && snapLng != null
                RentalDeliveryTask(
                    id = taskId,
                    version = r.longOrNull("version") ?: 0L,
                    kind = r.strOrNull("kind") ?: "delivery",
                    status = r.strOrNull("status") ?: "planned",
                    orderId = r.strOrNull("order_id"),
                    orderNumber = order?.strOrNull("number"),
                    customerName = order?.let { o -> o.strOrNull("customer_party_id")?.let(names::get) ?: o.strOrNull("walk_in_name") },
                    transferId = r.strOrNull("transfer_id"),
                    destinationLocationId = r.strOrNull("destination_location_id"),
                    destinationLabel = snapshot?.let(::address) ?: destination?.let { it.addressSummary ?: it.name },
                    destinationName = destination?.name,
                    latitude = if (hasSnapPoint) snapLat else destination?.latitude,
                    longitude = if (hasSnapPoint) snapLng else destination?.longitude,
                    contactName = r.strOrNull("contact_name"),
                    contactPhone = r.strOrNull("contact_phone"),
                    windowStart = r.instant("time_window_start"),
                    windowEnd = r.instant("time_window_end"),
                    driverUserId = r.strOrNull("driver_user_id"),
                    routeSeq = r.intOrNull("route_seq"),
                    notes = r.strOrNull("notes"),
                    startedAt = r.instant("started_at"),
                    arrivedAt = r.instant("arrived_at"),
                    completedAt = r.instant("completed_at"),
                    failedReason = r.strOrNull("failed_reason"),
                    etaAt = r.instant("eta_at"),
                    sync = sync[taskId] ?: RecordSyncState.SAVED,
                )
            }.sortedWith(
                compareBy<RentalDeliveryTask, Instant?>(nullsLast()) { it.windowStart }
                    .thenBy(nullsLast()) { it.routeSeq }
                    .thenBy { it.id },
            )
        }

        /** The explicit address of a task (`destination_snapshot.address`: text or `{line1, line2, city, ...}`). */
        fun snapshotAddress(text: String?): String? = snapshot(text)?.let(::address)

        private fun snapshot(text: String?): JsonObject? =
            text?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

        private fun coordinate(snapshot: JsonObject, key: String): Double? =
            (snapshot[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf { it.isFinite() }

        private fun address(snapshot: JsonObject): String? {
            return when (val address = snapshot["address"]) {
                is JsonPrimitive -> address.contentOrNull?.trim()?.ifEmpty { null }
                is JsonObject -> listOf("line1", "line2", "city", "region", "postal_code", "country")
                    .mapNotNull { k -> (address[k] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null } }
                    .joinToString(", ").ifEmpty { null }
                else -> null
            }
        }

        fun transfers(conn: V2Connection, id: String?): List<RentalTransfer> {
            val rows = if (id == null) {
                V2OverlayMerge.query(conn, "rental_transfers")
            } else {
                V2OverlayMerge.query(conn, "rental_transfers", "id = ?", listOf(id)) { it["id"] == id }
            }
            if (rows.isEmpty()) return emptyList()
            val assetIds = rows.map { it.str("asset_id") }.toSet()
            val names = V2OverlayMerge.query(conn, "assets").filter { it["id"] in assetIds }
                .associate { it.str("id") to it.strOrNull("name") }
            val sync = V2RentalRepository.syncStates(conn, "rental_transfers")
            return rows.map { r -> transfer(r, names, sync) }.sortedByDescending { it.shipAt }
        }

        private fun transfer(r: V2Row, names: Map<String, String?>, sync: Map<String, RecordSyncState>): RentalTransfer {
            val id = r.str("id")
            return RentalTransfer(
                id = id,
                version = r.longOrNull("version") ?: 0L,
                assetId = r.str("asset_id"),
                assetName = names[r.str("asset_id")],
                fromLocationId = r.strOrNull("from_location_id"),
                toLocationId = r.str("to_location_id"),
                status = r.strOrNull("status") ?: "planned",
                shipAt = r.instant("planned_during_start") ?: Instant.EPOCH,
                arriveBy = r.instant("planned_during_end"),
                shippedAt = r.instant("shipped_at"),
                receivedAt = r.instant("received_at"),
                reason = r.strOrNull("reason"),
                orderId = r.strOrNull("order_id"),
                sync = sync[id] ?: RecordSyncState.SAVED,
            )
        }
    }
}
