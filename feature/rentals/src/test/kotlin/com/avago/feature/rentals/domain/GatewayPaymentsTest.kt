package com.avago.feature.rentals.domain

import com.avago.feature.rentals.ui.OrderWave2ViewModel
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GatewayPaymentsTest {

    private fun obj(s: String): JsonObject = parseObj(s)!!

    private val full = obj(
        """{"online_payments_enabled":true,"deposit_capture_mode":"authorize",
           "card_on_file":{"brand":"visa","last4":"4242","exp":"12/30"},
           "can":{"charge":true,"refund":true,"request":true},
           "authorizations":[{"id":"h1","amount_minor":20000,"currency":"EUR ","authorization_expires_at":"2026-11-20T10:00:00Z","card_last4":"4242","expiring_soon":true},
                             {"id":"h0","amount_minor":0},{"amount_minor":5}],
           "refundable":[{"id":"p1","kind":"payment","amount_minor":5000,"refundable_minor":1500,"dispute_status":"needs_response"},
                         {"id":"p2","kind":"deposit_received","amount_minor":3000,"refundable_minor":0},
                         {"id":"p3","kind":"payment","amount_minor":100,"refundable_minor":-20}]}""",
    )

    @Test
    fun `gateway actions fail closed while disabled or without server permission`() {
        assertEquals(GatewayPayments.Gateway.NONE, GatewayPayments.parse(full, "EUR", enabled = false))
        assertEquals(GatewayPayments.Gateway.NONE, GatewayPayments.parse(null, "EUR", enabled = true))
        val noCan = GatewayPayments.parse(obj("""{"card_on_file":{"last4":"1"}}"""), "EUR", enabled = true)
        assertFalse(noCan.canCharge)
        assertFalse(noCan.canRefund)
        assertFalse(noCan.canChargeCard)
        val noCard = GatewayPayments.parse(obj("""{"card_on_file":null,"can":{"charge":true}}"""), "EUR", enabled = true)
        assertFalse(noCard.canChargeCard)
        assertTrue(noCard.isEmpty)
        // An order answer saying "off" hides gateway actions even when the cached status says on.
        assertNull(OnlinePayments.visible(true, obj("""{"online_payments_enabled":false,"can":{"charge":true}}"""), "EUR"))
        assertNull(OnlinePayments.visible(false, full, "EUR"))
        val shown = OnlinePayments.visible(true, full, "EUR")!!
        assertTrue(shown.gateway.canChargeCard)
    }

    @Test
    fun `parses holds and refundable entries in minor units`() {
        val g = GatewayPayments.parse(full, "USD", enabled = true)
        assertTrue(g.canChargeCard)
        assertTrue(g.offersDeposit)
        assertEquals(listOf("h1"), g.authorizations.map { it.id })
        val h = g.authorizations.single()
        assertEquals(20_000L, h.amount.minor)
        assertEquals("EUR", h.amount.currency)
        assertTrue(h.expiringSoon)
        assertEquals(listOf("p1", "p2", "p3"), g.refundable.map { it.id })
        assertEquals(1_500L, g.refundable[0].refundable.minor)
        assertEquals("USD", g.refundable[0].refundable.currency)
        assertEquals("needs_response", g.refundable[0].disputeStatus)
        assertFalse(g.refundable[1].canRefund)
        assertEquals(0L, g.refundable[2].refundable.minor)
        assertFalse(GatewayPayments.parse(obj("""{"deposit_capture_mode":"record_only"}"""), "EUR", true).offersDeposit)
    }

    @Test
    fun `amount input is strict and bounded`() {
        assertEquals(1_250L, GatewayPayments.amountMinor("12.50", "EUR"))
        assertEquals(1_234_50L, GatewayPayments.amountMinor("1,234.50", "EUR"))
        assertNull(GatewayPayments.amountMinor("12.505", "EUR"))
        assertNull(GatewayPayments.amountMinor("0", "EUR"))
        assertNull(GatewayPayments.amountMinor("-5", "EUR"))
        assertNull(GatewayPayments.amountMinor("abc", "EUR"))
        assertNull(GatewayPayments.amountMinor("12.5", "JPY"))
        assertEquals(12L, GatewayPayments.amountMinor("12", "JPY"))
        assertEquals(1_500L, GatewayPayments.amountMinor("15", "EUR", max = 1_500))
        assertNull(GatewayPayments.amountMinor("15.01", "EUR", max = 1_500))
        assertEquals(GatewayPayments.MAX_MINOR, GatewayPayments.amountMinor("999999.99", "EUR"))
        assertNull(GatewayPayments.amountMinor("1000000", "EUR"))
    }

    @Test
    fun `request bodies match the service contract`() {
        val c = GatewayPayments.chargeBody(2_500, GatewayPayments.PURPOSE_DEPOSIT, "uuid-1")
        assertEquals("2500", c.str("amount_minor"))
        assertEquals("deposit", c.str("purpose"))
        assertEquals("uuid-1", c.str("client_uuid"))
        assertThrows<IllegalArgumentException> { GatewayPayments.chargeBody(0, "payment", "u") }
        assertThrows<IllegalArgumentException> { GatewayPayments.chargeBody(1, "charge", "u") }
        // A full capture omits the amount so the server also captures any surcharge share.
        assertFalse(GatewayPayments.captureBody(20_000, 20_000).containsKey("amount_minor"))
        assertEquals("5000", GatewayPayments.captureBody(5_000, 20_000).str("amount_minor"))
        assertThrows<IllegalArgumentException> { GatewayPayments.captureBody(20_001, 20_000) }
        val r = GatewayPayments.refundBody(1_000, 1_500, "  damaged on arrival ")
        assertEquals("1000", r.str("amount_minor"))
        assertEquals("damaged on arrival", r.str("reason"))
        assertThrows<IllegalArgumentException> { GatewayPayments.refundBody(1_000, 1_500, " ") }
        assertThrows<IllegalArgumentException> { GatewayPayments.refundBody(1_501, 1_500, "x") }
    }

    @Test
    fun `charge outcomes including strong customer authentication`() {
        assertEquals(GatewayPayments.ChargeStatus.SUCCEEDED, GatewayPayments.parseCharge(obj("""{"status":"succeeded"}""")).status)
        assertEquals(GatewayPayments.ChargeStatus.HOLD_PLACED, GatewayPayments.parseCharge(obj("""{"status":"requires_capture"}""")).status)
        assertEquals(GatewayPayments.ChargeStatus.PROCESSING, GatewayPayments.parseCharge(obj("""{"status":"processing"}""")).status)
        assertEquals(GatewayPayments.ChargeStatus.UNKNOWN, GatewayPayments.parseCharge(obj("""{}""")).status)
        val sca = GatewayPayments.parseCharge(obj("""{"status":"requires_action","payment_request_id":"r1","pay_url":"https://app/pay/t"}"""))
        assertEquals(GatewayPayments.ChargeStatus.REQUIRES_ACTION, sca.status)
        assertEquals("https://app/pay/t", sca.payUrl)
        assertEquals("r1", sca.paymentRequestId)
    }

    @Test
    fun `only missing or transient answers are uncertain`() {
        assertTrue(GatewayPayments.isUncertain(null))
        listOf(408, 425, 429, 500, 502, 503, 504).forEach { assertTrue(GatewayPayments.isUncertain(it), "$it") }
        listOf(400, 402, 403, 404, 409).forEach { assertFalse(GatewayPayments.isUncertain(it), "$it") }
    }

    @Test
    fun `idempotency key is stable across uncertain retries and blocks a different amount`() {
        var n = 0
        val gen = { "k${++n}" }
        val a = GatewayPayments.Attempts()
        val scope = GatewayPayments.Attempts.scope("acc1", "charge", "o1")
        val first = a.begin(scope, "2500|EUR|payment", 2_500, gen) as GatewayPayments.Attempts.Begin.Go
        a.settle(scope, first.key, uncertain = true)
        val retry = a.begin(scope, "2500|EUR|payment", 2_500, gen) as GatewayPayments.Attempts.Begin.Go
        assertEquals(first.key, retry.key)
        val other = a.begin(scope, "3000|EUR|payment", 3_000, gen)
        assertTrue(other is GatewayPayments.Attempts.Begin.Blocked)
        assertEquals(2_500L, (other as GatewayPayments.Attempts.Begin.Blocked).pending.amountMinor)
        // Another account never shares the key.
        val otherAccount = a.begin(GatewayPayments.Attempts.scope("acc2", "charge", "o1"), "2500|EUR|payment", 2_500, gen)
        assertNotEquals(first.key, (otherAccount as GatewayPayments.Attempts.Begin.Go).key)
        // Survives process death.
        val restored = GatewayPayments.Attempts.decode(a.encode())
        assertEquals(first.key, (restored.begin(scope, "2500|EUR|payment", 2_500, gen) as GatewayPayments.Attempts.Begin.Go).key)
        // A stale settle does not clear; a definitive answer does, and the next attempt is new.
        a.settle(scope, "stale", uncertain = false)
        assertEquals(first.key, a.pending(scope)?.key)
        a.settle(scope, first.key, uncertain = false)
        assertNull(a.pending(scope))
        val next = a.begin(scope, "3000|EUR|payment", 3_000, gen) as GatewayPayments.Attempts.Begin.Go
        assertNotEquals(first.key, next.key)
        assertNull(GatewayPayments.Attempts.decode("not json").pending(scope))
        assertNull(GatewayPayments.Attempts.decode(null).pending(scope))
    }
}
