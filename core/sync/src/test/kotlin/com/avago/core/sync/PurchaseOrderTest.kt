package com.avago.core.sync

import com.avago.core.data.db.entity.PoLineEntity
import com.avago.core.data.db.entity.PurchaseOrderEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for PurchaseOrderEntity and PoLineEntity data-class contracts.
 *
 * Pure-JVM tests — no Room, no coroutines, no mocks required.
 */
class PurchaseOrderTest {

    companion object {
        private const val ACCOUNT_ID = "acct-po-test"
        private const val PO_ID = "po-test-001"
        private const val VENDOR_ID = "vendor-test-001"
        private const val CREATED_AT = 1_736_899_200_000L   // 2025-01-15T00:00:00Z
    }

    // ─── PurchaseOrderEntity construction ─────────────────────────────────────

    @Test
    fun `PurchaseOrderEntity can be constructed with required fields`() {
        val po = buildMinimalPo()
        assertEquals(PO_ID, po.poId)
        assertEquals(ACCOUNT_ID, po.accountId)
        assertEquals("draft", po.status)
    }

    @Test
    fun `PurchaseOrderEntity required fields are non-blank`() {
        val po = buildMinimalPo()
        assertFalse(po.poId.isBlank())
        assertFalse(po.accountId.isBlank())
        assertFalse(po.status.isBlank())
    }

    @Test
    fun `PurchaseOrderEntity optional fields default to null`() {
        val po = buildMinimalPo()
        assertNull(po.poNumber)
        assertNull(po.vendorId)
        assertNull(po.currency)
        assertNull(po.subtotal)
        assertNull(po.taxTotal)
        assertNull(po.shippingCost)
        assertNull(po.discount)
        assertNull(po.grandTotal)
        assertNull(po.notes)
        assertNull(po.deletedAt)
    }

    @Test
    fun `PurchaseOrderEntity status can be draft`() {
        val po = buildMinimalPo().copy(status = "draft")
        assertEquals("draft", po.status)
    }

    @Test
    fun `PurchaseOrderEntity status can be ordered`() {
        val po = buildMinimalPo().copy(status = "ordered")
        assertEquals("ordered", po.status)
    }

    @Test
    fun `PurchaseOrderEntity status can be received`() {
        val po = buildMinimalPo().copy(status = "received")
        assertEquals("received", po.status)
    }

    @Test
    fun `PurchaseOrderEntity status can be closed`() {
        val po = buildMinimalPo().copy(status = "closed")
        assertEquals("closed", po.status)
    }

    @Test
    fun `PurchaseOrderEntity timestamps are set on construction`() {
        val po = buildMinimalPo()
        assertEquals(CREATED_AT, po.createdAt)
        assertEquals(CREATED_AT, po.updatedAt)
        assertNull(po.deletedAt)
    }

    @Test
    fun `PurchaseOrderEntity cost fields can be set`() {
        val po = buildMinimalPo().copy(
            subtotal = 200.0,
            taxTotal = 18.0,
            shippingCost = 12.0,
            discount = 10.0,
            grandTotal = 220.0,
        )
        assertEquals(200.0, po.subtotal)
        assertEquals(18.0, po.taxTotal)
        assertEquals(12.0, po.shippingCost)
        assertEquals(10.0, po.discount)
        assertEquals(220.0, po.grandTotal)
    }

    @Test
    fun `PurchaseOrderEntity grandTotal equals subtotal plus tax plus shipping minus discount`() {
        val subtotal = 200.0
        val taxTotal = 18.0
        val shippingCost = 12.0
        val discount = 10.0
        val expected = subtotal + taxTotal + shippingCost - discount
        val po = buildMinimalPo().copy(
            subtotal = subtotal,
            taxTotal = taxTotal,
            shippingCost = shippingCost,
            discount = discount,
            grandTotal = expected,
        )
        assertEquals(220.0, po.grandTotal!!, 0.001)
    }

    @Test
    fun `PurchaseOrderEntity vendorId is optional`() {
        val withVendor = buildMinimalPo().copy(vendorId = VENDOR_ID)
        assertEquals(VENDOR_ID, withVendor.vendorId)

        val withoutVendor = buildMinimalPo()
        assertNull(withoutVendor.vendorId)
    }

