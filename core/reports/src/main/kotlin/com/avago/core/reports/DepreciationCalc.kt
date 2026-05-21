package com.avago.core.reports

import kotlin.math.pow

object DepreciationCalc {

    /**
     * Straight-line depreciation for [ageYears] of service.
     * Returns the accumulated depreciation (not the NBV).
     */
    fun straightLine(
        cost: Double,
        salvage: Double,
        usefulLifeYears: Int,
        ageYears: Double,
    ): Double {
        if (usefulLifeYears <= 0) return 0.0
        val annualDep = (cost - salvage) / usefulLifeYears
        return (annualDep * ageYears).coerceAtMost(cost - salvage).coerceAtLeast(0.0)
    }

    /**
     * Double-declining balance accumulated depreciation for [ageYears].
     * Switches to straight-line internally once SL gives a higher charge.
     */
    fun doubleDeclining(
        cost: Double,
        usefulLifeYears: Int,
        ageYears: Double,
    ): Double {
        if (usefulLifeYears <= 0) return 0.0
        val rate = 2.0 / usefulLifeYears
        // Accumulated DDB = cost * (1 - (1 - rate)^ageYears)
        val accumulated = cost * (1.0 - (1.0 - rate).pow(ageYears))
        return accumulated.coerceAtMost(cost).coerceAtLeast(0.0)
    }

    /**
     * Units-of-production accumulated depreciation.
     */
    fun unitsOfProduction(
        cost: Double,
        salvage: Double,
        totalUnits: Double,
        unitsUsed: Double,
    ): Double {
        if (totalUnits <= 0.0) return 0.0
        val depPerUnit = (cost - salvage) / totalUnits
        return (depPerUnit * unitsUsed).coerceAtMost(cost - salvage).coerceAtLeast(0.0)
    }

    /**
     * Net book value = cost − accumulated depreciation (floored at 0).
     */
    fun netBookValue(cost: Double, accumulated: Double): Double =
        maxOf(0.0, cost - accumulated)
}
