package com.avago.core.data.v2.commands

import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import com.avago.core.data.v2.V2Uuid
import java.math.BigDecimal
import java.time.Instant

/** The natural key of a stock position (one product at a place, lot and condition). */
data class PositionKey(
    val productId: String,
    val locationId: String,
    val binId: String?,
    val lotId: String?,
    val condition: String,
)

/** A current position: its id and on-hand micros. */
data class KnownPosition(val id: String, val onHandMicros: Long)

/**
 * Builds `stock.documents.create_and_post` commands with their optimistic overlay: the posted
 * document with its lines (under the same client-generated ids the server will use), and the
 * on-hand change of every position it touches (as `add` deltas, so a newer synced base keeps the
 * change right until the command retires).
 */
object StockCommands {
    fun createAndPost(
        draft: StockDocumentDraft,
        positions: (PositionKey) -> KnownPosition?,
        nowMillis: Long,
        userId: String?,
        newId: () -> String = { V2Uuid.v7(nowMillis) },
    ): V2NewCommand {
        require(draft.lines.isNotEmpty()) { "a stock document needs at least one line" }
        validate(draft)
        val movementId = newId()
        val lineIds = draft.lines.map { newId() }
        val payload = StockDocumentsCreateAndPost(
            id = movementId,
            kind = StockMovementKind.entries.first { it.wire == draft.kind.wire },
            locationId = draft.locationId,
            toLocationId = draft.toLocationId,
            workOrderId = draft.workOrderId,
            purchaseOrderId = draft.purchaseOrderId,
            issuedToUserId = draft.issuedToUserId,
            countScope = draft.countScope?.let { scope -> StockCountScope.entries.first { it.wire == scope } },
            referenceText = draft.reference?.trim()?.ifEmpty { null },
            effectiveAt = draft.effectiveAt?.let(Instant::ofEpochMilli),
            notes = draft.notes?.trim()?.ifEmpty { null },
            lines = draft.lines.mapIndexed { index, line -> linePayload(lineIds[index], line) },
        )
        val ops = mutableListOf<V2RowOp>()
        ops += V2RowOp.upsert(
            "stock_movements", movementId,
            mapOf(
                "kind" to draft.kind.wire,
                "status" to "posted",
                "location_id" to draft.locationId,
                "to_location_id" to draft.toLocationId,
                "work_order_id" to draft.workOrderId,
                "purchase_order_id" to draft.purchaseOrderId,
                "reference_text" to draft.reference,
                "notes" to draft.notes,
                "count_scope" to draft.countScope,
                "blind_count" to 0L,
                "effective_at" to (draft.effectiveAt ?: nowMillis),
                "posted_at" to nowMillis,
                "posted_by" to userId,
                "created_at" to nowMillis,
                "updated_at" to nowMillis,
                "created_by" to userId,
                "version" to 1L,
            ),
        )
        draft.lines.forEachIndexed { index, line ->
            ops += V2RowOp.upsert(
                "stock_movement_lines", lineIds[index],
                mapOf(
                    "movement_id" to movementId,
                    "line_no" to (index + 1).toLong(),
                    "product_id" to line.productId,
                    "location_id" to (line.locationId ?: draft.locationId),
                    "bin_id" to line.binId,
                    "to_bin_id" to line.toBinId,
                    "lot_id" to line.lotId,
                    "condition_code" to line.condition.wire,
                    "quantity_micros" to line.quantity?.let(V2Micros::fromDecimal),
                    "counted_qty_micros" to line.countedQty?.let(V2Micros::fromDecimal),
                    "recount_required" to 0L,
                    "purchase_order_line_id" to line.purchaseOrderLineId,
                    "work_order_material_id" to line.workOrderMaterialId,
                    "reason_code" to line.reasonCode,
                    "unit_cost" to line.unitCost?.stripTrailingZeros()?.toPlainString(),
                    "cost_currency" to line.currency,
                    "notes" to line.notes,
                    "created_at" to nowMillis,
                    "updated_at" to nowMillis,
                    "version" to 1L,
                ),
            )
        }
        val deltas = LinkedHashMap<PositionKey, Long>()
        for (line in draft.lines) {
            for ((key, delta) in effects(draft, line, positions)) deltas[key] = (deltas[key] ?: 0L) + delta
        }
        for ((key, delta) in deltas) {
            if (delta == 0L) continue
            val known = positions(key)
            ops += if (known != null) {
                V2RowOp.upsert("stock_positions", mapOf("id" to known.id), emptyMap(), mapOf("on_hand_qty_micros" to delta))
            } else {
                V2RowOp.upsert(
                    "stock_positions",
                    mapOf(
                        "product_id" to key.productId, "location_id" to key.locationId, "bin_id" to key.binId,
                        "lot_id" to key.lotId, "stock_unit_id" to null, "condition_code" to key.condition,
                    ),
                    mapOf("id" to newId(), "version" to 1L, "created_at" to nowMillis, "updated_at" to nowMillis),
                    mapOf("on_hand_qty_micros" to delta),
                )
            }
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    private fun validate(draft: StockDocumentDraft) {
        for (line in draft.lines) {
            when (draft.kind) {
                MovementKind.COUNT -> require(line.countedQty != null && line.countedQty.signum() >= 0) {
                    "a count line needs a counted quantity"
                }
                MovementKind.ADJUSTMENT -> require(line.quantity != null && line.quantity.signum() != 0) {
                    "an adjustment line needs a non-zero quantity"
                }
                else -> require(line.quantity != null && line.quantity.signum() > 0) { "quantity must be positive" }
            }
        }
        if (draft.kind == MovementKind.TRANSFER) {
            require(draft.toLocationId != null || draft.lines.all { it.toBinId != null }) {
                "a transfer needs a destination location or bin"
            }
        }
    }

    private fun linePayload(id: String, line: StockLineDraft) = StockDocumentLine(
        id = id,
        productId = line.productId,
        locationId = line.locationId,
        binId = line.binId,
        toBinId = line.toBinId,
        lotId = line.lotId,
        conditionCode = line.condition.wire,
        quantity = line.quantity,
        countedQty = line.countedQty,
        reasonCode = line.reasonCode,
        unitCost = line.unitCost,
        costCurrency = line.unitCost?.let { line.currency },
        purchaseOrderLineId = line.purchaseOrderLineId,
        workOrderMaterialId = line.workOrderMaterialId,
        notes = line.notes?.trim()?.ifEmpty { null },
    )

    /** The on-hand change (micros) a line makes at each position. */
    private fun effects(
        draft: StockDocumentDraft,
        line: StockLineDraft,
        positions: (PositionKey) -> KnownPosition?,
    ): List<Pair<PositionKey, Long>> {
        val location = line.locationId ?: draft.locationId
        val at = PositionKey(line.productId, location, line.binId, line.lotId, line.condition.wire)
        val qty = line.quantity?.let(V2Micros::fromDecimal) ?: 0L
        return when (draft.kind) {
            MovementKind.RECEIPT, MovementKind.RETURN, MovementKind.OPENING_BALANCE, MovementKind.TRANSFER_RECEIPT ->
                listOf(at to qty)
            MovementKind.ISSUE, MovementKind.TRANSFER_DISPATCH -> listOf(at to -qty)
            MovementKind.ADJUSTMENT -> listOf(at to qty)
            MovementKind.TRANSFER -> listOf(
                at to -qty,
                at.copy(locationId = draft.toLocationId ?: location, binId = line.toBinId) to qty,
            )
            MovementKind.COUNT -> {
                val counted = V2Micros.fromDecimal(line.countedQty ?: BigDecimal.ZERO)
                listOf(at to counted - (positions(at)?.onHandMicros ?: 0L))
            }
        }
    }
}