    @Test
    fun `PurchaseOrderEntity approval fields are nullable`() {
        val approved = buildMinimalPo().copy(
            approvedBy = "user-mgr-01",
            approvedAt = CREATED_AT + 3600_000L,
        )
        assertNotNull(approved.approvedBy)
        assertNotNull(approved.approvedAt)

        val pending = buildMinimalPo()
        assertNull(pending.approvedBy)
        assertNull(pending.approvedAt)
    }

    @Test
    fun `PurchaseOrderEntity orderedAt and closedAt are nullable`() {
        val po = buildMinimalPo()
        assertNull(po.orderedAt)
        assertNull(po.closedAt)
    }

    @Test
    fun `PurchaseOrderEntity soft-delete sets deletedAt`() {
        val now = System.currentTimeMillis()
        val deleted = buildMinimalPo().copy(deletedAt = now)
        assertNotNull(deleted.deletedAt)
        assertEquals(now, deleted.deletedAt)
    }

    @Test
    fun `PurchaseOrderEntity serverVersion defaults to zero`() {
        val po = buildMinimalPo()
        assertEquals(0L, po.serverVersion)
    }

    @Test
    fun `PurchaseOrderEntity equality is value-based`() {
        val a = buildMinimalPo()
        val b = buildMinimalPo()
        assertEquals(a, b)
    }

    @Test
    fun `PurchaseOrderEntity inequality when IDs differ`() {
        val a = buildMinimalPo()
        val b = buildMinimalPo().copy(poId = "po-test-002")
        assertFalse(a == b)
    }

    @Test
    fun `PurchaseOrderEntity assetId and workOrderId are nullable link fields`() {
        val po = buildMinimalPo()
        assertNull(po.assetId)
        assertNull(po.workOrderId)

        val linked = buildMinimalPo().copy(
            assetId = "asset-linked-001",
            workOrderId = "wo-linked-001",
        )
        assertEquals("asset-linked-001", linked.assetId)
        assertEquals("wo-linked-001", linked.workOrderId)
    }

    @Test
    fun `PurchaseOrderEntity baseGrandTotal and exchangeRate are nullable`() {
        val po = buildMinimalPo()
        assertNull(po.baseGrandTotal)
        assertNull(po.exchangeRateUsed)
    }

    // ─── PoLineEntity construction ────────────────────────────────────────────

    @Test
    fun `PoLineEntity can be constructed with required fields`() {
        val line = buildPoLine()
        assertEquals("pol-001", line.poLineId)
        assertEquals(PO_ID, line.poId)
        assertEquals(3.0, line.quantity)
    }

    @Test
    fun `PoLineEntity partId is optional`() {
        val withPart = buildPoLine().copy(partId = "part-001")
        assertEquals("part-001", withPart.partId)

        val withoutPart = buildPoLine()
        assertNull(withoutPart.partId)
    }

    @Test
    fun `PoLineEntity unitCost is optional`() {
        val line = buildPoLine()
        assertNull(line.unitCost)

        val priced = buildPoLine().copy(unitCost = 15.0)
        assertEquals(15.0, priced.unitCost)
    }

    @Test
    fun `PoLineEntity lineTotal equals quantity times unitCost when cost is set`() {
        val line = buildPoLine(quantity = 4.0).copy(unitCost = 12.50)
        val lineTotal = line.quantity * line.unitCost!!
        assertEquals(50.0, lineTotal, 0.001)
    }

    @Test
    fun `PoLineEntity lineTotal is zero when quantity is zero`() {
        val line = buildPoLine(quantity = 0.0).copy(unitCost = 99.0)
        val lineTotal = line.quantity * line.unitCost!!
        assertEquals(0.0, lineTotal, 0.001)
    }

    @Test
    fun `PoLineEntity optional fields default to null`() {
        val line = buildPoLine()
        assertNull(line.partId)
        assertNull(line.description)
        assertNull(line.unitCost)
        assertNull(line.currency)
        assertNull(line.glCode)
        assertNull(line.receivedQty)
    }

