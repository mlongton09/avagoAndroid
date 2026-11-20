package com.avago.feature.rentals.domain

import com.avago.core.data.domain.AssetMeter

/**
 * Meters a rental unit can track (`maint.asset_meters`). Hand-over readings are stored on the
 * asset meter whose kind is the rental class meter kind (`hours` or `distance`, server
 * `record_meter_reading`), so the rental tab offers exactly those kinds.
 */
object RentalMeters {
    const val HOURS = "hours"
    const val DISTANCE = "distance"
    val KINDS = listOf(HOURS, DISTANCE)
    val DISTANCE_UNITS = listOf("km", "mi")

    /** Rental meter kinds the unit does not track yet. */
    fun missing(meters: List<AssetMeter>): List<String> {
        val tracked = meters.map { it.kind }.toSet()
        return KINDS.filterNot { it in tracked }
    }

    /** Recorded unit of a new [kind] meter: hours, or the chosen (else the account's) distance unit. */
    fun unitFor(kind: String, distanceUnit: String): String = when (kind) {
        HOURS -> "hours"
        else -> distanceUnit.takeIf { it in DISTANCE_UNITS } ?: "km"
    }

    /** Default meter first, then by kind. */
    fun ordered(meters: List<AssetMeter>): List<AssetMeter> =
        meters.sortedWith(compareByDescending<AssetMeter> { it.isDefault }.thenBy { it.kind })
}
