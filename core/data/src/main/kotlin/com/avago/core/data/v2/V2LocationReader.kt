package com.avago.core.data.v2

import com.avago.core.data.db.entity.BinEntity
import com.avago.core.data.db.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Locations and bins from the v2 store, mapped onto the legacy read models so existing screens
 * work unchanged (iOS parity: `V2LocationReader`). Archived rows are returned with
 * `archived = true` / `active = false`, as the legacy DAOs did.
 *
 * v2 → legacy: `code` → `shortCode`, `address_line1` → `address`, `locality`/`region`/
 * `country_code` → `city`/`state`/`country`, `latitude`/`longitude` (decimal text) → Double,
 * `version` → `serverVersion`; bins: `capacity_qty_micros` / 1e6 → `capacity`, `bin_kind` →
 * `binType`, `name` falls back to `code`. Columns v2 dropped (bin aisle/shelf/slot, current count)
 * are null.
 */
class V2LocationReader(private val db: V2Database) {

    fun observeLocations(accountId: String): Flow<List<LocationEntity>> =
        db.observe(setOf("locations")) { conn -> conn.query(LOCATIONS_SQL + " ORDER BY name, id").map { location(it, accountId) } }

    suspend fun locations(accountId: String): List<LocationEntity> =
        db.read { conn -> conn.query(LOCATIONS_SQL + " ORDER BY name, id").map { location(it, accountId) } }

    suspend fun location(accountId: String, id: String): LocationEntity? =
        db.read { conn -> conn.query("$LOCATIONS_SQL WHERE id = ?", listOf(id)).firstOrNull()?.let { location(it, accountId) } }

    fun observeBins(): Flow<List<BinEntity>> =
        db.observe(setOf("bins")) { conn -> conn.query(BINS_SQL + " ORDER BY location_id, code, id").map(::bin) }

    suspend fun bins(locationId: String? = null): List<BinEntity> = db.read { conn ->
        if (locationId == null) {
            conn.query(BINS_SQL + " ORDER BY location_id, code, id").map(::bin)
        } else {
            conn.query("$BINS_SQL WHERE location_id = ? ORDER BY code, id", listOf(locationId)).map(::bin)
        }
    }

    suspend fun bin(id: String): BinEntity? =
        db.read { conn -> conn.query("$BINS_SQL WHERE id = ?", listOf(id)).firstOrNull()?.let(::bin) }

    companion object {
        private const val LOCATIONS_SQL =
            "SELECT id, created_at, updated_at, version, archived_at, parent_location_id, code, name, address_line1, " +
                "locality, region, postal_code, country_code, latitude, longitude, timezone, is_primary FROM locations"
        private const val BINS_SQL =
            "SELECT id, created_at, updated_at, created_by, updated_by, version, archived_at, location_id, code, name, " +
                "barcode, bin_kind, capacity_qty_micros FROM bins"

        internal fun location(r: V2Row, accountId: String) = LocationEntity(
            locationId = r["id"] as String,
            accountId = accountId,
            name = r["name"] as String,
            shortCode = r["code"] as String?,
            address = r["address_line1"] as String?,
            city = r["locality"] as String?,
            state = r["region"] as String?,
            postalCode = r["postal_code"] as String?,
            country = r["country_code"] as String?,
            latitude = (r["latitude"] as String?)?.toDoubleOrNull(),
            longitude = (r["longitude"] as String?)?.toDoubleOrNull(),
            timezone = r["timezone"] as String?,
            isPrimary = (r["is_primary"] as Number?)?.toLong() == 1L,
            archived = r["archived_at"] != null,
            createdAt = (r["created_at"] as Number).toLong(),
            updatedAt = (r["updated_at"] as Number).toLong(),
            deletedAt = null,
            serverVersion = (r["version"] as Number).toLong(),
            seq = null,
            addressStreet = r["address_line1"] as String?,
            parentLocationId = r["parent_location_id"] as String?,
        )

        internal fun bin(r: V2Row) = BinEntity(
            binId = r["id"] as String,
            locationId = r["location_id"] as String,
            name = (r["name"] as String?) ?: (r["code"] as String),
            code = r["code"] as String?,
            barcode = r["barcode"] as String?,
            aisle = null,
            shelf = null,
            slot = null,
            active = r["archived_at"] == null,
            createdAt = (r["created_at"] as Number).toLong(),
            updatedAt = (r["updated_at"] as Number).toLong(),
            serverVersion = (r["version"] as Number).toLong(),
            capacity = (r["capacity_qty_micros"] as Number?)?.toLong()?.let { it / 1_000_000 },
            currentCount = null,
            binType = r["bin_kind"] as String?,
            createdById = r["created_by"] as String?,
            updatedById = r["updated_by"] as String?,
        )
    }
}
