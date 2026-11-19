package com.avago.core.data.v2.commands

import com.avago.core.data.domain.BulkCountsDraft
import com.avago.core.data.domain.HandoverDamageDraft
import com.avago.core.data.domain.HandoverDraft
import com.avago.core.data.domain.HandoverOverride
import com.avago.core.data.domain.ReadinessCheck
import com.avago.core.data.domain.ReadinessCheckItem
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalBookingDraft
import com.avago.core.data.domain.RentalCustomer
import com.avago.core.data.domain.RentalCustomerDraft
import com.avago.core.data.domain.RentalOrder
import com.avago.core.data.domain.RentalOrderAction
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.domain.RentalTransfer
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class RentalCommandBuildersTest {

    private var seq = 0
    private val newId = { "00000000-0000-7000-8000-%012d".format(++seq) }
    private val start = Instant.parse("2026-11-20T08:00:00Z")
    private val end = Instant.parse("2026-11-22T08:00:00Z")

    private fun ops(overlay: JsonObject?) = V2OverlayPatch.parse(overlay!!)!!.ops

    private fun List<V2RowOp>.on(table: String) = filter { it.table == table }

    private fun order(status: String = "reserved") = RentalOrder(
        id = "o1", version = 3, number = "R-1", quoteNumber = null, status = status, source = "staff", kind = "standard",
        currency = "EUR", locale = null, customerPartyId = "p1", customerName = "Ada", walkInName = null, contactId = null,
        siteLocationId = null, locationId = "l1", startAt = start, endAt = end, offRentAt = null, holdExpiresAt = null,
        quoteExpiresAt = null, fulfilment = "pickup", poNumber = null, reference = null, notes = null, internalNotes = null,
        subtotalMinor = 0, discountMinor = 0, taxMinor = 0, totalMinor = 0, depositRequiredMinor = 0, invoicedMinor = null,
        invoicePaidMinor = null, cancelReason = null, needsReview = false, sync = RecordSyncState.SAVED,
    )

    private fun line(status: String = "open", assetId: String? = "a1", productId: String? = null, classId: String? = "c1") = RentalOrderLine(
        id = "ln1", version = 1, orderId = "o1", lineNo = 1, kind = "rental", classId = classId, assetId = assetId,
        productId = productId, parentLineId = null, description = "Excavator", qty = BigDecimal.ONE, startAt = start, endAt = end,
        actualOutAt = null, actualInAt = null, unitPriceMinor = 1000, amountMinor = 0, discountMinor = 0, taxMinor = 0,
        status = status, kitInstanceId = null, assignUnitLater = false, sync = RecordSyncState.SAVED,
    )

    private fun draft(checkout: Boolean) = HandoverDraft(
        orderId = "o1", lineId = "ln1", assetId = "a1", checkout = checkout, at = start,
        damage = listOf(HandoverDamageDraft(zone = "front", severity = "minor", label = "Dent", isNew = true, chargeMinor = 500)),
    )

    @Test
    fun `a booking creates a draft order with one line`() {
        val cmd = RentalCommands.createOrder(
            "o9", "ln9",
            RentalBookingDraft("p1", null, "c1", null, null, "l1", null, start, end, action = "reserve"),
            currency = "EUR", customerName = "Ada", nowMillis = 1, userId = "u",
        )

        assertEquals("rental.rental_orders.create", cmd.operation)
        val payload = cmd.payload.jsonObject
        assertNull(payload["currency"], "the server applies its default currency")
        assertEquals("c1", payload["lines"]!!.jsonArray.single().jsonObject["class_id"]!!.jsonPrimitive.content)
        val o = ops(cmd.overlayPatch)
        assertEquals("draft", o.on("rental_orders").single().set["status"])
        assertEquals("EUR", o.on("rental_orders").single().set["currency"])
        assertEquals("o9", o.on("rental_order_lines").single().set["order_id"])
    }

    @Test
    fun `a booking needs a customer and an end after its start`() {
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.createOrder("o", "l", RentalBookingDraft(null, " ", "c1", null, null, null, null, start, end, null), "EUR", null, 1, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.createOrder("o", "l", RentalBookingDraft("p1", null, "c1", null, null, null, null, end, start, null), "EUR", null, 1, null)
        }
    }

    @Test
    fun `reserving a unit line without allocations books one locally`() {
        val cmd = RentalCommands.act(order("draft"), RentalOrderAction.Reserve(), emptyList(), listOf(line()), newId, 1, "u")

        assertEquals("rental.rental_orders.reserve", cmd.operation)
        val o = ops(cmd.overlayPatch)
        assertEquals("reserved", o.on("rental_orders").single().set["status"])
        val alloc = o.on("resource_allocations").single()
        assertEquals("reserved", alloc.set["purpose"])
        assertEquals("a1", alloc.set["asset_id"])
    }

    @Test
    fun `cancelling releases allocations and needs a reason`() {
        val cmd = RentalCommands.act(order(), RentalOrderAction.Cancel("customer left"), listOf("al1"), listOf(line()), newId, 1, "u")

        val o = ops(cmd.overlayPatch)
        assertEquals(0L, o.on("resource_allocations").single().set["active"])
        assertEquals("customer left", o.on("rental_orders").single().set["cancel_reason"])
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.act(order(), RentalOrderAction.Cancel(" "), emptyList(), emptyList(), newId, 1, "u")
        }
    }

    @Test
    fun `extending moves the order, open lines and allocations`() {
        val later = end.plusSeconds(86_400)
        val cmd = RentalCommands.act(
            order("on_rent"), RentalOrderAction.Extend(later), listOf("al1"),
            listOf(line("out"), line("returned").copy(id = "ln2")), newId, 1, "u",
        )

        val o = ops(cmd.overlayPatch)
        assertEquals(listOf("ln1"), o.on("rental_order_lines").map { it.key["id"] })
        assertEquals(later.toEpochMilli(), o.on("resource_allocations").single().set["during_end"])
    }

    @Test
    fun `a check-out sends the signature dependency and puts the line out`() {
        val ctx = RentalCommands.LineContext(order(), line(), listOf("al1"), otherOpenLines = 0)
        val cmd = RentalCommands.handover(
            "h1", draft(checkout = true), ctx, emptyList(), null, signatureId = "s1", dependsOn = listOf("cmd-sig"),
            readinessId = "r1", newId = newId, nowMillis = 1, userId = "u",
        )

        assertEquals("rental.rental_handovers.checkout", cmd.operation)
        assertEquals(listOf("cmd-sig"), cmd.dependsOn)
        val handover = cmd.payload.jsonObject["handover"]!!.jsonObject
        assertEquals("s1", handover["customer_signature_id"]!!.jsonPrimitive.content)
        assertNull(handover["asset_id"], "the line already has its unit")
        val o = ops(cmd.overlayPatch)
        assertEquals("out", o.on("rental_order_lines").single().set["status"])
        assertEquals("on_rent", o.on("resource_allocations").single().set["purpose"])
        assertEquals("on_rent", o.on("rental_orders").single().set["status"])
        assertTrue(o.on("rental_handovers").none { it.set["kind"] == "readiness" })
    }

    @Test
    fun `the last check-in returns the order and opens a readiness checklist`() {
        val ctx = RentalCommands.LineContext(order("on_rent"), line("out"), listOf("al1"), otherOpenLines = 0)
        val cmd = RentalCommands.handover("h1", draft(checkout = false), ctx, emptyList(), null, null, emptyList(), "r1", newId, 1, "u")

        val damage = cmd.payload.jsonObject["handover"]!!.jsonObject["damage"]!!.jsonArray.single().jsonObject
        assertEquals("charge", damage["decision"]!!.jsonPrimitive.content)
        val o = ops(cmd.overlayPatch)
        assertEquals("returned", o.on("rental_order_lines").single().set["status"])
        assertEquals(0L, o.on("resource_allocations").single().set["active"])
        assertEquals("returned", o.on("rental_orders").single().set["status"])
        val readiness = o.on("rental_handovers").single { it.key["id"] == "r1" }
        assertEquals("pending", readiness.set["readiness_status"])
    }

    @Test
    fun `a check-in with other lines still out leaves the order on rent`() {
        val ctx = RentalCommands.LineContext(order("on_rent"), line("out"), emptyList(), otherOpenLines = 1)
        val cmd = RentalCommands.handover("h1", draft(checkout = false), ctx, emptyList(), null, null, emptyList(), "r1", newId, 1, "u")

        assertTrue(ops(cmd.overlayPatch).on("rental_orders").isEmpty())
    }

    @Test
    fun `an override is applied to the refused payload`() {
        val ctx = RentalCommands.LineContext(order(), line(), emptyList(), 0)
        val payload = RentalCommands.handover("h1", draft(true), ctx, emptyList(), null, null, emptyList(), "r1", newId, 1, "u")
            .payload.jsonObject

        val signed = RentalCommands.withOverride(payload, checkout = true, HandoverOverride.Signature)
        assertTrue(signed["override_signature"]!!.jsonPrimitive.boolean)
        val credit = RentalCommands.withOverride(payload, checkout = true, HandoverOverride.Credit(" ok by manager "))
        assertEquals("ok by manager", credit["override_reason"]!!.jsonPrimitive.content)
        assertTrue(credit["override_docs"]!!.jsonPrimitive.boolean)
        val meter = RentalCommands.withOverride(payload, checkout = false, HandoverOverride.Meter("reset", BigDecimal("12.5")))
        val h = meter["handover"]!!.jsonObject
        assertTrue(h["meter_override"]!!.jsonPrimitive.boolean)
        assertEquals("12.5", h["meter_value"]!!.jsonPrimitive.content)
        val reading = RentalCommands.withOverride(payload, checkout = true, HandoverOverride.Reading(BigDecimal("40")))
            .getValue("handover").jsonObject
        assertEquals("40", reading["meter_value"]!!.jsonPrimitive.content)
        assertEquals(null, reading["meter_override"])
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.withOverride(payload, checkout = false, HandoverOverride.Signature)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.withOverride(payload, checkout = true, HandoverOverride.Ready(" "))
        }
    }

    @Test
    fun `a swap closes the old line and continues on a new one`() {
        val ctx = RentalCommands.LineContext(order("on_rent"), line("out"), listOf("al1"), 0)
        val cmd = RentalCommands.swap(
            com.avago.core.data.domain.SwapDraft("o1", "ln1", "a2", "broken", null, null, null, null, start),
            ctx, "ln2", "hin", "hout", newId, 1, "u",
        )

        val lines = ops(cmd.overlayPatch).on("rental_order_lines").associateBy { it.key["id"] }
        assertEquals("swapped", lines["ln1"]!!.set["status"])
        assertEquals("a2", lines["ln2"]!!.set["asset_id"])
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.swap(
                com.avago.core.data.domain.SwapDraft("o1", "ln1", "a1", null, null, null, null, null, start),
                ctx, "ln2", "hin", "hout", newId, 1, "u",
            )
        }
    }

    @Test
    fun `a bulk return needs a bulk line and a quantity`() {
        val bulk = line(assetId = null, productId = "pr1", classId = null)
        val counts = BulkCountsDraft(BigDecimal("2"), BigDecimal.ZERO, BigDecimal("1"))
        val cmd = RentalCommands.bulkReturn("b1", bulk, counts, start, "l1", 1, "u")

        assertEquals(2_000_000L, ops(cmd.overlayPatch).single().set["returned_qty_micros"])
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.bulkReturn("b1", line(), counts, start, null, 1, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.bulkReturn("b1", bulk, BulkCountsDraft(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), start, null, 1, "u")
        }
    }

    @Test
    fun `readiness passes only with every item done and waives only with a reason`() {
        val check = ReadinessCheck("r1", 2, "a1", null, "pending", RentalCommands.DEFAULT_CHECKLIST, null, start, RecordSyncState.SAVED)
        val done = check.items.map { it.copy(done = true) }

        val passed = RentalCommands.updateCheck(check, "passed", done, null, 1)
        assertEquals("passed", passed.payload.jsonObject["set"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        assertEquals(2L, passed.baseVersion)
        assertEquals("passed", ops(passed.overlayPatch).single().set["readiness_status"])
        assertThrows(IllegalArgumentException::class.java) { RentalCommands.updateCheck(check, "passed", check.items, null, 1) }
        assertThrows(IllegalArgumentException::class.java) { RentalCommands.updateCheck(check, "waived", done, " ", 1) }
        assertThrows(IllegalArgumentException::class.java) {
            RentalCommands.updateCheck(check.copy(status = "passed"), "pending", done, null, 1)
        }
        val saved = RentalCommands.updateCheck(check, "pending", listOf(ReadinessCheckItem("inspected", "Inspected", true)), null, 1)
        assertNull(saved.payload.jsonObject["set"]!!.jsonObject["status"], "an unchanged status is not sent")
    }

    @Test
    fun `clearing a customer field sends it in clear and deletes the contact point`() {
        val stored = RentalCustomer(
            partyId = "p1", version = 4, kind = "person", name = "Ada", legalName = null, company = null, email = "a@x.io",
            phone = null, address = null, notes = null, status = "active", statusReason = null, creditLimitMinor = null,
            currency = null, paymentTermsDays = null, poRequired = false, doNotRent = false, doNotRentReason = null,
            taxExempt = false, locale = null, receivableMinor = null, archived = false, sync = RecordSyncState.SAVED,
        )
        val cmd = RentalCommands.updateCustomer(stored, RentalCustomerDraft(name = "Ada", email = null), mapOf("email" to "cp1"), newId, 1)!!

        val clear = cmd.payload.jsonObject["clear"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("email"), clear)
        assertEquals(4L, cmd.baseVersion)
        val point = ops(cmd.overlayPatch).on("party_contact_points").single()
        assertEquals(V2RowOp.Kind.DELETE, point.kind)
        assertNull(RentalCommands.updateCustomer(stored, RentalCustomerDraft(name = "Ada", email = "a@x.io"), emptyMap(), newId, 1))
    }

    @Test
    fun `only a planned transfer ships and receiving moves the unit`() {
        val t = RentalTransfer("t1", 1, "a1", null, "l1", "l2", "planned", start, end, null, null, null, null, RecordSyncState.SAVED)

        assertEquals("in_transit", ops(RentalCommands.shipTransfer(t, start, 1).overlayPatch).single().set["status"])
        val received = ops(RentalCommands.receiveTransfer(t.copy(status = "in_transit"), start, 1).overlayPatch)
        assertEquals("l2", received.on("assets").single().set["location_id"])
        assertThrows(IllegalArgumentException::class.java) { RentalCommands.shipTransfer(t.copy(status = "received"), start, 1) }
        assertFalse(RentalCommands.cancelTransfer(t, null, 1).payload.jsonObject.containsKey("reason"))
    }
}
