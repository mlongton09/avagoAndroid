package com.avago.feature.rentals.marketplace.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** Request bodies of the marketplace contract (§3, §6, §7); blank optional text is sent as null. */
object MktBodies {
    private fun text(v: String?, limit: Int): JsonPrimitive? = v?.trim()?.takeIf { it.isNotEmpty() }?.take(limit)?.let { JsonPrimitive(it) }

    fun magicLink(email: String, name: String?, locale: String, next: String?): JsonObject = buildJsonObject {
        put("email", email.trim())
        text(name, 200)?.let { put("name", it) }
        put("locale", locale)
        MktNext.sanitize(next)?.let { put("next", it) }
    }

    fun session(token: String): JsonObject = buildJsonObject { put("token", token) }

    fun profile(name: String, phone: String, locale: String?): JsonObject = buildJsonObject {
        put("name", name.trim())
        put("phone", text(phone, 40) ?: JsonNull)
        locale?.let { put("locale", it) }
    }

    fun reserve(
        start: Instant,
        end: Instant,
        locationId: String?,
        qty: Int,
        fulfilment: String,
        deliveryAddress: String?,
        notes: String?,
        clientUuid: String,
    ): JsonObject = buildJsonObject {
        put("start_at", start.toString())
        put("end_at", end.toString())
        put("location_id", locationId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("qty", qty)
        put("fulfilment", fulfilment)
        put("delivery_address", if (fulfilment == MktFulfilment.DELIVERY) text(deliveryAddress, 500) ?: JsonNull else JsonNull)
        put("notes", text(notes, 2000) ?: JsonNull)
        put("client_uuid", clientUuid)
    }

    fun meetup(
        slots: List<MktSlot>,
        message: String?,
        qty: Int,
        locationId: String?,
        rentalStart: Instant?,
        rentalEnd: Instant?,
    ): JsonObject = buildJsonObject {
        put("slots", slotsArray(slots))
        put("message", text(message, MktSlots.MESSAGE_LIMIT) ?: JsonNull)
        put("qty", qty)
        put("location_id", locationId?.let { JsonPrimitive(it) } ?: JsonNull)
        val period = rentalStart != null && rentalEnd != null
        put("rental_start_at", if (period) JsonPrimitive(rentalStart.toString()) else JsonNull)
        put("rental_end_at", if (period) JsonPrimitive(rentalEnd.toString()) else JsonNull)
    }

    fun slotsArray(slots: List<MktSlot>): JsonArray = buildJsonArray {
        slots.forEach { s -> add(buildJsonObject { put("start_at", s.start.toString()); put("end_at", s.end.toString()) }) }
    }

    // ---- renter changes (§6.5) -------------------------------------------------------------

    fun reschedule(slots: List<MktSlot>, message: String?): JsonObject = buildJsonObject {
        put("slots", slotsArray(slots))
        put("message", text(message, MktSlots.MESSAGE_LIMIT) ?: JsonNull)
    }

    fun changeDates(start: Instant, end: Instant, message: String?): JsonObject = buildJsonObject {
        put("start_at", start.toString())
        put("end_at", end.toString())
        put("message", text(message, 2000) ?: JsonNull)
    }

    fun cancelBooking(reason: String?): JsonObject = buildJsonObject { put("reason", text(reason, 2000) ?: JsonNull) }
}