    @Test
    fun `PoLineEntity receivedQty tracks partial receipt`() {
        val line = buildPoLine(quantity = 10.0).copy(receivedQty = 6.0)
        assertEquals(6.0, line.receivedQty)
        assertTrue(line.receivedQty!! < line.quantity, "Partial receipt: received < ordered")
    }

    @Test
    fun `PoLineEntity receivedQty can equal full quantity on complete receipt`() {
        val line = buildPoLine(quantity = 5.0).copy(receivedQty = 5.0)
        assertEquals(line.quantity, line.receivedQty!!, 0.001)
    }

    @Test
    fun `PoLineEntity displayOrder is preserved`() {
        val line = buildPoLine().copy(displayOrder = 2L)
        assertEquals(2L, line.displayOrder)
    }

    @Test
    fun `PoLineEntity serverVersion defaults to zero`() {
        val line = buildPoLine()
        assertEquals(0L, line.serverVersion)
    }

    @Test
    fun `PoLineEntity equality is value-based`() {
        val a = buildPoLine()
        val b = buildPoLine()
        assertEquals(a, b)
    }

    // ─── PO grand total from lines ────────────────────────────────────────────

    @Test
    fun `PO grand total derived from lines matches entity grandTotal`() {
        val lines = listOf(
            buildPoLine(lineId = "pol-1", quantity = 2.0).copy(unitCost = 10.0),
            buildPoLine(lineId = "pol-2", quantity = 3.0).copy(unitCost = 20.0),
            buildPoLine(lineId = "pol-3", quantity = 1.0).copy(unitCost = 5.0),
        )
        val linesSubtotal = lines.sumOf { it.quantity * (it.unitCost ?: 0.0) }
        // 2*10 + 3*20 + 1*5 = 20 + 60 + 5 = 85
        assertEquals(85.0, linesSubtotal, 0.001)

        val taxTotal = 8.5
        val shippingCost = 6.5
        val discount = 0.0
        val grandTotal = linesSubtotal + taxTotal + shippingCost - discount

        val po = buildMinimalPo().copy(
            subtotal = linesSubtotal,
            taxTotal = taxTotal,
            shippingCost = shippingCost,
            discount = discount,
            grandTotal = grandTotal,
        )
        assertEquals(100.0, po.grandTotal!!, 0.001)
    }

    @Test
    fun `PO grand total with discount reduces final amount`() {
        val subtotal = 100.0
        val taxTotal = 10.0
        val shippingCost = 5.0
        val discount = 15.0
        val expected = subtotal + taxTotal + shippingCost - discount

        val po = buildMinimalPo().copy(
            subtotal = subtotal,
            taxTotal = taxTotal,
            shippingCost = shippingCost,
            discount = discount,
            grandTotal = expected,
        )
        assertEquals(100.0, po.grandTotal!!, 0.001)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildMinimalPo() = PurchaseOrderEntity(
        poId = PO_ID,
        accountId = ACCOUNT_ID,
        poNumber = null,
        vendorId = null,
        status = "draft",
        currency = null,
        subtotal = null,
        taxTotal = null,
        shippingCost = null,
        discount = null,
        grandTotal = null,
        baseGrandTotal = null,
        exchangeRateUsed = null,
        expectedDelivery = null,
        shipToLocationId = null,
        workOrderId = null,
        assetId = null,
        requestedBy = null,
        approvedBy = null,
        approvedAt = null,
        orderedAt = null,
        closedAt = null,
        notes = null,
        vendorInvoiceNo = null,
        createdBy = null,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        deletedAt = null,
        serverVersion = 0L,
        seq = null,
    )

    private fun buildPoLine(
        lineId: String = "pol-001",
        quantity: Double = 3.0,
    ) = PoLineEntity(
        poLineId = lineId,
        poId = PO_ID,
        partId = null,
        description = null,
        quantity = quantity,
        unitCost = null,
        currency = null,
        glCode = null,
        receivedQty = null,
        displayOrder = 0L,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        serverVersion = 0L,
    )
}
