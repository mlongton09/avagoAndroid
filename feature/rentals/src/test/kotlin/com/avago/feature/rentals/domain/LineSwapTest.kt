package com.avago.feature.rentals.domain

import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.rental.LocalFleet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/** Line swap eligibility and replacement candidates (`rental.rental_order_lines.swap`). */
class LineSwapTest {
    private val at = Instant.parse("2026-05-02T12:00:00Z")
    private val now = at
    private val end = Instant.parse("2026-05-05T08:00:00Z")

    private val line = RentalOrderLine(
        id = "L1", version = 7, orderId = "O1", lineNo = 1, kind = "rental", classId = "K", assetId = "A",
        productId = null, parentLineId = null, description = "Van A", qty = BigDecimal.ONE,
        startAt = Instant.parse("2026-05-01T08:00:00Z"), endAt = end, actualOutAt = null, actualInAt = null,
        unitPriceMinor = null, amountMinor = 40000, discountMinor = 0, taxMinor = 0, status = "out",
        kitInstanceId = null, assignUnitLater = false, sync = RecordSyncState.SAVED,
    )

    @Test
    fun `only checked out rental lines with a unit are swappable`() {
        assertTrue(LineSwap.swappable(line))
        assertFalse(LineSwap.swappable(line.copy(status = "returned")))
        assertFalse(LineSwap.swappable(line.copy(status = "open")))
        assertFalse(LineSwap.swappable(line.copy(kind = "fee")))
        assertFalse(LineSwap.swappable(line.copy(assetId = null)))
    }

    private fun unit(id: String, cls: String?, ready: Boolean = true) = LocalFleet.FleetUnit(
        assetId = id, name = "Unit $id", assetType = null, classId = cls, className = null, locationId = null,
        timeZone = null, maintenanceOpen = false, readiness = if (ready) emptyList() else listOf(LocalFleet.REASON_OPEN_DEFECT),
    )

    @Test
    fun `candidates are free ready units of the same class except the old one`() {
        val units = listOf(unit("A", "K"), unit("C", "K"), unit("B", "K"), unit("D", "K", ready = false), unit("E", "Z"), unit("F", "K"))
        val busyF = LocalFleet.Alloc(
            id = "Y", assetId = "F", kind = "reserved", blocking = true,
            startAt = Instant.parse("2026-05-03T00:00:00Z"), endAt = Instant.parse("2026-05-04T00:00:00Z"),
            orderId = null, orderLineId = null, expiresAt = null,
        )
        val inputs = LocalFleet.Inputs(units, listOf(busyF))
        val fleet = LocalFleet.compute(inputs, now).values
        val busy = LocalSchedule.busy(inputs)
        assertEquals(listOf("B", "C"), LineSwap.candidates(fleet, busy, "K", "A", at, end, now).map { it.assetId })
        // No class on the line: the old unit's class is used.
        assertEquals(listOf("B", "C"), LineSwap.candidates(fleet, busy, null, "A", at, end, now).map { it.assetId })
    }
}
