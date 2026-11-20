package com.avago.feature.rentals.marketplace.domain

import com.avago.feature.rentals.domain.arr
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.double
import com.avago.feature.rentals.domain.instant
import com.avago.feature.rentals.domain.long
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.objs
import com.avago.feature.rentals.domain.str
import com.avago.feature.rentals.domain.strings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Typed views of the marketplace JSON (docs/rentals-api-marketplace.md). Parsing never throws. */

internal fun JsonObject.i18n(key: String): Map<String, String> =
    obj(key)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap() ?: emptyMap()

private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

data class MktArea(val city: String?, val region: String?, val country: String?, val lat: Double?, val lng: Double?) {
    val label: String get() = MktText.area(city, region, country)

    companion object {
        fun of(o: JsonObject?): MktArea? = o?.let {
            MktArea(it.str("city"), it.str("region") ?: it.str("state"), it.str("country"), it.double("lat"), it.double("lng"))
        }
    }
}

data class MktOwnerRef(val slug: String?, val name: String) {
    companion object {
        fun of(o: JsonObject?): MktOwnerRef = MktOwnerRef(o?.str("slug"), o?.str("name") ?: o?.str("slug").orEmpty())
    }
}

data class MktFulfilmentFlags(val pickup: Boolean, val delivery: Boolean, val meetup: Boolean, val deliveryRadiusKm: Double? = null) {
    companion object {
        fun of(o: JsonObject?) = MktFulfilmentFlags(
            o?.bool("pickup") == true, o?.bool("delivery") == true, o?.bool("meetup") == true, o?.double("delivery_radius_km"),
        )
    }
}

data class MktPlace(
    val key: String,
    val label: String,
    val name: String,
    val region: String?,
    val country: String?,
    val lat: Double,
    val lng: Double,
) {
    companion object {
        fun of(o: JsonObject): MktPlace? {
            val lat = o.double("lat") ?: return null
            val lng = o.double("lng") ?: return null
            val label = o.str("label") ?: o.str("name") ?: return null
            return MktPlace(o.str("key") ?: label, label, o.str("name") ?: label, o.str("region"), o.str("country"), lat, lng)
        }
    }
}

data class MktPlaces(val items: List<MktPlace>, val attribution: String?) {
    companion object {
        fun of(o: JsonObject) = MktPlaces(o.objs("items").mapNotNull { MktPlace.of(it) }, o.str("attribution"))
    }
}

/** `price` of a search card: "From $45/day" ([fromUnit] day | week | month). */
data class MktCardPrice(val currency: String, val fromMinor: Long?, val fromUnit: String?, val dayMinor: Long?) {
    companion object {
        fun of(o: JsonObject?): MktCardPrice? {
            val p = o ?: return null
            val currency = p.str("currency") ?: return null
            return MktCardPrice(currency, p.long("from_minor"), p.str("from_unit"), p.long("day_minor"))
        }
    }
}

data class MktSearchItem(
    val id: String,
    val title: String,
    val titleI18n: Map<String, String>,
    val summaryI18n: Map<String, String>,
    val category: String?,
    val photo: String?,
    val owner: MktOwnerRef,
    val price: MktCardPrice?,
    val distanceKm: Double?,
    val distanceMi: Double?,
    val area: MktArea?,
    val fulfilment: MktFulfilmentFlags,
    val bookingMode: String?,
    val available: Boolean?,
    val nextAvailable: Instant?,
    /** Earliest possible start (owner lead time + hours, §4); null = unknown. */
    val earliestStart: Instant? = null,
    /** Owner zone for [earliestStart]. */
    val timezone: String? = null,
) {
    fun title(locale: Locale): String = MktText.pick(titleI18n, locale, title) ?: title
    fun summary(locale: Locale): String? = MktText.pick(summaryI18n, locale)

    companion object {
        fun of(o: JsonObject): MktSearchItem? {
            val id = o.str("id") ?: return null
            return MktSearchItem(
                id = id,
                title = o.str("title").orEmpty(),
                titleI18n = o.i18n("title_i18n"),
                summaryI18n = o.i18n("summary_i18n"),
                category = o.str("category"),
                photo = o.str("photo"),
                owner = MktOwnerRef.of(o.obj("owner")),
                price = MktCardPrice.of(o.obj("price")),
                distanceKm = o.double("distance_km"),
                distanceMi = o.double("distance_mi"),
                area = MktArea.of(o.obj("area")),
                fulfilment = MktFulfilmentFlags.of(o.obj("fulfilment")),
                bookingMode = o.str("booking_mode"),
                available = o.bool("available"),
                nextAvailable = o.instant("next_available"),
                earliestStart = o.instant("earliest_start_at"),
                timezone = o.str("timezone"),
            )
        }
    }
}

