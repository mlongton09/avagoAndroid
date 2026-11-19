package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** A site, building, area, yard or transit location (v2 `locations`). */
data class Location(
    val id: String,
    val name: String,
    val code: String?,
    val kind: LocationKind,
    val parentId: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val locality: String?,
    val region: String?,
    val postalCode: String?,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    val isPrimary: Boolean,
    val archived: Boolean,
    val notes: String?,
    val version: Long,
) {
    /** One-line address for list rows ("12 Main St, Springfield"). */
    val addressSummary: String?
        get() = listOfNotNull(addressLine1, locality, region).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
}

enum class LocationKind(val wire: String) {
    SITE("site"), BUILDING("building"), AREA("area"), YARD("yard"), TRANSIT("transit"), POSTAL("postal");

    companion object {
        fun fromWire(raw: String?): LocationKind = entries.firstOrNull { it.wire == raw } ?: SITE
    }
}

/** A storage bin inside a location (v2 `bins`). */
data class Bin(
    val id: String,
    val locationId: String,
    val code: String,
    val name: String?,
    val barcode: String?,
    val kind: String?,
    val capacity: BigDecimal?,
    val archived: Boolean,
    val version: Long,
    /** Units on hand across every product stocked in the bin (`stock_positions`). */
    val onHand: BigDecimal = BigDecimal.ZERO,
) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: code
}

/** Reads of locations and bins for the signed-in account. */
interface LocationRepository {
    /** Active (non-archived) locations by name. */
    fun observeLocations(includeArchived: Boolean = false): Flow<List<Location>>

    suspend fun location(id: String): Location?

    /** Active bins, optionally of one location, by code. */
    fun observeBins(locationId: String? = null, includeArchived: Boolean = false): Flow<List<Bin>>

    suspend fun bin(id: String): Bin?

    suspend fun binByBarcode(barcode: String): Bin?
}
