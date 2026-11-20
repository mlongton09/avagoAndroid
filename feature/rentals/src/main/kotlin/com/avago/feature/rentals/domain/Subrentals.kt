package com.avago.feature.rentals.domain

import com.avago.core.data.domain.Subrental

/** Sub-rentals (equipment hired in from a vendor) as they show on the order's lines. */
object Subrentals {
    /** Hired in or on its way; returned and cancelled sub-rentals no longer cover a line. */
    private val ACTIVE = setOf("planned", "ordered", "received")

    /** Lines covered by an active sub-rental: linked to the line, or the line's unit is the sub-rented one. */
    fun lineIds(subrentals: List<Subrental>, lines: List<LineView>): Set<String> {
        val active = subrentals.filter { it.status in ACTIVE }
        val byLine = active.mapNotNullTo(HashSet()) { it.orderLineId }
        val byAsset = active.mapNotNullTo(HashSet()) { it.assetId }
        return lines.filter { it.id in byLine || (it.assetId != null && it.assetId in byAsset) }.mapTo(HashSet()) { it.id }
    }
}
