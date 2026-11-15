package com.avago.feature.rentals.marketplace.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Pure rules of the Avago Marketplace client (AvagoSvc docs/rentals-api-marketplace.md §4–§7,
 * §10). Mirrors AvagoWeb `src/lib/rentals/marketplace.ts` where the web already decided.
 */

/** Distance unit shown and sent (`radius_km` / `radius_mi`). */
enum class DistanceUnit(val param: String) {
    KM("radius_km"),
    MI("radius_mi"),
    ;

    companion object {
        /** Regions that measure road distance in miles (CLDR "US" measurement system). */
        private val IMPERIAL = setOf("US", "LR", "MM")

        /** Miles for US, Liberia and Myanmar; kilometres everywhere else (also for unknown regions). */
        fun forRegion(region: String?): DistanceUnit =
            if (region?.uppercase(Locale.ROOT) in IMPERIAL) MI else KM
    }
}

object MktRadius {
    /** Chips offered in the search UI (same numbers in km or mi, as on the web). */
    val CHOICES = listOf(10, 25, 50, 100, 200)
    const val DEFAULT = 50
    const val MIN = 1
    const val MAX = 500

    fun clamp(r: Int): Int = r.coerceIn(MIN, MAX)

    /** Next larger chip for the empty state's "Search within …" (null when already at the largest). */
    fun wider(current: Int): Int? = CHOICES.firstOrNull { it > current }
}

object MktDistance {
    /** Approximate distance in the display unit; never shown as less than 1. */
    fun value(km: Double?, mi: Double?, unit: DistanceUnit): Long? {
        val v = if (unit == DistanceUnit.MI) mi else km
        return v?.let { max(1L, it.roundToLong()) }
    }
}

/** Search sort keys; `relevance` is only valid with a keyword. */
object MktSort {
    const val DISTANCE = "distance"
    const val PRICE = "price"
    const val SOONEST = "soonest"
    const val RELEVANCE = "relevance"
    val ALL = listOf(DISTANCE, PRICE, SOONEST, RELEVANCE)

    fun effective(sort: String, keyword: String): String =
        when {
            sort !in ALL -> DISTANCE
            sort == RELEVANCE && keyword.isBlank() -> DISTANCE
            else -> sort
        }
}

object MktFulfilment {
    const val PICKUP = "pickup"
    const val DELIVERY = "delivery"
    const val MEETUP = "meetup"
    val ALL = listOf(PICKUP, DELIVERY, MEETUP)
}

object MktCategories {
    val ALL = listOf(
        "tools", "construction", "landscaping", "vehicles", "trailers", "rv_camping", "boats_water",
        "recreation", "party_events", "audio_video", "farm", "snow_winter", "other",
    )
}

/** Date-only rental windows (search filter / meet-up "I also want to rent") → UTC instants. */
object MktDates {
    /** 00:00 local on [day]. */
    fun startOfDay(day: LocalDate, zone: ZoneId): Instant = day.atStartOfDay(zone).toInstant()

    /** 00:00 local on the day **after** [lastDay] (exclusive end). */
    fun endExclusive(lastDay: LocalDate, zone: ZoneId): Instant = lastDay.plusDays(1).atStartOfDay(zone).toInstant()

    /**
     * A rental of the days [firstDay]..[lastDay] that starts at [time] on the first day and ends
     * at the same time the day after the last day (so N picked days = N rental days).
     */
    fun rentalWindow(firstDay: LocalDate, lastDay: LocalDate, time: LocalTime, zone: ZoneId): Pair<Instant, Instant> =
        ZonedDateTime.of(firstDay, time, zone).toInstant() to ZonedDateTime.of(lastDay.plusDays(1), time, zone).toInstant()

    /** Number of picked days (inclusive). */
    fun days(firstDay: LocalDate, lastDay: LocalDate): Long = lastDay.toEpochDay() - firstDay.toEpochDay() + 1

    /** Availability calendar window: the whole month of [month] (28–31 days; contract allows 1–62). */
    fun monthWindow(month: LocalDate): Pair<LocalDate, Int> {
        val first = month.withDayOfMonth(1)
        return first to first.lengthOfMonth()
    }
}

