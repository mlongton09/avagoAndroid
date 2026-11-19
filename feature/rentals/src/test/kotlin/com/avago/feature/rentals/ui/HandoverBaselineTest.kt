package com.avago.feature.rentals.ui

import com.avago.core.data.domain.RentalDamage
import com.avago.core.data.domain.RentalHandover
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class HandoverBaselineTest {

    private fun h(id: String, order: String, kind: String, at: String, damage: List<RentalDamage> = emptyList(), voided: Boolean = false) =
        RentalHandover(
            id = id, kind = kind, at = Instant.parse(at), orderId = order, orderLineId = null, assetId = "A",
            fuelLevelPct = null, chargeLevelPct = null, customerSignatureId = null, notes = null, damage = damage, voided = voided,
        )

    private fun d(label: String, severity: String?, location: String?) =
        RentalDamage(label = label, severity = severity, decision = null, chargeMinor = null, location = location)

    private val rows = listOf(
        h("1", "O1", "checkout", "2026-05-01T08:00:00Z"),
        h("2", "O0", "checkin", "2026-04-30T08:00:00Z", listOf(d("Dent", "major", "front_left"), d("Scuff", "minor", null))),
        h("3", "O9", "checkin", "2026-04-20T08:00:00Z"),
        h("4", "O9", "checkin", "2026-05-02T08:00:00Z", voided = true),
    )

    @Test
    fun `check in compares with this order's check out, check out with the latest non-voided check in`() {
        assertEquals("1", baselineFor("in", "O1", rows)?.id)
        assertNull(baselineFor("in", "O2", rows))
        assertEquals("2", baselineFor("out", "O2", rows)?.id)
    }

    @Test
    fun `damage becomes numbered sketch marks and skips findings without a location`() {
        val marks = marksOf(rows[1])
        assertEquals(1, marks.size)
        assertEquals("front_left", marks[0].zone)
        assertEquals("Dent", marks[0].label)
        assertEquals("major", marks[0].severity)
        assertEquals(1, marks[0].number)
        assertTrue(marksOf(null).isEmpty())
    }
}
