package com.avago.core.data.v2.commands

import com.avago.core.data.domain.CycleCountDraft
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.PartIssueDraft
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.ProductDraft
import com.avago.core.data.domain.StockMovement
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** A posted ledger leg of a document: the position it moved and by how much (micros, signed). */
data class PostedLeg(val positionId: String, val qtyMicros: Long)

/**
 * Builders for the stock catalog commands other than `create_and_post` ([StockCommands]): parts,
 * part issues, reversals and cycle counts. Each returns the outbox entry with its overlay.
 */
object InventoryCommands {

    // ------------------------------------------------------------------ products

    fun createProduct(
        id: String,
        draft: ProductDraft,
        categoryId: String?,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val name = requireNotNull(draft.name.trim().ifEmpty { null }) { "a part needs a name" }
        require(draft.modelNumber.clean() == null && draft.revision.clean() == null && draft.originCountryCode.clean() == null) {
            "model number, revision and origin are set by editing the part after it is created"
        }
        val payload = StockProductsCreate(
            id = id,
            name = name,
            sku = draft.sku.clean(),
            manufacturerName = draft.manufacturer.clean(),
            category = draft.category.clean(),
            uomCode = draft.uomCode.clean(),
            standardUnitCost = draft.standardUnitCost,
            standardCostCurrency = draft.standardUnitCost?.let { draft.costCurrency.clean() },
            defaultReorderQty = draft.defaultReorderQty,
            abcClass = draft.abcClass.clean(),
            gtin = draft.gtin.clean(),
            notes = draft.notes.clean(),
        )
        val row = mapOf(
            "name" to name,
            "sku" to (payload.sku ?: ""),
            "category_id" to categoryId,
            "uom_code" to (payload.uomCode ?: DEFAULT_UOM),
            "tracking_mode" to "none",
            "manufacturer_name" to payload.manufacturerName,
            "standard_unit_cost" to payload.standardUnitCost?.toPlainString(),
            "standard_cost_currency" to payload.standardCostCurrency,
            "default_reorder_qty_micros" to payload.defaultReorderQty?.let(V2Micros::fromDecimal),
            "abc_class" to payload.abcClass,
            "gtin" to payload.gtin,
            "notes" to payload.notes,
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch(listOf(V2RowOp.upsert("products", id, row))))
    }

