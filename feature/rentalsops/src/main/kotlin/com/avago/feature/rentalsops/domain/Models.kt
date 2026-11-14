package com.avago.feature.rentalsops.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.OffsetDateTime

/*
 * Typed read views over the verbatim server rows kept in the local cache. Field names follow
 * AvagoSvc stage A (rentals-api.md §10/§11) and stage C (migration 20261113000200).
 *
 * Fleet units / allocations are not modelled here: they are read from P1's shared store via
 * core/data LocalFleet (see the adapters at the end of this file).
 */

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotEmpty() }

internal fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.strList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content } ?: emptyList()

internal fun JsonObject.instant(key: String): Instant? = parseInstant(str(key))

fun parseInstant(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    return runCatching { Instant.parse(s) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
}

/** True for soft-deleted rows and tombstones. */
fun JsonObject.isDeleted(): Boolean = bool("_deleted") == true || str("deleted_at") != null

data class DeliveryTask(
    val id: String,
    val kind: TaskKind,
    val status: TaskStatus,
    val orderId: String?,
    val transferId: String?,
    val assetIds: List<String>,
    val locationId: String?,
    val jobSiteId: String?,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val contactName: String?,
    val contactPhone: String?,
    val scheduledAt: Instant?,
    val windowEnd: Instant?,
    val driverUserId: String?,
    val routeSeq: Int?,
    val notes: String?,
    val failedReason: String?,
    val proofPhotoCount: Int,
    val hasProofSignature: Boolean,
    val proofSignedName: String?,
    val orderNumber: String?,
    val orderStatus: String?,
    /** Customer company, else name, else the order's customer_name (stage C task JSON). */
    val customerName: String?,
    val customerPhone: String?,
    val jobSiteName: String?,
    /** ssets: [{asset_id, name, serial_number}] (task detail; absent in the sync feed). */
    val assets: List<TaskAsset>,
) {
    /** Phone to call: the task contact first, then the customer. */
    val phone: String? get() = contactPhone ?: customerPhone

    companion object {
        fun from(o: JsonObject): DeliveryTask? {
            val id = o.str("id") ?: return null
            return DeliveryTask(
                id = id,
                kind = TaskKind.parse(o.str("kind")) ?: TaskKind.DELIVERY,
                status = TaskStatus.parse(o.str("status")) ?: TaskStatus.PENDING,
                orderId = o.str("order_id"),
                transferId = o.str("transfer_id"),
                assetIds = o.strList("asset_ids"),
                locationId = o.str("location_id"),
                jobSiteId = o.str("job_site_id"),
                address = formatAddress(o["address"]),
                lat = o.dbl("lat"),
                lng = o.dbl("lng"),
                contactName = o.str("contact_name"),
                contactPhone = o.str("contact_phone"),
                scheduledAt = o.instant("scheduled_at"),
                windowEnd = o.instant("window_end"),
                driverUserId = o.str("driver_user_id"),
                routeSeq = o.int("route_seq"),
                notes = o.str("notes"),
                failedReason = o.str("failed_reason"),
                proofPhotoCount = o.strList("proof_photo_file_ids").size,
                hasProofSignature = o.str("proof_signature_file_id") != null,
                proofSignedName = o.str("proof_signed_name"),
                orderNumber = o.str("order_number"),
                orderStatus = o.str("order_status"),
                customerName = o.str("customer_display_name") ?: o.str("customer_name"),
                customerPhone = o.str("customer_phone"),
                jobSiteName = o.str("job_site_name"),
                assets = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { a ->
                    val aid = a.str("asset_id") ?: return@mapNotNull null
                    TaskAsset(aid, a.str("name"), a.str("serial_number"))
                },
            )
        }

        /** Address JSONB → one display line; unknown shapes fall back to joining string values. */
        fun formatAddress(e: JsonElement?): String? = when (e) {
            null, is JsonNull -> null
            is JsonPrimitive -> e.content.takeIf { it.isNotBlank() }
            is JsonObject -> {
                val known = listOf("line1", "street", "line2", "postal_code", "zip", "city", "region", "state", "country")
                val parts = known.mapNotNull { e.str(it) }.ifEmpty {
                    e.values.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
                }
                parts.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }
}

data class TaskAsset(val assetId: String, val name: String?, val serialNumber: String?)

enum class TransferStatus(val wire: String) {
    REQUESTED("requested"),
    IN_TRANSIT("in_transit"),
    RECEIVED("received"),
    CANCELLED("cancelled");

    companion object {
        fun parse(s: String?): TransferStatus? = entries.firstOrNull { it.wire == s }
    }
}

data class Transfer(
    val id: String,
    val assetId: String?,
    val fromLocationId: String?,
    val toLocationId: String?,
    val status: TransferStatus,
    val shipAt: Instant?,
    val arriveBy: Instant?,
    val shippedAt: Instant?,
    val receivedAt: Instant?,
    val reason: String?,
) {
    companion object {
        fun from(o: JsonObject): Transfer? {
            val id = o.str("id") ?: return null
            return Transfer(
                id = id,
                assetId = o.str("asset_id"),
                fromLocationId = o.str("from_location_id"),
                toLocationId = o.str("to_location_id"),
                status = TransferStatus.parse(o.str("status")) ?: TransferStatus.REQUESTED,
                shipAt = o.instant("ship_at"),
                arriveBy = o.instant("arrive_by"),
                shippedAt = o.instant("shipped_at"),
                receivedAt = o.instant("received_at"),
                reason = o.str("reason"),
            )
        }
    }
}

/**
 * A class-booked line still waiting for a unit — one item of stage C
 * `GET …/unassigned-lines` (`rental.view`). Pass [orderVersion] as `version` to `assign-unit`.
 * [classFreeUnits] is the class's free units for the line window (null without dates;
 * 0 = every unit is spoken for).
 */
data class UnassignedLine(
    val lineId: String,
    val lineNo: Int?,
    val orderId: String,
    val orderNumber: String?,
    val orderStatus: String?,
    val orderVersion: Long?,
    val customerName: String?,
    val classId: String?,
    val className: String?,
    val description: String?,
    val qty: Int?,
    val locationId: String?,
    val start: Instant?,
    val end: Instant?,
    val holdExpiresAt: Instant?,
    val classFreeUnits: Int?,
) {
    val title: String? get() = className ?: description

    companion object {
        fun from(o: JsonObject): UnassignedLine? {
            val id = o.str("line_id") ?: return null
            return UnassignedLine(
                lineId = id,
                lineNo = o.int("line_no"),
                orderId = o.str("order_id") ?: return null,
                orderNumber = o.str("order_number"),
                orderStatus = o.str("order_status"),
                orderVersion = (o["order_version"] as? JsonPrimitive)?.longOrNull,
                customerName = o.str("customer_name"),
                classId = o.str("class_id"),
                className = o.str("class_name"),
                description = o.str("description"),
                qty = o.int("qty"),
                locationId = o.str("location_id"),
                start = o.instant("start_at"),
                end = o.instant("end_at"),
                holdExpiresAt = o.instant("hold_expires_at"),
                classFreeUnits = o.int("class_free_units"),
            )
        }
    }
}

/*
 * Fleet units and allocations come from P1's shared rental store (core/data `rental_records`,
 * parsed by [LocalFleet]); these adapters map them onto the assignment domain.
 */
fun LocalFleet.FleetUnit.toAssignmentUnit() = UnitAssignment.Unit(
    assetId = assetId,
    name = name.ifBlank { assetId.take(8) },
    classId = classId,
    locationId = locationId,
    ready = ready,
    inMaintenance = maintenanceOpen || serverStatus == FleetStatus.IN_MAINTENANCE,
)

fun LocalFleet.Alloc.toAssignmentAllocation() = UnitAssignment.Allocation(
    allocationId = id,
    assetId = assetId,
    kind = kind,
    start = startAt,
    end = endAt ?: Instant.MAX,
    active = true, // LocalFleet.allocOf already drops inactive/deleted rows
    blocking = blocking,
    orderLineId = orderLineId,
)