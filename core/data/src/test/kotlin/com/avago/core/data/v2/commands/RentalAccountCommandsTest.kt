package com.avago.core.data.v2.commands

import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.PaymentDraft
import com.avago.core.data.domain.PostCharge
import com.avago.core.data.domain.PostChargeDraft
import com.avago.core.data.domain.PriceAdjustmentDraft
import com.avago.core.data.domain.ReadinessCheck
import com.avago.core.data.domain.ReadinessCheckItem
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalOrder
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import com.avago.core.data.v2.V2StagedFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant

class RentalAccountCommandsTest {

    private val start = Instant.parse("2026-11-20T08:00:00Z")
    private val evidence = V2StagedFile("00000000-0000-7000-8000-00000000000f", File("ticket.jpg"), "ab", 10, "image/jpeg", "ticket.jpg")

    private fun ops(overlay: JsonObject?) = V2OverlayPatch.parse(overlay!!)!!.ops

    private fun List<V2RowOp>.on(table: String) = filter { it.table == table }

    private fun order(status: String) = RentalOrder(
        id = "o1", version = 3, number = "R-1", quoteNumber = null, status = status, source = "staff", kind = "standard",
        currency = "EUR", locale = null, customerPartyId = "p1", customerName = "Ada", walkInName = null, contactId = null,
        siteLocationId = null, locationId = "l1", startAt = start, endAt = start, offRentAt = null, holdExpiresAt = null,
        quoteExpiresAt = null, fulfilment = "pickup", poNumber = null, reference = null, notes = null, internalNotes = null,
        subtotalMinor = 0, discountMinor = 0, taxMinor = 0, totalMinor = 0, depositRequiredMinor = 0, invoicedMinor = null,
        invoicePaidMinor = null, cancelReason = null, needsReview = false, sync = RecordSyncState.SAVED,
    )

    private fun charge(amount: Long = 2500) = PostChargeDraft(
        orderId = "o1", kind = "ticket", amountMinor = amount, adminFeeMinor = 500, incurredAt = start,
        evidence = LocalFileRef("content://x"), assetId = "a1", reference = " PCN-7 ", authority = "City", description = null,
    )

    private fun stored(status: String = PostCharge.STATUS_PENDING, paymentRequest: String? = null, invoiced: Boolean = false) = PostCharge(
        id = "pc1", version = 4, orderId = "o1", assetId = null, kind = "toll", amountMinor = 100, adminFeeMinor = 0, currency = "EUR",
        incurredAt = start, reference = null, authority = null, description = null, evidenceFileId = "f", status = status,
        paymentRequestId = paymentRequest, chargeError = null, voidReason = null, invoiced = invoiced,
    )

    @Test
    fun `a post charge uploads its evidence and shows pending at once`() {
        val cmd = RentalAccountCommands.createPostCharge("pc9", charge(), order("returned"), evidence, 1, "u")

        assertEquals("rental.post_charges.create", cmd.operation)
        val payload = cmd.payload.jsonObject
        assertEquals("ticket", payload["kind"]!!.jsonPrimitive.content)
        assertEquals("PCN-7", payload["reference"]!!.jsonPrimitive.content)
        assertEquals(evidence.fileId, payload["evidence_file_object_id"]!!.jsonPrimitive.content)
        assertEquals(listOf(evidence.fileId), cmd.uploads.map { it.fileId })
        val o = ops(cmd.overlayPatch)
        val row = o.on("post_charges").single().set
        assertEquals("pending", row["status"])
        assertEquals(500L, row["admin_fee_minor"])
        assertEquals("EUR", row["currency"])
        assertEquals(1, o.on("files").size)
    }