/** Search state → `/public/marketplace/search` query (§4). */
data class MktSearchQuery(
    val lat: Double? = null,
    val lng: Double? = null,
    val place: String? = null,
    val country: String? = null,
    val radius: Int = MktRadius.DEFAULT,
    val unit: DistanceUnit = DistanceUnit.KM,
    val keyword: String = "",
    val category: String? = null,
    val firstDay: LocalDate? = null,
    val lastDay: LocalDate? = null,
    val minPriceMinor: Long? = null,
    val maxPriceMinor: Long? = null,
    val currency: String? = null,
    val fulfilment: String? = null,
    val sort: String = MktSort.DISTANCE,
    val limit: Int = 20,
) {
    val hasCoordinates: Boolean get() = lat != null && lng != null
    val hasLocation: Boolean get() = hasCoordinates || !place.isNullOrBlank()

    /** Number of active filters for the "Filters (n)" button (keyword, category, dates, price, fulfilment). */
    val activeFilters: Int
        get() = listOf(
            keyword.isNotBlank(), category != null, firstDay != null,
            minPriceMinor != null || maxPriceMinor != null, fulfilment != null,
        ).count { it }

    /** Query parameters; [cursor] pages. Coordinates win over a typed place. */
    fun params(zone: ZoneId, cursor: String? = null): List<Pair<String, String>> = buildList {
        if (lat != null && lng != null) {
            add("lat" to fmtCoord(lat))
            add("lng" to fmtCoord(lng))
        } else if (!place.isNullOrBlank()) {
            add("place" to place.trim().take(100))
            country?.takeIf { it.isNotBlank() }?.let { add("country" to it) }
        }
        add(unit.param to MktRadius.clamp(radius).toString())
        val q = keyword.trim().take(100)
        if (q.isNotEmpty()) add("q" to q)
        category?.takeIf { it in MktCategories.ALL }?.let { add("category" to it) }
        if (firstDay != null) {
            val last = lastDay?.takeIf { !it.isBefore(firstDay) } ?: firstDay
            add("start_at" to MktDates.startOfDay(firstDay, zone).toString())
            add("end_at" to MktDates.endExclusive(last, zone).toString())
        }
        var min = minPriceMinor
        var max = maxPriceMinor
        if (min != null && max != null && min > max) {
            val t = min
            min = max
            max = t
        }
        min?.let { add("min_price" to it.toString()) }
        max?.let { add("max_price" to it.toString()) }
        if ((min != null || max != null) && !currency.isNullOrBlank()) add("currency" to currency)
        fulfilment?.takeIf { it in MktFulfilment.ALL }?.let { add("fulfilment" to it) }
        add("sort" to MktSort.effective(sort, q))
        cursor?.takeIf { it.isNotBlank() }?.let { add("cursor" to it) }
        add("limit" to limit.coerceIn(1, 50).toString())
    }

    private fun fmtCoord(v: Double): String = String.format(Locale.ROOT, "%.5f", v)
}

/** A selectable meet-up time (owner zone shown in the UI; instants on the wire). */
data class MktSlot(val start: Instant, val end: Instant)

object MktSlots {
    const val MAX = 3
    val MAX_LENGTH: Duration = Duration.ofHours(4)
    const val MAX_DAYS_AHEAD = 90L
    const val MESSAGE_LIMIT = 500

    /** Toggle [slot] in a selection of at most [max], kept in time order (web `toggleSlot`). */
    fun toggle(selected: List<MktSlot>, slot: MktSlot, max: Int = MAX): List<MktSlot> = when {
        selected.any { it.start == slot.start } -> selected.filterNot { it.start == slot.start }
        selected.size >= max -> selected
        else -> (selected + slot).sortedBy { it.start }
    }

    /** Client-side check of the server rules (§6); returns an error code or null. */
    fun validate(selected: List<MktSlot>, leadHours: Int, now: Instant): String? {
        if (selected.isEmpty()) return "pick_slot"
        if (selected.size > MAX) return "invalid_slots"
        if (selected.map { it.start }.toSet().size != selected.size) return "invalid_slots"
        if (selected.zipWithNext().any { (a, b) -> !a.start.isBefore(b.start) }) return "invalid_slots"
        if (selected.any { !it.end.isAfter(it.start) || Duration.between(it.start, it.end) > MAX_LENGTH }) return "invalid_slots"
        val earliest = now.plus(Duration.ofHours(leadHours.toLong().coerceAtLeast(0)))
        if (selected.any { it.start.isBefore(earliest) }) return "lead_time"
        if (selected.any { it.start.isAfter(now.plus(Duration.ofDays(MAX_DAYS_AHEAD))) }) return "invalid_slots"
        return null
    }

    /** Slots grouped by their local day in [zone], in time order. */
    fun groupByDay(slots: List<MktSlot>, zone: ZoneId): List<Pair<LocalDate, List<MktSlot>>> =
        slots.sortedBy { it.start }
            .groupBy { it.start.atZone(zone).toLocalDate() }
            .toList()

