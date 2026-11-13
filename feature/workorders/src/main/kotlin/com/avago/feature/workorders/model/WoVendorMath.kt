package com.avago.feature.workorders.model

import kotlin.math.abs
import kotlin.math.roundToLong

/** Port of web `vendorMath.ts` (W5): estimate vs actual variance for outsourced WOs. */
object WoVendorMath {

    enum class Level { UNDER, ON, OVER }

    enum class ActualSource { INVOICE, LINES, WO }

    /** [diff] = actual − estimate; [pct] is null when the estimate is 0. */
    data class Variance(val diff: Double, val pct: Double?, val level: Level)

    data class Actual(val amount: Double, val source: ActualSource)

    private fun Double?.finite(): Double? = this?.takeIf { it.isFinite() }

    /** Variance of [actual] against [estimate]; `ON` within ±[tolerancePct]. */
    fun costVariance(estimate: Double?, actual: Double?, tolerancePct: Double = 5.0): Variance? {
        val e = estimate.finite() ?: return null
        val a = actual.finite() ?: return null
        val diff = ((a - e) * 100).roundToLong() / 100.0
        val pct = if (e == 0.0) null else (diff / e * 1000).roundToLong() / 10.0
        val level = when {
            pct == null -> when {
                diff > 0 -> Level.OVER
                diff < 0 -> Level.UNDER
                else -> Level.ON
            }
            abs(pct) <= tolerancePct -> Level.ON
            pct > 0 -> Level.OVER
            else -> Level.UNDER
        }
        return Variance(diff, pct, level)
    }

    /** Best "actual" figure: vendor invoice, else cost-line total (> 0), else WO total. */
    fun actualCost(invoiceTotal: Double?, linesTotal: Double?, woTotal: Double?): Actual? {
        invoiceTotal.finite()?.let { return Actual(it, ActualSource.INVOICE) }
        linesTotal.finite()?.takeIf { it > 0 }?.let { return Actual(it, ActualSource.LINES) }
        woTotal.finite()?.let { return Actual(it, ActualSource.WO) }
        return null
    }
}
