package com.avago.feature.rentals.marketplace

import com.avago.feature.rentals.domain.parseObj
import com.avago.feature.rentals.marketplace.domain.MktCancelMode
import com.avago.feature.rentals.marketplace.domain.MktListing
import com.avago.feature.rentals.marketplace.domain.MktMyRequests
import com.avago.feature.rentals.marketplace.domain.MktReservation
import com.avago.feature.rentals.marketplace.domain.MktSearchPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

class MktModelsTest {
    private fun json(s: String) = parseObj(s)!!

    @Test
    fun `search page parses cards, facets and paging and skips items without id`() {
        val p = MktSearchPage.of(
            json(
                """{"centre":{"label":"100 Mile House, BC","source":"place"},"total":2,"truncated":true,"next_cursor":"c2",
                "facets":{"categories":[{"category":"trailers","count":3}]},
                "items":[{"id":"l1","title":"Trailer","title_i18n":{"de-DE":"Anhänger"},"photo":"https://x/p.jpg",
                  "owner":{"slug":"acme","name":"Acme"},"price":{"currency":"CAD","from_minor":4500,"from_unit":"day"},
                  "distance_km":12.4,"distance_mi":7.7,"area":{"city":"100 Mile House","region":"BC","country":"CA"},
                  "fulfilment":{"pickup":true,"delivery":false,"meetup":true},"booking_mode":"hold","next_available":"2026-11-20T08:00:00Z"},
                  {"title":"no id"}]}""",
            ),
        )
        assertEquals("100 Mile House, BC", p.centreLabel)
        assertTrue(p.truncated)
        assertEquals("c2", p.nextCursor)
        assertEquals(listOf("trailers" to 3), p.facets)
        assertEquals(1, p.items.size)
        val i = p.items[0]
        assertEquals("Anhänger", i.title(Locale.GERMANY))
        assertEquals("Trailer", i.title(Locale.US))
        assertEquals(4500L, i.price!!.fromMinor)
        assertTrue(i.fulfilment.meetup)
        assertEquals("100 Mile House, BC", i.area!!.label)
        assertEquals(Instant.parse("2026-11-20T08:00:00Z"), i.nextAvailable)
    }

    @Test
    fun `listing parses nested blocks with safe defaults`() {
        val l = MktListing.of(
            json(
                """{"id":"l1","currency":"CAD","listing":{"title":"Trailer","specs":{"Length":"12 ft","Axles":[2]}},
                "photos":["a","b"],"price":{"rate_day_minor":4500,"rate_week_minor":20000},
                "owner":{"name":"Acme","review_count":0},"location":{"location_id":"loc1","area":{"city":"100 Mile House"},"distance_km":3.2},
                "fulfilment":{"pickup":false,"delivery":false,"meetup":true},
                "booking":{"mode":"quote","min_lead_minutes":120,"max_days":14},"meetup":{"slot_minutes":45,"lead_hours":6,"timezone":"America/Vancouver"},
                "policies":{"deposit_minor":10000,"require_licence":true},"online_payments":false}""",
            ),
        )!!
        assertEquals(listOf("a", "b"), l.photos)
        assertEquals("CAD", l.price!!.currency)
        assertEquals(4500L, l.price!!.day)
        assertTrue(l.isQuote)
        assertFalse(l.canReserve)
        assertTrue(l.canMeet)
        assertEquals(45, l.meetup!!.slotMinutes)
        assertEquals("loc1", l.locationId)
        assertEquals(listOf("Length" to "12 ft", "Axles" to "2"), l.specs)
        assertFalse(l.policies.isEmpty)
        assertFalse(l.onlinePayments)
        assertNull(MktListing.of(json("""{"title":"x"}""")))
    }

    @Test
    fun `my requests expose address and owner contact only when present`() {
        val r = MktMyRequests.of(
            json(
                """{"meetups":[{"id":"m1","status":"accepted","title":"Trailer","accepted":{"start_at":"2026-11-20T17:00:00Z","end_at":"2026-11-20T17:30:00Z"},
                  "address":{"name":"Yard","address_line1":"1 Main St","city":"100 Mile House","region":"BC","lat":51.6,"lng":-121.3},
                  "owner_contact":{"email":"o@x.ca","phone":null}},
                  {"id":"m2","status":"countered","counter":{"start_at":"2026-11-21T17:00:00Z","end_at":"2026-11-21T17:30:00Z"}}],
                "bookings":[{"order_id":"o1","quote_number":"Q-1","status":"quoted"}]}""",
            ),
        )
        val a = r.meetups[0]
        assertEquals("1 Main St, 100 Mile House, BC", a.address!!.oneLine)
        assertTrue(a.address!!.geoUri().startsWith("geo:51.6,-121.3"))
        assertEquals("o@x.ca", a.contact!!.email)
        assertNull(r.meetups[1].address)
        assertNull(r.meetups[1].contact)
        assertEquals(Instant.parse("2026-11-21T17:00:00Z"), r.meetups[1].counter!!.start)
        assertEquals("Q-1", r.bookings[0].reference)
    }

    @Test
    fun `renter changes, actions and the display number are parsed`() {
        val r = MktMyRequests.of(
            json(
                """{"meetups":[{"id":"m1","status":"accepted","title":"Trailer","actions":{"reschedule":true,"cancel":false},
                  "pending_change":{"id":"c1","kind":"meetup_time","proposed_slots":[{"start_at":"2026-11-22T17:00:00Z","end_at":"2026-11-22T17:30:00Z"}]},
                  "history":[{"action":"requested","actor":"renter","at":"2026-11-19T10:00:00Z"},{"action":"reschedule_requested","actor":"renter"}]}],
                "bookings":[{"order_id":"o1","number":"RO-1","quote_number":"Q-1","display_number":"RQ-1","status":"quoted",
                  "actions":{"change_dates":true,"cancel":"free"},
                  "last_change":{"id":"c2","kind":"booking_dates","status":"declined","owner_message":"Busy"}}]}""",
            ),
        )
        val m = r.meetups[0]
        assertTrue(m.canReschedule)
        assertFalse(m.canCancel)
        assertEquals("c1", m.pendingChange!!.id)
        assertEquals(Instant.parse("2026-11-22T17:00:00Z"), m.pendingChange!!.proposed.single().start)
        assertEquals(listOf("requested", "reschedule_requested"), m.history.map { it.action })
        val b = r.bookings[0]
        assertEquals("RQ-1", b.reference)
        assertTrue(b.canChangeDates)
        assertEquals(MktCancelMode.FREE, b.cancelMode)
        assertEquals("declined", b.lastChange!!.status)
        assertNull(b.pendingChange)
    }
    @Test
    fun `reservation reads the order view`() {
        val q = MktReservation.of(json("""{"order":{"id":"o1","quote_number":"Q-7","status":"quoted"},"online_payments":true}"""))
        assertEquals("Q-7", q.reference)
        assertTrue(q.isQuote)
        assertTrue(q.onlinePayments)
        assertFalse(MktReservation.of(json("""{"order":{"id":"o2","number":"R-1","status":"reserved"}}""")).isQuote)
    }
}
