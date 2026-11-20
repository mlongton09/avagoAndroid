package com.avago.feature.rentals.domain

import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.domain.Subrental
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class OrderAccountsTest {
    private val now = Instant.parse("2026-03-01T12:00:00Z")
    private val evidence = LocalFileRef("content://x/1", "image/jpeg")

    @Test
    fun `post charges are accepted from on rent through closed`() {
        assertTrue(PostCharges.allowedFor(OrderStatus.ON_RENT))
        assertTrue(PostCharges.allowedFor(OrderStatus.CLOSED))
        assertFalse(PostCharges.allowedFor(OrderStatus.RESERVED))
        assertFalse(PostCharges.allowedFor(OrderStatus.CANCELLED))
    }

    @Test
    fun `post charges close the owner's window after the return`() {
        val end = Instant.parse("2026-01-10T00:00:00Z")
        val line = RentalOrderLine(
            id = "L1", version = 1, orderId = "O1", lineNo = 1, kind = "rental", classId = "K", assetId = "A",
            productId = null, parentLineId = null, description = "Van", qty = BigDecimal.ONE,
            startAt = null, endAt = end, actualOutAt = null, actualInAt = Instant.parse("2026-01-12T00:00:00Z"),
            unitPriceMinor = null, amountMinor = 0, discountMinor = 0, taxMinor = 0, status = "returned",
            kitInstanceId = null, assignUnitLater = false, sync = RecordSyncState.SAVED,
        )
        val second = line.copy(id = "L2", actualInAt = Instant.parse("2026-01-11T00:00:00Z"))
        assertNull(PostCharges.returnedAt(OrderStatus.ON_RENT, listOf(line), end))
        val back = PostCharges.returnedAt(OrderStatus.RETURNED, listOf(second, line), end)
        assertEquals(line.actualInAt, back)
        assertEquals(end, PostCharges.returnedAt(OrderStatus.CLOSED, listOf(line.copy(actualInAt = null)), end))
        assertTrue(PostCharges.withinWindow(null, 0, now))
        assertTrue(PostCharges.withinWindow(back, 90, back!!.plus(Duration.ofDays(90))))
        assertFalse(PostCharges.withinWindow(back, 90, back.plus(Duration.ofDays(90)).plusSeconds(1)))
    }

    @Test
    fun `post charge form validates amount, fee, date and evidence`() {
        val empty = PostChargeForm(currency = "EUR", incurredAt = now.plusSeconds(60))
        assertEquals(
            mapOf("amount" to "required", "incurred_at" to "future", "evidence" to "evidence"),
            empty.errors(now),
        )
        val bad = empty.copy(amount = "12.345", adminFee = "-1", incurredAt = now, evidence = evidence)
        assertEquals(mapOf("amount" to "amount", "admin_fee" to "amount"), bad.errors(now))
        assertEquals(mapOf("amount" to "amount"), bad.copy(amount = "0", adminFee = "").errors(now))
        // Yen has no minor digits: "1500" is 1500 minor units.
        val jpy = PostChargeForm(currency = "JPY", amount = "1500", incurredAt = now, evidence = evidence)
        assertTrue(jpy.errors(now).isEmpty())
        assertEquals(1500L, jpy.toDraft("o1", null).amountMinor)
    }

    @Test
    fun `post charge goes online for charge now or a closed order`() {
        val f = PostChargeForm(currency = "EUR", amount = "25.50", adminFee = "5", incurredAt = now, evidence = evidence, reference = " T-1 ")
        assertFalse(f.online(OrderStatus.RETURNED))
        assertTrue(f.online(OrderStatus.CLOSED))
        assertTrue(f.copy(chargeNow = true).online(OrderStatus.ON_RENT))
        val d = f.toDraft("o1", "a1")
        assertEquals(2550L, d.amountMinor)
        assertEquals(500L, d.adminFeeMinor)
        assertEquals("T-1", d.reference)
        assertNull(d.description)
        val body = f.copy(chargeNow = true).toRestBody("u1", "file1", null)
        assertEquals(2550L, body["amount_minor"]!!.jsonPrimitive.long)
        assertEquals("file1", body["evidence_file_id"]!!.jsonPrimitive.content)
        assertEquals("u1", body["client_uuid"]!!.jsonPrimitive.content)
        assertTrue(body["charge_now"]!!.jsonPrimitive.boolean)
        assertFalse(body.containsKey("asset_id"))
        assertFalse(body.containsKey("description"))
    }

    @Test
    fun `price adjustments take a minus only for adjustment and never zero`() {
        assertEquals(-1050L, PriceAdjustments.amountMinor("adjustment", "-10.50", "EUR"))
        assertEquals(1050L, PriceAdjustments.amountMinor("adjustment", "10.50", "EUR"))
        assertNull(PriceAdjustments.amountMinor("fee", "-10", "EUR"))
        assertNull(PriceAdjustments.amountMinor("discount", "-10", "EUR"))
        assertEquals(1000L, PriceAdjustments.amountMinor("discount", "10", "EUR"))
        assertNull(PriceAdjustments.amountMinor("fee", "0", "EUR"))
        assertNull(PriceAdjustments.amountMinor("fee", "1.5", "JPY"))
    }

    @Test
    fun `price adjustment draft needs description, reason and a known kind`() {
        val d = PriceAdjustments.draft("o1", "fuel", " Refuel ", "30", " empty tank ", "EUR")!!
        assertEquals("Refuel", d.description)
        assertEquals("empty tank", d.reason)
        assertEquals(3000L, d.amountMinor)
        assertNull(PriceAdjustments.draft("o1", "fuel", "Refuel", "30", " ", "EUR"))
        assertNull(PriceAdjustments.draft("o1", "fuel", "", "30", "x", "EUR"))
        assertNull(PriceAdjustments.draft("o1", "bonus", "x", "30", "x", "EUR"))
        assertTrue(PriceAdjustments.allowedFor(OrderStatus.ON_RENT))
        assertFalse(PriceAdjustments.allowedFor(OrderStatus.CLOSED))
        assertFalse(PriceAdjustments.allowedFor("no_show"))
    }

    private fun sub(id: String, status: String, line: String? = null, asset: String? = null) = Subrental(
        id = id, number = null, vendorPartyId = null, vendorName = null, description = null, classId = null,
        assetId = asset, qty = null, costRateMinor = null, costPeriod = null, currency = null, plannedStart = null,
        plannedEnd = null, receivedAt = null, returnedAt = null, status = status, orderId = "o1", orderLineId = line,
        poReference = null, notes = null,
    )

    private fun line(id: String, asset: String?) =
        LineView(id, "o1", "rental", null, asset, id, BigDecimal.ONE, null, null, null, null, "out")

    @Test
    fun `sub-rented lines are matched by line or by unit while active`() {
        val lines = listOf(line("l1", null), line("l2", "a2"), line("l3", "a3"), line("l4", "a4"))
        val subs = listOf(
            sub("s1", "ordered", line = "l1"),
            sub("s2", "received", asset = "a2"),
            sub("s3", "returned", asset = "a3"),
            sub("s4", "cancelled", line = "l4"),
        )
        assertEquals(setOf("l1", "l2"), Subrentals.lineIds(subs, lines))
    }

    @Test
    fun `online answers parse pay links, buy-out quotes and post-charge outcomes`() {
        assertEquals("https://p/1", OnlinePayments.payUrlOf(parseObj("""{"payment_request":{"pay_url":"https://p/1"}}""")!!))
        assertNull(OnlinePayments.payUrlOf(parseObj("""{"pay_url":" "}""")!!))
        val req = OnlinePayments.requestBody("o1")
        assertEquals("balance", req["kind"]!!.jsonPrimitive.content)
        val q = BuyoutQuote.parse(parseObj("""{"currency":"usd","quote_minor":125000,"equity_minor":30000,"as_of":"2026-03-01T00:00:00Z"}"""))!!
        assertEquals(125000L, q.quote!!.minor)
        assertEquals("USD", q.quote!!.currency.uppercase())
        assertNull(q.floor)
        val out = PostChargeOutcome.parse(parseObj("""{"data":{"status":"charge_failed","charge_error":"card_declined","pay_url":""}}""")!!)
        assertTrue(out.failed)
        assertNull(out.payUrl)
        assertEquals("card_declined", out.chargeError)
    }
}