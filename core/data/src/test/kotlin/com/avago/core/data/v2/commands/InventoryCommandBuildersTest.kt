package com.avago.core.data.v2.commands

import com.avago.core.data.domain.FieldNotClearableException
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import com.avago.core.data.domain.TrackingMode
import com.avago.core.data.domain.toDraft
import com.avago.core.data.v2.V2OverlayPatch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InventoryCommandBuildersTest {

    private var seq = 0
    private val newId = { "00000000-0000-7000-8000-%012d".format(++seq) }
    private val product = "00000000-0000-7000-8000-0000000000aa"
    private val siteA = "00000000-0000-7000-8000-0000000000a1"
    private val siteB = "00000000-0000-7000-8000-0000000000b1"

    private fun build(draft: StockDocumentDraft, positions: Map<PositionKey, KnownPosition> = emptyMap()) =
        StockCommands.createAndPost(draft, positions::get, nowMillis = 1_000, userId = "u", newId = newId)

    private fun ops(overlay: JsonObject?) = V2OverlayPatch.parse(overlay!!)!!.ops

    private fun positionOps(overlay: JsonObject?) = ops(overlay).filter { it.table == "stock_positions" }

    @Test
    fun `a receipt posts the document and adds to a known position`() {
        val known = mapOf(PositionKey(product, siteA, null, null, "good") to KnownPosition("pos-1", 2_000_000))
        val command = build(
            StockDocumentDraft(MovementKind.RECEIPT, siteA, listOf(StockLineDraft(product, quantity = BigDecimal("1.5")))),
            known,
        )

        assertEquals("stock.documents.create_and_post", command.operation)
        assertEquals("stock_movements", command.entityType)
        assertNull(command.baseVersion)
        val payload = command.payload.jsonObject
        assertEquals("receipt", payload["kind"]!!.jsonPrimitive.content)
        val line = payload["lines"]!!.jsonArray.single().jsonObject
        assertEquals("1.5", line["quantity"]!!.jsonPrimitive.content)
        assertEquals(payload["id"]!!.jsonPrimitive.content, command.entityKey)

        val position = positionOps(command.overlayPatch).single()
        assertEquals(mapOf("id" to "pos-1"), position.key)
        assertEquals(mapOf("on_hand_qty_micros" to 1_500_000L), position.add)
        assertEquals(1, ops(command.overlayPatch).count { it.table == "stock_movement_lines" })
    }

    @Test
    fun `an issue to an unknown position creates it by natural key with a negative delta`() {
        val command = build(
            StockDocumentDraft(MovementKind.ISSUE, siteA, listOf(StockLineDraft(product, quantity = BigDecimal.ONE))),
        )
        val position = positionOps(command.overlayPatch).single()
        assertEquals(product, position.key["product_id"])
        assertEquals(siteA, position.key["location_id"])
        assertEquals(-1_000_000L, position.add["on_hand_qty_micros"])
    }

    @Test
    fun `a transfer moves stock between both positions`() {
        val command = build(
            StockDocumentDraft(
                MovementKind.TRANSFER, siteA, listOf(StockLineDraft(product, quantity = BigDecimal("3"))), toLocationId = siteB,
            ),
        )
        val deltas = positionOps(command.overlayPatch).associate { it.key["location_id"] to it.add["on_hand_qty_micros"] }
        assertEquals(mapOf(siteA to -3_000_000L, siteB to 3_000_000L), deltas)
    }

    @Test
    fun `a count applies the variance against the known on hand`() {
        val key = PositionKey(product, siteA, null, null, "good")
        val command = build(
            StockDocumentDraft(MovementKind.COUNT, siteA, listOf(StockLineDraft(product, countedQty = BigDecimal("7")))),
            mapOf(key to KnownPosition("pos-1", 10_000_000)),
        )
        assertEquals(-3_000_000L, positionOps(command.overlayPatch).single().add["on_hand_qty_micros"])
        assertEquals("7", command.payload.jsonObject["lines"]!!.jsonArray.single().jsonObject["counted_qty"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a count that matches the on hand changes no position`() {
        val key = PositionKey(product, siteA, null, null, "good")
        val command = build(
            StockDocumentDraft(MovementKind.COUNT, siteA, listOf(StockLineDraft(product, countedQty = BigDecimal("10")))),
            mapOf(key to KnownPosition("pos-1", 10_000_000)),
        )
        assertTrue(positionOps(command.overlayPatch).isEmpty())
    }

    @Test
    fun `invalid documents are refused before anything is queued`() {
        assertThrows(IllegalArgumentException::class.java) {
            build(StockDocumentDraft(MovementKind.RECEIPT, siteA, emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            build(StockDocumentDraft(MovementKind.ISSUE, siteA, listOf(StockLineDraft(product, quantity = BigDecimal.ZERO))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            build(StockDocumentDraft(MovementKind.TRANSFER, siteA, listOf(StockLineDraft(product, quantity = BigDecimal.ONE))))
        }
    }

    private val stored = Product(
        id = product, sku = "SKU-1", name = "Filter", categoryId = null, categoryName = null, uomCode = "ea",
        trackingMode = TrackingMode.NONE, manufacturer = "Acme", modelNumber = null, gtin = null,
        standardUnitCost = BigDecimal("2.50"), costCurrency = "USD", defaultReorderQty = BigDecimal("4"),
        abcClass = null, notes = "keep dry", archived = false, version = 7,
    )

    @Test
    fun `a product update sends only changed fields with the base version`() {
        val command = InventoryCommands.updateProduct(
            stored, stored.toDraft().copy(name = "Oil filter", standardUnitCost = BigDecimal("2.5")), null, 5_000,
        )!!
        assertEquals("stock.products.update", command.operation)
        assertEquals(7L, command.baseVersion)
        assertEquals(setOf("name"), command.payload.jsonObject["set"]!!.jsonObject.keys)
        assertEquals(0, (command.payload.jsonObject["clear"] as JsonArray).size)
        assertEquals("Oil filter", ops(command.overlayPatch).single().set["name"])
    }

    @Test
    fun `clearing a clearable field sends it in clear and nulls the overlay column`() {
        val command = InventoryCommands.updateProduct(stored, stored.toDraft().copy(defaultReorderQty = null), null, 5_000)!!
        assertEquals(listOf("default_reorder_qty"), command.payload.jsonObject["clear"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(command.payload.jsonObject["set"]!!.jsonObject.isEmpty())
        val overlay = ops(command.overlayPatch).single()
        assertTrue(overlay.set.containsKey("default_reorder_qty_micros"))
        assertNull(overlay.set["default_reorder_qty_micros"])
    }

    @Test
    fun `clearing a field the catalog cannot clear is refused`() {
        val error = assertThrows(FieldNotClearableException::class.java) {
            InventoryCommands.updateProduct(stored, stored.toDraft().copy(manufacturer = " "), null, 5_000)
        }
        assertEquals(listOf("manufacturer_name"), error.fields)
    }

    @Test
    fun `an unchanged product produces no command`() {
        assertNull(InventoryCommands.updateProduct(stored, stored.toDraft(), null, 5_000))
    }
}
