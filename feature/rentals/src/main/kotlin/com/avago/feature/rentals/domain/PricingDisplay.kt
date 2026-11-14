package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * Price breakdown shown on Quick-book / Order (spec §8). [confirmed] is true only for an
 * answer from `POST /rentals/pricing/preview` (or a synced order); offline estimates from
 * cached rate cards are always "not confirmed" and exclude tax.
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

        fun trimQty(q: String): String =
            q.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: q

        /**
         * Offline estimate from a cached rate card (`rental_rate_card` row): the cheapest of
         * day / week / 4-week / month combinations over the rounded-up rental days, never
         * below `min_charge_minor`. Hour rates apply to rentals under one day.
         */
        fun estimate(card: JsonObject, label: String, start: Instant, end: Instant, qty: Int = 1): PricingDisplay? {
            val cur = card.str("currency")?.trim() ?: return null
            if (!end.isAfter(start)) return null
            val unit = estimateUnitMinor(card, Duration.between(start, end)) ?: return null
            val n = qty.coerceAtLeast(1)
            val amount = Money(Math.multiplyExact(unit, n.toLong()), cur)
            val days = daysOf(Duration.between(start, end))
            val deposit: Money? = null
            return PricingDisplay(
                currency = cur,
                rows = listOf(Row(label, n.toString(), amount, "${days}d")),
                subtotal = amount,
                discount = Money.zero(cur),
                tax = null,
                total = amount,
                deposit = deposit,
                confirmed = false,
            )
        }

        fun daysOf(d: Duration): Long {
            val mins = d.toMinutes()
            return max(1L, (mins + 24 * 60 - 1) / (24 * 60))
        }

        /** Per-unit price in minor units for [d]; null when the card has no usable rate. */
        fun estimateUnitMinor(card: JsonObject, d: Duration): Long? {
            val hourly = card.long("hourly_minor")
            val r4h = card.long("rate_4h_minor")
            val r8h = card.long("rate_8h_minor")
            val day = card.long("rate_day_minor")
            val week = card.long("rate_week_minor")
            val fourWeek = card.long("rate_4week_minor")
            val month = card.long("rate_month_minor")
            val minCharge = card.long("min_charge_minor") ?: 0L
            val candidates = mutableListOf<Long>()
            val hours = max(1L, (d.toMinutes() + 59) / 60)
            if (hours < 24) {
                hourly?.let { candidates += it * hours }
                if (hours <= 4) r4h?.let { candidates += it }
                if (hours <= 8) r8h?.let { candidates += it }
            }
            val days = daysOf(d)
            // Greedy combinations: big periods first, remainder with the next smaller unit or days.
            fun combo(periodDays: Long, periodPrice: Long?): Long? {
                periodPrice ?: return null
                val full = days / periodDays
                val rem = days % periodDays
                val remCost = when {
                    rem == 0L -> 0L
                    day != null -> minOf(rem * day, periodPrice)
                    else -> periodPrice
                }
                return full * periodPrice + remCost
            }
            day?.let { candidates += days * it }
            combo(7, week)?.let { candidates += it }
            combo(28, fourWeek)?.let { candidates += it }
            combo(30, month)?.let { candidates += it }
            val best = candidates.minOrNull() ?: return null
            return max(best, minCharge)
        }
    }
}
