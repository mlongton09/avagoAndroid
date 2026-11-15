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

    // ---- owner -----------------------------------------------------------------------------

    fun accept(slotIndex: Int, message: String?, createReservation: Boolean, serverVersion: Long?): JsonObject = buildJsonObject {
        put("slot_index", slotIndex)
        text(message, 2000)?.let { put("message", it) }
        put("create_reservation", createReservation)
        serverVersion?.let { put("server_version", it) }
    }

    fun counter(start: Instant, end: Instant, message: String?, serverVersion: Long?): JsonObject = buildJsonObject {
        put("start_at", start.toString())
        put("end_at", end.toString())
        text(message, 2000)?.let { put("message", it) }
        serverVersion?.let { put("server_version", it) }
    }

    fun decline(message: String?, serverVersion: Long?): JsonObject = buildJsonObject {
        text(message, 2000)?.let { put("message", it) }
        serverVersion?.let { put("server_version", it) }
    }

    fun block(reason: String?): JsonObject = buildJsonObject { text(reason, 500)?.let { put("reason", it) } }

    fun location(lat: Double, lng: Double): JsonObject = buildJsonObject {
        put("latitude", lat)
        put("longitude", lng)
    }

    fun weekly(w: Map<String, List<Pair<String, String>>>): JsonObject = buildJsonObject {
        MktWindows.DAYS.forEach { d ->
            val list = w[d].orEmpty()
            if (list.isNotEmpty()) put(d, buildJsonArray { list.forEach { (a, b) -> add(buildJsonArray { add(JsonPrimitive(a)); add(JsonPrimitive(b)) }) } })
        }
    }

    /** Full settings update (every editable field) with optimistic `server_version`. */
    fun settings(s: MktOwnerSettings): JsonObject = buildJsonObject {
        put("enabled", s.enabled)
        put("display_name", text(s.displayName, 120) ?: JsonPrimitive(""))
        put("about", text(s.about, 2000) ?: JsonPrimitive(""))
        put("offer_pickup", s.offerPickup)
        put("offer_delivery", s.offerDelivery)
        put("delivery_radius_km", s.deliveryRadiusKm ?: 0.0)
        put("offer_meetup", s.offerMeetup)
        put("meet_availability", weekly(s.meetAvailability))
        put("meet_blackouts", buildJsonArray {
            s.meetBlackouts.forEach { (a, b) -> add(buildJsonObject { put("from", a.toString()); put("to", b.toString()) }) }
        })
        put("meet_slot_minutes", s.slotMinutes)
        put("meet_lead_hours", s.leadHours)
        put("meet_timezone", s.timezone?.trim().orEmpty())
        s.serverVersion?.let { put("server_version", it) }
    }

    /** Listing options; `null` offer flags inherit the account setting. */
    fun listing(l: MktOwnerListing, summaryEn: String, summaryDe: String): JsonObject = buildJsonObject {
        put("listed", l.listed)
        put("category", l.category.orEmpty())
        put("summary_i18n", buildJsonObject {
            summaryEn.trim().take(300).takeIf { it.isNotEmpty() }?.let { put("en-US", it) }
            summaryDe.trim().take(300).takeIf { it.isNotEmpty() }?.let { put("de-DE", it) }
        })
        val inherit = l.offerPickup == null && l.offerDelivery == null && l.offerMeetup == null
        put("offer_pickup", l.offerPickup?.let { JsonPrimitive(it) } ?: JsonNull)
        put("offer_delivery", l.offerDelivery?.let { JsonPrimitive(it) } ?: JsonNull)
        put("offer_meetup", l.offerMeetup?.let { JsonPrimitive(it) } ?: JsonNull)
        put("clear_overrides", inherit)
    }
}