    /** Null when nothing changed. */
    fun updateProduct(stored: Product, draft: ProductDraft, categoryId: String?, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(StockProductsUpdate.Clear.entries)
        val name = d.text("name", stored.name, draft.name)
        val sku = d.text("sku", stored.sku, draft.sku)
        val manufacturer = d.text("manufacturer_name", stored.manufacturer, draft.manufacturer)
        val model = d.text("model_number", stored.modelNumber, draft.modelNumber)
        val revision = d.text("revision", stored.revision, draft.revision)
        val origin = d.text("origin_country_code", stored.originCountryCode, draft.originCountryCode?.trim()?.uppercase())
        val category = d.text("category", stored.categoryName, draft.category, column = null)
        if (category != null) d.overlay["category_id"] = categoryId
        val uom = d.text("uom_code", stored.uomCode, draft.uomCode)
        val cost = d.decimal("standard_unit_cost", stored.standardUnitCost, draft.standardUnitCost)
        val currency = d.text("standard_cost_currency", stored.costCurrency, draft.costCurrency)
        val reorder = d.decimal(
            "default_reorder_qty", stored.defaultReorderQty, draft.defaultReorderQty,
            column = "default_reorder_qty_micros", toColumn = V2Micros::fromDecimal,
        )
        val abc = d.text("abc_class", stored.abcClass, draft.abcClass)
        val gtin = d.text("gtin", stored.gtin, draft.gtin)
        val notes = d.text("notes", stored.notes, draft.notes)
        d.check()
        if (d.isEmpty) return null
        val payload = StockProductsUpdate(
            id = stored.id, name = name, sku = sku, manufacturerName = manufacturer, modelNumber = model,
            revision = revision, originCountryCode = origin, category = category,
            uomCode = uom, standardUnitCost = cost, standardCostCurrency = currency, defaultReorderQty = reorder,
            abcClass = abc, gtin = gtin, notes = notes, clear = d.clear,
        )
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.upsert("products", stored.id, d.overlay + updated(nowMillis)))),
        )
    }

    fun setProductArchived(stored: Product, archived: Boolean, nowMillis: Long): V2NewCommand =
        StockProductsUpdate(id = stored.id, archived = archived).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(
                listOf(
                    V2RowOp.upsert(
                        "products", stored.id,
                        mapOf("archived_at" to if (archived) nowMillis else null) + updated(nowMillis),
                    ),
                ),
            ),
        )

    fun deleteProduct(stored: Product): V2NewCommand =
        StockProductsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.delete("products", stored.id))),
        )

    // ------------------------------------------------------------------ part issues and reversals

    fun issueToWorkOrder(
        id: String,
        lineIds: List<String>,
        draft: PartIssueDraft,
        sourceLocationId: String,
        positionFor: (productId: String, binId: String?) -> KnownPosition?,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(draft.lines.isNotEmpty()) { "a part issue needs at least one line" }
        require(draft.lines.all { it.quantity.signum() > 0 }) { "quantity must be positive" }
        val payload = StockPartIssuesCreate(
            id = id,
            workOrderId = draft.workOrderId,
            issuedToUserId = draft.issuedToUserId,
            locationId = draft.locationId,
            notes = draft.notes.clean(),
            lines = draft.lines.mapIndexed { i, line ->
                IssueLine(
                    id = lineIds[i],
                    productId = line.productId,
                    binId = line.binId,
                    quantity = line.quantity,
                )
            },
        )
        val ops = mutableListOf(
            V2RowOp.upsert(
                "stock_movements", id,
                mapOf(
                    "kind" to MovementKind.ISSUE.wire,
                    "status" to "posted",
                    "location_id" to sourceLocationId,
                    "work_order_id" to draft.workOrderId,
                    "issued_to_user_id" to draft.issuedToUserId,
                    "notes" to payload.notes,
                    "blind_count" to 0L,
                    "effective_at" to nowMillis,
                    "posted_at" to nowMillis,
                    "posted_by" to userId,
                ) + created(nowMillis, userId),
            ),
        )
        draft.lines.forEachIndexed { i, line ->
            ops += V2RowOp.upsert(
                "stock_movement_lines", lineIds[i],
                mapOf(
                    "movement_id" to id,
                    "line_no" to (i + 1).toLong(),
                    "product_id" to line.productId,
                    "location_id" to sourceLocationId,
                    "bin_id" to line.binId,
                    "condition_code" to "good",
                    "quantity_micros" to V2Micros.fromDecimal(line.quantity),
                    "recount_required" to 0L,
                ) + created(nowMillis, userId),
            )
            positionFor(line.productId, line.binId)?.let { pos ->
                ops += V2RowOp.upsert(
                    "stock_positions", mapOf("id" to pos.id), emptyMap(),
                    mapOf("on_hand_qty_micros" to -V2Micros.fromDecimal(line.quantity)),
                )
            }
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /**
     * Reverses a posted document: goods receipts and part issues use their own reverse commands
     * (they also unwind PO receipts / work-order costs), everything else `stock.documents.reverse`.
     * The overlay is the reversing document plus the negated ledger legs.
     */
    fun reverse(
        movement: StockMovement,
        reversalId: String,
        legs: List<PostedLeg>,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(movement.isReversible) { "only a posted, unreversed document can be reversed" }
        val payload: V2CommandPayload = when {
            movement.kind == MovementKind.RECEIPT && movement.purchaseOrderId != null ->
                StockGoodsReceiptsReverse(id = movement.id, reversalId = reversalId)
            movement.kind == MovementKind.ISSUE && movement.workOrderId != null ->
                StockPartIssuesReverse(id = movement.id, reversalId = reversalId)
            else -> StockDocumentsReverse(id = movement.id, reversalId = reversalId)
        }
        val ops = mutableListOf(
            V2RowOp.upsert(
                "stock_movements", reversalId,
                mapOf(
                    "kind" to movement.kind.wire,
                    "status" to "posted",
                    "location_id" to movement.locationId,
                    "to_location_id" to movement.toLocationId,
                    "work_order_id" to movement.workOrderId,
                    "purchase_order_id" to movement.purchaseOrderId,
                    "reverses_movement_id" to movement.id,
                    "blind_count" to 0L,
                    "effective_at" to nowMillis,
                    "posted_at" to nowMillis,
                    "posted_by" to userId,
                ) + created(nowMillis, userId),
            ),
        )
        legs.groupBy { it.positionId }.forEach { (positionId, list) ->
            val delta = -list.sumOf { it.qtyMicros }
            if (delta != 0L) {
                ops += V2RowOp.upsert("stock_positions", mapOf("id" to positionId), emptyMap(), mapOf("on_hand_qty_micros" to delta))
            }
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    // ------------------------------------------------------------------ cycle counts

    fun planCycleCount(id: String, draft: CycleCountDraft, nowMillis: Long, userId: String?): V2NewCommand {
        val payload = StockCycleCountsCreate(
            id = id,
            locationId = draft.locationId,
            countScope = StockCountScope.entries.first { it.wire == draft.scope.wire },
            blindCount = draft.blindCount,
            scheduledOn = draft.scheduledOn?.let(LocalDate::parse),
            notes = draft.notes.clean(),
        )
        val row = mapOf(
            "kind" to MovementKind.COUNT.wire,
            "status" to "draft",
            "location_id" to draft.locationId,
            "count_scope" to draft.scope.wire,
            "blind_count" to if (draft.blindCount) 1L else 0L,
            "scheduled_on" to draft.scheduledOn,
            "notes" to payload.notes,
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch(listOf(V2RowOp.upsert("stock_movements", id, row))))
    }

    fun startCycleCount(id: String, nowMillis: Long, userId: String?): V2NewCommand =
        StockCycleCountsStart(id = id).toNewCommand(
            overlay = movementPatch(id, mapOf("started_at" to nowMillis, "counted_by" to userId), nowMillis),
        )

    fun recordCount(
        lineId: String,
        countedQty: BigDecimal,
        notes: String?,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(countedQty.signum() >= 0) { "a counted quantity cannot be negative" }
        val payload = StockCycleCountsCountLine(
            id = lineId,
            countedQty = countedQty,
            countedAt = Instant.ofEpochMilli(nowMillis),
            notes = notes.clean(),
        )
        val set = buildMap<String, Any?> {
            put("counted_qty_micros", V2Micros.fromDecimal(countedQty))
            put("counted_at", nowMillis)
            put("counted_by", userId)
            payload.notes?.let { put("notes", it) }
            put("updated_at", nowMillis)
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(listOf(V2RowOp.upsert("stock_movement_lines", lineId, set))))
    }

    fun completeCycleCount(id: String, nowMillis: Long): V2NewCommand =
        StockCycleCountsComplete(id = id).toNewCommand(overlay = movementPatch(id, mapOf("completed_at" to nowMillis), nowMillis))

    /** [variances]: position id → counted − expected (micros), applied optimistically. */
    fun postCycleCount(id: String, variances: Map<String, Long>, nowMillis: Long, userId: String?): V2NewCommand {
        val ops = mutableListOf(
            V2RowOp.upsert(
                "stock_movements", id,
                mapOf("status" to "posted", "posted_at" to nowMillis, "posted_by" to userId, "effective_at" to nowMillis) +
                    updated(nowMillis),
            ),
        )
        variances.filterValues { it != 0L }.forEach { (positionId, delta) ->
            ops += V2RowOp.upsert("stock_positions", mapOf("id" to positionId), emptyMap(), mapOf("on_hand_qty_micros" to delta))
        }
        return StockCycleCountsPost(id = id).toNewCommand(overlay = V2OverlayPatch(ops))
    }

    fun cancelCycleCount(id: String, nowMillis: Long): V2NewCommand =
        StockCycleCountsCancel(id = id).toNewCommand(overlay = movementPatch(id, mapOf("status" to "cancelled"), nowMillis))

    // ------------------------------------------------------------------ helpers

    private const val DEFAULT_UOM = "each"

    private fun movementPatch(id: String, set: Map<String, Any?>, nowMillis: Long) =
        V2OverlayPatch(listOf(V2RowOp.upsert("stock_movements", id, set + updated(nowMillis))))
}
