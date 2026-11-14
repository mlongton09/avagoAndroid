package com.avago.core.data.rental

import com.avago.core.data.db.rental.RentalRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * Offline fleet status (spec §11.1/§15): recomputes every unit's `rental_status` on the
 * device from synced rows with the same rule and inputs as `GET /rentals/fleet`:
 *
 *  * units + readiness flags (`maintenance_open`, `ready`, `time_zone`) from the
 *    `rental_fleet` feed,
 *  * ACTIVE allocations from the `rental_allocation` feed (pending local ones included),
 *  * `on_rent` due time = order line `end_at` ?: order `end_at` ?: allocation upper bound
 *    (the server keeps extending overdue allocations, so the bound is not the due time).
 *
 * When no allocation rows are synced yet the server's `rental_status` is kept.
 */
object LocalFleet {

    const val ENTITY_FLEET = "rental_fleet"
    const val ENTITY_ALLOCATION = "rental_allocation"
    const val ENTITY_ORDER = "rental_order"
    const val ENTITY_ORDER_LINE = "rental_order_line"
    const val ENTITY_META = "rental_meta"
    const val META_FLEET_ID = "fleet"
    /** Stage B readiness checklists (`rental_readiness_check` feed + checks edited offline). */
    const val ENTITY_READINESS = "rental_readiness_check"
    /** Fleet readiness reason code of an open (pending / failed) checklist. */
    const val READINESS_REASON = "readiness_checklist"
    /** Checklist statuses that keep a unit Not ready (server rule). */
    val READINESS_OPEN = setOf("pending", "failed")

    /** Feeds [compute] reads (handy for `RentalDao.observeEntities`). */
    val INPUT_ENTITIES = listOf(ENTITY_FLEET, ENTITY_ALLOCATION, ENTITY_ORDER, ENTITY_ORDER_LINE, ENTITY_META, ENTITY_READINESS)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class FleetUnit(
        val assetId: String,
        val name: String,
        val assetType: String?,
        val classId: String?,
        val className: String?,
        val locationId: String?,
        val timeZone: String?,
        val maintenanceOpen: Boolean,
        val ready: Boolean,
        val readiness: List<String>,
        val serverStatus: FleetStatus?,
        val localState: String,
    )

    data class Alloc(
        val id: String,
        val assetId: String,
        val kind: String,
        val blocking: Boolean,
        val startAt: Instant,
        /** Upper bound of `during` (null = open-ended). */
        val endAt: Instant?,
        val orderId: String?,
        val orderLineId: String?,
        val expiresAt: Instant?,
        val localState: String,
        /** Stage B `rental_allocation` feed `due_at` (on_rent only; server line ?: order ?: upper). */
        val feedDueAt: Instant? = null,
    )

    data class Result(
        val unit: FleetUnit,
        val status: FleetStatus,
        val dueBackToday: Boolean,
        val nextChangeAt: Instant?,
        val current: Alloc?,
        val next: Alloc?,
        /** Due time of [current] when it is an on-rent allocation. */
        val dueAt: Instant?,
        /** True when computed on the device (false = server value kept). */
        val local: Boolean,
        /** The unit's deciding readiness checklist (null = none synced / edited). */
        val checklist: ReadinessFlag? = null,
    ) {
        /** A readiness change made on this device is still waiting to sync. */
        val readinessPending: Boolean get() = checklist?.pending == true
    }

    /** The latest readiness checklist of a unit (`rental_readiness_check` row). */
    data class ReadinessFlag(
        val checkId: String,
        /** pending | passed | failed | waived */
        val status: String,
        /** True when the row was changed on the device and is not confirmed yet. */
        val pending: Boolean,
        val createdAt: Instant?,
    ) {
        val open: Boolean get() = status in READINESS_OPEN
    }

    /**
     * The deciding checklist per asset (server rule: the latest check feeds the fleet). A row
     * changed on the device wins over synced rows (it is the newest intent until confirmed);
     * otherwise the newest `created_at` (ties: highest `server_version`).
     */
    fun latestReadiness(records: List<RentalRecordEntity>): Map<String, ReadinessFlag> {
        data class Row(val asset: String, val flag: ReadinessFlag, val local: Boolean, val version: Long)
        val rows = records.filter { it.entity == ENTITY_READINESS }.mapNotNull { r ->
            val o = obj(r.json) ?: return@mapNotNull null
            if (o.bool("_deleted") == true) return@mapNotNull null
            val asset = o.str("asset_id") ?: r.assetId ?: return@mapNotNull null
            val status = o.str("status") ?: return@mapNotNull null
            val local = r.localState != RentalRecordEntity.STATE_SYNCED
            val created = o.instant("created_at") ?: Instant.ofEpochMilli(r.updatedAt)
            Row(asset, ReadinessFlag(r.id, status, local, created), local, o.long("server_version") ?: 0L)
        }
        return rows.groupBy { it.asset }.mapValues { (_, list) ->
            list.maxWith(
                compareBy<Row> { it.local }
                    .thenBy { it.flag.createdAt ?: Instant.EPOCH }
                    .thenBy { it.version }
                    .thenBy { it.flag.checkId },
            ).flag
        }
    }

