package com.avago.feature.rentals.ops.domain

import com.avago.core.data.domain.RentalDeliveryTask
import com.avago.core.data.domain.RentalOrder
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.domain.RentalOrderStatus
import com.avago.core.data.domain.RentalTransfer
import com.avago.core.data.rental.ClassAvailability
import com.avago.core.data.rental.LocalFleet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.OffsetDateTime

/*
 * Feature views over the v2 store rows (core/data rental domain): delivery tasks, transfers and
 * class-booked lines waiting for a unit. JSON helpers below parse the read-only report and route
 * responses (statement, AR aging, my-route).
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
    val orderNumber: String?,
    val customerName: String?,
    /** The destination location (job site / branch) name. */
    val jobSiteName: String?,
) {
    /** Phone to call: the task contact. */
    val phone: String? get() = contactPhone

    companion object {
        fun of(t: RentalDeliveryTask) = DeliveryTask(
            id = t.id,
            kind = TaskKind.parse(t.kind) ?: TaskKind.DELIVERY,
            status = TaskStatus.parse(t.status) ?: TaskStatus.PLANNED,
            orderId = t.orderId,
            transferId = t.transferId,
            address = t.destinationLabel,
            lat = t.latitude,
            lng = t.longitude,
            contactName = t.contactName,
            contactPhone = t.contactPhone,
            scheduledAt = t.windowStart,
            windowEnd = t.windowEnd,
            driverUserId = t.driverUserId,
            routeSeq = t.routeSeq,
            notes = t.notes,
            failedReason = t.failedReason,
            orderNumber = t.orderNumber,
            customerName = t.customerName,
            jobSiteName = t.destinationName?.takeIf { it != t.destinationLabel },
        )

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

enum class TransferStatus(val wire: String) {
    PLANNED("planned"),
    IN_TRANSIT("in_transit"),
    RECEIVED("received"),
    CANCELLED("cancelled");

    companion object {
        fun parse(s: String?): TransferStatus? = entries.firstOrNull { it.wire == s }
    }
}

data class Transfer(
    val id: String,
    val assetId: String,
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
        fun of(t: RentalTransfer) = Transfer(
            id = t.id,
            assetId = t.assetId,
            fromLocationId = t.fromLocationId,
            toLocationId = t.toLocationId,
            status = TransferStatus.parse(t.status) ?: TransferStatus.PLANNED,
            shipAt = t.shipAt,
            arriveBy = t.arriveBy,
            shippedAt = t.shippedAt,
            receivedAt = t.receivedAt,
            reason = t.reason,
        )
    }
}

/** A class-booked rental line of a live order that still has no unit. */
data class UnassignedLine(
    val lineId: String,
    val lineNo: Int?,
    val orderId: String,
    val orderNumber: String?,
    val orderStatus: String?,
    val customerName: String?,
    val classId: String?,
    val className: String?,
    val description: String?,
    val qty: Int?,
    val locationId: String?,
    val start: Instant?,
    val end: Instant?,
    val holdExpiresAt: Instant?,
    /** Free units of the class at the branch over the line's window (synced allocations); null without a window. */
    val classFreeUnits: Int? = null,
) {
    val title: String? get() = className ?: description

    companion object {
        /** Open class-booked rental lines (no unit yet) of orders that still hold stock. */
        fun of(
            orders: List<RentalOrder>,
            lines: List<RentalOrderLine>,
            classes: Map<String, String>,
            availability: ClassAvailability.Facts = ClassAvailability.Facts.EMPTY,
            now: Instant = Instant.now(),
        ): List<UnassignedLine> {
            val live = orders.filter { it.status in RentalOrderStatus.ACTIVE }.associateBy { it.id }
            return lines.filter { it.kind == "rental" && it.classId != null && it.assetId == null && it.status == "open" }
                .mapNotNull { l ->
                    val o = live[l.orderId] ?: return@mapNotNull null
                    val start = l.startAt ?: o.startAt
                    val end = l.endAt ?: o.endAt
                    UnassignedLine(
                        lineId = l.id,
                        lineNo = l.lineNo,
                        orderId = o.id,
                        orderNumber = o.number ?: o.quoteNumber,
                        orderStatus = o.status,
                        customerName = o.customerName,
                        classId = l.classId,
                        className = l.classId?.let(classes::get),
                        description = l.description.ifBlank { null },
                        qty = l.qty.toInt(),
                        locationId = o.locationId,
                        start = start,
                        end = end,
                        holdExpiresAt = o.holdExpiresAt,
                        classFreeUnits = if (start != null && end != null) {
                            ClassAvailability.freeUnits(availability, l.classId!!, o.locationId, start, end, now)
                        } else {
                            null
                        },
                    )
                }
        }
    }
}

/* Fleet units and allocations come from the v2 store via core/data [LocalFleet]. */
fun LocalFleet.FleetUnit.toAssignmentUnit() = UnitAssignment.Unit(
    assetId = assetId,
    name = name.ifBlank { assetId.take(8) },
    classId = classId,
    locationId = locationId,
    ready = ready,
    inMaintenance = maintenanceOpen,
)

fun LocalFleet.Alloc.toAssignmentAllocation() = UnitAssignment.Allocation(
    allocationId = id,
    assetId = assetId,
    kind = kind,
    start = startAt,
    end = endAt ?: Instant.MAX,
    active = true, // LocalFleet.live drops ended allocations
    blocking = blocking,
    orderLineId = orderLineId,
)
