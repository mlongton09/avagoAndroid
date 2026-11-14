package com.avago.feature.rentals.data

import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.db.rental.RentalRecordEntity.Companion.STATE_DRAFT
import com.avago.core.data.db.rental.RentalRecordEntity.Companion.STATE_PENDING
import com.avago.core.data.db.rental.RentalRecordEntity.Companion.STATE_SYNCED
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.rental.Money
import com.avago.feature.rentals.domain.LineSwap
import com.avago.feature.rentals.domain.OrderStatus
import com.avago.feature.rentals.domain.OutboxPlanner
import com.avago.feature.rentals.domain.Readiness
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.RentalJson
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.long
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.str
import com.avago.feature.rentals.domain.strings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first rentals store (spec §15). Every screen reads Room; every write is applied
 * to Room first and queued in the shared `sync_queue` (entity types [OutboxPlanner.TYPES],
 * one row per action, `entity_id` = the action's `client_uuid`). [RentalOutbox] pushes the
 * queue; [RentalSync] pulls the feeds. Bookings, extensions and invoices made on the device
 * are stored as **drafts** until the server confirms them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RentalRepository @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val scheduler: RentalSyncScheduler,
) {
    val accountId: Flow<String?> get() = identity.activeAccountId

    private fun <T> perAccount(empty: T, block: suspend (String) -> Flow<T>): Flow<T> =
        identity.activeAccountId.flatMapLatest { id ->
            if (id == null) flowOf(empty) else kotlinx.coroutines.flow.flow { emitAll(block(id)) }
        }

    private suspend fun dao(accountId: String) = dbFactory.get(accountId).rentalDao()

    private fun requireAccount(): String =
        identity.getActiveAccountId() ?: throw IllegalStateException("No active account")

    // ── Reads ──────────────────────────────────────────────────────────────────────

    fun records(entity: String): Flow<List<RentalRecordEntity>> =
        perAccount(emptyList()) { dao(it).observeAll(entity) }

    fun recordsOf(entities: List<String>): Flow<List<RentalRecordEntity>> =
        perAccount(emptyList()) { dao(it).observeEntities(entities) }

    fun fleetInputs(): Flow<List<RentalRecordEntity>> = recordsOf(LocalFleet.INPUT_ENTITIES)

    /** The record, following the local→server id map once a draft has been confirmed. */
    fun record(entity: String, id: String): Flow<RentalRecordEntity?> = perAccount(null) { acc ->
        val d = dao(acc)
        combine(d.observe(entity, id), d.observe(RentalEntity.ID_MAP, id)) { r, map -> r to map }
            .flatMapLatest { (r, map) ->
                val serverId = map?.obj?.str("server_id")
                if (r == null && serverId != null && serverId != id) d.observe(entity, serverId) else flowOf(r)
            }
    }

    fun byOrder(entity: String, orderId: String): Flow<List<RentalRecordEntity>> =
        perAccount(emptyList()) { dao(it).observeByOrder(entity, orderId) }

    fun byAsset(entity: String, assetId: String): Flow<List<RentalRecordEntity>> =
        perAccount(emptyList()) { dao(it).observeByAsset(entity, assetId) }

    fun byCustomer(entity: String, customerId: String): Flow<List<RentalRecordEntity>> =
        perAccount(emptyList()) { dao(it).observeByCustomer(entity, customerId) }

    fun lastSyncedAt(): Flow<Long?> = perAccount(null) { dao(it).observeLastSyncedAt() }

    /** Queued rental actions (pending / error / conflict) — drives pending-sync badges. */
    fun queue(): Flow<List<SyncQueueEntity>> = perAccount(emptyList()) {
        dbFactory.get(it).rentalDao().observeQueueItemsOfTypes(OutboxPlanner.TYPES.toList())
    }

    fun unsynced(): Flow<List<RentalRecordEntity>> = perAccount(emptyList()) { dao(it).observeUnsynced() }

    fun syncErrors(): Flow<List<String>> = perAccount(emptyList()) { acc ->
        dao(acc).observeSyncStates().map { s -> s.mapNotNull { it.lastError?.let { e -> "${it.entity}: $e" } } }
    }

    fun requestSync() = scheduler.syncNow()

    // ── Writes (local first + queue) ────────────────────────────────────────────────

    private fun newId() = UUID.randomUUID().toString()

    private fun JsonObject.with(vararg pairs: Pair<String, JsonElement?>): JsonObject =
        JsonObject(this + pairs.associate { (k, v) -> k to (v ?: JsonNull) })

    private fun encode(o: JsonObject) = RentalJson.encodeToString(JsonObject.serializer(), o)

    private suspend fun enqueue(
        accountId: String,
        type: String,
        clientUuid: String,
        group: String,
        body: JsonObject,
        extra: JsonObject = JsonObject(emptyMap()),
        dependsOn: List<String> = emptyList(),
    ) {
        val now = System.currentTimeMillis()
        val payload = buildJsonObject {
            extra.forEach { (k, v) -> put(k, v) }
            put("client_uuid", clientUuid)
            put("group", group)
            put("depends_on", JsonArray(dependsOn.map(::JsonPrimitive)))
            put("body", body.with("client_uuid" to JsonPrimitive(clientUuid)))
        }
        dbFactory.get(accountId).syncQueueDao().upsert(
            SyncQueueEntity(
                queueId = clientUuid,
                entityType = type,
                entityId = clientUuid,
                operation = "insert",
                serverVersion = null,
                payload = encode(payload),
                syncStatus = "pending",
                attempts = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** client_uuids still queued for [localId] (an offline-created customer or order). */
    private suspend fun pendingCreate(accountId: String, localId: String): Boolean =
        dbFactory.get(accountId).syncQueueDao().getById(localId) != null

    data class CustomerInput(
        val name: String,
        val company: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val address: String? = null,
        val dateOfBirth: String? = null,
        val notes: String? = null,
        val preferredLocale: String? = null,
        val preferredCurrency: String? = null,
    )


    /** Creates (id = null) or updates a customer locally and queues it; returns the id. */
    suspend fun saveCustomer(id: String?, input: CustomerInput): String {
        val acc = requireAccount()
        val d = dao(acc)
        val body = RentalRequests.customer(input)
        if (id == null) {
            val localId = newId()
            val json = body.with("rental_customer_id" to JsonPrimitive(localId))
            d.upsert(RentalRecords.record(RentalEntity.CUSTOMER, json, STATE_PENDING, localId)!!)
            enqueue(acc, OutboxPlanner.TYPE_CUSTOMER, localId, "customer:$localId", body, buildJsonObject { put("local_id", localId) })
            scheduler.syncNow()
            return localId
        }
        val existing = d.get(RentalEntity.CUSTOMER, id)
        val merged = JsonObject((existing?.obj ?: JsonObject(emptyMap())) + body)
        // Optimistic PATCH: a stale `server_version` answers 409 `conflict` with `current`.
        val patch = RentalRequests.customer(input, existing?.takeIf { it.localState == STATE_SYNCED }?.obj?.long("server_version"))
        d.upsert(RentalRecords.record(RentalEntity.CUSTOMER, merged.with("rental_customer_id" to JsonPrimitive(id)), STATE_PENDING)!!)
        val action = newId()
        enqueue(
            acc, OutboxPlanner.TYPE_CUSTOMER, action, "customer:$id", patch,
            buildJsonObject { put("customer_id", id); put("update", true) },
            dependsOn = listOfNotNull(id.takeIf { pendingCreate(acc, it) }),
        )
        scheduler.syncNow()
        return id
    }

    data class DocumentInput(
        val kind: String,
        val number: String?,
        val issuingCountry: String?,
        val issuingRegion: String?,
        val classCodes: String?,
        val issuedOn: String?,
        val expiresOn: String?,
        val notes: String? = null,
    )

    suspend fun addCustomerDocument(customerId: String, doc: DocumentInput): String {
        val acc = requireAccount()
        val localId = newId()
        val body = RentalRequests.document(doc)
        dao(acc).upsert(
            RentalRecords.record(
                RentalEntity.CUSTOMER_DOCUMENT,
                body.with("id" to JsonPrimitive(localId), "customer_id" to JsonPrimitive(customerId)),
                STATE_PENDING, localId,
            )!!,
        )
        enqueue(
            acc, OutboxPlanner.TYPE_CUSTOMER_DOCUMENT, localId, "customer:$customerId", body,
            buildJsonObject { put("customer_id", customerId); put("local_id", localId) },
            dependsOn = listOfNotNull(customerId.takeIf { pendingCreate(acc, it) }),
        )
        scheduler.syncNow()
        return localId
    }

    data class BookingInput(
        val customerId: String?,
        val customerName: String?,
        val classId: String?,
        val assetId: String?,
        val assetName: String?,
        val locationId: String?,
        val currency: String,
        val start: Instant,
        val end: Instant,
        /** quote | hold | reserve | null (stay draft) */
        val action: String?,
        val notes: String? = null,
        val estimate: Money? = null,
        /** Device time zone id the times were picked in (shown on the draft). */
        val zoneId: String? = null,
    )

    /** Saves a booking as a local **draft** and queues create (+ requested transition). */
    suspend fun createBooking(input: BookingInput): String {
        val acc = requireAccount()
        val d = dao(acc)
        val orderId = newId()
        val lineId = newId()
        val line = RentalRequests.rentalLine(input.classId, input.assetId, input.assetName, input.start, input.end, lineId)
        val body = RentalRequests.orderCreate(input, line)
        val local = body.with(
            "id" to JsonPrimitive(orderId),
            "status" to JsonPrimitive(OrderStatus.DRAFT),
            "requested_action" to input.action?.let(::JsonPrimitive),
            "total_minor" to input.estimate?.minor?.let(::JsonPrimitive),
            "estimate" to JsonPrimitive(true),
            "time_zone" to input.zoneId?.let(::JsonPrimitive),
        )
        val records = mutableListOf(RentalRecords.record(RentalEntity.ORDER, local, STATE_DRAFT, orderId)!!)
        records += RentalRecords.record(
            RentalEntity.ORDER_LINE,
            line.with("id" to JsonPrimitive(lineId), "order_id" to JsonPrimitive(orderId), "status" to JsonPrimitive("open")),
            STATE_DRAFT, lineId,
        )!!
        if (input.assetId != null && input.action in setOf("hold", "reserve")) {
            val allocId = newId()
            records += RentalRecords.record(
                RentalEntity.ALLOCATION,
                buildJsonObject {
                    put("id", allocId)
                    put("asset_id", input.assetId)
                    put("order_id", orderId)
                    put("order_line_id", lineId)
                    put("kind", if (input.action == "hold") "hold" else "reserved")
                    put("start_at", input.start.toString())
                    put("end_at", input.end.toString())
                    put("active", true)
                    put("blocking", true)
                },
                STATE_DRAFT,
            )!!
        }
        d.upsertAll(records)
        enqueue(
            acc, OutboxPlanner.TYPE_ORDER, orderId, "order:$orderId", body,
            buildJsonObject { put("local_id", orderId) },
            dependsOn = listOfNotNull(input.customerId?.takeIf { pendingCreate(acc, it) }),
        )
        if (input.action != null) {
            enqueue(
                acc, OutboxPlanner.TYPE_ORDER_ACTION, newId(), "order:$orderId", buildJsonObject { },
                buildJsonObject { put("order_id", orderId); put("action", input.action) },
                dependsOn = listOf(orderId),
            )
        }
        scheduler.syncNow()
        return orderId
    }

    private fun statusAfter(action: String, current: String): String = when (action) {
        "quote" -> OrderStatus.QUOTED
        "hold" -> OrderStatus.HELD
        "reserve" -> OrderStatus.RESERVED
        "cancel", "no-show" -> OrderStatus.CANCELLED
        "checkout" -> OrderStatus.ON_RENT
        "off-rent" -> OrderStatus.OFF_RENT_REQUESTED
        "checkin" -> OrderStatus.RETURNED
        "close" -> OrderStatus.CLOSED
        else -> current
    }

    /**
     * Applies a transition optimistically and queues it. `extend` (new [endAt]) leaves the order
     * as a draft extension until the server confirms the price and availability.
     */
    suspend fun orderAction(orderId: String, action: String, reason: String? = null, endAt: Instant? = null, at: Instant? = null) {
        val acc = requireAccount()
        val d = dao(acc)
        val rec = d.get(RentalEntity.ORDER, orderId)
        val o = rec?.obj ?: JsonObject(emptyMap())
        val status = o.str("status") ?: OrderStatus.DRAFT
        val body = RentalRequests.transition(reason, endAt, at ?: Instant.now(), o["version"].takeIf { rec?.localState == STATE_SYNCED })
        if (rec != null) {
            var updated = o.with("status" to JsonPrimitive(statusAfter(action, status)))
            if (endAt != null) updated = updated.with("end_at" to JsonPrimitive(endAt.toString()))
            val state = if (action == "extend" || rec.localState == STATE_DRAFT) STATE_DRAFT else STATE_PENDING
            d.upsert(RentalRecords.record(RentalEntity.ORDER, updated, state, rec.clientUuid)!!.copy(id = rec.id))
            applyAllocationEffects(acc, orderId, action, endAt)
        }
        enqueue(
            acc, OutboxPlanner.TYPE_ORDER_ACTION, newId(), "order:$orderId", body,
            buildJsonObject { put("order_id", orderId); put("action", action) },
            dependsOn = listOfNotNull(orderId.takeIf { pendingCreate(acc, it) }),
        )
        scheduler.syncNow()
    }

    private suspend fun applyAllocationEffects(acc: String, orderId: String, action: String, endAt: Instant?) {
        val d = dao(acc)
        val allocs = d.byOrder(RentalEntity.ALLOCATION, orderId)
        val changed = allocs.mapNotNull { r ->
            val o = r.obj
            val next = when (action) {
                "checkout" -> o.with("kind" to JsonPrimitive("on_rent"))
                "checkin", "cancel", "no-show", "close" -> o.with("active" to JsonPrimitive(false))
                "reserve" -> o.with("kind" to JsonPrimitive("reserved"))
                "extend" -> endAt?.let { o.with("end_at" to JsonPrimitive(it.toString())) }
                else -> null
            } ?: return@mapNotNull null
            RentalRecords.record(RentalEntity.ALLOCATION, next, STATE_PENDING, r.clientUuid)?.copy(id = r.id)
        }
        if (changed.isNotEmpty()) d.upsertAll(changed)
    }

    data class HandoverInput(
        val orderId: String,
        /** The rental line being handed over (`order_line_id`, required by stage B). */
        val orderLineId: String?,
        val assetId: String,
        /** out (check-out) | in (check-in) */
        val direction: String,
        val meterReading: Double?,
        /** hours | distance */
        val meterKind: String = "hours",
        /** 0..100 % */
        val fuelLevel: Int?,
        val marks: List<Mark>,
        val notes: String?,
        val logId: String?,
        val photoIds: List<String>,
        val signatureDataUrl: String?,
        /** Resolved by the UI (a localised fallback when the customer leaves it blank). */
        val signerName: String?,
        /** Device clock at the moment of handover (spec §15: event time is the device time). */
        val occurredAt: Instant,
        val zoneId: String,
    ) {
        /**
         * A sketch mark. New check-in damage carries either a [chargeMinor] (> 0, order
         * currency) or a waive [reason]; baseline / check-out marks are `pre_existing`.
         */
        data class Mark(
            val id: String,
            val zone: String,
            val severity: String,
            val label: String,
            val isNew: Boolean,
            val chargeMinor: Long? = null,
            val reason: String? = null,
        )
    }

    /** Queues signature → handover (waits for the inspection log and its photos). */
    suspend fun saveHandover(input: HandoverInput): String {
        val acc = requireAccount()
        val d = dao(acc)
        val group = "order:${input.orderId}"
        // The stage B route is per line: POST /orders/{o}/lines/{l}/checkout|checkin.
        val lineId = input.orderLineId
            ?: d.byOrder(RentalEntity.ORDER_LINE, input.orderId).firstOrNull { it.assetId == input.assetId }?.id
            ?: d.byOrder(RentalEntity.ORDER_LINE, input.orderId).firstOrNull { it.obj.str("kind") == "rental" }?.id
            ?: throw IllegalStateException("No rental line for this unit on the order")
        input.marks.firstOrNull { input.direction == "in" && it.isNew && (it.chargeMinor ?: 0L) <= 0L && it.reason.isNullOrBlank() }
            ?.let { throw IllegalArgumentException("New damage needs a charge or a waive reason") }
        val orderPending = pendingCreate(acc, input.orderId)
        var signatureId: String? = null
        if (input.signatureDataUrl != null) {
            signatureId = newId()
            val signer = requireNotNull(input.signerName?.takeIf { it.isNotBlank() }) { "signer name required" }
            val sig = RentalRequests.signature(signer, input.signatureDataUrl, input.occurredAt)
            d.upsert(
                RentalRecords.record(
                    RentalEntity.SIGNATURE,
                    sig.with("id" to JsonPrimitive(signatureId), "order_id" to JsonPrimitive(input.orderId), "image_png_base64" to null),
                    STATE_PENDING, signatureId,
                )!!,
            )
            enqueue(
                acc, OutboxPlanner.TYPE_SIGNATURE, signatureId, group, sig,
                buildJsonObject { put("local_id", signatureId); put("order_id", input.orderId) },
                dependsOn = listOfNotNull(input.orderId.takeIf { orderPending }),
            )
        }
        val handoverId = newId()
        val kind = if (input.direction == "out") "checkout" else "checkin"
        val body = RentalRequests.handover(input, signatureId)
        d.upsert(
            RentalRecords.record(
                RentalEntity.HANDOVER,
                body.with(
                    "id" to JsonPrimitive(handoverId),
                    "kind" to JsonPrimitive(kind),
                    "order_id" to JsonPrimitive(input.orderId),
                    "order_line_id" to JsonPrimitive(lineId),
                    "start_at" to JsonPrimitive(input.occurredAt.toString()),
                    "photo_ids" to JsonArray(input.photoIds.map(::JsonPrimitive)),
                ),
                STATE_PENDING, handoverId,
            )!!,
        )
        enqueue(
            acc, OutboxPlanner.TYPE_HANDOVER, handoverId, group, body,
            buildJsonObject {
                put("order_id", input.orderId)
                put("line_id", lineId)
                put("kind", kind)
                put("local_id", handoverId)
                input.logId?.let { put("log_id", it) }
                put("photo_ids", JsonArray(input.photoIds.map(::JsonPrimitive)))
            },
            dependsOn = listOfNotNull(signatureId, input.orderId.takeIf { orderPending }),
        )
        // Optimistic status (the handover performs the transition on the server).
        orderStatusLocal(acc, input.orderId, if (input.direction == "out") "checkout" else "checkin", input.occurredAt)
        lineStatusLocal(acc, lineId, input.direction, input.occurredAt)
        if (input.direction == "in") {
            // The server opens a readiness checklist on check-in (stage B §2): mirror it so the
            // unit shows Not ready offline. Replaced by the server row once the handover syncs.
            val checkId = newId()
            d.upsert(
                RentalRecords.record(
                    RentalEntity.READINESS_CHECK,
                    Readiness.placeholder(checkId, input.assetId, input.orderId, handoverId, input.occurredAt),
                    STATE_PENDING,
                )!!,
            )
        }
        scheduler.syncNow()
        return handoverId
    }

    /** The handed-over line goes `out` / `returned` locally (server: handover_line). */
    private suspend fun lineStatusLocal(acc: String, lineId: String, direction: String, at: Instant) {
        val d = dao(acc)
        val rec = d.get(RentalEntity.ORDER_LINE, lineId) ?: return
        val o = rec.obj
        val updated = if (direction == "out") {
            o.with("status" to JsonPrimitive("out"), "actual_out_at" to JsonPrimitive(at.toString()))
        } else {
            o.with("status" to JsonPrimitive("returned"), "actual_in_at" to JsonPrimitive(at.toString()))
        }
        val state = if (rec.localState == STATE_DRAFT) STATE_DRAFT else STATE_PENDING
        d.upsert(RentalRecords.record(RentalEntity.ORDER_LINE, updated, state, rec.clientUuid)!!.copy(id = rec.id))
    }

    // ── Readiness checklist (stage B §3) ────────────────────────────────────────────

    /** Checklists of a unit (synced + edited / created on the device). */
    fun readinessChecks(assetId: String): Flow<List<RentalRecordEntity>> = byAsset(RentalEntity.READINESS_CHECK, assetId)

    data class ReadinessInput(
        /** The check being decided (null = start a new check for a unit without an open one). */
        val checkId: String?,
        val assetId: String,
        /** passed | failed | waived */
        val status: String,
        val items: List<Readiness.Item>,
        val notes: String?,
        /** The caller holds `rental.override_ready` (required for `waived`). */
        val canWaive: Boolean,
        val at: Instant = Instant.now(),
    )

    /**
     * Applies a checklist decision locally (the fleet updates at once, marked pending) and
     * queues it: PATCH the server check, after resolving / creating it for checks that only
     * exist on the device. Photos upload first (the outbox waits for them).
     */
    suspend fun saveReadiness(input: ReadinessInput): String {
        val acc = requireAccount()
        val d = dao(acc)
        val rec = input.checkId?.let { d.get(RentalEntity.READINESS_CHECK, it) }
        val current = rec?.obj ?: Readiness.fresh(newId(), input.assetId, input.at)
        val from = current.str("status") ?: Readiness.PENDING
        Readiness.validate(from, input.status, input.items, input.notes, input.canWaive)?.let {
            throw IllegalArgumentException("readiness: ${it.name.lowercase()}")
        }
        val id = rec?.id ?: current.str("id")!!
        val mode = when {
            current.bool(Readiness.KEY_PLACEHOLDER) == true -> "resolve"
            current.bool("_new") == true -> "create"
            else -> "patch"
        }
        val updated = Readiness.applyLocal(current, input.status, input.items, input.notes, input.at)
        d.upsert(RentalRecords.record(RentalEntity.READINESS_CHECK, updated, STATE_PENDING, rec?.clientUuid)!!.copy(id = id))
        val photoIds = input.items.flatMap { it.photoIds }.distinct()
        val clientUuid = newId()
        enqueue(
            acc, OutboxPlanner.TYPE_READINESS, clientUuid, "readiness:${input.assetId}",
            buildJsonObject { },
            buildJsonObject {
                put("local_id", id)
                put("asset_id", input.assetId)
                put("mode", mode)
                put("status", input.status)
                put("items", Readiness.itemsJson(input.items))
                input.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.trim()) }
                current.long("server_version")?.let { put("server_version", it) }
                put("photo_ids", JsonArray(photoIds.map(::JsonPrimitive)))
            },
            // A placeholder waits for its check-in (the server opens the real check there).
            dependsOn = listOfNotNull(current.str(Readiness.KEY_HANDOVER_LOCAL_ID)),
        )
        scheduler.syncNow()
        return id
    }

    /** Registers a photo taken for a checklist item (uploaded by the photo sweep before the check syncs). */
    suspend fun addReadinessPhoto(ownerId: String, file: java.io.File, sortOrder: Long): String {
        val acc = requireAccount()
        val now = System.currentTimeMillis()
        val id = newId()
        dbFactory.get(acc).photoDao().upsert(
            com.avago.core.data.db.entity.PhotoEntity(
                photoId = id,
                entityId = ownerId,
                entityType = RentalEntity.READINESS_CHECK,
                accountId = acc,
                storageKey = null,
                downloadUrl = null,
                sortOrder = sortOrder,
                isPrimary = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                serverVersion = 0,
                localPath = file.absolutePath,
            ),
        )
        return id
    }

    // ── Line swap (stage B §2) ──────────────────────────────────────────────────────

    data class SwapInput(
        val orderId: String,
        val lineId: String,
        val newAssetId: String,
        val reason: String?,
        /** Readings of the unit coming back. */
        val inMeter: Double?,
        val inFuelPct: Int?,
        /** Readings of the replacement going out. */
        val outMeter: Double?,
        val outFuelPct: Int?,
        /** Device time of the swap (spec §15). */
        val at: Instant,
    )

    /**
     * Swaps the unit of a checked-out line: applied locally first ([LineSwap.plan]: old line
     * `swapped`, its allocation released, a tentative line + on-rent allocation for the
     * replacement) and queued after the order's earlier steps. A 409 (`allocation_conflict`,
     * `line_not_out`, `invalid_transition`) comes back as a conflict notice on the order.
     */
    suspend fun swapLine(input: SwapInput): String {
        val acc = requireAccount()
        val d = dao(acc)
        val order = d.get(RentalEntity.ORDER, input.orderId) ?: throw IllegalStateException("order not found")
        val line = d.get(RentalEntity.ORDER_LINE, input.lineId) ?: throw IllegalStateException("line not found")
        require(LineSwap.swappable(line.obj, order.obj.str("status"))) { "only a checked-out rental line can be swapped" }
        val newLineId = newId()
        val newAllocId = newId()
        val allocs = d.byOrder(RentalEntity.ALLOCATION, input.orderId).map { it.id to it.obj }
        val plan = LineSwap.plan(input.orderId, line.id, line.obj, allocs, input.newAssetId, newLineId, newAllocId, input.at)
        val records = mutableListOf<RentalRecordEntity>()
        records += RentalRecords.record(RentalEntity.ORDER_LINE, plan.oldLine, STATE_PENDING, line.clientUuid)!!.copy(id = line.id)
        records += RentalRecords.record(RentalEntity.ORDER_LINE, plan.newLine, STATE_PENDING, newLineId)!!
        plan.released.forEach { (id, a) ->
            val prev = d.get(RentalEntity.ALLOCATION, id)
            records += RentalRecords.record(RentalEntity.ALLOCATION, a, STATE_PENDING, prev?.clientUuid)!!.copy(id = id)
        }
        records += RentalRecords.record(RentalEntity.ALLOCATION, plan.newAllocation, STATE_PENDING)!!
        val clientUuid = newId()
        // The returned unit is checked in by the swap: Not ready until its checklist is done.
        line.obj.str("asset_id")?.let { oldAsset ->
            records += RentalRecords.record(
                RentalEntity.READINESS_CHECK,
                Readiness.placeholder(newId(), oldAsset, input.orderId, clientUuid, input.at),
                STATE_PENDING,
            )!!
        }
        d.upsertAll(records)
        if (order.localState == STATE_SYNCED) d.setState(order.entity, order.id, STATE_PENDING, null)
        val body = RentalRequests.swap(
            clientUuid, input.at, input.newAssetId, input.reason,
            RentalRequests.swapSide(input.inMeter, input.inFuelPct),
            RentalRequests.swapSide(input.outMeter, input.outFuelPct),
        )
        enqueue(
            acc, OutboxPlanner.TYPE_LINE_SWAP, clientUuid, "order:${input.orderId}", body,
            buildJsonObject {
                put("order_id", input.orderId)
                put("line_id", line.id)
                line.obj.str("asset_id")?.let { put("old_asset_id", it) }
                put("new_line_id", newLineId)
                put("new_alloc_id", newAllocId)
                put("released_alloc_ids", JsonArray(plan.released.map { JsonPrimitive(it.first) }))
            },
            dependsOn = listOfNotNull(input.orderId.takeIf { pendingCreate(acc, it) }),
        )
        scheduler.syncNow()
        return newLineId
    }

    private suspend fun orderStatusLocal(acc: String, orderId: String, action: String, at: Instant) {
        val d = dao(acc)
        val rec = d.get(RentalEntity.ORDER, orderId) ?: return
        val o = rec.obj
        val key = if (action == "checkout") "actual_out_at" else "actual_in_at"
        val updated = o.with("status" to JsonPrimitive(statusAfter(action, o.str("status") ?: "")), key to JsonPrimitive(at.toString()))
        val state = if (rec.localState == STATE_DRAFT) STATE_DRAFT else STATE_PENDING
        d.upsert(RentalRecords.record(RentalEntity.ORDER, updated, state, rec.clientUuid)!!.copy(id = rec.id))
        applyAllocationEffects(acc, orderId, action, null)
    }

    data class PaymentInput(
        val orderId: String,
        /** Ledger kind (§5): payment | refund | deposit_received | deposit_refunded */
        val kind: String,
        /** §5 methods: cash | card_external | bank_transfer | cheque | mobile_money | direct_debit | voucher | other */
        val method: String,
        val amount: Money,
        val reference: String?,
        /** Card brand / last 4 of an external terminal payment (never the full number). */
        val cardBrand: String?,
        val last4: String?,
        val payerName: String?,
        val payerEmail: String?,
        /** Card authorization hold (deposit taken on an external terminal). */
        val isAuthorization: Boolean,
        val notes: String?,
        val receivedAt: Instant,
    ) {
        companion object {
            val KINDS = listOf("payment", "deposit_received", "refund", "deposit_refunded")
            val METHODS = listOf("cash", "card_external", "bank_transfer", "cheque", "mobile_money", "direct_debit", "voucher", "other")
        }
    }

    /**
     * Records a manually captured ledger entry (§5.1: `provider = null`; gateway columns are
     * left for a later provider integration).
     */
    suspend fun recordPayment(input: PaymentInput): String {
        require(input.last4 == null || input.last4.matches(Regex("\\d{4}"))) { "last4 must be 4 digits" }
        require(input.amount.minor > 0) { "amount must be positive" }
        val acc = requireAccount()
        val id = newId()
        val body = RentalRequests.ledgerEntry(input)
        dao(acc).upsert(
            RentalRecords.record(
                RentalEntity.LEDGER,
                body.with("id" to JsonPrimitive(id), "order_id" to JsonPrimitive(input.orderId), "start_at" to JsonPrimitive(input.receivedAt.toString())),
                STATE_PENDING, id,
            )!!,
        )
        enqueue(
            acc, OutboxPlanner.TYPE_LEDGER, id, "order:${input.orderId}", body,
            buildJsonObject { put("order_id", input.orderId); put("local_id", id) },
            dependsOn = listOfNotNull(input.orderId.takeIf { pendingCreate(acc, it) }),
        )
        scheduler.syncNow()
        return id
    }

    /** Invoice drafted on the device; numbered and priced by the server on sync. */
    suspend fun createInvoiceDraft(orderId: String): String {
        val acc = requireAccount()
        val id = newId()
        val body = RentalRequests.invoice()
        dao(acc).upsert(
            RentalRecords.record(
                RentalEntity.INVOICE,
                body.with("id" to JsonPrimitive(id), "order_id" to JsonPrimitive(orderId), "status" to JsonPrimitive("draft")),
                STATE_DRAFT, id,
            )!!,
        )
        enqueue(
            acc, OutboxPlanner.TYPE_INVOICE, id, "order:$orderId", body,
            buildJsonObject { put("order_id", orderId); put("local_id", id) },
            dependsOn = listOfNotNull(orderId.takeIf { pendingCreate(acc, it) }),
        )
        scheduler.syncNow()
        return id
    }

    /**
     * Make-rentable toggle (`POST /rentals/assets/{id}/enable|disable`). Enabling requires a day
     * rate ([dayRate], `EnableBody.rate_day_minor` > 0); the server reuses or auto-creates the class.
     */
    suspend fun setRentable(
        assetId: String,
        assetName: String,
        assetType: String?,
        enabled: Boolean,
        dayRate: Money? = null,
        weekRate: Money? = null,
        deposit: Money? = null,
    ) {
        require(!enabled || dayRate != null) { "a day rate is required to make an asset rentable" }
        val acc = requireAccount()
        val d = dao(acc)
        val existing = d.get(RentalEntity.FLEET, assetId)
        val base = existing?.obj ?: buildJsonObject {
            put("asset_id", assetId)
            put("name", assetName)
            assetType?.let { put("asset_type", it) }
            put("rental_status", "available")
            put("ready", true)
        }
        val json = base.with("_deleted" to JsonPrimitive(!enabled))
        d.upsert(RentalRecords.record(RentalEntity.FLEET, json, STATE_PENDING)!!)
        val body = if (enabled) RentalRequests.enableAsset(dayRate!!, weekRate, deposit = deposit) else buildJsonObject { }
        enqueue(
            acc, OutboxPlanner.TYPE_ASSET, newId(), "asset:$assetId", body,
            buildJsonObject { put("asset_id", assetId); put("enabled", enabled) },
        )
        scheduler.syncNow()
    }

    /** Re-queues a conflicted or failed action after the user reviewed the notice. */
    suspend fun retry(queueId: String) {
        val acc = requireAccount()
        val q = dbFactory.get(acc).syncQueueDao()
        val item = q.getById(queueId)
        q.resetConflictToPending(queueId)
        // The records go back to "pending" (the notice is cleared) until the outbox confirms them.
        val p = item?.let { com.avago.feature.rentals.domain.parseObj(it.payload) }
        val d = dao(acc)
        val entity = item?.let { entityOf(it.entityType) }
        // Device-created bookings / invoices stay drafts until the server confirms them.
        val state = if (item?.entityType == OutboxPlanner.TYPE_ORDER || item?.entityType == OutboxPlanner.TYPE_INVOICE) STATE_DRAFT else STATE_PENDING
        p?.str("local_id")?.let { id -> entity?.let { e -> d.get(e, id)?.let { d.setState(it.entity, it.id, state, null) } } }
        if (item?.entityType == OutboxPlanner.TYPE_LINE_SWAP) {
            p?.str("line_id")?.let { id -> d.get(RentalEntity.ORDER_LINE, id)?.let { d.setState(it.entity, it.id, STATE_PENDING, null) } }
        }
        p?.str("order_id")?.takeIf { item?.entityType != OutboxPlanner.TYPE_ORDER }?.let { oid ->
            d.get(RentalEntity.ORDER, oid)?.takeIf { it.localState == RentalRecordEntity.STATE_CONFLICT }
                ?.let { d.setState(it.entity, it.id, if (pendingCreate(acc, oid)) STATE_DRAFT else STATE_PENDING, null) }
        }
        scheduler.syncNow()
    }

    private fun entityOf(type: String): String? = when (type) {
        OutboxPlanner.TYPE_ORDER -> RentalEntity.ORDER
        OutboxPlanner.TYPE_CUSTOMER -> RentalEntity.CUSTOMER
        OutboxPlanner.TYPE_HANDOVER -> RentalEntity.HANDOVER
        OutboxPlanner.TYPE_LEDGER -> RentalEntity.LEDGER
        OutboxPlanner.TYPE_INVOICE -> RentalEntity.INVOICE
        OutboxPlanner.TYPE_SIGNATURE -> RentalEntity.SIGNATURE
        OutboxPlanner.TYPE_CUSTOMER_DOCUMENT -> RentalEntity.CUSTOMER_DOCUMENT
        OutboxPlanner.TYPE_READINESS -> RentalEntity.READINESS_CHECK
        else -> null
    }

    /** Restores the pre-edit server copy kept in `_base` (synced), else deletes the device-only row. */
    private suspend fun restoreOrDelete(acc: String, entity: String, id: String, baseKey: String) {
        val d = dao(acc)
        val rec = d.get(entity, id) ?: return
        val base = rec.obj[baseKey] as? JsonObject
        if (base != null) {
            RentalRecords.record(entity, base, STATE_SYNCED, rec.clientUuid)?.copy(id = id)?.let { d.upsert(it) }
        } else {
            d.delete(entity, id)
        }
    }

    /**
     * Drops a conflicted action and its local draft; the next pull restores the server copy.
     */
    suspend fun discard(queueId: String) {
        val acc = requireAccount()
        val q = dbFactory.get(acc).syncQueueDao()
        val item = q.getById(queueId) ?: return
        q.markSuccess(queueId)
        val p = com.avago.feature.rentals.domain.parseObj(item.payload)
        val d = dao(acc)
        when (item.entityType) {
            OutboxPlanner.TYPE_READINESS -> {
                p?.str("local_id")?.let { restoreOrDelete(acc, RentalEntity.READINESS_CHECK, it, Readiness.KEY_BASE) }
                scheduler.syncNow()
                return
            }
            OutboxPlanner.TYPE_LINE_SWAP -> {
                p?.str("new_line_id")?.let { d.delete(RentalEntity.ORDER_LINE, it) }
                p?.str("new_alloc_id")?.let { d.delete(RentalEntity.ALLOCATION, it) }
                p?.str("line_id")?.let { restoreOrDelete(acc, RentalEntity.ORDER_LINE, it, LineSwap.KEY_BASE) }
                p?.strings("released_alloc_ids")?.forEach { restoreOrDelete(acc, RentalEntity.ALLOCATION, it, LineSwap.KEY_BASE) }
            }
        }
        val localId = p?.str("local_id")
        val entity = entityOf(item.entityType)
        if (entity != null && localId != null) d.delete(entity, localId)
        if (item.entityType == OutboxPlanner.TYPE_ORDER && localId != null) {
            d.byOrder(RentalEntity.ORDER_LINE, localId).forEach { d.delete(it.entity, it.id) }
            d.byOrder(RentalEntity.ALLOCATION, localId).forEach { d.delete(it.entity, it.id) }
        }
        // Server-backed rows that were changed optimistically go back to "synced" so the next
        // pull overwrites them with the authoritative copy.
        p?.str("order_id")?.let { oid -> d.get(RentalEntity.ORDER, oid)?.takeIf { it.localState != STATE_DRAFT }?.let { d.setState(it.entity, it.id, STATE_SYNCED, null) } }
        p?.str("customer_id")?.let { cid -> d.get(RentalEntity.CUSTOMER, cid)?.let { d.setState(it.entity, it.id, STATE_SYNCED, null) } }
        scheduler.syncNow()
    }
}
