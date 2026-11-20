package com.avago.core.data.rental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ClassAvailabilityTest {

    private val now = Instant.parse("2026-05-01T00:00:00Z")
    private fun day(d: Int) = Instant.parse("2026-05-%02dT08:00:00Z".format(d))

    private val units = listOf(
        ClassAvailability.Unit("u1", "c1", "l1"),
        ClassAvailability.Unit("u2", "c1", "l1"),
        ClassAvailability.Unit("u3", "c1", null),
        ClassAvailability.Unit("u4", "c1", "l2"),
        ClassAvailability.Unit("u5", "c2", "l1"),
    )

    private fun hold(start: Int, end: Int, qty: String = "1", status: String = "reserved", expires: Instant? = null) =
        ClassAvailability.ClassHold("c1", null, BigDecimal(qty), day(start), day(end), expires, status)

    @Test
    fun `units at the branch or without one count, minus the peak of busy units and class holds`() {
        val facts = ClassAvailability.Facts(
            units,
            busy = listOf(
                ClassAvailability.UnitBusy("u1", day(2), day(4)),
                ClassAvailability.UnitBusy("u4", day(2), day(4)),
            ),
            holds = listOf(hold(5, 7)),
        )

        // u1, u2, u3 at l1 (u3 has no branch); u1 busy 2nd-4th, a class hold from the 5th: peak 1.
        assertEquals(2, ClassAvailability.freeUnits(facts, "c1", "l1", day(1), day(8), now))
        // A hold from the 3rd overlaps u1's booking: both count at that point.
        val overlap = facts.copy(holds = listOf(hold(3, 7)))
        assertEquals(1, ClassAvailability.freeUnits(overlap, "c1", "l1", day(1), day(8), now))
        // Any branch: four units, peak of two busy units.
        assertEquals(2, ClassAvailability.freeUnits(facts, "c1", null, day(1), day(8), now))
    }

    @Test
    fun `holds of dead orders, expired holds and allocations outside the window are ignored and the count never goes negative`() {
        val facts = ClassAvailability.Facts(
            units.take(1),
            busy = listOf(ClassAvailability.UnitBusy("u1", day(10), null)),
            holds = listOf(
                hold(1, 9, status = "cancelled"),
                hold(1, 9, expires = now.minusSeconds(60)),
                hold(1, 9, qty = "3"),
            ),
        )

        assertEquals(0, ClassAvailability.freeUnits(facts, "c1", "l1", day(1), day(5), now))
        assertEquals(0, ClassAvailability.freeUnits(facts, "c1", "l1", day(12), null, now))
        assertEquals(1, ClassAvailability.freeUnits(facts.copy(holds = emptyList()), "c1", "l1", day(1), day(10), now))
    }
}
