package com.avago.feature.rentalsops.domain

import com.avago.feature.rentalsops.domain.UnitAssignment.Allocation
import com.avago.feature.rentalsops.domain.UnitAssignment.Line
import com.avago.feature.rentalsops.domain.UnitAssignment.ServerUnit
import com.avago.feature.rentalsops.domain.UnitAssignment.Unit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class UnitAssignmentTest {

    private val d1 = Instant.parse("2026-06-01T08:00:00Z")
    private val d3 = Instant.parse("2026-06-03T08:00:00Z")
    private val d5 = Instant.parse("2026-06-05T08:00:00Z")
    private val line = Line("L1", "O1", classId = "C", locationId = "BR1", start = d1, end = d3)

    private fun u(id: String, name: String = id, cls: String = "C", loc: String? = "BR1", ready: Boolean = true, maint: Boolean = false) =
        Unit(id, name, cls, loc, ready, inMaintenance = maint)

    private fun a(id: String, asset: String, kind: String, s: Instant, e: Instant, active: Boolean = true, blocking: Boolean = true, line: String? = null) =
        Allocation(id, asset, kind, s, e, active, blocking, line)

    @Test
    fun `filters class, maintenance and overlapping blocking allocations`() {
        val units = listOf(u("A"), u("B", cls = "OTHER"), u("C", maint = true), u("D"), u("E"))
        val allocs = listOf(
            a("x", "D", "reserved", d1, d5),            // overlaps → excluded
            a("y", "E", "quote", d1, d5),                // quote never blocks
            a("z", "A", "on_rent", d3, d5),              // half-open: starts exactly at line end → free
        )
        val ids = UnitAssignment.candidates(line, units, allocs).map { it.unit.assetId }
        assertEquals(listOf("A", "E"), ids)
    }

    @Test
    fun `own allocation, inactive and non-blocking rows do not conflict`() {
        val units = listOf(u("A"), u("B"), u("C"))
        val allocs = listOf(
            a("own", "A", "hold", d1, d3, line = "L1"),
            a("old", "B", "reserved", d1, d3, active = false),
            a("rev", "C", "reserved", d1, d3, blocking = false),
        )
        assertEquals(3, UnitAssignment.candidates(line, units, allocs).size)
    }

    @Test
    fun `transfer allocation blocks the unit while in transit`() {
        val allocs = listOf(a("t", "A", "transfer", d1, d5))
        assertTrue(UnitAssignment.candidates(line, listOf(u("A")), allocs).isEmpty())
    }

    @Test
    fun `suggestion prefers ready, then same branch, then name`() {
        val units = listOf(
            u("1", "Zeta", loc = "BR1", ready = false),
            u("2", "beta", loc = "BR2"),
            u("3", "Alpha", loc = "BR2"),
            u("4", "Omega", loc = "BR1"),
        )
        val order = UnitAssignment.candidates(line, units, emptyList()).map { it.unit.name }
        assertEquals(listOf("Omega", "Alpha", "beta", "Zeta"), order)
        assertTrue(UnitAssignment.candidates(line, units, emptyList()).none { it.confirmed })
    }

    @Test
    fun `server result wins and confirms`() {
        val units = listOf(u("A"), u("B"))
        val local = UnitAssignment.candidates(line, units, emptyList())
        val server = listOf(
            ServerUnit("A", "A", available = false, ready = true, nextFreeAt = d5), // booked meanwhile
            ServerUnit("B", "B", available = true, ready = false, nextFreeAt = null),
            ServerUnit("N", "New", available = true, ready = true, nextFreeAt = null), // not cached yet
        )
        val merged = UnitAssignment.mergeServer(line, local, server, units.associateBy { it.assetId })
        assertEquals(listOf("N", "B"), merged.map { it.unit.assetId })
        assertTrue(merged.all { it.confirmed })
        assertFalse(merged.first { it.unit.assetId == "B" }.unit.ready)
    }

    @Test
    fun `needs assignment only for open class-booked rental lines`() {
        assertTrue(UnitAssignment.needsAssignment("rental", null, "C", "open"))
        assertFalse(UnitAssignment.needsAssignment("rental", "A", "C", "open"))
        assertFalse(UnitAssignment.needsAssignment("rental", null, null, "open"))
        assertFalse(UnitAssignment.needsAssignment("rental", null, "C", "cancelled"))
        assertFalse(UnitAssignment.needsAssignment("fee", null, "C", "open"))
    }

    @Test
    fun `overlap is half open`() {
        assertFalse(UnitAssignment.overlaps(d1, d3, d3, d5))
        assertTrue(UnitAssignment.overlaps(d1, d5, d3, d3.plusSeconds(1)))
    }
}