data class MktSearchPage(
    val centreLabel: String?,
    val centreSource: String?,
    val total: Int,
    val truncated: Boolean,
    val nextCursor: String?,
    val facets: List<Pair<String, Int>>,
    val items: List<MktSearchItem>,
) {
    companion object {
        fun of(o: JsonObject): MktSearchPage = MktSearchPage(
            centreLabel = o.obj("centre")?.str("label"),
            centreSource = o.obj("centre")?.str("source"),
            total = o.int("total") ?: 0,
            truncated = o.bool("truncated") == true,
            nextCursor = o.str("next_cursor"),
            facets = o.obj("facets")?.objs("categories")?.mapNotNull { f -> f.str("category")?.let { it to (f.int("count") ?: 0) } }.orEmpty(),
            items = o.objs("items").mapNotNull { MktSearchItem.of(it) },
        )
    }
}

data class MktPriceTable(
    val currency: String,
    val hourly: Long?,
    val day: Long?,
    val weekend: Long?,
    val week: Long?,
    val month: Long?,
    val minCharge: Long?,
) {
    val isEmpty: Boolean get() = listOf(hourly, day, weekend, week, month).all { it == null }

    companion object {
        fun of(o: JsonObject?, currency: String?): MktPriceTable? {
            val p = o ?: return null
            val c = p.str("currency") ?: currency ?: return null
            return MktPriceTable(
                c, p.long("hourly_minor"), p.long("rate_day_minor"), p.long("rate_weekend_minor"),
                p.long("rate_week_minor"), p.long("rate_month_minor"), p.long("min_charge_minor"),
            )
        }
    }
}

data class MktPolicies(
    val depositMinor: Long?,
    val depositCurrency: String?,
    val requiredDocs: List<String>,
    val minDriverAge: Int?,
    val requireSignature: Boolean,
    val requireLicence: Boolean,
    val requireCoi: Boolean,
) {
    val isEmpty: Boolean
        get() = (depositMinor ?: 0) <= 0 && requiredDocs.isEmpty() && minDriverAge == null &&
            !requireSignature && !requireLicence && !requireCoi

    companion object {
        fun of(o: JsonObject?) = MktPolicies(
            o?.long("deposit_minor"), o?.str("deposit_currency"), o?.strings("required_docs").orEmpty(),
            o?.int("min_driver_age"), o?.bool("require_signature") == true, o?.bool("require_licence") == true,
            o?.bool("require_coi") == true,
        )
    }
}

data class MktMeetupInfo(val slotMinutes: Int, val leadHours: Int, val timezone: String?)

