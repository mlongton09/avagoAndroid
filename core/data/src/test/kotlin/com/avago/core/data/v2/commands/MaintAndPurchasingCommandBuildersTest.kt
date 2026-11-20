package com.avago.core.data.v2.commands

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetDraft
import com.avago.core.data.domain.AssetStatusCode
import com.avago.core.data.domain.FieldNotClearableException
import com.avago.core.data.domain.PurchaseOrder
import com.avago.core.data.domain.PurchaseOrderStatus
import com.avago.core.data.domain.toEdit
import com.avago.core.data.v2.V2OverlayPatch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MaintAndPurchasingCommandBuildersTest {

    private val stored = Asset(
        id = "00000000-0000-7000-8000-000000000001", name = "Truck 7", assetTypeCode = "vehicle", make = "Volvo",
        model = "FH", modelYear = 2020, serialNumber = "SN-1", vin = null, tags = listOf("fleet"),
        locationId = "00000000-0000-7000-8000-0000000000a1", locationName = "Depot", parentAssetId = null,
        status = AssetStatusCode.ONLINE, statusSince = 0, downtimeKind = null, conditionScore = 4, rentable = false,
        avatarColor = null, avatarInitial = null, attributes = """{"color":"red"}""", globalUid = "g", version = 9,
    )

    @Test
    fun `an asset edit clears emptied fields to null`() {
        val command = MaintCommands.updateAsset(
            stored, AssetDraft.of(stored).copy(serialNumber = "", locationId = null, make = "Scania"), 10_000,
        )!!
        val payload = command.payload.jsonObject
        assertEquals("maint.assets.update", command.operation)
        assertEquals(9L, command.baseVersion)
        assertEquals(setOf("make_override"), payload["set"]!!.jsonObject.keys)
        assertEquals(listOf("location_id", "serial_number"), payload["clear"]!!.jsonArray.map { it.jsonPrimitive.content })
        val overlay = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertEquals("Scania", overlay["make_override"])
        assertTrue(overlay.containsKey("serial_number") && overlay["serial_number"] == null)
        assertTrue(overlay.containsKey("location_id") && overlay["location_id"] == null)
    }

    @Test
    fun `an asset name cannot be cleared`() {
        val error = assertThrows(FieldNotClearableException::class.java) {
            MaintCommands.updateAsset(stored, AssetDraft.of(stored).copy(name = "  "), 10_000)
        }
        assertEquals(listOf("name"), error.fields)
    }

    @Test
    fun `unchanged attributes are not sent and changed ones replace the object`() {
        assertNull(MaintCommands.updateAsset(stored, AssetDraft.of(stored).copy(attributes = """{ "color" : "red" }"""), 10_000))
        val command = MaintCommands.updateAsset(stored, AssetDraft.of(stored).copy(attributes = """{"color":"blue"}"""), 10_000)!!
        assertEquals("blue", command.payload.jsonObject["set"]!!.jsonObject["attributes"]!!.jsonObject["color"]!!.jsonPrimitive.content)
    }

    @Test
    fun `purchase order line amounts round per currency`() {
        val usd = PurchasingCommands.lineAmounts(BigDecimal("3"), BigDecimal("1.236"), BigDecimal("10"), "USD")
        assertEquals(371L, usd.subtotalMinor)
        assertEquals(37L, usd.taxMinor)
        assertEquals(408L, usd.totalMinor)

        val jpy = PurchasingCommands.lineAmounts(BigDecimal("2"), BigDecimal("150.4"), null, "JPY")
        assertEquals(301L, jpy.subtotalMinor)
        assertEquals(0L, jpy.taxMinor)
    }

    private fun po(status: PurchaseOrderStatus) = PurchaseOrder(
        id = "po-1", number = "PO-1", supplierPartyId = "sup", supplierName = null, status = status, currency = "USD",
        subtotal = BigDecimal("100"), taxTotal = BigDecimal.ZERO, shipping = BigDecimal("5"), discount = BigDecimal.ZERO,
        grandTotal = BigDecimal("105"), expectedOn = null, shipToLocationId = null, workOrderId = null, assetId = null,
        requestedBy = null, approvedBy = null, approvedAt = null, orderedAt = null, closedAt = null, notes = null,
        supplierInvoiceRef = null, createdAt = 0, version = 4, lines = emptyList(),
    )

    @Test
    fun `purchase order shipping and discount are sent in minor units and move the grand total`() {
        val stored = po(PurchaseOrderStatus.DRAFT)
        val command = PurchasingCommands.updatePurchaseOrder(
            stored, stored.toEdit().copy(shipping = BigDecimal("7.5"), discount = BigDecimal("2")), 1,
        )!!
        val set = command.payload.jsonObject["set"]!!.jsonObject
        assertEquals(750L, set["shipping_minor"]!!.jsonPrimitive.content.toLong())
        assertEquals(200L, set["discount_minor"]!!.jsonPrimitive.content.toLong())
        assertEquals(4L, command.baseVersion)
        val add = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.first { it.add.isNotEmpty() }.add
        assertEquals(250L, add["shipping_minor"])
        assertEquals(200L, add["discount_minor"])
        assertEquals(50L, add["grand_total_minor"])
        assertNull(PurchasingCommands.updatePurchaseOrder(stored, stored.toEdit(), 1))
        assertThrows(IllegalArgumentException::class.java) {
            PurchasingCommands.updatePurchaseOrder(stored, stored.toEdit().copy(discount = BigDecimal("-1")), 1)
        }
    }

    @Test
    fun `rejecting a purchase order carries the reason`() {
        val command = PurchasingCommands.transition(
            po(PurchaseOrderStatus.PENDING_APPROVAL), PurchasingCommands.Transition.REJECT, 1, "u", reason = " too dear ",
        )
        assertEquals("stock.purchase_orders.reject", command.operation)
        assertEquals("too dear", command.payload.jsonObject["reason"]!!.jsonPrimitive.content)
        assertThrows(IllegalArgumentException::class.java) {
            PurchasingCommands.transition(po(PurchaseOrderStatus.DRAFT), PurchasingCommands.Transition.REJECT, 1, "u")
        }
    }

    private val activity = Activity(
        id = "act", assetId = "asset", assetName = null, workOrderId = "wo1", parentActivityId = null,
        type = ActivityType.REPAIR, categoryLabel = "repair", title = "Brakes", occurredAt = 5_000, performedByUserId = null,
        performedByName = null, providerName = null, currency = "EUR", overtimeMultiplier = null, notes = null,
        payload = "{}", attributes = "{}", meterValue = null, totalCost = BigDecimal.ZERO, createdAt = 0, version = 3,
    )

    private fun draftOf(a: Activity) = ActivityDraft(
        assetId = a.assetId, workOrderId = a.workOrderId, type = a.type, categoryLabel = a.categoryLabel, title = a.title,
        occurredAt = a.occurredAt, currency = a.currency, payload = a.payload, attributes = a.attributes,
    )

    @Test
    fun `moving an activity to another work order moves its cost lines in the overlay`() {
        val command = MaintCommands.updateActivity(activity, draftOf(activity).copy(workOrderId = "wo2"), 10_000, listOf("l1", "l2"))!!
        assertEquals("maint.activity_records.update", command.operation)
        assertEquals("wo2", command.payload.jsonObject["set"]!!.jsonObject["work_order_id"]!!.jsonPrimitive.content)
        val ops = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops
        assertEquals(listOf("activity_records", "activity_cost_lines", "activity_cost_lines"), ops.map { it.table })
        assertTrue(ops.drop(1).all { it.set == mapOf("work_order_id" to "wo2") })
    }

    @Test
    fun `detaching an activity clears the work order and leaves lines untouched when not moved`() {
        val detached = MaintCommands.updateActivity(activity, draftOf(activity).copy(workOrderId = null), 10_000, listOf("l1"))!!
        assertEquals(listOf("work_order_id"), detached.payload.jsonObject["clear"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertNull(V2OverlayPatch.parse(detached.overlayPatch!!)!!.ops.last().set["work_order_id"])
        val retitled = MaintCommands.updateActivity(activity, draftOf(activity).copy(title = "Pads"), 10_000, listOf("l1"))!!
        assertEquals(listOf("activity_records"), V2OverlayPatch.parse(retitled.overlayPatch!!)!!.ops.map { it.table })
    }

    @Test
    fun `an activity's kind changes and its meter value is set or cleared`() {
        val rekinded = MaintCommands.updateActivity(activity, draftOf(activity).copy(type = ActivityType.SERVICE), 10_000)!!
        assertEquals("service", rekinded.payload.jsonObject["set"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals("service", V2OverlayPatch.parse(rekinded.overlayPatch!!)!!.ops.single().set["kind"])

        val metered = MaintCommands.updateActivity(activity, draftOf(activity).copy(meterValue = BigDecimal("120.5")), 10_000)
        assertEquals("120.5", metered!!.payload.jsonObject["set"]!!.jsonObject["meter_value"]!!.jsonPrimitive.content, "a meter-only edit is sent")

        val unmetered = MaintCommands.updateActivity(activity.copy(meterValue = BigDecimal.TEN), draftOf(activity), 10_000)!!
        assertEquals(listOf("meter_value"), unmetered.payload.jsonObject["clear"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
}
