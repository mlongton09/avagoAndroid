package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PayoutsTest {
    @Test
    fun `instant-book evaluate reads the data envelope`() {
        val c = InstantBookCheck.from(parseObj("""{"data":{"eligible":false,"outcome":"request","reasons":["lead_time","rating_unavailable"]}}""")!!)
        assertFalse(c.eligible)
        assertEquals(listOf("lead_time", "rating_unavailable"), c.reasons)
        val ok = InstantBookCheck.from(parseObj("""{"eligible":true,"outcome":"instant_pay","reasons":[]}""")!!)
        assertTrue(ok.eligible)
        assertEquals("instant_pay", ok.outcome)
    }

    @Test
    fun `fee breakdown in connect mode`() {
        val f = FeeBreakdown.from(
            parseObj(
                """{"data":{"currency":"eur","mode":"connect","order_total_minor":10000,"renter_fee_bp":500,"renter_fee_minor":500,
                   "renter_total_minor":10500,"deposit_hold_minor":0,
                   "owner":{"commission_bp":750,"commission_minor":750,"labor_commission_minor":0,"absorbed_renter_fee_minor":0,"collection":"application_fee","net_payout_minor":9250}}}""",
            )!!,
        )!!
        assertTrue(f.connect)
        assertEquals(Money(500, "EUR"), f.renterFee)
        assertEquals(Money(9250, "EUR"), f.netPayout)
        assertNull(f.depositHold)
        assertNull(f.laborCommission)
        assertEquals("7.5", FeeBreakdown.percent(f.commissionBp))
        assertEquals("5", FeeBreakdown.percent(f.renterFeeBp))
    }

    @Test
    fun `fee breakdown in keys mode has no renter fee and needs a currency`() {
        val f = FeeBreakdown.from(parseObj("""{"currency":"USD","mode":"keys","order_total_minor":2000,"renter_fee_minor":0,"owner":{"commission_minor":100,"net_payout_minor":1900}}""")!!)!!
        assertFalse(f.connect)
        assertNull(f.renterFee)
        assertEquals(Money(100, "USD"), f.commission)
        assertNull(FeeBreakdown.from(parseObj("""{"mode":"keys"}""")!!))
    }
}
