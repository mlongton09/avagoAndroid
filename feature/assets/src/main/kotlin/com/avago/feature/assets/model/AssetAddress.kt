package com.avago.feature.assets.model

import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.Location

/**
 * Where an asset is: its location's postal address and coordinates (`locations`), with the
 * asset's own `street_address` / `latitude` / `longitude` attributes as a fallback for assets
 * without a location or locations without an address.
 */
data class AssetAddress(
    val site: String?,
    val lines: List<String>,
    val latitude: Double?,
    val longitude: Double?,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    companion object {
        fun of(asset: Asset, location: Location?, attributes: Map<String, String>): AssetAddress? {
            val site = (location?.name ?: asset.locationName)?.takeIf { it.isNotBlank() }
            val fromLocation = location?.let {
                listOfNotNull(
                    it.addressLine1,
                    it.addressLine2,
                    listOfNotNull(it.postalCode, it.locality).filter(String::isNotBlank).joinToString(" "),
                    it.region,
                    it.countryCode,
                ).map(String::trim).filter(String::isNotEmpty)
            }.orEmpty()
            val lines = fromLocation.ifEmpty {
                listOfNotNull(
                    attributes["street_address"],
                    listOfNotNull(attributes["city"], attributes["state"], attributes["zip_code"])
                        .filter(String::isNotBlank).joinToString(", "),
                    attributes["country"],
                ).map(String::trim).filter(String::isNotEmpty)
            }
            val lat = location?.latitude ?: attributes["latitude"]?.toDoubleOrNull()
            val lng = location?.longitude ?: attributes["longitude"]?.toDoubleOrNull()
            val coords = lat != null && lng != null
            if (site == null && lines.isEmpty() && !coords) return null
            return AssetAddress(site, lines, lat.takeIf { coords }, lng.takeIf { coords })
        }
    }
}
