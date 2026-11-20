package com.avago.core.data.v2.commands

import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.MovementStatus
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import com.avago.core.data.domain.StockMovement
import com.avago.core.data.domain.StockingPolicy
import com.avago.core.data.domain.StockingPolicyDraft
import com.avago.core.data.domain.TransferRequestDraft
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
            if (delta != 0L) ops += positionDelta(key, delta, positions, nowMillis, newId)
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    // ------------------------------------------------------------------ stocking policies

    /**
     * `stock.stocking_policies.set`: replaces every level of the (product, location) policy, so
     * levels left null are cleared. [existing] is the current policy there, if any.
     */
    fun setStockingPolicy(
        draft: StockingPolicyDraft,
        existing: StockingPolicy?,
        nowMillis: Long,
        userId: String?,
        newId: () -> String = { V2Uuid.v7(nowMillis) },
    ): V2NewCommand {
        val levels = listOf(draft.minQty, draft.maxQty, draft.reorderQty, draft.economicOrderQty, draft.safetyQty)
        require(levels.all { it == null || it.signum() >= 0 }) { "stocking levels cannot be negative" }
        if (draft.minQty != null && draft.maxQty != null) {
            require(draft.maxQty >= draft.minQty) { "max must not be below min" }
        }
        require(draft.consumptionWindowDays == null || draft.consumptionWindowDays > 0) {
            "the consumption window must be at least one day"
        }
        val payload = StockStockingPoliciesSet(
            productId = draft.productId,
            locationId = draft.locationId,
            minQty = draft.minQty,
            maxQty = draft.maxQty,
            reorderQty = draft.reorderQty,
            economicOrderQty = draft.economicOrderQty,
            safetyQty = draft.safetyQty,
            consumptionWindowDays = draft.consumptionWindowDays,
        )
        val levelsRow = mapOf(
            "min_qty_micros" to draft.minQty?.let(V2Micros::fromDecimal),
            "max_qty_micros" to draft.maxQty?.let(V2Micros::fromDecimal),
            "reorder_qty_micros" to draft.reorderQty?.let(V2Micros::fromDecimal),
            "economic_order_qty_micros" to draft.economicOrderQty?.let(V2Micros::fromDecimal),
            "safety_qty_micros" to draft.safetyQty?.let(V2Micros::fromDecimal),
            "consumption_window_days" to draft.consumptionWindowDays?.toLong(),
        )
        val op = if (existing != null) {
            V2RowOp.upsert("stocking_policies", existing.id, levelsRow + updated(nowMillis))
        } else {
            V2RowOp.upsert(
                "stocking_policies",
                mapOf("product_id" to draft.productId, "location_id" to draft.locationId),
                levelsRow + mapOf("id" to newId()) + created(nowMillis, userId),
            )
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(listOf(op)))
    }

    fun deleteStockingPolicy(stored: StockingPolicy): V2NewCommand =
        StockStockingPoliciesDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.delete("stocking_policies", stored.id))),
        )

    /** `stock.reorder_suggestions.dismiss`: the suggestion leaves the list at once. */
    fun dismissReorderSuggestion(id: String): V2NewCommand =
        StockReorderSuggestionsDismiss(id = id).toNewCommand(
            overlay = V2OverlayPatch(listOf(V2RowOp.delete("reorder_suggestions", id))),
        )

    // ------------------------------------------------------------------ transfer requests

    /** `stock.transfer_requests.create`: a one-line transfer document in status requested. */
    fun requestTransfer(
        id: String,
        lineId: String,
        draft: TransferRequestDraft,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(draft.quantity.signum() > 0) { "quantity must be positive" }
        require(draft.fromLocationId != draft.toLocationId) { "the locations must differ" }
        val notes = draft.notes.clean()
        val payload = StockTransferRequestsCreate(
            id = id,
            productId = draft.productId,
            quantity = draft.quantity,
            fromLocationId = draft.fromLocationId,
            toLocationId = draft.toLocationId,
            notes = notes,
        )
        val qty = V2Micros.fromDecimal(draft.quantity)
        val ops = listOf(
            V2RowOp.upsert(
                "stock_movements", id,
                mapOf(
                    "kind" to MovementKind.TRANSFER.wire,
                    "status" to MovementStatus.REQUESTED.wire,
                    "location_id" to draft.fromLocationId,
                    "to_location_id" to draft.toLocationId,
                    "requested_by" to userId,
                    "blind_count" to 0L,
                    "notes" to notes,
                ) + created(nowMillis, userId),
            ),
            V2RowOp.upsert(
                "stock_movement_lines", lineId,
                mapOf(
                    "movement_id" to id,
                    "line_no" to 1L,
                    "product_id" to draft.productId,
                    "location_id" to draft.fromLocationId,
                    "condition_code" to "good",
                    "requested_qty_micros" to qty,
                    "quantity_micros" to qty,
                    "recount_required" to 0L,
                    "created_at" to nowMillis,
                    "updated_at" to nowMillis,
                    "version" to 1L,
                ),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /**
     * `stock.transfer_requests.decide`. Posting moves the requested quantity of good stock from
     * the source to the destination location (unbinned), optimistically as position deltas.
     */
    fun decideTransferRequest(
        request: StockMovement,
        status: MovementStatus,
        note: String?,
        positions: (PositionKey) -> KnownPosition?,
        nowMillis: Long,
        userId: String?,
        newId: () -> String = { V2Uuid.v7(nowMillis) },
    ): V2NewCommand {
        require(request.kind == MovementKind.TRANSFER) { "not a transfer request" }
        require(status in DECISIONS) { "a transfer request can be approved, declined, cancelled or posted" }
        val open = setOf(MovementStatus.DRAFT, MovementStatus.REQUESTED, MovementStatus.APPROVED)
        require(request.status in open) { "a ${request.status.wire} request cannot change" }
        val cleanNote = note.clean()
        val head = mapOf("status" to status.wire) + updated(nowMillis)
        val decided = when (status) {
            MovementStatus.APPROVED, MovementStatus.DECLINED ->
                mapOf("approved_by" to userId, "approved_at" to nowMillis, "decision_note" to cleanNote)
            MovementStatus.POSTED -> mapOf("posted_at" to nowMillis, "posted_by" to userId, "effective_at" to nowMillis)
            else -> mapOf("decision_note" to cleanNote)
        }
        val ops = mutableListOf<V2RowOp>(V2RowOp.upsert("stock_movements", request.id, head + decided))
        if (status == MovementStatus.POSTED) {
            val toLocation = requireNotNull(request.toLocationId) { "the request has no destination" }
            val deltas = LinkedHashMap<PositionKey, Long>()
            for (line in request.lines) {
                val qty = V2Micros.fromDecimal(line.requestedQty ?: line.quantity ?: BigDecimal.ZERO)
                val from = PositionKey(line.productId, line.locationId ?: request.locationId, null, null, "good")
                deltas[from] = (deltas[from] ?: 0L) - qty
                val to = from.copy(locationId = toLocation)
                deltas[to] = (deltas[to] ?: 0L) + qty
            }
            for ((key, delta) in deltas) if (delta != 0L) ops += positionDelta(key, delta, positions, nowMillis, newId)
        }
        return StockTransferRequestsDecide(
            id = request.id,
            status = StockMovementStatus.entries.first { it.wire == status.wire },
            note = cleanNote,
        ).toNewCommand(overlay = V2OverlayPatch(ops))
    }

    private val DECISIONS = setOf(
        MovementStatus.APPROVED, MovementStatus.DECLINED, MovementStatus.CANCELLED, MovementStatus.POSTED,
    )

    private fun positionDelta(
        key: PositionKey,
        delta: Long,
        positions: (PositionKey) -> KnownPosition?,
        nowMillis: Long,
        newId: () -> String,
    ): V2RowOp {
        val known = positions(key)
        return if (known != null) {
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
