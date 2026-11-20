package com.avago.feature.workorders.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecurrenceTest {

    @Test
    fun `presets round trip through the rrule`() {
        for (f in RecurrenceFrequency.entries.filter { it != RecurrenceFrequency.CUSTOM }) {
            assertEquals(f, RecurrenceFrequency.fromRrule(buildRrule(f)))
        }
    }

    @Test
    fun `unmatched interval is custom and keeps its interval`() {
        val rrule = buildRrule(RecurrenceFrequency.CUSTOM, interval = 5)
        assertEquals("FREQ=WEEKLY;INTERVAL=5", rrule)
        assertEquals(RecurrenceFrequency.CUSTOM, RecurrenceFrequency.fromRrule(rrule))
        assertEquals(5, rruleInterval(rrule))
    }

    @Test
    fun `fixed presets ignore the interval argument`() {
        assertEquals("FREQ=MONTHLY;INTERVAL=3", buildRrule(RecurrenceFrequency.QUARTERLY, interval = 9))
    }

    @Test
    fun `blank rule defaults to monthly and missing interval to one`() {
        assertEquals(RecurrenceFrequency.MONTHLY, RecurrenceFrequency.fromRrule(""))
        assertEquals(RecurrenceFrequency.DAILY, RecurrenceFrequency.fromRrule("FREQ=DAILY"))
        assertEquals(1, rruleInterval("FREQ=DAILY"))
    }
}
