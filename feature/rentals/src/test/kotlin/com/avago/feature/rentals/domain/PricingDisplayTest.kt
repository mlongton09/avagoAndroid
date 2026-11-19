package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PricingDisplayTest {

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
