package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PricingDisplayTest {

    private val card = parseObj(
        """{"currency":"EUR","rate_day_minor":5000,"rate_week_minor":25000,"hourly_minor":1000,"rate_4h_minor":3000,"min_charge_minor":2000}""",
    )!!
    private val start = Instant.parse("2026-05-01T08:00:00Z")

    @Test
    fun `days round up and never below one`() {
        assertEquals(1, PricingDisplay.daysOf(Duration.ofMinutes(1)))
        assertEquals(1, PricingDisplay.daysOf(Duration.ofHours(24)))
        assertEquals(2, PricingDisplay.daysOf(Duration.ofHours(24).plusMinutes(1)))
    }

    @Test
    fun `cheapest of hourly, 4h, day and week combinations with min charge`() {
        // 1 h: hourly 1000 → min charge 2000
        assertEquals(2000, PricingDisplay.estimateUnitMinor(card, Duration.ofHours(1)))
        // 3 h: hourly 3000 vs 4h 3000 vs day 5000 → 3000
        assertEquals(3000, PricingDisplay.estimateUnitMinor(card, Duration.ofHours(3)))
        // 6 days: day 30000 vs week 25000 → 25000
        assertEquals(25000, PricingDisplay.estimateUnitMinor(card, Duration.ofDays(6)))
        // 8 days: 1 week + 1 day = 30000 vs 8 days 40000
        assertEquals(30000, PricingDisplay.estimateUnitMinor(card, Duration.ofDays(8)))
        // no rates → null
        assertNull(PricingDisplay.estimateUnitMinor(parseObj("""{"currency":"EUR"}""")!!, Duration.ofDays(1)))
    }

    @Test
    fun `offline estimate is unconfirmed, tax free and multiplied by qty`() {
        val p = PricingDisplay.estimate(card, "Excavator", start, start.plus(Duration.ofDays(2)), qty = 3)!!
        assertFalse(p.confirmed)
        assertNull(p.tax)
        assertEquals(Money(30000, "EUR"), p.total)
        assertEquals("3", p.rows.single().qty)
        assertEquals("2d", p.rows.single().detail)
        assertNull(PricingDisplay.estimate(card, "x", start, start, 1))
    }

    @Test
    fun `server priced object maps to a confirmed display`() {
        val priced = parseObj(
            """{"currency":"JPY","subtotal_minor":"12000","discount_minor":0,"tax_minor":1200,"total_minor":13200,
               "deposit_required_minor":0,
               "lines":[{"kind":"rental","description":"","qty":"2.000","amount_minor":12000,"pricing":{"days":2}}]}""",
        )!!
        val p = PricingDisplay.fromServer(priced)!!
        assertTrue(p.confirmed)
        assertEquals(Money(13200, "JPY"), p.total)
        assertEquals(Money(1200, "JPY"), p.tax)
        assertNull(p.deposit, "zero deposit is hidden")
        val row = p.rows.single()
        assertEquals("rental", row.label)
        assertEquals("2", row.qty)
        assertEquals("2d", row.detail)
        assertNull(PricingDisplay.fromServer(parseObj("""{"total_minor":1}""")!!))
    }
}