data class MktListing(
    val id: String,
    val category: String?,
    val currency: String?,
    val title: String,
    val titleI18n: Map<String, String>,
    val summaryI18n: Map<String, String>,
    val descriptionI18n: Map<String, String>,
    val specs: List<Pair<String, String>>,
    val photos: List<String>,
    val price: MktPriceTable?,
    val owner: MktOwnerRef,
    val ownerAbout: String?,
    val reviewCount: Int,
    val locationId: String?,
    val area: MktArea?,
    val distanceKm: Double?,
    val distanceMi: Double?,
    val fulfilment: MktFulfilmentFlags,
    val bookingMode: String,
    val minLeadMinutes: Int,
    val maxDays: Int?,
    val meetup: MktMeetupInfo?,
    val policies: MktPolicies,
    val onlinePayments: Boolean,
    /** Earliest possible rental start (lead time + opening hours, §5). */
    val earliestStart: Instant? = null,
    /** Owner zone of the shown location. */
    val bookingTimezone: String? = null,
) {
    fun title(locale: Locale): String = MktText.pick(titleI18n, locale, title) ?: title
    fun summary(locale: Locale): String? = MktText.pick(summaryI18n, locale)
    fun description(locale: Locale): String? = MktText.pick(descriptionI18n, locale)

    val isQuote: Boolean get() = bookingMode == "quote"

    /** Reserve is offered when the owner offers pickup or delivery (§10.2). */
    val canReserve: Boolean get() = fulfilment.pickup || fulfilment.delivery
    val canMeet: Boolean get() = fulfilment.meetup

    companion object {
        fun of(o: JsonObject): MktListing? {
            val id = o.str("id") ?: return null
            val l = o.obj("listing") ?: JsonObject(emptyMap())
            val loc = o.obj("location")
            val booking = o.obj("booking")
            val meet = o.obj("meetup")
            val owner = o.obj("owner")
            val specs = l.obj("specs")?.mapNotNull { (k, v) ->
                val text = (v as? JsonPrimitive)?.contentOrNull ?: (v as? JsonArray)?.joinToString(", ") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
                text?.takeIf { it.isNotBlank() }?.let { k to it }
            }.orEmpty()
            return MktListing(
                id = id,
                category = o.str("category"),
                currency = o.str("currency"),
                title = l.str("title") ?: o.str("title").orEmpty(),
                titleI18n = l.i18n("title_i18n"),
                summaryI18n = l.i18n("summary_i18n"),
                descriptionI18n = l.i18n("description_i18n"),
                specs = specs,
                photos = o.arr("photos").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                price = MktPriceTable.of(o.obj("price"), o.str("currency")),
                owner = MktOwnerRef.of(owner),
                ownerAbout = owner?.str("about"),
                reviewCount = owner?.int("review_count") ?: 0,
                locationId = loc?.str("location_id"),
                area = MktArea.of(loc?.obj("area")),
                distanceKm = loc?.double("distance_km"),
                distanceMi = loc?.double("distance_mi"),
                fulfilment = MktFulfilmentFlags.of(o.obj("fulfilment")),
                bookingMode = booking?.str("mode") ?: "hold",
                minLeadMinutes = booking?.int("min_lead_minutes") ?: 0,
                maxDays = booking?.int("max_days"),
                meetup = meet?.let { MktMeetupInfo(it.int("slot_minutes") ?: 30, it.int("lead_hours") ?: 0, it.str("timezone")) },
                policies = MktPolicies.of(o.obj("policies") ?: l),
                onlinePayments = o.bool("online_payments") == true,
                earliestStart = booking?.instant("earliest_start_at"),
                bookingTimezone = booking?.str("timezone"),
            )
        }
    }
}

data class MktAvailabilityDay(val day: LocalDate, val start: Instant, val available: Boolean, val qty: Int)

object MktAvailability {
    fun of(o: JsonObject, zone: java.time.ZoneId): List<MktAvailabilityDay> = o.objs("days").mapNotNull { d ->
        val start = d.instant("start_at") ?: return@mapNotNull null
        MktAvailabilityDay(start.atZone(zone).toLocalDate(), start, d.bool("available") == true, d.int("available_qty") ?: 0)
    }
}

data class MktMeetSlots(val locationId: String?, val timezone: String?, val slotMinutes: Int, val slots: List<MktSlot>) {
    companion object {
        fun of(o: JsonObject) = MktMeetSlots(
            o.str("location_id"), o.str("timezone"), o.int("slot_minutes") ?: 30,
            o.objs("slots").mapNotNull { s -> slot(s) },
        )

        fun slot(s: JsonObject?): MktSlot? {
            val a = s?.instant("start_at") ?: return null
            val b = s.instant("end_at") ?: return null
            return MktSlot(a, b)
        }
    }
}

