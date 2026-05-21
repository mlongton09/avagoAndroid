package com.avago.feature.inventory.parts

object StockingLevelPolicy {
    fun needsReorder(onHand: Double, minQty: Double, safetyStock: Double): Boolean =
        onHand <= (minQty + safetyStock)

    fun suggestedReplenishmentQty(onHand: Double, minQty: Double, maxQty: Double?): Double =
        (maxQty ?: (minQty * 2)) - onHand

    fun daysOfCover(onHand: Double, dailyRate: Double): Double =
        if (dailyRate <= 0) Double.MAX_VALUE else onHand / dailyRate
}
