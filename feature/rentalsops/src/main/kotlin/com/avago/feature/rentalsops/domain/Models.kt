package com.avago.feature.rentalsops.domain

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
 * Typed read views over the verbatim server rows kept in the local cache. Field names follow
 * AvagoSvc stage A (rentals-api.md §10/§11) and stage C (migration 20261113000200).
 *
 * TODO(P1 reconcile): feature/rentals (P1) defines its own fleet/order/line models; once it
 * merges, swap these for the shared ones where shapes match.
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
    val customerName: String?,
) {
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
                // Denormalised display fields, if the feed includes them (TODO stage C).
                orderNumber = o.str("order_number"),
                customerName = o.str("customer_name"),
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

data class FleetCard(
    val assetId: String,
    val name: String,
    val classId: String?,
    val locationId: String?,
    val rentalStatus: String?,
    val dueAt: Instant?,
    val dueBackToday: Boolean,
    val timeZone: String?,
    val ready: Boolean,
    val orderNumber: String?,
    val customerName: String?,
    val deleted: Boolean,
) {
    fun toWidgetRow() = WidgetCounts.FleetRow(rentalStatus, dueAt, dueBackToday, timeZone, deleted)

    fun toUnit() = UnitAssignment.Unit(
        assetId = assetId,
        name = name,
        classId = classId,
        locationId = locationId,
        ready = ready,
        inMaintenance = rentalStatus == "in_maintenance",
        deleted = deleted,
    )

    companion object {
        fun from(o: JsonObject): FleetCard? {
            val id = o.str("asset_id") ?: return null
            val current = o.obj("current")
            return FleetCard(
                assetId = id,
                name = o.str("name") ?: id.take(8),
                classId = o.str("rental_class_id") ?: o.str("class_id"),
                locationId = o.str("location_id"),
                rentalStatus = o.str("rental_status"),
                dueAt = current?.instant("due_at") ?: current?.instant("end_at"),
                dueBackToday = o.bool("due_back_today") == true,
                timeZone = o.str("time_zone"),
                ready = o.bool("ready") ?: true,
                orderNumber = current?.str("order_number"),
                customerName = current?.str("customer_name"),
                deleted = o.isDeleted(),
            )
        }
    }
}

data class OrderLine(
    val id: String,
    val orderId: String,
    val kind: String?,
    val classId: String?,
    val assetId: String?,
    val locationId: String?,
    val description: String?,
    val start: Instant?,
    val end: Instant?,
    val status: String?,
    val assignUnitLater: Boolean,
    val deleted: Boolean,
) {
    val needsUnit: Boolean
        get() = !deleted && UnitAssignment.needsAssignment(kind, assetId, classId, status)

    companion object {
        fun from(o: JsonObject): OrderLine? {
            val id = o.str("id") ?: return null
            return OrderLine(
                id = id,
                orderId = o.str("order_id") ?: return null,
                kind = o.str("kind"),
                classId = o.str("class_id"),
                assetId = o.str("asset_id"),
                locationId = o.str("location_id"),
                description = o.str("description"),
                start = o.instant("start_at"),
                end = o.instant("end_at"),
                status = o.str("status"),
                assignUnitLater = o.bool("assign_unit_later") == true,
                deleted = o.isDeleted(),
            )
        }
    }
}

data class OrderSummary(
    val id: String,
    val number: String?,
    val status: String?,
    val customerId: String?,
    val locationId: String?,
    val start: Instant?,
    val end: Instant?,
) {
    companion object {
        fun from(o: JsonObject): OrderSummary? {
            val id = o.str("id") ?: return null
            return OrderSummary(
                id, o.str("number"), o.str("status"), o.str("customer_id"), o.str("location_id"),
                o.instant("start_at"), o.instant("end_at"),
            )
        }
    }
}

fun allocationFrom(o: JsonObject): UnitAssignment.Allocation? {
    val id = o.str("id") ?: return null
    val start = o.instant("start_at") ?: return null
    val end = o.instant("end_at") ?: Instant.MAX
    return UnitAssignment.Allocation(
        allocationId = id,
        assetId = o.str("asset_id"),
        kind = o.str("kind") ?: "reserved",
        start = start,
        end = end,
        active = (o.bool("active") ?: true) && !o.isDeleted(),
        blocking = o.bool("blocking") ?: true,
        orderLineId = o.str("order_line_id"),
    )
}
