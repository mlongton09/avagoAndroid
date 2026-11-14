package com.avago.feature.rentals.domain

import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Per-asset rental profit (§14 asset Rental tab): rental revenue from this unit's order lines
 * (orders not cancelled) minus maintenance/log costs recorded on the asset. Only amounts in
 * [currency] are summed — other currencies are counted in [skipped] rather than converted.
 */
object AssetProfit {

    data class Revenue(val orderStatus: String?, val amount: Money?)
    data class Cost(val amountMajor: Double?, val currency: String?)

    data class Result(val revenue: Money, val costs: Money, val skipped: Int) {
        val profit: Money get() = revenue - costs
        /** Margin in percent (null when there is no revenue). */
        val marginPct: Int? get() = if (revenue.minor == 0L) null else ((profit.minor * 100) / revenue.minor).toInt()
    }

    fun compute(currency: String, revenue: List<Revenue>, costs: List<Cost>): Result {
        val cur = currency.uppercase()
        var skipped = 0
        var rev = Money.zero(cur)
        revenue.filter { it.orderStatus != "cancelled" && it.orderStatus != "draft" }.forEach { r ->
            val a = r.amount ?: return@forEach
            if (a.currency.equals(cur, true)) rev += a else skipped++
        }
        var cost = Money.zero(cur)
        costs.forEach { c ->
            val v = c.amountMajor ?: return@forEach
            if (c.currency == null || c.currency.equals(cur, true)) cost += toMinor(v, cur) else skipped++
        }
        return Result(rev, cost, skipped)
    }

    /** Major-unit double (log cost) → minor units using the currency's fraction digits. */
    fun toMinor(major: Double, currency: String): Money {
        val digits = MoneyFormatter.fractionDigits(currency)
        val minor = BigDecimal.valueOf(major).setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).longValueExact()
        return Money(minor, currency.uppercase())
    }
}
