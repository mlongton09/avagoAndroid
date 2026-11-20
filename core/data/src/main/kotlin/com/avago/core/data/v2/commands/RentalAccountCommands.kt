package com.avago.core.data.v2.commands

import com.avago.core.data.domain.PostCharge
import com.avago.core.data.domain.PostChargeDraft
import com.avago.core.data.domain.PriceAdjustmentDraft
import com.avago.core.data.domain.RentalOrder
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import com.avago.core.data.v2.V2StagedFile

/**
 * Builders for the post-rental money commands: post charges (`rental.post_charges.*`) and manual
 * price lines (`rental.rental_order_lines.create` with a unit price), with their optimistic rows.
 */
object RentalAccountCommands {

    private const val ONE_MICROS = 1_000_000L
    /** Server `post_charges.rs` ALLOWED_STATUSES without `closed` (a closed order is billed online). */
    val COMMAND_STATUSES = setOf("on_rent", "off_rent_requested", "returned")

    /** Statuses an order can take a post charge in at all (closed: only the online charge-now). */
    val ALLOWED_STATUSES = COMMAND_STATUSES + "closed"

    /**
     * `rental.post_charges.create` with the evidence upload. The server bills the order while it is
     * open (and went out); billing a closed order or charging at once is the online action instead.
     */
    fun createPostCharge(
        id: String,
        draft: PostChargeDraft,
        order: RentalOrder,
        evidence: V2StagedFile,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(order.id == draft.orderId) { "the charge is for another order" }
        require(order.status in COMMAND_STATUSES) { "post charges go on an order that went out and is not closed" }
        require(draft.amountMinor > 0) { "the amount must be positive" }
        require((draft.adminFeeMinor ?: 0L) >= 0) { "the admin fee cannot be negative" }
        val kind = PostChargeKind.entries.firstOrNull { it.wire == draft.kind } ?: throw IllegalArgumentException("unknown kind ${draft.kind}")
        val payload = RentalPostChargesCreate(
            id = id,
            orderId = order.id,
            kind = kind,
            amountMinor = draft.amountMinor,
            adminFeeMinor = draft.adminFeeMinor?.takeIf { it > 0 },
            incurredAt = draft.incurredAt,
            evidenceFileObjectId = evidence.fileId,
            assetId = draft.assetId,
            reference = draft.reference.clean(),
            authority = draft.authority.clean(),
            description = draft.description.clean(),
        )
        val row = V2RowOp.upsert(
            "post_charges", id,
            mapOf(
                "order_id" to order.id, "asset_id" to draft.assetId, "kind" to kind.wire, "amount_minor" to draft.amountMinor,
                "admin_fee_minor" to (payload.adminFeeMinor ?: 0L), "currency" to order.currency,
                "incurred_at" to draft.incurredAt.toEpochMilli(), "reference" to payload.reference, "authority" to payload.authority,
                "description" to payload.description, "evidence_file_object_id" to evidence.fileId, "status" to PostCharge.STATUS_PENDING,
                "attempts" to 0L,
            ) + created(nowMillis, userId),
        )
        return payload.toNewCommand(
            overlay = V2OverlayPatch(listOf(MaintCommands.pendingFileRow(evidence, nowMillis, userId), row)),
            uploads = listOf(evidence.toUpload()),
        )
    }

    fun voidPostCharge(stored: PostCharge, reason: String?, nowMillis: Long, userId: String?): V2NewCommand {
        require(stored.voidable) {
            when {
                stored.invoiced -> "an invoiced charge is credited, not voided"
                stored.requiresAction -> "the customer is completing this card charge"
                else -> "a ${stored.status} charge cannot be voided"
            }
        }
        return RentalPostChargesVoid(id = stored.id, reason = reason.clean()).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    "post_charges", stored.id,
                    mapOf(
                        "status" to PostCharge.STATUS_VOIDED, "voided_at" to nowMillis, "voided_by" to userId,
                        "void_reason" to reason.clean(),
                    ) + updated(nowMillis),
                ),
            ),
        )
    }

    /**
     * A manual price line. A discount always reduces the order (the server stores it negative);
     * an adjustment keeps its sign; the other kinds are positive charges.
     */
    fun priceAdjustment(id: String, draft: PriceAdjustmentDraft, order: RentalOrder, lineNo: Long, nowMillis: Long, userId: String?): V2NewCommand {
        require(order.id == draft.orderId) { "the line is for another order" }
        require(order.status !in setOf("closed", "cancelled", "no_show")) { "a ${order.status} order cannot change" }
        val kind = RentalLineKind.entries.firstOrNull { it.wire == draft.kind && it.wire in PriceAdjustmentDraft.KINDS }
            ?: throw IllegalArgumentException("${draft.kind} is not a manual price line")
        val description = requireNotNull(draft.description.clean()) { "the line needs a description" }
        val reason = requireNotNull(draft.reason.clean()) { "a manual price needs a reason" }
        require(draft.amountMinor != 0L) { "the amount cannot be zero" }
        require(kind == RentalLineKind.ADJUSTMENT || kind == RentalLineKind.DISCOUNT || draft.amountMinor > 0) { "a charge must be positive" }
        val unit = when (kind) {
            RentalLineKind.DISCOUNT -> -kotlin.math.abs(draft.amountMinor)
            else -> draft.amountMinor
        }
        val line = RentalOrderLineIn(id = id, kind = kind, description = description, unitPriceMinor = unit, overrideReason = reason)
        return RentalRentalOrderLinesCreate(id = id, orderId = order.id, line = line).toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    "rental_order_lines", id,
                    mapOf(
                        "order_id" to order.id, "line_no" to lineNo, "kind" to kind.wire, "description" to description,
                        "qty_micros" to ONE_MICROS, "unit_price_minor" to unit, "amount_minor" to unit, "discount_minor" to 0L,
                        "taxable" to 1L, "tax_snapshot" to "{}", "tax_minor" to 0L, "status" to "open", "assign_unit_later" to 0L,
                        "override_reason" to reason, "price_overridden_by" to userId,
                    ) + created(nowMillis, userId),
                ),
            ),
        )
    }
}
