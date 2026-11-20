package com.avago.feature.rentals.domain

import com.avago.core.data.domain.PriceAdjustmentDraft
import com.avago.core.data.rental.MoneyFormatter

/** Manual price lines on an order (`rental.rental_order_lines.create` with a unit price and a reason). */
object PriceAdjustments {
    /** `rental.quote`; adjustment kinds do not need `rental.price_override`. */
    const val PERMISSION = "rental.quote"

    private val LOCKED = setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED, "no_show")

    fun allowedFor(orderStatus: String): Boolean = orderStatus !in LOCKED

    /**
     * The signed amount in minor units, or null when invalid. Only `adjustment` takes a leading
     * minus (it lowers the price); a discount is entered positive and always reduces the total;
     * every other kind is a positive charge.
     */
    fun amountMinor(kind: String, input: String, currency: String): Long? {
        val text = input.trim()
        val negative = text.startsWith("-")
        if (negative && kind != KIND_ADJUSTMENT) return null
        val minor = MoneyFormatter.parsePlainMinor(text.removePrefix("-").trim(), currency) ?: return null
        if (minor == 0L) return null
        return if (negative) -minor else minor
    }

    /** The draft, or null while any field is invalid. */
    fun draft(orderId: String, kind: String, description: String, amount: String, reason: String, currency: String): PriceAdjustmentDraft? {
        if (kind !in PriceAdjustmentDraft.KINDS) return null
        val minor = amountMinor(kind, amount, currency) ?: return null
        val d = description.trim().takeIf { it.isNotEmpty() } ?: return null
        val r = reason.trim().takeIf { it.isNotEmpty() } ?: return null
        return PriceAdjustmentDraft(orderId, kind, d, minor, r)
    }

    const val KIND_ADJUSTMENT = "adjustment"
}
