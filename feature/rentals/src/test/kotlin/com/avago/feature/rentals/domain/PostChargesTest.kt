package com.avago.feature.rentals.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PostChargesTest {
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject
    private val now: Instant = Instant.parse("2026-03-01T12:00:00Z")

    @Test
    fun `list parses rows, totals and actions`() {
        val list = PostChargeList.from(
            json(
                """{"data":{"window_days":30,"kinds":["toll","ticket","citation"],"items":[
                {"id":"p1","kind":"toll","amount_minor":1250,"admin_fee_minor":500,"currency":"USD ","status":"billed","evidence_name":"toll.pdf"},
                {"id":"p2","kind":"ticket","amount_minor":9000,"currency":"USD","status":"charged"},
                {"id":"p3","kind":"citation","amount_minor":100,"currency":"USD","status":"charge_failed","charge_error":"card_declined"},
                {"kind":"toll"}]}}""",
            ),
        )
        assertEquals(30, list.windowDays)
        assertEquals(listOf("p1", "p2", "p3"), list.items.map { it.id })
        val p1 = list.items[0]
        assertEquals(1750L, p1.total.minor)
        assertEquals("USD", p1.total.currency)
        assertTrue(p1.canCharge && p1.canVoid)
        assertFalse(list.items[1].canCharge || list.items[1].canVoid)
        assertTrue(list.items[2].canCharge)
        assertEquals("card_declined", list.items[2].chargeError)
    }

    @Test
    fun `charge response carries the pay url`() {
        val pc = PostCharge.from(json("""{"id":"p1","status":"requires_action","pay_url":"https://pay.example/x"}"""))!!
        assertEquals("https://pay.example/x", pc.payUrl)
        assertTrue(pc.canCharge)
    }

    @Test
    fun `only out-going order statuses accept charges`() {
        assertTrue(PostCharges.allowedFor(OrderStatus.ON_RENT))
        assertTrue(PostCharges.allowedFor(OrderStatus.CLOSED))
        assertFalse(PostCharges.allowedFor(OrderStatus.RESERVED))
        assertFalse(PostCharges.allowedFor(OrderStatus.CANCELLED))
    }

    @Test
    fun `form validation`() {
        val blank = PostChargeForm(currency = "USD", incurredAt = now.plusSeconds(3600))
        assertEquals(mapOf("amount" to "required", "incurred_at" to "future", "evidence" to "evidence"), blank.errors(now))
        val bad = PostChargeForm(currency = "USD", amount = "0", adminFee = "1.234", incurredAt = now, evidenceFileId = "f1")
        assertEquals(mapOf("amount" to "amount", "admin_fee" to "amount"), bad.errors(now))
        val yen = PostChargeForm(currency = "JPY", amount = "12.5", adminFee = "100", incurredAt = now, evidenceFileId = "f1")
        assertEquals(mapOf("amount" to "amount"), yen.errors(now), "JPY has no minor digits")
    }

    @Test
    fun `form body uses the order currency's minor units`() {
        val yen = PostChargeForm(currency = "JPY", amount = "1500", adminFee = "1,000", incurredAt = now, evidenceFileId = "f1")
        val body = yen.toBody("cu-2")
        assertEquals(1500L, body["amount_minor"]!!.jsonPrimitive.long)
        assertEquals(1000L, body["admin_fee_minor"]!!.jsonPrimitive.long)
    }

    @Test
    fun `form body`() {
        val body = PostChargeForm(
            currency = "USD",
            kind = "ticket", amount = "45.50", incurredAt = now, reference = " TK-1 ", evidenceFileId = "f1", chargeNow = true,
        ).toBody("cu-1")
        assertEquals("ticket", body["kind"]!!.jsonPrimitive.content)
        assertEquals(4550L, body["amount_minor"]!!.jsonPrimitive.long)
        assertFalse(body.containsKey("admin_fee_minor"))
        assertEquals("2026-03-01T12:00:00Z", body["incurred_at"]!!.jsonPrimitive.content)
        assertEquals("TK-1", body["reference"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("authority"))
        assertEquals("f1", body["evidence_file_id"]!!.jsonPrimitive.content)
        assertEquals("cu-1", body["client_uuid"]!!.jsonPrimitive.content)
        assertEquals("true", body["charge_now"]!!.jsonPrimitive.content)
    }
}