    /** True when the owner's zone shows different wall-clock times than the device ("(owner's time)"). */
    fun zoneDiffers(owner: ZoneId, device: ZoneId, at: Instant): Boolean =
        owner.rules.getOffset(at) != device.rules.getOffset(at)
}

/** Reserve sheet validation (§10.3) — returns an error code or null. */
object MktReserve {
    fun validate(
        firstDay: LocalDate?,
        lastDay: LocalDate?,
        time: LocalTime,
        zone: ZoneId,
        minLeadMinutes: Int,
        maxDays: Int?,
        fulfilment: String,
        deliveryAddress: String,
        qty: Int,
        now: Instant,
    ): String? {
        if (firstDay == null || lastDay == null) return "dates_required"
        if (lastDay.isBefore(firstDay)) return "invalid_range"
        if (qty < 1) return "invalid_value"
        val (start, _) = MktDates.rentalWindow(firstDay, lastDay, time, zone)
        if (start.isBefore(now.plus(Duration.ofMinutes(minLeadMinutes.toLong().coerceAtLeast(0))))) return "lead_time"
        if (maxDays != null && maxDays > 0 && MktDates.days(firstDay, lastDay) > maxDays) return "too_long"
        if (fulfilment == MktFulfilment.DELIVERY && deliveryAddress.isBlank()) return "address_required"
        return null
    }
}

/** Owner "Propose another time" validation (§7 counter): future, end after start, ≤ 4 h, ≤ 90 days. */
object MktCounter {
    fun validate(start: Instant?, end: Instant?, now: Instant): String? = when {
        start == null -> "err_start"
        !start.isAfter(now) -> "err_past"
        end == null || !end.isAfter(start) -> "err_end"
        Duration.between(start, end) > MktSlots.MAX_LENGTH -> "invalid_slots"
        start.isAfter(now.plus(Duration.ofDays(MktSlots.MAX_DAYS_AHEAD))) -> "invalid_slots"
        else -> null
    }
}

object MktCoords {
    private val NUMBER = Regex("^-?\\d{1,3}(\\.\\d+)?$")

    /** Parses typed coordinates (dot or comma decimals); null when out of range or 0,0 (web `parseCoords`). */
    fun parse(lat: String, lng: String): Pair<Double, Double>? {
        val a = coord(lat, 90.0) ?: return null
        val b = coord(lng, 180.0) ?: return null
        if (a == 0.0 && b == 0.0) return null
        return a to b
    }

    fun valid(lat: Double?, lng: Double?): Boolean =
        lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)

    private fun coord(text: String, limit: Double): Double? {
        val t = text.trim().replace(',', '.')
        if (!NUMBER.matches(t)) return null
        return t.toDoubleOrNull()?.takeIf { it in -limit..limit }
    }
}

/** Weekly meet-up windows `{mon: [["09:00","17:00"]]}` ↔ editable "09:00-12:00, 13:00-17:00". */
object MktWindows {
    val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    private val HHMM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$|^24:00$")
    private val SHORT = Regex("^\\d:\\d\\d$")
    private val DASH = Regex("\\s*[-–]\\s*")
    private val RANGE = Regex("\\s*(?:\\.\\.|–|—|\\bto\\b|\\bbis\\b)\\s*")

    /** Null when malformed, overlapping or end ≤ start; empty text → empty list. */
    fun parse(text: String): List<Pair<String, String>>? {
        val out = mutableListOf<Pair<String, String>>()
        for (part in text.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val m = part.split(DASH)
            if (m.size != 2) return null
            val (a, b) = m.map { if (SHORT.matches(it)) "0$it" else it }
            if (!HHMM.matches(a) || !HHMM.matches(b) || b <= a) return null
            out += a to b
        }
        out.sortBy { it.first }
        for (i in 1 until out.size) if (out[i].first < out[i - 1].second) return null
        return out
    }

    fun format(w: List<Pair<String, String>>): String = w.joinToString(", ") { "${it.first}-${it.second}" }

    /** Blackouts "2026-12-24..2026-12-26" per line (single dates allowed); null when malformed. */
    fun parseBlackouts(text: String): List<Pair<LocalDate, LocalDate>>? {
        val out = mutableListOf<Pair<LocalDate, LocalDate>>()
        for (line in text.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val parts = line.split(RANGE)
            if (parts.size > 2) return null
            val a = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
            val b = if (parts.size == 2) runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null else a
            if (b.isBefore(a)) return null
            out += a to b
        }
        return out
    }

