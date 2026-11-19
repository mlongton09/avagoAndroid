package com.avago.feature.rentals.domain

import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class RentalsTodayTest {

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun ev(type: String, overdue: Boolean = false, id: String = type) = ScheduleEvent(
        type = type, at = now, overdue = overdue, allocationId = id, kind = null, assetId = "a",
        assetName = "Unit", orderId = "o", orderNumber = null, customerName = null, startAt = null, endAt = null,
    )

    private fun alloc(kind: String, orderId: String? = "o1") = LocalFleet.Alloc(
        id = "al-$kind", assetId = "a", kind = kind, blocking = true, startAt = now, endAt = null,
        orderId = orderId, orderLineId = null, expiresAt = null,
    )

    private fun result(status: FleetStatus, current: LocalFleet.Alloc? = null, next: LocalFleet.Alloc? = null) = LocalFleet.Result(
        unit = LocalFleet.FleetUnit(
            assetId = "a", name = "Unit", assetType = null, classId = null, className = null, locationId = null,
            timeZone = null, maintenanceOpen = false, readiness = emptyList(),
        ),
        status = status, dueBackToday = false, nextChangeAt = null, current = current, next = next, dueAt = null,
    )

    @Test
    fun `today is split into overdue, going out, due back and maintenance`() {
        val a = TodayAgenda.of(
            listOf(ev("return", overdue = true, id = "r1"), ev("pickup"), ev("hold"), ev("return", id = "r2"), ev("maintenance")),
        )
        assertEquals(listOf("r1"), a.overdue.map { it.allocationId })
        assertEquals(listOf("pickup", "hold"), a.goingOut.map { it.type })
        assertEquals(listOf("r2"), a.dueBack.map { it.allocationId })
        assertEquals(listOf("maintenance"), a.maintenance.map { it.type })
    }

    @Test
    fun `a unit out on rent is waiting for check-in, a reserved one for check-out`() {
        assertEquals(HandoverTarget("o1", HandoverTarget.IN), HandoverTarget.of(result(FleetStatus.OVERDUE, current = alloc("on_rent"))))
        assertEquals(HandoverTarget("o1", HandoverTarget.OUT), HandoverTarget.of(result(FleetStatus.RESERVED, current = alloc("reserved"))))
        // A reservation starting soon (status reserved, not yet running) is also ready to check out.
        assertEquals(HandoverTarget("o1", HandoverTarget.OUT), HandoverTarget.of(result(FleetStatus.RESERVED, next = alloc("reserved"))))
    }

    @Test
    fun `no handover without a booking`() {
        assertNull(HandoverTarget.of(result(FleetStatus.AVAILABLE)))
        // A far-future booking (unit still available) is not offered for check-out yet.
        assertNull(HandoverTarget.of(result(FleetStatus.AVAILABLE, next = alloc("reserved"))))
        // Maintenance blocks and allocations without an order are not handovers.
        assertNull(HandoverTarget.of(result(FleetStatus.IN_MAINTENANCE, current = alloc("maintenance"))))
        assertNull(HandoverTarget.of(result(FleetStatus.ON_RENT, current = alloc("on_rent", orderId = null))))
    }
}
