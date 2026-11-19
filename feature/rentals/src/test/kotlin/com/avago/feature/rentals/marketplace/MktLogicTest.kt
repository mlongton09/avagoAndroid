package com.avago.feature.rentals.marketplace

import com.avago.feature.rentals.marketplace.domain.DistanceUnit
import com.avago.feature.rentals.marketplace.domain.MktAvailabilityHint
import com.avago.feature.rentals.marketplace.domain.MktBodies
import com.avago.feature.rentals.marketplace.domain.MktChangeDates
import com.avago.feature.rentals.marketplace.domain.MktDates
import com.avago.feature.rentals.marketplace.domain.MktDistance
import com.avago.feature.rentals.marketplace.domain.MktEarliest
import com.avago.feature.rentals.marketplace.domain.MktEmail
import com.avago.feature.rentals.marketplace.domain.MktError
import com.avago.feature.rentals.marketplace.domain.MktFulfilment
import com.avago.feature.rentals.marketplace.domain.MktNext
import com.avago.feature.rentals.marketplace.domain.MktRadius
import com.avago.feature.rentals.marketplace.domain.MktReserve
import com.avago.feature.rentals.marketplace.domain.MktSearchQuery
import com.avago.feature.rentals.marketplace.domain.MktSlot
import com.avago.feature.rentals.marketplace.domain.MktSlots
import com.avago.feature.rentals.marketplace.domain.MktSort
import com.avago.feature.rentals.marketplace.domain.MktText
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class MktLogicTest {
    // Pacific time with US rules (UTC-8 in late November; BC moves to permanent UTC-7 in newer tzdata).
    private val pacific = ZoneId.of("America/Los_Angeles")
    private val now = Instant.parse("2026-11-16T18:00:00Z")

    // ---- units / radius / distance ----

    @Test
    fun `miles only for US, Liberia and Myanmar`() {
        assertEquals(DistanceUnit.MI, DistanceUnit.forRegion("US"))
        assertEquals(DistanceUnit.MI, DistanceUnit.forRegion("lr"))
        assertEquals(DistanceUnit.MI, DistanceUnit.forRegion("MM"))
        assertEquals(DistanceUnit.KM, DistanceUnit.forRegion("CA"))
        assertEquals(DistanceUnit.KM, DistanceUnit.forRegion("GB"))
        assertEquals(DistanceUnit.KM, DistanceUnit.forRegion(null))
    }

    @Test
    fun `radius is clamped and widened to the next chip`() {
        assertEquals(1, MktRadius.clamp(0))
        assertEquals(500, MktRadius.clamp(900))
        assertEquals(100, MktRadius.wider(50))
        assertEquals(10, MktRadius.wider(5))
        assertNull(MktRadius.wider(200))
    }

    @Test
    fun `distance uses the display unit and never shows below 1`() {
        assertEquals(12L, MktDistance.value(12.4, 7.7, DistanceUnit.KM))
        assertEquals(8L, MktDistance.value(12.4, 7.7, DistanceUnit.MI))
        assertEquals(1L, MktDistance.value(0.2, 0.1, DistanceUnit.KM))
        assertNull(MktDistance.value(null, null, DistanceUnit.KM))
    }

    // ---- search query ----

    @Test
    fun `coordinates win over a typed place and radius uses the unit param`() {
        val q = MktSearchQuery(lat = 51.6448, lng = -121.2959, place = "100 Mile House", radius = 25, unit = DistanceUnit.MI)
        val p = q.params(pacific).toMap()
        assertEquals("51.64480", p["lat"])
        assertEquals("-121.29590", p["lng"])
        assertFalse(p.containsKey("place"))
        assertEquals("25", p["radius_mi"])
        assertFalse(p.containsKey("radius_km"))
        assertEquals("distance", p["sort"])
        assertEquals("20", p["limit"])
    }

    @Test
    fun `typed place is sent with country and trimmed keyword`() {
        val q = MktSearchQuery(place = " V0K ", country = "CA", keyword = "  trailer ", sort = MktSort.RELEVANCE)
        val p = q.params(pacific).toMap()
        assertEquals("V0K", p["place"])
        assertEquals("CA", p["country"])
        assertEquals("trailer", p["q"])
        assertEquals("relevance", p["sort"])
        assertEquals("50", p["radius_km"])
    }

    @Test
    fun `relevance falls back to distance without a keyword`() {
        assertEquals("distance", MktSearchQuery(place = "x", sort = MktSort.RELEVANCE).params(pacific).toMap()["sort"])
        assertEquals("distance", MktSort.effective("bogus", "q"))
    }

    @Test
    fun `dates are sent as the renter's local calendar days with the zone`() {
        val q = MktSearchQuery(place = "x", firstDay = LocalDate.of(2026, 11, 20), lastDay = LocalDate.of(2026, 11, 22))
        val p = q.params(pacific).toMap()
        assertEquals("2026-11-20", p["start_date"])
        assertEquals("2026-11-22", p["end_date"])
        assertEquals(pacific.id, p["tz"])
        assertFalse(p.containsKey("start_at"))
        // A single day is both start and end; fixed offsets send no tz.
        val single = MktSearchQuery(place = "x", firstDay = LocalDate.of(2026, 11, 20)).params(java.time.ZoneOffset.ofHours(-8)).toMap()
        assertEquals("2026-11-20", single["end_date"])
        assertFalse(single.containsKey("tz"))
    }

    @Test
    fun `availability hint uses the earliest bookable start`() {
        val now = Instant.parse("2026-11-20T18:00:00Z")
        assertEquals(MktAvailabilityHint.Now, MktEarliest.hint(now, now.plusSeconds(600), now))
        val later = now.plusSeconds(3 * 3600)
        assertEquals(MktAvailabilityHint.From(later), MktEarliest.hint(now, later, now))
        val days = now.plusSeconds(3 * 86400)
        assertEquals(MktAvailabilityHint.Next(days), MktEarliest.hint(days, later, now))
        assertEquals(MktAvailabilityHint.None, MktEarliest.hint(null, null, now))
        assertEquals(MktAvailabilityHint.Now, MktEarliest.hint(now, null, now))
    }

    @Test
    fun `change dates keeps the booking's times of day and rejects the same window`() {
        val start = Instant.parse("2026-11-20T17:00:00Z") // 09:00 Pacific
        val end = Instant.parse("2026-11-23T17:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 20) to LocalDate.of(2026, 11, 22), MktChangeDates.days(start, end, pacific))
        val (s, e) = MktChangeDates.window(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 26), start, end, pacific)
        assertEquals(Instant.parse("2026-11-25T17:00:00Z"), s)
        assertEquals(Instant.parse("2026-11-27T17:00:00Z"), e)
        val now = Instant.parse("2026-11-19T00:00:00Z")
        assertNull(MktChangeDates.validate(s, e, start, end, null, null, now))
        assertEquals("same_dates", MktChangeDates.validate(start, end, start, end, null, null, now))
        assertEquals("lead_time", MktChangeDates.validate(s, e, start, end, s.plusSeconds(60), null, now))
        assertEquals("too_long", MktChangeDates.validate(s, e, start, end, null, 1, now))
        assertEquals(MktError.SameDates, MktError.ofCode("same_dates"))
    }

    @Test
    fun `price bounds are ordered and carry the currency, invalid filters are dropped`() {
        val q = MktSearchQuery(place = "x", minPriceMinor = 9000, maxPriceMinor = 1000, currency = "CAD", category = "nope", fulfilment = "drone")
        val p = q.params(pacific, cursor = "o20").toMap()
        assertEquals("1000", p["min_price"])
        assertEquals("9000", p["max_price"])
        assertEquals("CAD", p["currency"])
        assertFalse(p.containsKey("category"))
        assertFalse(p.containsKey("fulfilment"))
        assertEquals("o20", p["cursor"])
        assertFalse(MktSearchQuery(place = "x").params(pacific).toMap().containsKey("currency"))
    }

    @Test
    fun `location and active filter count`() {
        assertFalse(MktSearchQuery().hasLocation)
        assertTrue(MktSearchQuery(place = "V0K").hasLocation)
        assertTrue(MktSearchQuery(lat = 1.0, lng = 2.0).hasLocation)
        assertEquals(3, MktSearchQuery(keyword = "a", category = "tools", fulfilment = MktFulfilment.MEETUP).activeFilters)
    }

    // ---- slots ----

    private fun slot(h: Long, len: Long = 30) = MktSlot(now.plus(Duration.ofHours(h)), now.plus(Duration.ofHours(h)).plus(Duration.ofMinutes(len)))

    @Test
    fun `toggle keeps at most three slots in time order`() {
        var s = MktSlots.toggle(emptyList(), slot(30))
        s = MktSlots.toggle(s, slot(20))
        s = MktSlots.toggle(s, slot(40))
        assertEquals(listOf(slot(20), slot(30), slot(40)), s)
        assertEquals(3, MktSlots.toggle(s, slot(50)).size)
        assertEquals(listOf(slot(20), slot(40)), MktSlots.toggle(s, slot(30)))
    }

    @Test
    fun `slot validation mirrors the server rules`() {
        assertEquals("pick_slot", MktSlots.validate(emptyList(), 12, now))
        assertNull(MktSlots.validate(listOf(slot(20), slot(30)), 12, now))
        assertEquals("invalid_slots", MktSlots.validate(listOf(slot(30), slot(20)), 12, now))
        assertEquals("invalid_slots", MktSlots.validate(listOf(slot(20), slot(20)), 12, now))
        assertEquals("invalid_slots", MktSlots.validate(listOf(slot(20), slot(30), slot(40), slot(50)), 12, now))
        assertEquals("invalid_slots", MktSlots.validate(listOf(slot(20, len = 5 * 60)), 12, now))
        assertEquals("lead_time", MktSlots.validate(listOf(slot(5)), 12, now))
        assertEquals("invalid_slots", MktSlots.validate(listOf(slot(24 * 91)), 12, now))
    }

    @Test
    fun `slots group by the owner's local day`() {
        // 2026-11-17T07:30Z is still the 16th in Pacific time.
        val a = MktSlot(Instant.parse("2026-11-17T07:30:00Z"), Instant.parse("2026-11-17T08:00:00Z"))
        val b = MktSlot(Instant.parse("2026-11-17T17:00:00Z"), Instant.parse("2026-11-17T17:30:00Z"))
        val g = MktSlots.groupByDay(listOf(b, a), pacific)
        assertEquals(listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 17)), g.map { it.first })
        assertTrue(MktSlots.zoneDiffers(pacific, ZoneId.of("Europe/Berlin"), now))
        assertFalse(MktSlots.zoneDiffers(pacific, java.time.ZoneOffset.ofHours(-8), now))
    }

    // ---- reserve / counter ----

    @Test
    fun `reserve validation covers dates, lead time, max days and delivery address`() {
        val d = LocalDate.of(2026, 11, 20)
        val t = LocalTime.of(9, 0)
        fun v(first: LocalDate?, last: LocalDate?, lead: Int = 60, max: Int? = 7, ful: String = "pickup", addr: String = "", qty: Int = 1) =
            MktReserve.validate(first, last, t, pacific, lead, max, ful, addr, qty, now)
        assertEquals("dates_required", v(null, null))
        assertEquals("invalid_range", v(d, d.minusDays(1)))
        assertEquals("invalid_value", v(d, d, qty = 0))
        assertEquals("lead_time", v(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16)))
        assertEquals("too_long", v(d, d.plusDays(7)))
        assertNull(v(d, d.plusDays(6)))
        assertEquals("address_required", v(d, d, ful = "delivery"))
        assertNull(v(d, d, ful = "delivery", addr = "1 Main St"))
    }

    @Test
    fun `rental window ends at the same time after the last day`() {
        val (s, e) = MktDates.rentalWindow(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21), LocalTime.of(9, 0), pacific)
        assertEquals(Instant.parse("2026-11-20T17:00:00Z"), s)
        assertEquals(Instant.parse("2026-11-22T17:00:00Z"), e)
        assertEquals(2, MktDates.days(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21)))
        assertEquals(LocalDate.of(2026, 2, 1) to 28, MktDates.monthWindow(LocalDate.of(2026, 2, 17)))
    }

    @Test
    fun `locale text prefers exact, then language, then en-US, then the fallback`() {
        val m = mapOf("en-US" to "Trailer", "de-DE" to "Anhänger")
        assertEquals("Anhänger", MktText.pick(m, Locale.GERMANY))
        assertEquals("Anhänger", MktText.pick(m, Locale("de", "AT")))
        assertEquals("Trailer", MktText.pick(m, Locale.FRANCE))
        assertEquals("Base", MktText.pick(mapOf("de-DE" to "Anhänger"), Locale.US, "Base"))
        assertEquals("Anhänger", MktText.pick(mapOf("de-DE" to "Anhänger"), Locale.US))
        assertNull(MktText.pick(emptyMap(), Locale.US))
        assertEquals("100 Mile House, BC", MktText.area("100 Mile House", "BC", "CA"))
        assertEquals("CA", MktText.area(" ", null, "CA"))
    }

    @Test
    fun `error codes map to friendly errors`() {
        assertEquals(MktError.Network, MktError.of(null, null))
        assertEquals(MktError.RateLimited, MktError.of(429, null))
        assertEquals(MktError.Unavailable, MktError.of(409, "unavailable"))
        assertEquals(MktError.MeetupExists, MktError.of(409, "meetup_exists"))
        assertEquals(MktError.Unauthorized, MktError.of(401, null))
        assertTrue(MktError.of(401, "unauthorized").needsSignIn)
        assertEquals(MktError.Forbidden, MktError.of(403, "forbidden_x"))
        assertEquals(MktError.Network, MktError.of(503, null))
        assertEquals(MktError.Generic, MktError.of(400, "weird"))
        assertEquals(MktError.LeadTime, MktError.ofCode("lead_time"))
    }

    @Test
    fun `email and next path`() {
        assertTrue(MktEmail.valid(" ann@example.com "))
        assertFalse(MktEmail.valid("ann@"))
        assertEquals("/marketplace/abc", MktNext.sanitize("/marketplace/abc"))
        assertNull(MktNext.sanitize("/app/settings"))
        assertNull(MktNext.sanitize("/marketplace//evil.com"))
    }

    @Test
    fun `reserve body only sends the address for delivery`() {
        val s = Instant.parse("2026-11-20T17:00:00Z")
        val e = Instant.parse("2026-11-21T17:00:00Z")
        val pickup = MktBodies.reserve(s, e, null, 1, "pickup", "1 Main", " ", "u1")
        assertEquals(JsonNull, pickup["delivery_address"])
        assertEquals(JsonNull, pickup["notes"])
        assertEquals("u1", pickup["client_uuid"]!!.jsonPrimitive.content)
        val delivery = MktBodies.reserve(s, e, "loc", 2, "delivery", "1 Main", "hi", "u1")
        assertEquals("1 Main", delivery["delivery_address"]!!.jsonPrimitive.content)
        assertEquals("2026-11-20T17:00:00Z", delivery["start_at"]!!.jsonPrimitive.content)
    }

    @Test
    fun `meetup body sends the rental period only when complete`() {
        val a = slot(20)
        val body = MktBodies.meetup(listOf(a), "x".repeat(800), 1, null, Instant.EPOCH, null)
        assertEquals(1, body["slots"]!!.jsonArray.size)
        assertEquals(500, body["message"]!!.jsonPrimitive.content.length)
        assertEquals(JsonNull, body["rental_start_at"])
        assertEquals(JsonNull, body["rental_end_at"])
    }
}
