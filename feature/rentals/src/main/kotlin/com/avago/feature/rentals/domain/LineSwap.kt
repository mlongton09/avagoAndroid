package com.avago.feature.rentals.domain

import com.avago.core.data.rental.LocalFleet
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

/**
 * Line swap (rentals-api-stage-b.md §2 "Swap"): the checked-out unit of a rental line comes
 * back and a replacement of the same class goes out at the same instant. The server marks the
 * old line `swapped` (`end_at = at`), creates a new line on the replacement starting at `at`
 * and running to the old end, and checks it out. [plan] mirrors that on the device so the
 * fleet and the order update before the swap syncs.
 */
object LineSwap {
    /** Pre-edit copy of a server row (restored when the queued swap is discarded). */
    const val KEY_BASE = "_base"

    /** Line statuses a swap may start from (server: `kind = rental AND status = out`). */
    fun swappable(line: JsonObject, orderStatus: String?): Boolean {
        if ((line.str("kind") ?: "rental") != "rental" || line.str("asset_id") == null) return false
        return when (line.str("status")) {
            "out" -> true
            // Checked out on this device but not synced yet: the order is on rent locally.
            null, "open" -> orderStatus == OrderStatus.ON_RENT
            else -> false
        }
    }

    data class Plan(
        /** The old line: `swapped`, ends at the swap. */
        val oldLine: JsonObject,
        /** The new line on the replacement unit (tentative until the server confirms). */
        val newLine: JsonObject,
        /** Allocations of the old unit released by the swap (id → row with `active = false`). */
        val released: List<Pair<String, JsonObject>>,
        /** Tentative on-rent allocation of the replacement. */
        val newAllocation: JsonObject,
    )

    private fun JsonObject.with(vararg pairs: Pair<String, JsonElement?>): JsonObject =
        JsonObject(this + pairs.associate { (k, v) -> k to (v ?: JsonNull) })

    private fun JsonObject.withBase(): JsonObject =
        if (containsKey(KEY_BASE)) this else with(KEY_BASE to JsonObject(this))

    /**
     * @param oldLineId id of [oldLine] (local or server).
     * @param allocations the order's allocation rows (id → JSON).
     */
    fun plan(
        orderId: String,
        oldLineId: String,
        oldLine: JsonObject,
        allocations: List<Pair<String, JsonObject>>,
        newAssetId: String,
        newLineId: String,
        newAllocationId: String,
        at: Instant,
    ): Plan {
        val oldAsset = oldLine.str("asset_id")
        require(oldAsset != newAssetId) { "the replacement must be a different unit" }
        val oldEnd = oldLine.instant("end_at")
        val atText = JsonPrimitive(at.toString())
        val updatedOld = oldLine.withBase().with(
            "status" to JsonPrimitive("swapped"),
            "end_at" to atText,
            "actual_in_at" to atText,
        )
        val newLine = JsonObject(
            oldLine - KEY_BASE - "client_uuid" - "server_version" - "seq" - "actual_in_at" - "amount_minor" - "tax_minor" - "description",
        ).with(
            "id" to JsonPrimitive(newLineId),
            "order_id" to JsonPrimitive(orderId),
            "asset_id" to JsonPrimitive(newAssetId),
            "start_at" to atText,
            "end_at" to oldEnd?.let { JsonPrimitive(it.toString()) },
            "status" to JsonPrimitive("out"),
            "actual_out_at" to atText,
            "swapped_from_line_id" to JsonPrimitive(oldLineId),
            "estimate" to JsonPrimitive(true),
        )
        val mine = allocations.filter { (_, a) ->
            a.bool("active") != false && a.bool("_deleted") != true &&
                (a.str("order_line_id") == oldLineId || (a.str("order_line_id") == null && a.str("asset_id") == oldAsset))
        }
        val released = mine.map { (id, a) -> id to a.withBase().with("active" to JsonPrimitive(false), "end_at" to atText) }
        val allocEnd = oldEnd ?: mine.firstNotNullOfOrNull { it.second.instant("end_at") }
        val newAlloc = buildJsonObject {
            put("id", newAllocationId)
            put("asset_id", newAssetId)
            put("order_id", orderId)
            put("order_line_id", newLineId)
            put("kind", "on_rent")
            put("start_at", at.toString())
            if (allocEnd != null) put("end_at", allocEnd.toString()) else put("end_at", JsonNull)
            put("active", true)
            put("blocking", true)
            put("tentative", true)
        }
        return Plan(updatedOld, newLine, released, newAlloc)
    }

    /**
     * Replacement candidates from local availability: units of the line's class (any class when
     * the line has none and the old unit is unclassified), not the unit on rent, ready, not in
     * maintenance, and free from [at] to the line end (an open-ended line checks one hour).
     * Sorted by name.
     */
    fun candidates(
        fleet: Collection<LocalFleet.Result>,
        busy: List<Availability.Busy>,
        classId: String?,
        oldAssetId: String?,
        at: Instant,
        end: Instant?,
        now: Instant,
    ): List<LocalFleet.FleetUnit> {
        val cls = classId ?: fleet.firstOrNull { it.unit.assetId == oldAssetId }?.unit?.classId
        val to = end?.takeIf { it.isAfter(at) } ?: at.plus(Duration.ofHours(1))
        return fleet.asSequence()
            .map { it.unit to it }
            .filter { (u, _) -> u.assetId != oldAssetId && u.classId == cls }
            .filter { (u, r) -> u.ready && !u.maintenanceOpen && r.status != com.avago.core.data.rental.FleetStatus.IN_MAINTENANCE }
            .filter { (u, _) -> Availability.unit(u.assetId, busy, at, to, now).available }
            .map { it.first }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()
    }
}