    fun formatBlackouts(b: List<Pair<LocalDate, LocalDate>>): String =
        b.joinToString("\n") { (a, z) -> if (a == z) a.toString() else "$a..$z" }
}

object MktText {
    /**
     * Picks the text for [locale] from an i18n map: exact tag, same language, en-US, en, the
     * untranslated [fallback], then the first translation.
     */
    fun pick(i18n: Map<String, String>, locale: Locale, fallback: String? = null): String? {
        val clean = i18n.filterValues { it.isNotBlank() }
        clean[locale.toLanguageTag()]?.let { return it }
        clean.entries.firstOrNull { it.key.substringBefore('-').equals(locale.language, true) }?.let { return it.value }
        return clean["en-US"] ?: clean["en"] ?: fallback?.takeIf { it.isNotBlank() } ?: clean.values.firstOrNull()
    }

    /** "100 Mile House, BC" — never a street address. */
    fun area(city: String?, region: String?, country: String?): String =
        listOfNotNull(city?.trim()?.takeIf { it.isNotEmpty() }, region?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(", ")
            .ifEmpty { country.orEmpty() }
}

/** Server code → friendly error (the UI maps each to a string). */
enum class MktError {
    Network, RateLimited, Unauthorized, NameRequired, LocationRequired, PlaceNotFound,
    InvalidSlots, SlotUnavailable, LeadTime, InvalidRange, TooLong, Unavailable, StorefrontDisabled,
    FulfilmentUnavailable, MeetupUnavailable, MeetupExists, InvalidState, BookingUnavailable,
    ClientUuidConflict, InvalidValue, SlotPassed, VersionConflict, StorefrontRequired,
    LocationCoordinatesRequired, NotFound, Forbidden, InvalidEmail, DatesRequired, AddressRequired,
    PickSlot, CounterStart, CounterPast, CounterEnd, Generic,
    ;

    /** The renter must sign in again (token cleared). */
    val needsSignIn: Boolean get() = this == Unauthorized

    companion object {
        /** [status] null = no answer (offline / transport). */
        fun of(status: Int?, code: String?): MktError = when {
            status == null -> Network
            status == 429 || code == "rate_limited" -> RateLimited
            code != null && code in CODES -> CODES.getValue(code)
            status == 401 -> Unauthorized
            status == 403 -> Forbidden
            status == 404 -> NotFound
            status == 408 || status >= 500 -> Network
            else -> Generic
        }

        /** Local validation codes (from [MktSlots], [MktReserve], [MktCounter]) share the map. */
        fun ofCode(code: String): MktError = CODES[code] ?: Generic

        private val CODES = mapOf(
            "rate_limited" to RateLimited,
            "unauthorized" to Unauthorized,
            "name_required" to NameRequired,
            "location_required" to LocationRequired,
            "place_not_found" to PlaceNotFound,
            "invalid_slots" to InvalidSlots,
            "slot_unavailable" to SlotUnavailable,
            "lead_time" to LeadTime,
            "invalid_range" to InvalidRange,
            "too_long" to TooLong,
            "unavailable" to Unavailable,
            "storefront_disabled" to StorefrontDisabled,
            "fulfilment_unavailable" to FulfilmentUnavailable,
            "meetup_unavailable" to MeetupUnavailable,
            "meetup_exists" to MeetupExists,
            "invalid_state" to InvalidState,
            "booking_unavailable" to BookingUnavailable,
            "client_uuid_conflict" to ClientUuidConflict,
            "invalid_value" to InvalidValue,
            "slot_passed" to SlotPassed,
            "version_conflict" to VersionConflict,
            "storefront_required" to StorefrontRequired,
            "location_coordinates_required" to LocationCoordinatesRequired,
            "not_found" to NotFound,
            "invalid_email" to InvalidEmail,
            "dates_required" to DatesRequired,
            "address_required" to AddressRequired,
            "pick_slot" to PickSlot,
            "err_start" to CounterStart,
            "err_past" to CounterPast,
            "err_end" to CounterEnd,
        )
    }
}

object MktEmail {
    private val RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun valid(email: String): Boolean = email.trim().let { RE.matches(it) && it.length <= 254 }
}

/** Magic-link `next`: only `/marketplace…` paths are kept (§3). */
object MktNext {
    fun sanitize(next: String?): String? = next?.trim()?.takeIf { it.startsWith("/marketplace") && !it.contains("//") }

    fun forListing(listingId: String): String = "/marketplace/$listingId"
}
