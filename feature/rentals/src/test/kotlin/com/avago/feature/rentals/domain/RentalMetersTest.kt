package com.avago.feature.rentals.domain

import com.avago.core.data.domain.AssetMeter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RentalMetersTest {
    private fun meter(kind: String, default: Boolean = false) =
        AssetMeter(id = kind, assetId = "A", kind = kind, unit = "km", isDefault = default, lastValue = null, lastReadAt = null)

    @Test
    fun `only rental meter kinds the unit lacks are offered`() {
        assertEquals(listOf("hours", "distance"), RentalMeters.missing(emptyList()))
        // An asset-side odometer is another kind: hand-over readings would not land on it.
        assertEquals(listOf("hours", "distance"), RentalMeters.missing(listOf(meter("odometer"))))
        assertEquals(listOf("distance"), RentalMeters.missing(listOf(meter("hours"))))
        assertEquals(emptyList<String>(), RentalMeters.missing(listOf(meter("distance"), meter("hours"))))
    }

    @Test
    fun `new meter unit follows the kind and the distance setting`() {
        assertEquals("hours", RentalMeters.unitFor("hours", "mi"))
        assertEquals("mi", RentalMeters.unitFor("distance", "mi"))
        assertEquals("km", RentalMeters.unitFor("distance", "furlong"))
    }

    @Test
    fun `default meter is listed first`() {
        val ordered = RentalMeters.ordered(listOf(meter("hours"), meter("odometer"), meter("distance", default = true)))
        assertEquals(listOf("distance", "hours", "odometer"), ordered.map { it.kind })
    }
}
