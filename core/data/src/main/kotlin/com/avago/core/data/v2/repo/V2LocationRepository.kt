package com.avago.core.data.v2.repo

import com.avago.core.data.domain.Bin
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationKind
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.microsOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2LocationRepository @Inject constructor(private val store: V2StoreAccess) : LocationRepository {

    override fun observeLocations(includeArchived: Boolean): Flow<List<Location>> =
        store.observe(setOf("locations"), emptyList()) { conn -> locations(conn, includeArchived) }

    override suspend fun location(id: String): Location? = store.read(null) { conn -> locationById(conn, id) }

    override fun observeBins(locationId: String?, includeArchived: Boolean): Flow<List<Bin>> =
        store.observe(setOf("bins", "stock_positions"), emptyList()) { conn -> bins(conn, locationId, includeArchived) }

    override suspend fun bin(id: String): Bin? = store.read(null) { conn ->
        V2OverlayMerge.query(conn, "bins", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()?.let(::bin)
    }

    override suspend fun binByBarcode(barcode: String): Bin? = store.read(null) { conn ->
        V2OverlayMerge.query(conn, "bins", "barcode = ? OR code = ?", listOf(barcode, barcode)) {
            it["barcode"] == barcode || it["code"] == barcode
        }.map(::bin).firstOrNull { !it.archived }
    }

    companion object {
        fun locations(conn: V2Connection, includeArchived: Boolean = false): List<Location> =
            V2OverlayMerge.query(
                conn, "locations",
                where = if (includeArchived) null else "archived_at IS NULL",
            ) { includeArchived || it["archived_at"] == null }
                .map(::location)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Location::name).thenBy(Location::id))

        fun locationById(conn: V2Connection, id: String): Location? =
            V2OverlayMerge.query(conn, "locations", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()?.let(::location)

        fun bins(conn: V2Connection, locationId: String?, includeArchived: Boolean = false): List<Bin> {
            val clauses = buildList {
                if (locationId != null) add("location_id = ?")
                if (!includeArchived) add("archived_at IS NULL")
            }
            return V2OverlayMerge.query(
                conn, "bins",
                where = clauses.joinToString(" AND ").ifEmpty { null },
                args = listOfNotNull(locationId),
            ) { (locationId == null || it["location_id"] == locationId) && (includeArchived || it["archived_at"] == null) }
                .map(::bin)
                .let { list ->
                    val onHand = V2OverlayMerge.query(conn, "stock_positions", "bin_id IS NOT NULL") { it["bin_id"] != null }
                        .groupBy { it.str("bin_id") }
                        .mapValues { (_, rows) -> V2Micros.toDecimal(rows.sumOf { it.longOrNull("on_hand_qty_micros") ?: 0L }) }
                    list.map { bin -> onHand[bin.id]?.let { bin.copy(onHand = it) } ?: bin }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Bin::code).thenBy(Bin::id))
        }

        fun location(r: V2Row) = Location(
            id = r.str("id"),
            name = r.str("name"),
            code = r.strOrNull("code"),
            kind = LocationKind.fromWire(r.strOrNull("kind")),
            parentId = r.strOrNull("parent_location_id"),
            addressLine1 = r.strOrNull("address_line1"),
            addressLine2 = r.strOrNull("address_line2"),
            locality = r.strOrNull("locality"),
            region = r.strOrNull("region"),
            postalCode = r.strOrNull("postal_code"),
            countryCode = r.strOrNull("country_code"),
            latitude = r.strOrNull("latitude")?.toDoubleOrNull(),
            longitude = r.strOrNull("longitude")?.toDoubleOrNull(),
            timezone = r.strOrNull("timezone"),
            isPrimary = r.bool("is_primary"),
            archived = r.longOrNull("archived_at") != null,
            notes = r.strOrNull("notes"),
            version = r.longOrNull("version") ?: 0L,
        )

        fun bin(r: V2Row) = Bin(
            id = r.str("id"),
            locationId = r.str("location_id"),
            code = r.str("code"),
            name = r.strOrNull("name"),
            barcode = r.strOrNull("barcode"),
            kind = r.strOrNull("bin_kind"),
            capacity = r.microsOrNull("capacity_qty_micros"),
            archived = r.longOrNull("archived_at") != null,
            version = r.longOrNull("version") ?: 0L,
        )
    }
}
