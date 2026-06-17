package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Change 58: LocationResponse handles both flat address fields and a nested "address" object.
 *
 * Flat form (legacy):
 *   { "address": "123 Main St", "city": "...", "state": "...", "postal_code": "...", "country": "..." }
 *
 * Nested form (new):
 *   { "address": { "address_line1": "123 Main St", "city": "...", "state": "...",
 *                  "zip": "...", "postal_code": "...", "country": "..." } }
 *
 * The "address" key is either a String or an Object in JSON. We capture it as a raw
 * [JsonElement] (addressRaw) and resolve the effective fields via computed properties.
 */
@Serializable
data class LocationAddressObject(
    val address_line1: String? = null,
    val address_line2: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    // Some API versions send "zip" instead of "postal_code"
    val zip: String? = null,
    val country: String? = null,
) {
    val effectivePostalCode: String? get() = postalCode ?: zip
}

@Serializable
data class LocationResponse(
    val location_id: String,
    val account_id: String,
    val name: String,
    val short_code: String? = null,
    // Raw JSON for "address" — may be a String (legacy) or an Object (new form).
    @SerialName("address") val addressRaw: JsonElement? = null,
    // Flat address fields (present in legacy form alongside a flat address string)
    val city: String? = null,
    val state: String? = null,
    val postal_code: String? = null,
    val country: String? = null,
    val parent_location_id: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val is_primary: Boolean = false,
    val archived: Boolean = false,
    val created_at: String = "",
    val updated_at: String = "",
    val server_version: Long = 0,
) {
    /**
     * Returns a [LocationAddressObject] parsed from [addressRaw]:
     * - If addressRaw is a JsonObject, maps its fields directly.
     * - If addressRaw is a JsonPrimitive string, uses the string as address_line1.
     * - Otherwise returns null.
     */
    @Transient
    val addressObject: LocationAddressObject? = when {
        addressRaw is JsonObject -> LocationAddressObject(
            address_line1 = addressRaw["address_line1"]?.jsonPrimitive?.content,
            address_line2 = addressRaw["address_line2"]?.jsonPrimitive?.content,
            city = addressRaw["city"]?.jsonPrimitive?.content,
            state = addressRaw["state"]?.jsonPrimitive?.content,
            postalCode = addressRaw["postal_code"]?.jsonPrimitive?.content,
            zip = addressRaw["zip"]?.jsonPrimitive?.content,
            country = addressRaw["country"]?.jsonPrimitive?.content,
        )
        addressRaw is JsonPrimitive -> LocationAddressObject(
            address_line1 = addressRaw.content.takeIf { it.isNotEmpty() },
        )
        else -> null
    }

    /** Resolved address line 1 — prefers nested object, falls back to flat string form. */
    @Transient
    val effectiveAddressLine1: String? = addressObject?.address_line1

    /** Resolved city — prefers nested object, falls back to flat [city]. */
    @Transient
    val effectiveCity: String? = addressObject?.city ?: city

    /** Resolved state — prefers nested object, falls back to flat [state]. */
    @Transient
    val effectiveState: String? = addressObject?.state ?: state

    /** Resolved postal code — prefers nested object (zip or postal_code), falls back to flat [postal_code]. */
    @Transient
    val effectivePostalCode: String? = addressObject?.effectivePostalCode ?: postal_code

    /** Resolved country — prefers nested object, falls back to flat [country]. */
    @Transient
    val effectiveCountry: String? = addressObject?.country ?: country
}
