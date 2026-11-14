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

    /** Feeds [compute] reads (handy for `RentalDao.observeEntities`). */
    val INPUT_ENTITIES = listOf(ENTITY_FLEET, ENTITY_ALLOCATION, ENTITY_ORDER, ENTITY_ORDER_LINE, ENTITY_META)

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
    )

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
        )
    }

    /** The configured reserved window (minutes) from the last fleet pull, else the default. */
    fun reservedWindow(records: List<RentalRecordEntity>): Long =
        records.firstOrNull { it.entity == ENTITY_META && it.id == META_FLEET_ID }
            ?.let { obj(it.json)?.long("reserved_window_minutes") }
            ?: FleetInput.DEFAULT_RESERVED_WINDOW_MINUTES

    /**
     * `on_rent` due time = line end ?: order end ?: server `due_at` ?: allocation upper bound
     * (server rule). [serverDue] (allocation id → `due_at` from the fleet cards' `current`/`next`)
     * covers legacy rentals, whose due time lives in `rental_periods` and whose allocation is
     * auto-extended while overdue.
     */
    fun dueAt(
        a: Alloc,
        lineEnds: Map<String, Instant?>,
        orderEnds: Map<String, Instant?>,
        serverDue: Map<String, Instant?> = emptyMap(),
    ): Instant? =
        a.orderLineId?.let { lineEnds[it] } ?: a.orderId?.let { orderEnds[it] } ?: serverDue[a.id] ?: a.endAt

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
        return units.associate { u ->
            u.assetId to computeUnit(u, allocs[u.assetId].orEmpty(), lineEnds, orderEnds, now, window, haveAllocFeed, serverDue)
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
