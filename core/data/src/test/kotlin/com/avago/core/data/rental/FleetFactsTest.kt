package com.avago.core.data.rental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Server fleet rule (fleet.rs) on stored facts, decided at the time the fleet is shown. */
class FleetFactsTest {

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun facts(vararg units: FleetFacts.UnitFacts) =
        FleetFacts(units.toList(), emptyList(), emptyMap(), emptyMap(), emptyMap())

    private fun unit(
        defectRank: Int = 0,
        offline: Boolean = false,
        work: List<FleetFacts.WorkWindow> = emptyList(),
        pm: List<FleetFacts.PmDue> = emptyList(),
        checklistOpen: Boolean = false,
    ) = FleetFacts.UnitFacts("A", "Unit A", null, null, null, null, "UTC", offline, defectRank, work, pm, checklistOpen)

    private fun reasons(f: FleetFacts, at: Instant = now) = f.toInputs(at).units.single().readiness

    @Test
    fun `a clean unit is ready`() {
        val u = facts(unit()).toInputs(now).units.single()
        assertTrue(u.ready)
        assertFalse(u.maintenanceOpen)
    }

    @Test
    fun `defects count from major and out of service opens maintenance`() {
        assertEquals(emptyList<String>(), reasons(facts(unit(defectRank = FleetFacts.severityRank("minor")))))
        assertEquals(listOf(LocalFleet.REASON_OPEN_DEFECT), reasons(facts(unit(defectRank = FleetFacts.severityRank("major")))))
        val oos = facts(unit(defectRank = FleetFacts.severityRank("out_of_service"))).toInputs(now).units.single()
        assertTrue(oos.maintenanceOpen)
        assertEquals(listOf(LocalFleet.REASON_OPEN_DEFECT), oos.readiness)
    }

    @Test
    fun `a planned work order only counts while its window covers now`() {
        val later = FleetFacts.WorkWindow(false, Instant.parse("2026-05-01T12:00:00Z"), Instant.parse("2026-05-01T14:00:00Z"))
        val f = facts(unit(work = listOf(later)))
        assertTrue(f.toInputs(now).units.single().ready)
        val during = f.toInputs(Instant.parse("2026-05-01T13:00:00Z")).units.single()
        assertEquals(listOf(LocalFleet.REASON_OPEN_WORK_ORDER), during.readiness)
        assertTrue(during.maintenanceOpen)
        // No end: one day from the start.
        val noEnd = FleetFacts.WorkWindow(false, Instant.parse("2026-04-30T11:00:00Z"), null)
        assertTrue(noEnd.coversNow(now))
        assertFalse(noEnd.coversNow(Instant.parse("2026-05-01T11:00:00Z")))
        assertTrue(FleetFacts.WorkWindow(true, null, null).coversNow(now))
    }

    @Test
    fun `pm overdue by date or due by meter`() {
        val byDate = FleetFacts.PmDue(Instant.parse("2026-05-01T11:00:00Z"), meterDue = false)
        val f = facts(unit(pm = listOf(byDate)))
        assertTrue(reasons(f).isEmpty())
        assertEquals(listOf(LocalFleet.REASON_PM_OVERDUE), reasons(f, Instant.parse("2026-05-01T12:00:00Z")))
        val byMeter = FleetFacts.PmDue(null, meterDue = true)
        assertEquals(listOf(LocalFleet.REASON_PM_METER_DUE), reasons(facts(unit(pm = listOf(byMeter)))))
    }

    @Test
    fun `offline status and an open checklist keep the unit not ready`() {
        assertEquals(
            listOf(LocalFleet.REASON_ASSET_STATUS, LocalFleet.READINESS_REASON),
            reasons(facts(unit(offline = true, checklistOpen = true))),
        )
    }
}
