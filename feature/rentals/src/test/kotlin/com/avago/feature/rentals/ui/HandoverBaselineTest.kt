package com.avago.feature.rentals.ui

import com.avago.core.data.db.rental.RentalRecordEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HandoverBaselineTest {

    private fun h(id: String, order: String, kind: String, at: String, marks: String = "[]") = RentalRecordEntity(
        entity = "handover", id = id, orderId = order,
        json = """{"id":"$id","kind":"$kind","at":"$at","damage_summary":$marks}""",
    )

    private val rows = listOf(
        h("1", "O1", "checkout", "2026-05-01T08:00:00Z"),
        h("2", "O0", "checkin", "2026-04-30T08:00:00Z", """[{"zone":"front_left","severity":"major","label":"Dent"},{"severity":"minor"}]"""),
        h("3", "O9", "checkin", "2026-04-20T08:00:00Z"),
    )

    @Test
    fun `check in compares with this order's check out, check out with the latest check in`() {
        assertEquals("1", baselineFor("in", "O1", rows)?.id)
        assertNull(baselineFor("in", "O2", rows))
        assertEquals("2", baselineFor("out", "O2", rows)?.id)
    }

    @Test
    fun `damage summary becomes numbered sketch marks and skips marks without a zone`() {
        val marks = marksOf(rows[1])
        assertEquals(1, marks.size)
        assertEquals("front_left", marks[0].zone)
        assertEquals("Dent", marks[0].label)
        assertEquals(1, marks[0].number)
        assertTrue(marksOf(null).isEmpty())
    }

    @Test
    fun `stage B damage_summary uses location and a pending copy reads damage`() {
        val server = RentalRecordEntity(
            entity = "handover", id = "s", orderId = "O1",
            json = """{"id":"s","kind":"checkin","damage_summary":[{"defect_id":null,"label":"Mirror","severity":"minor",
                "decision":"charge","charge_minor":4500,"location":"cab"}]}""",
        )
        assertEquals(listOf("cab"), marksOf(server).map { it.zone })
        val pending = RentalRecordEntity(
            entity = "handover", id = "p", orderId = "O1",
            json = """{"id":"p","kind":"checkout","damage":[{"label":"Scratch","severity":"major","decision":"pre_existing","location":"front"}]}""",
        )
        val m = marksOf(pending).single()
        assertEquals("front", m.zone)
        assertEquals("major", m.severity)
    }
}
