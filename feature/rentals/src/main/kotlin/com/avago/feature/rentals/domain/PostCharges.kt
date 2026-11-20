package com.avago.feature.rentals.domain

import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.PostCharge
import com.avago.core.data.domain.PostChargeDraft
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.rental.MoneyFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

/** Tolls, tickets and citations billed to an order after the rental (`post_charges`). */
object PostCharges {
    /** Order statuses the server accepts post charges for. */
    private val ORDER_STATUSES = setOf(
        OrderStatus.ON_RENT, OrderStatus.OFF_RENT_REQUESTED, OrderStatus.RETURNED, OrderStatus.CLOSED,
    )

    fun allowedFor(orderStatus: String): Boolean = orderStatus in ORDER_STATUSES

    /**
     * When the order came back (server `post_charges.rs` `returned_at`): the last line check-in,
     * else the order end; null while the order is not returned or closed.
     */
    fun returnedAt(orderStatus: String, lines: List<RentalOrderLine>, orderEnd: Instant?): Instant? {
        if (orderStatus != OrderStatus.RETURNED && orderStatus != OrderStatus.CLOSED) return null
        return lines.mapNotNull { it.actualInAt }.maxOrNull() ?: orderEnd
    }

    /** Charges are accepted up to [windowDays] (owner scope policy) after the return; always before it. */
    fun withinWindow(returnedAt: Instant?, windowDays: Int, now: Instant): Boolean =
        returnedAt == null || !now.isAfter(returnedAt.plus(Duration.ofDays(windowDays.coerceAtLeast(0).toLong())))

    const val PERMISSION_CREATE = "rental.quote"
    const val PERMISSION_CHARGE = "rental.payment_charge"
}

/**
 * Fields of the add-charge dialog; amounts are in the order's [currency]. Saving queues
 * `rental.post_charges.create` ([toDraft]). Charging the saved card at once ([chargeNow]) or
 * billing a closed order is the online REST create instead ([toRestBody], needs a connection).
 */
data class PostChargeForm(
    val currency: String,
    val kind: String = PostCharge.KINDS.first(),
    val amount: String = "",
    val adminFee: String = "",
    val incurredAt: Instant,
    val reference: String = "",
    val authority: String = "",
    val description: String = "",
    val evidence: LocalFileRef? = null,
    val evidenceName: String? = null,
    val chargeNow: Boolean = false,
) {
    /** Field → error code (`required`, `amount`, `future`, `evidence`). */
    fun errors(now: Instant): Map<String, String> = buildMap {
        when (val a = MoneyFormatter.parsePlainMinor(amount, currency)) {
            null -> put("amount", if (amount.isBlank()) "required" else "amount")
            else -> if (a <= 0) put("amount", "amount")
        }
        if (adminFee.isNotBlank()) {
            val fee = MoneyFormatter.parsePlainMinor(adminFee, currency)
            if (fee == null || fee < 0) put("admin_fee", "amount")
        }
        if (incurredAt.isAfter(now)) put("incurred_at", "future")
        if (evidence == null) put("evidence", "evidence")
    }

    private fun amountMinor(): Long = requireNotNull(MoneyFormatter.parsePlainMinor(amount, currency)) { "invalid amount" }

    private fun adminFeeMinor(): Long? = adminFee.takeIf { it.isNotBlank() }?.let { MoneyFormatter.parsePlainMinor(it, currency) }

    /** The queued command's draft (call only when [errors] is empty). */
    fun toDraft(orderId: String, assetId: String?): PostChargeDraft = PostChargeDraft(
        orderId = orderId,
        kind = kind,
        amountMinor = amountMinor(),
        adminFeeMinor = adminFeeMinor(),
        incurredAt = incurredAt,
        evidence = requireNotNull(evidence) { "evidence required" },
        assetId = assetId,
        reference = reference.trim().ifEmpty { null },
        authority = authority.trim().ifEmpty { null },
        description = description.trim().ifEmpty { null },
    )

    /** Whether saving is the online REST create rather than the queued command. */
    fun online(orderStatus: String): Boolean = chargeNow || orderStatus == OrderStatus.CLOSED

    /**
     * `POST /orders/{o}/post-charges` (rental.quote, plus rental.payment_charge with `charge_now`).
     * [clientUuid] stays the same for every retry of this form, so the server replays instead of
     * billing twice; [evidenceFileId] is the uploaded evidence.
     */
    fun toRestBody(clientUuid: String, evidenceFileId: String, assetId: String?): JsonObject = buildJsonObject {
        put("kind", kind)
        put("amount_minor", amountMinor())
        adminFeeMinor()?.let { put("admin_fee_minor", it) }
        put("incurred_at", incurredAt.toString())
        reference.trim().ifEmpty { null }?.let { put("reference", it) }
        authority.trim().ifEmpty { null }?.let { put("authority", it) }
        description.trim().ifEmpty { null }?.let { put("description", it) }
        put("evidence_file_id", evidenceFileId)
        assetId?.let { put("asset_id", it) }
        put("client_uuid", clientUuid)
        put("charge_now", chargeNow)
    }
}
