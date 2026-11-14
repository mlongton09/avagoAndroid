package com.avago.feature.rentals.data

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.RentalTime
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.RentalJson
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.instant
import com.avago.feature.rentals.domain.long
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.objs
import com.avago.feature.rentals.domain.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Maps server / local JSON rows onto [RentalRecordEntity] (extracting the indexed columns). */
object RentalRecords {

    fun idOf(entity: String, item: JsonObject): String? =
        item.str(RentalEntity.pkOf(entity)) ?: item.str("id")

    fun isTombstone(item: JsonObject): Boolean = item.bool("_deleted") == true

    /** An order response split into the rows the offline store keeps. */
    data class OrderRows(
        val orderId: String,
        /** Header without the embedded lines / allocations / customer (+ `customer_name`). */
        val order: JsonObject,
        /** Lines with `order_id` set. */
        val lines: List<JsonObject>,
        /** Allocations with `order_id` set. */
        val allocations: List<JsonObject>,
        /** Offline line id (`client_uuid`) → server line id. */
        val lineIdsByClientUuid: Map<String, String>,
    )

    /**
     * Splits `order_json` (`GET/POST /rentals/orders`: the header plus `customer`, `lines`,
     * `allocations`, `bulk_allocations`; also accepts a `{order: …}` wrapper).
     */
    fun splitOrder(resp: JsonObject): OrderRows? {
        val order = resp.obj("order") ?: resp
        val sid = order.str("id") ?: return null
        val withOrder = { o: JsonObject -> JsonObject(o + ("order_id" to JsonPrimitive(sid))) }
        val customerName = order.obj("customer")?.str("name")
        val header = JsonObject(
            order - "lines" - "allocations" - "bulk_allocations" - "customer" +
                (customerName?.let { mapOf("customer_name" to JsonPrimitive(it)) } ?: emptyMap()),
        )
        val lines = order.objs("lines")
        return OrderRows(
            orderId = sid,
            order = header,
            lines = lines.map(withOrder),
            allocations = order.objs("allocations").map(withOrder),
            lineIdsByClientUuid = lines.mapNotNull { l ->
                val id = l.str("id")
                val cu = l.str("client_uuid")
                if (id != null && cu != null && cu != id) cu to id else null
            }.toMap(),
        )
    }

    fun record(
        entity: String,
        item: JsonObject,
        localState: String = RentalRecordEntity.STATE_SYNCED,
        clientUuid: String? = null,
        notice: String? = null,
        now: Long = System.currentTimeMillis(),
    ): RentalRecordEntity? {
        val id = idOf(entity, item) ?: return null
        val range = RentalTime.parseRange(item.str("during"))
        val start = item.instant("start_at") ?: range?.start ?: item.instant("valid_from")
        val end = if (item.containsKey("end_at")) item.instant("end_at") else range?.end ?: item.instant("valid_to")
        return RentalRecordEntity(
            entity = entity,
            id = id,
            json = RentalJson.encodeToString(JsonObject.serializer(), item),
            assetId = item.str("asset_id"),
            orderId = when (entity) {
                RentalEntity.ORDER -> id
                else -> item.str("order_id")
            },
            customerId = when (entity) {
                RentalEntity.CUSTOMER -> id
                else -> item.str("customer_id") ?: item.str("rental_customer_id")
            },
            classId = item.str("class_id") ?: item.str("rental_class_id"),
            locationId = item.str("location_id"),
            status = item.str("status") ?: item.str("rental_status") ?: item.str("kind"),
            name = item.str("name") ?: item.str("customer_name") ?: item.str("number") ?: item.str("description"),
            startAt = start?.toEpochMilli(),
            endAt = end?.toEpochMilli(),
            seq = item.long("seq") ?: 0L,
            updatedAt = item.instant("updated_at")?.toEpochMilli() ?: now,
            localState = localState,
            clientUuid = clientUuid ?: item.str("client_uuid"),
            notice = notice,
        )
    }
}
