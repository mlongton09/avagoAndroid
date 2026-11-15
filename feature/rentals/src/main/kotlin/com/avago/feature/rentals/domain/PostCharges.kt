package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.long
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import com.avago.core.network.json.strs
import com.avago.core.network.json.int
import kotlinx.serialization.json.JsonObject
import java.math.RoundingMode
import java.time.Instant

// Feature I post-rental charges (tolls, tickets, citations) on an order:
// AvagoSvc `routes/rentals_api/post_charges.rs`.

data class PostCharge(
    val id: String,
    /** toll | ticket | citation */
    val kind: String,
    val amount: Money,
    val adminFee: Money,
    val reference: String?,
    val authority: String?,
    val description: String?,
    /** billed | charged | requires_action | charge_failed | void */
    val status: String,
    val incurredAt: String?,
    val evidenceName: String?,
    val chargeError: String?,
    val voidReason: String?,
    /** Hosted payment page when the card needs 3-D Secure; only on charge responses. */
    val payUrl: String?,
) {
    val total: Money get() = Money(amount.minor + adminFee.minor, amount.currency)
    val canCharge: Boolean get() = status in CHARGEABLE
    val canVoid: Boolean get() = status != "charged" && status != "void"

    companion object {
        private val CHARGEABLE = setOf("billed", "charge_failed", "requires_action")

        fun from(root: JsonObject): PostCharge? {
            val o = root.obj("data") ?: root
            val id = o.str("id") ?: return null
            val cur = o.str("currency")?.trim()?.ifEmpty { null } ?: "USD"
            return PostCharge(
                id = id,
                kind = o.str("kind") ?: "toll",
                amount = Money(o.long("amount_minor") ?: 0, cur),
                adminFee = Money(o.long("admin_fee_minor") ?: 0, cur),
                reference = o.str("reference"),
                authority = o.str("authority"),
                description = o.str("description"),
                status = o.str("status") ?: "billed",
                incurredAt = o.str("incurred_at"),
                evidenceName = o.str("evidence_name"),
                chargeError = o.str("charge_error"),
                voidReason = o.str("void_reason"),
                payUrl = o.str("pay_url"),
            )
        }
    }
}

data class PostChargeList(val items: List<PostCharge>, val windowDays: Int?, val kinds: List<String>) {
    companion object {
        fun from(root: JsonObject): PostChargeList {
            val o = root.obj("data") ?: root
            val items = o.objs("items").mapNotNull(PostCharge::from)
            return PostChargeList(
                items = items,
                windowDays = o.int("window_days"),
                kinds = o.strs("kinds").ifEmpty { PostCharges.KINDS },
            )
        }
    }
}

object PostCharges {
    val KINDS = listOf("toll", "ticket", "citation")

    /** Order statuses the server accepts post-charges for. */
    private val ORDER_STATUSES = setOf(
        OrderStatus.ON_RENT, OrderStatus.OFF_RENT_REQUESTED, OrderStatus.RETURNED, OrderStatus.CLOSED,
    )

    fun allowedFor(orderStatus: String): Boolean = orderStatus in ORDER_STATUSES

    /** "12.50" → 1250; null when blank, negative or more than 2 decimals. */
    fun parseMinor(s: String): Long? {
        val t = s.trim().replace(",", "").replace(" ", "")
        if (t.isEmpty()) return null
        val d = t.toBigDecimalOrNull() ?: return null
        if (d.signum() < 0 || d.scale() > 2) return null
        return d.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).toLong()
    }
}

/** Fields of the add-charge dialog. */
data class PostChargeForm(
    val kind: String = "toll",
    val amount: String = "",
    val adminFee: String = "",
    val incurredAt: Instant,
    val reference: String = "",
    val authority: String = "",
    val description: String = "",
    val evidenceFileId: String? = null,
    val evidenceName: String? = null,
    val chargeNow: Boolean = false,
) {
    /** Field → error code (`required`, `amount`, `future`, `evidence`). */
    fun errors(now: Instant): Map<String, String> = buildMap {
        when (val a = PostCharges.parseMinor(amount)) {
            null -> put("amount", if (amount.isBlank()) "required" else "amount")
            else -> if (a <= 0) put("amount", "amount")
        }
        if (adminFee.isNotBlank() && PostCharges.parseMinor(adminFee) == null) put("admin_fee", "amount")
        if (incurredAt.isAfter(now)) put("incurred_at", "future")
        if (evidenceFileId == null) put("evidence", "evidence")
    }

    fun toBody(clientUuid: String): JsonObject = jsonBody(
        "kind" to kind,
        "amount_minor" to PostCharges.parseMinor(amount),
        "admin_fee_minor" to PostCharges.parseMinor(adminFee),
        "incurred_at" to incurredAt.toString(),
        "reference" to reference.trim().ifEmpty { null },
        "authority" to authority.trim().ifEmpty { null },
        "description" to description.trim().ifEmpty { null },
        "evidence_file_id" to evidenceFileId,
        "client_uuid" to clientUuid,
        "charge_now" to chargeNow,
    )
}