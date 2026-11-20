package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import com.avago.core.data.rental.RatePolicy
import com.avago.core.data.rental.RateValues
import com.avago.core.data.rental.RentalPricing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PricingDisplayTest {

    @Test
    fun `offline estimate is an unconfirmed one-unit display without tax`() {
        val b = RentalPricing.priceLine(
            RateValues(day = 10_000, week = 30_000),
            Instant.parse("2026-01-05T09:00:00Z"), Instant.parse("2026-01-09T09:00:00Z"), RatePolicy(),
        )!!
        val p = PricingDisplay.estimate(b, "EUR", "Van")
        assertFalse(p.confirmed)
        assertNull(p.tax)
        assertEquals(Money(30_000, "EUR"), p.total)
        assertEquals(PricingDisplay.Row("Van", "1", Money(30_000, "EUR"), "4d"), p.rows.single())
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
