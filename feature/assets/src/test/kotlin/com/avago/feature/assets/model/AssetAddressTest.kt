package com.avago.feature.assets.model

import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetStatusCode
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AssetAddressTest {
    private val asset = Asset(
        id = "a", name = "Truck", assetTypeCode = "vehicle", make = null, model = null, modelYear = null,
        serialNumber = null, vin = null, tags = emptyList(), locationId = "loc", locationName = "Depot", parentAssetId = null,
        status = AssetStatusCode.ONLINE, statusSince = 0, downtimeKind = null, conditionScore = null, rentable = false,
        avatarColor = null, avatarInitial = null, attributes = "{}", globalUid = "g", version = 1,
    )
    private val location = Location(
        id = "loc", name = "Depot North", code = null, kind = LocationKind.SITE, parentId = null,
        addressLine1 = "1 Main St", addressLine2 = " ", locality = "Springfield", region = "IL", postalCode = "62701",
        countryCode = "US", latitude = 39.8, longitude = -89.6, timezone = null, isPrimary = false, archived = false,
        notes = null, version = 1,
    )

    @Test
    fun `the location supplies the address and coordinates`() {
        val address = AssetAddress.of(asset, location, mapOf("street_address" to "ignored"))!!
        assertEquals("Depot North", address.site)
        assertEquals(listOf("1 Main St", "62701 Springfield", "IL", "US"), address.lines)
        assertEquals(39.8, address.latitude)
    }

    @Test
    fun `attributes fill in for a location without an address`() {
        val bare = location.copy(addressLine1 = null, addressLine2 = null, locality = null, region = null, postalCode = null,
            countryCode = null, latitude = null, longitude = null)
        val address = AssetAddress.of(asset, bare, mapOf("street_address" to "9 Yard Rd", "city" to "Austin", "latitude" to "30.2"))!!
        assertEquals(listOf("9 Yard Rd", "Austin"), address.lines)
        assertNull(address.latitude)
    }

    @Test
    fun `nothing to show yields no card`() {
        assertNull(AssetAddress.of(asset.copy(locationId = null, locationName = null), null, emptyMap()))
    }
}