data class MktRenter(
    val id: String,
    val email: String,
    val name: String?,
    val phone: String?,
    val locale: String?,
    val emailVerified: Boolean,
) {
    val hasName: Boolean get() = !name.isNullOrBlank()

    companion object {
        fun of(o: JsonObject?): MktRenter? {
            val r = o ?: return null
            return MktRenter(r.str("id") ?: return null, r.str("email").orEmpty(), r.str("name"), r.str("phone"), r.str("locale"), r.bool("email_verified") == true)
        }
    }
}

/** Exact address — only present once a meet-up / booking is confirmed. */
data class MktAddress(
    val name: String?,
    val line1: String?,
    val line2: String?,
    val city: String?,
    val region: String?,
    val postalCode: String?,
    val country: String?,
    val lat: Double?,
    val lng: Double?,
) {
    val oneLine: String
        get() = listOfNotNull(line1, line2, listOfNotNull(city, region).joinToString(", ").takeIf { it.isNotBlank() }, postalCode, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")

    /** `geo:` URI for the platform Maps app (coordinates + label query when known). */
    fun geoUri(): String {
        val q = java.net.URLEncoder.encode(oneLine.ifBlank { name.orEmpty() }, "UTF-8")
        return if (lat != null && lng != null) "geo:$lat,$lng?q=$lat,$lng($q)" else "geo:0,0?q=$q"
    }

    companion object {
        fun of(o: JsonObject?): MktAddress? = o?.let {
            MktAddress(it.str("name"), it.str("address_line1"), it.str("address_line2"), it.str("city"), it.str("region") ?: it.str("state"),
                it.str("postal_code"), it.str("country"), it.double("lat"), it.double("lng"))
        }
    }
}

data class MktContact(val email: String?, val phone: String?) {
    companion object {
        fun of(o: JsonObject?): MktContact? = o?.let { MktContact(it.str("email"), it.str("phone")) }?.takeIf { it.email != null || it.phone != null }
    }
}

// ---- renter changes, cancellation and history (§6.5) ------------------------------------------

object MktChangeKind {
    const val MEETUP_TIME = "meetup_time"
    const val BOOKING_DATES = "booking_dates"
    const val BOOKING_CANCEL = "booking_cancel"
}

/** Booking `actions.cancel`: cancelled at once, or the owner decides. */
object MktCancelMode {
    const val FREE = "free"
    const val REQUEST = "request"
}

/** The renter's open change request (waiting for the owner). */
data class MktPendingChange(
    val id: String,
    val kind: String,
    val proposed: List<MktSlot>,
    val start: Instant?,
    val end: Instant?,
    val message: String?,
    val createdAt: Instant?,
) {
    companion object {
        fun of(o: JsonObject?): MktPendingChange? {
            val id = o?.str("id") ?: return null
            return MktPendingChange(
                id, o.str("kind").orEmpty(), o.objs("proposed_slots").mapNotNull { MktMeetSlots.slot(it) },
                o.instant("start_at"), o.instant("end_at"), o.str("message"), o.instant("created_at"),
            )
        }
    }
}

/** The latest answered / closed change request. */
data class MktLastChange(val id: String, val kind: String, val status: String, val ownerMessage: String?, val respondedAt: Instant?) {
    companion object {
        fun of(o: JsonObject?): MktLastChange? {
            val id = o?.str("id") ?: return null
            return MktLastChange(id, o.str("kind").orEmpty(), o.str("status").orEmpty(), o.str("owner_message"), o.instant("responded_at"))
        }
    }
}

/** One request-history step; `status` only for the synthetic order-status action. */
data class MktHistoryEvent(val action: String, val actor: String, val at: Instant?, val status: String?) {
    companion object {
        fun list(o: JsonObject): List<MktHistoryEvent> = o.objs("history").mapNotNull { e ->
            val action = e.str("action") ?: return@mapNotNull null
            MktHistoryEvent(action, e.str("actor") ?: "system", e.instant("at"), e.obj("data")?.str("status"))
        }
    }
}

object MktMeetupStatus {
    const val PENDING = "pending"
    const val COUNTERED = "countered"
    const val ACCEPTED = "accepted"
    const val DECLINED = "declined"
    const val CANCELLED = "cancelled"
    const val EXPIRED = "expired"

    /** Renter may cancel while pending, countered or accepted (§6). */
    fun renterCanCancel(status: String) = status == PENDING || status == COUNTERED || status == ACCEPTED

    /** Owner answers only pending requests. */
    fun ownerCanAnswer(status: String) = status == PENDING
}

data class MktRenterMeetup(
    val id: String,
    val status: String,
    val listingId: String?,
    val title: String,
    val titleI18n: Map<String, String>,
    val owner: MktOwnerRef,
    val qty: Int,
    val proposed: List<MktSlot>,
    val counter: MktSlot?,
    val accepted: MktSlot?,
    val rentalStart: Instant?,
    val rentalEnd: Instant?,
    val message: String?,
    val ownerMessage: String?,
    val orderId: String?,
    val expiresAt: Instant?,
    val createdAt: Instant?,
    val area: MktArea?,
    val address: MktAddress?,
    val contact: MktContact?,
    val pendingChange: MktPendingChange? = null,
    val lastChange: MktLastChange? = null,
    val history: List<MktHistoryEvent> = emptyList(),
    /** `actions.reschedule` (§6.5). */
    val canReschedule: Boolean = false,
    /** `actions.cancel`; null when the server did not send actions. */
    val cancelAction: Boolean? = null,
) {
    fun title(locale: Locale): String = MktText.pick(titleI18n, locale, title) ?: title
    val canCancel: Boolean get() = cancelAction ?: MktMeetupStatus.renterCanCancel(status)

    companion object {
        fun of(o: JsonObject): MktRenterMeetup? {
            val id = o.str("id") ?: return null
            val actions = o.obj("actions")
            return MktRenterMeetup(
                id = id,
                status = o.str("status") ?: MktMeetupStatus.PENDING,
                listingId = o.str("listing_id"),
                title = o.str("title").orEmpty(),
                titleI18n = o.i18n("title_i18n"),
                owner = MktOwnerRef.of(o.obj("owner")),
                qty = o.int("qty") ?: 1,
                proposed = o.objs("proposed_slots").mapNotNull { MktMeetSlots.slot(it) },
                counter = MktMeetSlots.slot(o.obj("counter")),
                accepted = MktMeetSlots.slot(o.obj("accepted")),
                rentalStart = o.instant("rental_start_at"),
                rentalEnd = o.instant("rental_end_at"),
                message = o.str("message"),
                ownerMessage = o.str("owner_message"),
                orderId = o.str("order_id"),
                expiresAt = o.instant("expires_at"),
                createdAt = o.instant("created_at"),
                area = MktArea.of(o.obj("area")),
                address = MktAddress.of(o.obj("address")),
                contact = MktContact.of(o.obj("owner_contact")),
                pendingChange = MktPendingChange.of(o.obj("pending_change")),
                lastChange = MktLastChange.of(o.obj("last_change")),
                history = MktHistoryEvent.list(o),
                canReschedule = actions?.bool("reschedule") == true,
                cancelAction = actions?.bool("cancel"),
            )
        }
    }
}

data class MktRenterBooking(
    val orderId: String,
    val number: String?,
    val quoteNumber: String?,
    val status: String,
    val listingId: String?,
    val title: String,
    val titleI18n: Map<String, String>,
    val owner: MktOwnerRef,
    val start: Instant?,
    val end: Instant?,
    val fulfilment: String?,
    val currency: String?,
    val totalMinor: Long?,
    val balanceMinor: Long?,
    val holdExpiresAt: Instant?,
    val quoteExpiresAt: Instant?,
    val address: MktAddress?,
    val contact: MktContact?,
    /** Renter-facing reference (quote number for quote requests, §6). */
    val displayNumber: String? = null,
    val pendingChange: MktPendingChange? = null,
    val lastChange: MktLastChange? = null,
    val history: List<MktHistoryEvent> = emptyList(),
    /** `actions.change_dates`. */
    val canChangeDates: Boolean = false,
    /** `actions.cancel`: [MktCancelMode.FREE], [MktCancelMode.REQUEST] or null. */
    val cancelMode: String? = null,
) {
    fun title(locale: Locale): String = MktText.pick(titleI18n, locale, title) ?: title
    val reference: String? get() = displayNumber ?: quoteNumber ?: number

    companion object {
        fun of(o: JsonObject): MktRenterBooking? {
            val id = o.str("order_id") ?: return null
            val actions = o.obj("actions")
            return MktRenterBooking(
                orderId = id,
                number = o.str("number"),
                quoteNumber = o.str("quote_number"),
                status = o.str("status").orEmpty(),
                listingId = o.str("listing_id"),
                title = o.str("title").orEmpty(),
                titleI18n = o.i18n("title_i18n"),
                owner = MktOwnerRef.of(o.obj("owner")),
                start = o.instant("start_at"),
                end = o.instant("end_at"),
                fulfilment = o.str("fulfilment"),
                currency = o.str("currency"),
                totalMinor = o.long("total_minor"),
                balanceMinor = o.long("balance_minor"),
                holdExpiresAt = o.instant("hold_expires_at"),
                quoteExpiresAt = o.instant("quote_expires_at"),
                address = MktAddress.of(o.obj("address")),
                contact = MktContact.of(o.obj("owner_contact")),
                displayNumber = o.str("display_number"),
                pendingChange = MktPendingChange.of(o.obj("pending_change")),
                lastChange = MktLastChange.of(o.obj("last_change")),
                history = MktHistoryEvent.list(o),
                canChangeDates = actions?.bool("change_dates") == true,
                cancelMode = actions?.str("cancel")?.takeIf { it == MktCancelMode.FREE || it == MktCancelMode.REQUEST },
            )
        }
    }
}

data class MktMyRequests(val meetups: List<MktRenterMeetup>, val bookings: List<MktRenterBooking>) {
    companion object {
        fun of(o: JsonObject) = MktMyRequests(
            o.objs("meetups").mapNotNull { MktRenterMeetup.of(it) },
            o.objs("bookings").mapNotNull { MktRenterBooking.of(it) },
        )
    }
}

/** Reserve answer: the portal order view (number / status) + whether online payment exists. */
data class MktReservation(val orderId: String?, val reference: String?, val status: String?, val onlinePayments: Boolean, val replay: Boolean) {
    /** Quote storefronts create a quote request; hold storefronts a held order. */
    val isQuote: Boolean get() = status == "quoted" || status == "draft" || status == "quote_requested"

    companion object {
        fun of(o: JsonObject, replay: Boolean = false): MktReservation {
            val order = o.obj("order")
            return MktReservation(
                orderId = order?.str("id"),
                reference = order?.str("display_number") ?: order?.str("quote_number") ?: order?.str("number"),
                status = order?.str("status"),
                onlinePayments = o.bool("online_payments") == true,
                replay = replay,
            )
        }
    }
}

// ---- owner side (§7) -------------------------------------------------------------------------

data class MktOwnerRenter(val id: String?, val name: String, val emailVerified: Boolean, val email: String?, val phone: String?, val blocked: Boolean)

data class MktOwnerMeetup(
    val id: String,
    val status: String,
    val classId: String?,
    val className: String,
    val locationId: String?,
    val locationName: String?,
    val qty: Int,
    val renter: MktOwnerRenter,
    val proposed: List<MktSlot>,
    val counter: MktSlot?,
    val accepted: MktSlot?,
    val rentalStart: Instant?,
    val rentalEnd: Instant?,
    val message: String?,
    val ownerMessage: String?,
    val orderId: String?,
    val orderNumber: String?,
    val expiresAt: Instant?,
    val createdAt: Instant?,
    val serverVersion: Long?,
) {
    val hasRentalPeriod: Boolean get() = rentalStart != null && rentalEnd != null

    companion object {
        fun of(o: JsonObject): MktOwnerMeetup? {
            val id = o.str("id") ?: return null
            val r = o.obj("renter")
            return MktOwnerMeetup(
                id = id,
                status = o.str("status") ?: MktMeetupStatus.PENDING,
                classId = o.str("class_id"),
                className = o.str("class_name").orEmpty(),
                locationId = o.str("location_id"),
                locationName = o.str("location_name"),
                qty = o.int("qty") ?: 1,
                renter = MktOwnerRenter(r?.str("id"), r?.str("name").orEmpty(), r?.bool("email_verified") == true, r?.str("email"), r?.str("phone"), r?.bool("blocked") == true),
                proposed = o.objs("proposed_slots").mapNotNull { MktMeetSlots.slot(it) },
                counter = MktMeetSlots.slot(o.obj("counter")),
                accepted = MktMeetSlots.slot(o.obj("accepted")),
                rentalStart = o.instant("rental_start_at"),
                rentalEnd = o.instant("rental_end_at"),
                message = o.str("message"),
                ownerMessage = o.str("owner_message"),
                orderId = o.str("order_id"),
                orderNumber = o.str("order_number"),
                expiresAt = o.instant("expires_at"),
                createdAt = o.instant("created_at"),
                serverVersion = o.long("server_version"),
            )
        }
    }
}

data class MktOwnerMeetupPage(val items: List<MktOwnerMeetup>, val nextCursor: String?, val pendingCount: Int) {
    companion object {
        fun of(o: JsonObject) = MktOwnerMeetupPage(o.objs("items").mapNotNull { MktOwnerMeetup.of(it) }, o.str("next_cursor"), o.int("pending_count") ?: 0)
    }
}

data class MktBlockedRenter(val renterId: String, val name: String, val reason: String?, val createdAt: Instant?) {
    companion object {
        fun list(o: JsonObject) = o.objs("items").mapNotNull { b ->
            b.str("renter_id")?.let { MktBlockedRenter(it, b.str("name").orEmpty(), b.str("reason"), b.instant("created_at")) }
        }
    }
}

data class MktOwnerLocation(
    val id: String,
    val name: String,
    val line1: String?,
    val line2: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    val offered: Boolean,
    val hours: Map<String, List<Pair<String, String>>>,
) {
    val hasCoordinates: Boolean get() = MktCoords.valid(latitude, longitude)
    val addressLine: String
        get() = listOfNotNull(line1, line2, listOfNotNull(city, state).joinToString(", ").takeIf { it.isNotBlank() }, postalCode, country)
            .filter { it.isNotBlank() }.joinToString(", ")

    companion object {
        fun of(o: JsonObject): MktOwnerLocation? {
            val id = o.str("id") ?: o.str("location_id") ?: return null
            return MktOwnerLocation(
                id, o.str("name").orEmpty(), o.str("address_line1"), o.str("address_line2"), o.str("city"), o.str("state"),
                o.str("postal_code"), o.str("country"), o.double("latitude"), o.double("longitude"), o.str("timezone"),
                o.bool("offered") == true, weekly(o.obj("hours")),
            )
        }
    }
}

/** `{mon: [["09:00","17:00"]]}` → typed windows (unknown day keys dropped). */
fun weekly(o: JsonObject?): Map<String, List<Pair<String, String>>> = o?.entries?.mapNotNull { (day, v) ->
    if (day !in MktWindows.DAYS) return@mapNotNull null
    val windows = (v as? JsonArray)?.mapNotNull { w ->
        val pair = (w as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (pair != null && pair.size == 2) pair[0] to pair[1] else null
    }.orEmpty()
    day to windows
}?.toMap().orEmpty()

data class MktOwnerSettings(
    val enabled: Boolean,
    val displayName: String?,
    val about: String?,
    val offerPickup: Boolean,
    val offerDelivery: Boolean,
    val deliveryRadiusKm: Double?,
    val offerMeetup: Boolean,
    val meetAvailability: Map<String, List<Pair<String, String>>>,
    val meetBlackouts: List<Pair<LocalDate, LocalDate>>,
    val slotMinutes: Int,
    val leadHours: Int,
    val timezone: String?,
    val serverVersion: Long?,
)

data class MktReadiness(
    val storefrontConfigured: Boolean,
    val storefrontEnabled: Boolean,
    val slug: String?,
    val bookingMode: String?,
    val publicClasses: Int,
    val listedClasses: Int,
    val locations: List<MktOwnerLocation>,
) {
    val hasLocatedOfferedLocation: Boolean get() = locations.any { it.offered && it.hasCoordinates }

    /** The listing toggle can be turned on (§10.7). */
    val canEnable: Boolean get() = storefrontEnabled && hasLocatedOfferedLocation
}

data class MktOwnerMarketplace(
    val settings: MktOwnerSettings,
    val readiness: MktReadiness,
    val categories: List<String>,
    val publicUrl: String?,
) {
    companion object {
        fun of(o: JsonObject): MktOwnerMarketplace {
            val s = o.obj("settings") ?: JsonObject(emptyMap())
            val r = o.obj("readiness") ?: JsonObject(emptyMap())
            return MktOwnerMarketplace(
                settings = MktOwnerSettings(
                    enabled = s.bool("enabled") == true,
                    displayName = s.str("display_name"),
                    about = s.str("about"),
                    offerPickup = s.bool("offer_pickup") ?: true,
                    offerDelivery = s.bool("offer_delivery") == true,
                    deliveryRadiusKm = s.double("delivery_radius_km"),
                    offerMeetup = s.bool("offer_meetup") == true,
                    meetAvailability = weekly(s.obj("meet_availability")),
                    meetBlackouts = s.objs("meet_blackouts").mapNotNull { b ->
                        val a = b.str("from")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                        val z = b.str("to")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: a
                        a to z
                    },
                    slotMinutes = s.int("meet_slot_minutes") ?: 30,
                    leadHours = s.int("meet_lead_hours") ?: 12,
                    timezone = s.str("meet_timezone"),
                    serverVersion = s.long("server_version"),
                ),
                readiness = MktReadiness(
                    storefrontConfigured = r.bool("storefront_configured") == true,
                    storefrontEnabled = r.bool("storefront_enabled") == true,
                    slug = r.str("slug"),
                    bookingMode = r.str("booking_mode"),
                    publicClasses = r.int("public_classes") ?: 0,
                    listedClasses = r.int("listed_classes") ?: 0,
                    locations = r.objs("locations").mapNotNull { MktOwnerLocation.of(it) },
                ),
                categories = o.strings("categories").ifEmpty { MktCategories.ALL },
                publicUrl = o.str("public_url"),
            )
        }
    }
}

data class MktOwnerListing(
    val classId: String,
    val name: String,
    val nameI18n: Map<String, String>,
    val eligible: Boolean,
    val listed: Boolean,
    val category: String?,
    val summaryI18n: Map<String, String>,
    /** null = inherit the account setting. */
    val offerPickup: Boolean?,
    val offerDelivery: Boolean?,
    val offerMeetup: Boolean?,
    val photoCount: Int,
    val serverVersion: Long?,
) {
    fun name(locale: Locale): String = MktText.pick(nameI18n, locale, name) ?: name

    companion object {
        fun of(o: JsonObject): MktOwnerListing? {
            val id = o.str("class_id") ?: return null
            return MktOwnerListing(
                id, o.str("name").orEmpty(), o.i18n("name_i18n"), o.bool("eligible") == true, o.bool("listed") == true,
                o.str("category"), o.i18n("summary_i18n"), o.bool("offer_pickup"), o.bool("offer_delivery"), o.bool("offer_meetup"),
                o.int("photo_count") ?: 0, o.long("server_version"),
            )
        }

        fun list(o: JsonObject) = o.objs("items").mapNotNull { of(it) }
    }
}
