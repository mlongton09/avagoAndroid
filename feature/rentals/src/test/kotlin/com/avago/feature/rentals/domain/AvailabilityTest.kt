package com.avago.feature.rentals.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AvailabilityTest {

    private fun t(s: String) = Instant.parse("2026-05-${s}Z")
    private val now = t("01T10:00:00")

    @Test
    fun `half open intervals do not overlap at the boundary`() {
        assertFalse(Availability.overlaps(t("01T10:00:00"), t("02T10:00:00"), t("02T10:00:00"), t("03T10:00:00")))
        assertTrue(Availability.overlaps(t("01T10:00:00"), t("02T10:00:01"), t("02T10:00:00"), t("03T10:00:00")))
        // Open-ended busy interval blocks everything after its start.
        assertTrue(Availability.overlaps(t("10T00:00:00"), t("11T00:00:00"), t("02T00:00:00"), null))
    }

    @Test
    fun `back to back booking is available and next free skips consecutive blocks`() {
        val busy = listOf(
            Availability.Busy("A", "reserved", t("02T08:00:00"), t("04T08:00:00")),
            Availability.Busy("A", "buffer", t("04T08:00:00"), t("04T12:00:00")),
            Availability.Busy("A", "reserved", t("05T08:00:00"), t("06T08:00:00")),
        )
        val free = Availability.unit("A", busy, t("01T08:00:00"), t("02T08:00:00"), now)
        assertTrue(free.available)
        assertEquals(t("01T08:00:00"), free.nextFreeAt)

        // 1 day starting on the 3rd → blocked; the 4h gap after the buffer is too short for a day
        // before the next reservation (04T12 + 24h > 05T08), so the next free start is 06T08.
        val r = Availability.unit("A", busy, t("03T08:00:00"), t("04T08:00:00"), now)
        assertFalse(r.available)
        assertEquals(1, r.conflicts.size)
        assertEquals(t("06T08:00:00"), r.nextFreeAt)
    }

    @Test
    fun `overdue on rent keeps the unit busy until now and open maintenance has no next free`() {
        val overdue = Availability.Busy("A", "on_rent", t("01T00:00:00"), t("01T08:00:00"))
        val r = Availability.unit("A", listOf(overdue), t("01T09:00:00"), t("01T09:30:00"), now)
        assertFalse(r.available)
        assertEquals(now.plusSeconds(3600), r.nextFreeAt, "server extend_overdue horizon")
        assertFalse(Availability.unit("A", listOf(overdue), now, now.plusSeconds(60), now).available)
        // A rental due in 2 h is not stretched.
        val onTime = Availability.Busy("A", "on_rent", t("01T00:00:00"), t("01T12:00:00"))
        assertEquals(t("01T12:00:00"), Availability.effective(onTime, now).end)

        val maint = Availability.Busy("B", "maintenance", t("01T00:00:00"), null)
        val m = Availability.unit("B", listOf(maint), t("03T00:00:00"), t("04T00:00:00"), now)
        assertFalse(m.available)
        assertNull(m.nextFreeAt)
    }

    @Test
    fun `non blocking and quote allocations do not block`() {
        val busy = listOf(
            Availability.Busy("A", "reserved", t("02T00:00:00"), t("03T00:00:00"), blocking = false),
            Availability.Busy("A", "quote", t("02T00:00:00"), t("03T00:00:00")),
        )
        assertTrue(Availability.unit("A", busy, t("02T00:00:00"), t("03T00:00:00"), now).available)
    }

    @Test
    fun `class result counts free units against qty`() {
        val busy = listOf(Availability.Busy("A", "hold", t("02T00:00:00"), t("03T00:00:00")))
        val (cls, units) = Availability.forClass("C1", listOf("A", "B", "B"), busy, t("02T00:00:00"), t("02T12:00:00"), 2, now)
        assertEquals(2, cls.totalUnits)
        assertEquals(1, cls.freeUnits)
        assertFalse(cls.enough)
        assertEquals(listOf("A", "B"), units.map { it.assetId })
        val (one, _) = Availability.forClass("C1", listOf("A", "B"), busy, t("02T00:00:00"), t("02T12:00:00"), 0, now)
        assertEquals(1, one.qty)
        assertTrue(one.enough)
    }

    @Test
    fun `end before start is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Availability.unit("A", emptyList(), t("02T00:00:00"), t("01T00:00:00"), now)
        }
        assertEquals(t("01T00:00:00"), Availability.nextFreeAt(emptyList(), t("01T00:00:00"), Duration.ofHours(1)))
    }
}