    @Test
    fun `post charges need an order that went out and a positive amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            RentalAccountCommands.createPostCharge("pc9", charge(), order("reserved"), evidence, 1, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RentalAccountCommands.createPostCharge("pc9", charge(), order("closed"), evidence, 1, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RentalAccountCommands.createPostCharge("pc9", charge(amount = 0), order("on_rent"), evidence, 1, "u")
        }
    }

    @Test
    fun `only an open uninvoiced charge is voided, against its version`() {
        val cmd = RentalAccountCommands.voidPostCharge(stored(PostCharge.STATUS_FAILED), " wrong car ", 9, "u")
        assertEquals("rental.post_charges.void", cmd.operation)
        assertEquals(4L, cmd.baseVersion)
        assertEquals("wrong car", cmd.payload.jsonObject["reason"]!!.jsonPrimitive.content)
        assertEquals("voided", ops(cmd.overlayPatch).on("post_charges").single().set["status"])

        listOf(stored(invoiced = true), stored(paymentRequest = "pr1"), stored(PostCharge.STATUS_CHARGED)).forEach { charge ->
            assertThrows(IllegalArgumentException::class.java) { RentalAccountCommands.voidPostCharge(charge, null, 9, "u") }
        }
    }

    @Test
    fun `a discount always reduces the order and a fee must be positive`() {
        val discount = RentalAccountCommands.priceAdjustment(
            "ln9", PriceAdjustmentDraft("o1", "discount", "Loyalty", 1500, "regular"), order("reserved"), 3, 1, "u",
        )
        val line = discount.payload.jsonObject["line"]!!.jsonObject
        assertEquals(-1500L, line["unit_price_minor"]!!.jsonPrimitive.long)
        assertEquals("regular", line["override_reason"]!!.jsonPrimitive.content)
        val row = ops(discount.overlayPatch).on("rental_order_lines").single().set
        assertEquals(-1500L, row["amount_minor"])
        assertEquals(3L, row["line_no"])

        val credit = RentalAccountCommands.priceAdjustment(
            "ln9", PriceAdjustmentDraft("o1", "adjustment", "Goodwill", -700, "late delivery"), order("on_rent"), 3, 1, "u",
        )
        assertEquals(-700L, credit.payload.jsonObject["line"]!!.jsonObject["unit_price_minor"]!!.jsonPrimitive.long)

        assertThrows(IllegalArgumentException::class.java) {
            RentalAccountCommands.priceAdjustment("ln9", PriceAdjustmentDraft("o1", "fee", "Fee", -5, "x"), order("on_rent"), 3, 1, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RentalAccountCommands.priceAdjustment("ln9", PriceAdjustmentDraft("o1", "rental", "Rent", 5, "x"), order("on_rent"), 3, 1, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RentalAccountCommands.priceAdjustment("ln9", PriceAdjustmentDraft("o1", "fee", "Fee", 5, " "), order("on_rent"), 3, 1, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RentalAccountCommands.priceAdjustment("ln9", PriceAdjustmentDraft("o1", "fee", "Fee", 5, "x"), order("closed"), 3, 1, "u")
        }
    }

    private fun payment(email: String?, hold: Boolean = false) = PaymentDraft(
        orderId = "o1", kind = "payment", method = "cash", amountMinor = 1000, currency = "eur", reference = null, cardBrand = null,
        cardLast4 = null, payerName = "Ada", isAuthorization = hold, notes = null, receivedAt = start, payerEmail = email,
    )

    @Test
    fun `a payment carries the payer e-mail and shows in the ledger at once`() {
        val cmd = RentalCommands.recordPayment("e1", payment(" Ada@Example.com "), "p1", 1, "u")
        assertEquals("ada@example.com", cmd.payload.jsonObject["payer_email"]!!.jsonPrimitive.content)
        val row = ops(cmd.overlayPatch).on("customer_ledger_entries").single().set
        assertEquals("settled", row["state"])
        assertEquals("p1", row["customer_party_id"])
        assertEquals("EUR", row["currency"])

        assertNull(RentalCommands.recordPayment("e1", payment(null, hold = true), "p1", 1, "u").overlayPatch)
        assertThrows(IllegalArgumentException::class.java) { RentalCommands.recordPayment("e1", payment("ada"), "p1", 1, "u") }
    }

    @Test
    fun `readiness photos upload with the check and later photos keep the earlier ones`() {
        val created = RentalCommands.createCheck("h1", "a1", emptyList(), null, 1, "u", listOf(evidence))
        assertEquals(evidence.fileId, created.payload.jsonObject["photo_file_ids"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(listOf(evidence.fileId), created.uploads.map { it.fileId })
        assertEquals("photo", ops(created.overlayPatch).on("rental_handover_files").single().set["role"])

        val check = ReadinessCheck(
            id = "h1", version = 2, assetId = "a1", orderId = null, status = "pending",
            items = listOf(ReadinessCheckItem("inspected", "Inspected", false)), notes = null, at = start,
            sync = RecordSyncState.SAVED, photoFileIds = listOf("old"),
        )
        val more = RentalCommands.updateCheck(check, "pending", check.items, null, 2, listOf(evidence), "u")
        val set = more.payload.jsonObject["set"]?.jsonObject ?: more.payload.jsonObject
        assertEquals(listOf("old", evidence.fileId), set["photo_file_ids"]!!.jsonArray.map { it.jsonPrimitive.content })

        val none = RentalCommands.updateCheck(check, "pending", check.items, null, 2)
        val unchanged = none.payload.jsonObject["set"]?.jsonObject ?: none.payload.jsonObject
        assertNull(unchanged["photo_file_ids"], "no new photos leave the stored ones alone")
        assertEquals(emptyList<Any>(), none.uploads)
    }
}