    /**
     * Applies the deciding checklist to a unit's readiness: an open check adds
     * [READINESS_REASON] (Not ready); a passed / waived check removes it and the unit is ready
     * again when no other reason is left. No check → the fleet card is kept.
     */
    fun withReadiness(u: FleetUnit, flag: ReadinessFlag?): FleetUnit {
        flag ?: return u
        return if (flag.open) {
            u.copy(ready = false, readiness = (u.readiness + READINESS_REASON).distinct())
        } else {
            val rest = u.readiness - READINESS_REASON
            u.copy(ready = u.ready || (rest.isEmpty() && READINESS_REASON in u.readiness), readiness = rest)
        }
    }

    fun obj(raw: String?): JsonObject? =
        raw?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

    fun JsonObject.str(key: String): String? = when (val v = this[key]) {
        null, is JsonNull -> null
        is JsonPrimitive -> v.contentOrNull
        else -> v.toString()
    }

    fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let {
        it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull()
    }

    fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let {
        it.longOrNull ?: it.contentOrNull?.toBigDecimalOrNull()?.toLong()
    }

    fun JsonObject.instant(key: String): Instant? = RentalTime.parseInstant(str(key))

    private fun JsonElement?.strings(): List<String> =
        (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    fun unitOf(r: RentalRecordEntity): FleetUnit? {
        val o = obj(r.json) ?: return null
        if (o.bool("_deleted") == true) return null
        val readiness = o["readiness"].strings()
        return FleetUnit(
            assetId = o.str("asset_id") ?: r.assetId ?: r.id,
            name = o.str("name") ?: r.name ?: "",
            assetType = o.str("asset_type"),
            classId = o.str("rental_class_id") ?: r.classId,
            className = o.str("class_name"),
            locationId = o.str("location_id") ?: r.locationId,
            timeZone = o.str("time_zone"),
            maintenanceOpen = o.bool("maintenance_open") ?: false,
            ready = o.bool("ready") ?: readiness.isEmpty(),
            readiness = readiness,
            serverStatus = FleetStatus.fromWire(o.str("rental_status") ?: r.status),
            localState = r.localState,
        )
    }

    /** An ACTIVE allocation row (inactive / deleted / malformed → null). */
    fun allocOf(r: RentalRecordEntity): Alloc? {
        val o = obj(r.json) ?: return null
        if (o.bool("_deleted") == true || o.bool("active") == false) return null
        val range = RentalTime.parseRange(o.str("during"))
        val start = o.instant("start_at") ?: range?.start ?: r.startAt?.let(Instant::ofEpochMilli) ?: return null
        val end = when {
            o.containsKey("end_at") -> o.instant("end_at") // explicit null = open-ended
            range != null -> range.end
            else -> r.endAt?.let(Instant::ofEpochMilli)
        }
        return Alloc(
            id = r.id,
            assetId = o.str("asset_id") ?: r.assetId ?: return null,
            kind = o.str("kind") ?: return null,
            blocking = o.bool("blocking") ?: true,
            startAt = start,
            endAt = end,
            orderId = o.str("order_id") ?: r.orderId,
            orderLineId = o.str("order_line_id"),
            expiresAt = o.instant("expires_at"),
            localState = r.localState,
            feedDueAt = o.instant("due_at"),
        )
    }

    /** The configured reserved window (minutes) from the last fleet pull, else the default. */
    fun reservedWindow(records: List<RentalRecordEntity>): Long =
        records.firstOrNull { it.entity == ENTITY_META && it.id == META_FLEET_ID }
            ?.let { obj(it.json)?.long("reserved_window_minutes") }
            ?: FleetInput.DEFAULT_RESERVED_WINDOW_MINUTES

    /**
     * `on_rent` due time = line end ?: order end ?: server `due_at` ?: allocation upper bound
     * (server rule). Local line/order rows win (they carry offline extensions). [serverDue]
     * (allocation id → `due_at` from the fleet cards' `current`/`next`) covers legacy rentals,
     * whose due time lives in `rental_periods` and whose allocation is auto-extended while
     * overdue; the stage B allocation feed's own `due_at` covers lines/orders not synced yet.
     */
    fun dueAt(
        a: Alloc,
        lineEnds: Map<String, Instant?>,
        orderEnds: Map<String, Instant?>,
        serverDue: Map<String, Instant?> = emptyMap(),
    ): Instant? =
        a.orderLineId?.let { lineEnds[it] } ?: a.orderId?.let { orderEnds[it] } ?: serverDue[a.id]
            ?: a.feedDueAt ?: a.endAt

    /** Allocation id → `due_at` from the synced fleet cards (`current` / `next`). */
    fun serverDueAts(records: List<RentalRecordEntity>): Map<String, Instant?> {
        val m = HashMap<String, Instant?>()
        records.filter { it.entity == ENTITY_FLEET }.forEach { r ->
            val o = obj(r.json) ?: return@forEach
            listOf("current", "next").forEach { k ->
                val a = o[k] as? JsonObject ?: return@forEach
                val id = a.str("allocation_id") ?: return@forEach
                a.instant("due_at")?.let { m[id] = it }
            }
        }
        return m
    }

    /**
     * Computes every unit's status from [records] (any mix of [INPUT_ENTITIES]).
     * Returns results keyed by asset id.
     */
    fun compute(records: List<RentalRecordEntity>, now: Instant): Map<String, Result> {
        val units = records.filter { it.entity == ENTITY_FLEET }.mapNotNull(::unitOf)
        if (units.isEmpty()) return emptyMap()
        val allocRows = records.filter { it.entity == ENTITY_ALLOCATION }
        val haveAllocFeed = allocRows.isNotEmpty() ||
            records.any { it.entity == ENTITY_META && it.id == "allocations_synced" }
        val allocs = allocRows.mapNotNull(::allocOf).groupBy { it.assetId }
        val lineEnds = records.filter { it.entity == ENTITY_ORDER_LINE }
            .associate { it.id to obj(it.json)?.instant("end_at") }
        val orderEnds = records.filter { it.entity == ENTITY_ORDER }
            .associate { it.id to obj(it.json)?.instant("end_at") }
        val window = reservedWindow(records)
        val serverDue = serverDueAts(records)
        val readiness = latestReadiness(records)
        return units.associate { raw ->
            val flag = readiness[raw.assetId]
            val u = withReadiness(raw, flag)
            val res = computeUnit(u, allocs[u.assetId].orEmpty(), lineEnds, orderEnds, now, window, haveAllocFeed, serverDue)
            // Server status kept (no allocation feed yet): still reflect a newer checklist.
            val status = when {
                res.local || flag == null -> res.status
                flag.open && res.status == FleetStatus.AVAILABLE -> FleetStatus.NOT_READY
                !flag.open && res.status == FleetStatus.NOT_READY && u.ready -> FleetStatus.AVAILABLE
                else -> res.status
            }
            u.assetId to res.copy(status = status, checklist = flag)
        }
    }

    fun computeUnit(
        u: FleetUnit,
        unitAllocs: List<Alloc>,
        lineEnds: Map<String, Instant?>,
        orderEnds: Map<String, Instant?>,
        now: Instant,
        windowMinutes: Long = FleetInput.DEFAULT_RESERVED_WINDOW_MINUTES,
        haveAllocFeed: Boolean = true,
        serverDue: Map<String, Instant?> = emptyMap(),
    ): Result {
        val sorted = unitAllocs.sortedWith(compareBy<Alloc> { it.startAt }.thenBy { it.id })
        val ends = sorted.map { a -> if (a.kind == "on_rent") dueAt(a, lineEnds, orderEnds, serverDue) else a.endAt }
        val out = FleetStatusRule.compute(
            FleetInput(
                now = now,
                timeZone = u.timeZone,
                reservedWindowMinutes = windowMinutes,
                maintenanceOpen = u.maintenanceOpen,
                ready = u.ready,
                allocations = sorted.mapIndexed { i, a -> FleetAlloc(a.kind, a.startAt, ends[i], a.blocking) },
            ),
        )
        if (!haveAllocFeed && u.serverStatus != null) {
            return Result(u, u.serverStatus, false, null, null, null, null, local = false)
        }
        val current = out.current?.let(sorted::getOrNull)
        return Result(
            unit = u,
            status = out.status,
            dueBackToday = out.dueBackToday,
            nextChangeAt = out.nextChangeAt,
            current = current,
            next = out.next?.let(sorted::getOrNull),
            dueAt = out.current?.takeIf { current?.kind == "on_rent" }?.let { ends[it] },
            local = true,
        )
    }

    /** Counts per status (every status present, zero included) + due-back-today. */
    fun counts(results: Collection<Result>): Pair<Map<FleetStatus, Int>, Int> {
        val m = FleetStatus.entries.associateWith { 0 }.toMutableMap()
        results.forEach { m[it.status] = (m[it.status] ?: 0) + 1 }
        return m to results.count { it.dueBackToday }
    }
}
