package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import com.avago.core.data.rental.RentalPricing
import kotlinx.serialization.json.JsonObject

/**
 * Price breakdown shown on Quick-book / Order (spec §8). [confirmed] is true for an
 * answer from `POST /rentals/pricing/preview` (or a synced order); the offline [estimate] from
 * the synced rate cards is never confirmed and excludes fees, discounts and tax.
 */
data class PricingDisplay(
    val currency: String,
    val rows: List<Row>,
    val subtotal: Money,
    val discount: Money,
    val tax: Money?,
    val total: Money,
    val deposit: Money?,
    val confirmed: Boolean,
    val error: String? = null,
) {
    data class Row(val label: String, val qty: String, val amount: Money, val detail: String? = null)

    companion object {

        /** Builds the display from the server `Priced` object. */
        fun fromServer(priced: JsonObject): PricingDisplay? {
            val cur = priced.str("currency")?.trim() ?: return null
            fun m(k: String) = priced.money(k, cur) ?: Money.zero(cur)
            val rows = priced.objs("lines").map { l ->
                val qty = l.str("qty") ?: "1"
                PricingDisplay.Row(
                    label = l.str("description")?.takeIf { it.isNotBlank() } ?: l.str("kind") ?: "",
                    qty = trimQty(qty),
                    amount = l.money("amount_minor", cur) ?: Money.zero(cur),
                    detail = l.obj("pricing")?.let { p ->
                        val days = p.long("days")
                        if (days != null) "${days}d" else null
                    },
                )
            }
            return PricingDisplay(
                currency = cur,
                rows = rows,
                subtotal = m("subtotal_minor"),
                discount = m("discount_minor"),
                tax = m("tax_minor"),
                total = m("total_minor"),
                deposit = priced.money("deposit_required_minor", cur)?.takeIf { !it.isZero },
                confirmed = true,
            )
        }

        /** The offline estimate of one unit priced by [RentalPricing.priceLine]. */
        fun estimate(breakdown: RentalPricing.Breakdown, currency: String, label: String): PricingDisplay {
            val amount = Money(breakdown.totalMinor, currency)
            return PricingDisplay(
                currency = currency,
                rows = listOf(Row(label, "1", amount, "${breakdown.billedDays}d")),
                subtotal = amount,
                discount = Money.zero(currency),
                tax = null,
                total = amount,
                deposit = null,
                confirmed = false,
            )
        }

        fun trimQty(q: String): String =
            q.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: q
    }
}
