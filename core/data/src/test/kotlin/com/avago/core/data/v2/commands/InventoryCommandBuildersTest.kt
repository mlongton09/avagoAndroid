package com.avago.core.data.v2.commands

import com.avago.core.data.domain.FieldNotClearableException
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.MovementStatus
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.StockCondition
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import com.avago.core.data.domain.StockMovement
import com.avago.core.data.domain.StockMovementLine
import com.avago.core.data.domain.StockingPolicy
import com.avago.core.data.domain.StockingPolicyDraft
import com.avago.core.data.domain.TransferRequestDraft
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
        trackingMode = TrackingMode.NONE, manufacturer = "Acme", modelNumber = null, revision = "B",
        originCountryCode = null, gtin = null,
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
            InventoryCommands.updateProduct(stored, stored.toDraft().copy(sku = " "), null, 5_000)
        }
        assertEquals(listOf("sku"), error.fields)
    }

    @Test
    fun `model number, revision and origin are edited and cleared like other part fields`() {
        val command = InventoryCommands.updateProduct(
            stored, stored.toDraft().copy(modelNumber = "X-1", revision = "", originCountryCode = " de"), null, 5_000,
        )!!
        val payload = command.payload.jsonObject
        assertEquals("X-1", payload["set"]!!.jsonObject["model_number"]!!.jsonPrimitive.content)
        assertEquals("DE", payload["set"]!!.jsonObject["origin_country_code"]!!.jsonPrimitive.content)
        assertEquals(listOf("revision"), payload["clear"]!!.jsonArray.map { it.jsonPrimitive.content })
        val overlay = ops(command.overlayPatch).single().set
        assertEquals("DE", overlay["origin_country_code"])
        assertTrue(overlay.containsKey("revision") && overlay["revision"] == null)
    }

    @Test
    fun `a new part cannot carry fields only an edit can set`() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCommands.createProduct("p", stored.toDraft().copy(revision = null, modelNumber = "M"), null, 1, "u")
        }
    }

    // ------------------------------------------------------------------ stocking policies

    private val policy = StockingPolicy(
        id = "pol-1", productId = product, locationId = siteA, minQty = BigDecimal("2"), maxQty = BigDecimal("10"),
        reorderQty = null, economicOrderQty = null, safetyQty = null, consumptionWindowDays = null, version = 3,
    )

    @Test
    fun `setting levels where there is no policy creates it keyed by product and location`() {
        val command = StockCommands.setStockingPolicy(
            StockingPolicyDraft(product, siteB, minQty = BigDecimal("1"), maxQty = BigDecimal("5"), consumptionWindowDays = 30),
            existing = null, nowMillis = 9, userId = "u", newId = newId,
        )
        assertEquals("stock.stocking_policies.set", command.operation)
        assertNull(command.baseVersion)
        val payload = command.payload.jsonObject
        assertEquals("5", payload["max_qty"]!!.jsonPrimitive.content)
        assertNull(payload["reorder_qty"]) // omitted = unset (the set replaces every level)
        val op = ops(command.overlayPatch).single()
        assertEquals(mapOf("product_id" to product, "location_id" to siteB), op.key)
        assertEquals(1_000_000L, op.set["min_qty_micros"])
        assertEquals(30L, op.set["consumption_window_days"])
        assertEquals("00000000-0000-7000-8000-000000000001", op.set["id"])
    }

    @Test
    fun `setting levels on an existing policy replaces every level`() {
        val command = StockCommands.setStockingPolicy(
            StockingPolicyDraft(product, siteA, reorderQty = BigDecimal("4")), policy, nowMillis = 9, userId = "u",
        )
        val op = ops(command.overlayPatch).single()
        assertEquals(mapOf("id" to "pol-1"), op.key)
        assertNull(op.set["min_qty_micros"])
        assertTrue(op.set.containsKey("max_qty_micros"))
        assertEquals(4_000_000L, op.set["reorder_qty_micros"])
    }

    @Test
    fun `stocking levels are validated`() {
        assertThrows(IllegalArgumentException::class.java) {
            StockCommands.setStockingPolicy(StockingPolicyDraft(product, siteA, minQty = BigDecimal("5"), maxQty = BigDecimal("2")), null, 1, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockCommands.setStockingPolicy(StockingPolicyDraft(product, siteA, safetyQty = BigDecimal("-1")), null, 1, null)
        }
    }

    @Test
    fun `deleting a policy and dismissing a suggestion remove the rows at once`() {
        val delete = StockCommands.deleteStockingPolicy(policy)
        assertEquals("stock.stocking_policies.delete", delete.operation)
        assertEquals(3L, delete.baseVersion)
        assertEquals("stocking_policies", ops(delete.overlayPatch).single().table)

        val dismiss = StockCommands.dismissReorderSuggestion("sug-1")
        assertEquals("stock.reorder_suggestions.dismiss", dismiss.operation)
        val op = ops(dismiss.overlayPatch).single()
        assertEquals("reorder_suggestions", op.table)
        assertEquals(mapOf("id" to "sug-1"), op.key)
    }

    // ------------------------------------------------------------------ transfer requests

    @Test
    fun `a transfer request is a requested one-line transfer that moves no stock`() {
        val command = StockCommands.requestTransfer(
            "mv-1", "ln-1", TransferRequestDraft(product, BigDecimal("3"), siteA, siteB, notes = " urgent "), 5, "u",
        )
        assertEquals("stock.transfer_requests.create", command.operation)
        assertEquals("mv-1", command.entityKey)
        assertEquals("urgent", command.payload.jsonObject["notes"]!!.jsonPrimitive.content)
        val ops = ops(command.overlayPatch)
        assertEquals(listOf("stock_movements", "stock_movement_lines"), ops.map { it.table })
        assertEquals("requested", ops[0].set["status"])
        assertEquals(siteB, ops[0].set["to_location_id"])
        assertEquals(3_000_000L, ops[1].set["requested_qty_micros"])
        assertThrows(IllegalArgumentException::class.java) {
            StockCommands.requestTransfer("m", "l", TransferRequestDraft(product, BigDecimal.ONE, siteA, siteA), 5, "u")
        }
    }

    private fun request(status: MovementStatus) = StockMovement(
        id = "mv-1", kind = MovementKind.TRANSFER, status = status, number = null, locationId = siteA, toLocationId = siteB,
        effectiveAt = null, postedAt = null, requestedBy = "u", approvedBy = null, decisionNote = null,
        purchaseOrderId = null, workOrderId = null, countScope = null, blindCount = false, countedBy = null,
        startedAt = null, completedAt = null, scheduledOn = null, carrier = null, trackingNumber = null,
        packingSlipNo = null, reference = null, notes = null, createdAt = 0, createdBy = "u", version = 1,
        lines = listOf(
            StockMovementLine(
                id = "ln-1", movementId = "mv-1", lineNo = 1, productId = product, productName = null, productSku = null,
                uomCode = null, locationId = siteA, binId = null, toBinId = null, lotId = null,
                condition = StockCondition.GOOD, requestedQty = BigDecimal("3"), quantity = BigDecimal("3"),
                expectedQty = null, countedQty = null, countedAt = null, countedBy = null, recountRequired = false,
                purchaseOrderLineId = null, workOrderMaterialId = null, discrepancyQty = null, discrepancyReason = null,
                reasonCode = null, unitCost = null, notes = null, version = 1,
            ),
        ),
    )

    @Test
    fun `approving a transfer request records the decision`() {
        val command = StockCommands.decideTransferRequest(
            request(MovementStatus.REQUESTED), MovementStatus.APPROVED, "ok", { null }, 7, "boss",
        )
        assertEquals("stock.transfer_requests.decide", command.operation)
        assertEquals("approved", command.payload.jsonObject["status"]!!.jsonPrimitive.content)
        val op = ops(command.overlayPatch).single()
        assertEquals("approved", op.set["status"])
        assertEquals("boss", op.set["approved_by"])
        assertEquals("ok", op.set["decision_note"])
    }

    @Test
    fun `posting a transfer request moves the requested stock between locations`() {
        val known = mapOf(PositionKey(product, siteA, null, null, "good") to KnownPosition("pos-a", 5_000_000))
        val command = StockCommands.decideTransferRequest(
            request(MovementStatus.APPROVED), MovementStatus.POSTED, null, known::get, 7, "boss", newId,
        )
        val positions = positionOps(command.overlayPatch)
        assertEquals(2, positions.size)
        assertEquals(mapOf("id" to "pos-a"), positions[0].key)
        assertEquals(mapOf("on_hand_qty_micros" to -3_000_000L), positions[0].add)
        assertEquals(siteB, positions[1].key["location_id"])
        assertEquals(mapOf("on_hand_qty_micros" to 3_000_000L), positions[1].add)
    }

    @Test
    fun `a closed transfer request cannot be decided again`() {
        assertThrows(IllegalArgumentException::class.java) {
            StockCommands.decideTransferRequest(request(MovementStatus.POSTED), MovementStatus.CANCELLED, null, { null }, 7, "u")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockCommands.decideTransferRequest(request(MovementStatus.REQUESTED), MovementStatus.DRAFT, null, { null }, 7, "u")
        }
    }

    @Test
    fun `an unchanged product produces no command`() {
        assertNull(InventoryCommands.updateProduct(stored, stored.toDraft(), null, 5_000))
    }
}
