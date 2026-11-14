package com.avago.core.data.rental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RentalTimeTest {

    @Test
    fun `parses postgres tstzrange text from to_jsonb rows`() {
        val r = RentalTime.parseRange("[\"2026-05-01 08:00:00+00\",\"2026-05-03 08:00:00+00\")")!!
        assertEquals(Instant.parse("2026-05-01T08:00:00Z"), r.start)
        assertEquals(Instant.parse("2026-05-03T08:00:00Z"), r.end)
    }

    @Test
    fun `parses offsets fractions and open bounds`() {
        val r = RentalTime.parseRange("[\"2026-05-01 10:30:00.123456+02:30\",)")!!
        assertEquals(Instant.parse("2026-05-01T08:00:00.123456Z"), r.start)
        assertNull(r.end)
        val r2 = RentalTime.parseRange("[2026-05-01T08:00:00Z,2026-05-02T08:00:00Z)")!!
        assertEquals(Instant.parse("2026-05-02T08:00:00Z"), r2.end)
        assertTrue(RentalTime.parseRange("empty")!!.empty)
        assertNull(RentalTime.parseRange("garbage"))
        assertNull(RentalTime.parseRange(null))
    }

    @Test
    fun `parses rfc3339 and postgres instants`() {
        assertEquals(Instant.parse("2026-05-01T08:00:00Z"), RentalTime.parseInstant("2026-05-01T08:00:00Z"))
        assertEquals(Instant.parse("2026-05-01T06:00:00Z"), RentalTime.parseInstant("2026-05-01T08:00:00+02:00"))
        assertEquals(Instant.parse("2026-05-01T08:00:00Z"), RentalTime.parseInstant("2026-05-01 08:00:00+00"))
        assertNull(RentalTime.parseInstant("infinity"))
        assertNull(RentalTime.parseInstant(""))
    }
}
