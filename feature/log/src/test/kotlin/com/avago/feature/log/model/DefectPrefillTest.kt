package com.avago.feature.log.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Mirrors avagoweb work-orders/create/prefill.test.ts (defectPrefill). */
class DefectPrefillTest {

    private val uuid = "0b9c6a8e-1f2d-4c3b-9a8e-7d6c5b4a3f21"
    private val log = "5f0e4d3c-2b1a-4f9e-8d7c-6b5a4f3e2d10"

    @Test
    fun `single defect maps title description priority and source`() {
        val r = DefectPrefill.build(
            listOf(
                DefectPrefill.Defect(
                    id = uuid, label = "Tires", sectionTitle = "Exterior", cornerLabel = "Front left",
                    severity = InspectionV2.SEV_OUT_OF_SERVICE, bucket = InspectionV2.Bucket.NR,
                    note = "  Sidewall cut ", location = "fl", sourceLogId = log,
                ),
            ),
            severityLabel = { "Out of service" },
            locationLabel = { "Front left" },
        )!!
        assertEquals("Tires — Front left", r.title)
        assertEquals("Exterior · Tires · Front left [Out of service]\nLocation: Front left\nNote: Sidewall cut", r.description)
        assertEquals("critical", r.priority)
        assertEquals(listOf(uuid), r.defectIds)
        assertEquals(log, r.inspectionLogId)
    }

    @Test
    fun `multiple defects use the highest priority and drop non-uuid ids`() {
        val r = DefectPrefill.build(
            listOf(
                DefectPrefill.Defect(id = "local-1", label = "Wipers", severity = InspectionV2.SEV_MINOR, bucket = InspectionV2.Bucket.M),
                DefectPrefill.Defect(id = uuid, label = "Brakes", severity = InspectionV2.SEV_MINOR, bucket = InspectionV2.Bucket.NR),
            ),
        )!!
        assertEquals("Wipers (+1)", r.title)
        assertEquals("- Wipers [minor]\n- Brakes [minor]", r.description)
        assertEquals("high", r.priority)
        assertEquals(listOf(uuid), r.defectIds)
        assertNull(r.inspectionLogId)
    }

    @Test
    fun `empty input yields no prefill`() {
        assertNull(DefectPrefill.build(emptyList()))
    }
}
