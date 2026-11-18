package com.avago.core.data.v2

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * Single-row marker identifying a file as a v2 store: schema version, contract version and
 * creation time. Generated contract tables (see [V2Contract]) join this entity later.
 */
@Entity(tableName = "v2_store_marker")
data class V2StoreMarkerEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "contract_version") val contractVersion: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
interface V2StoreMarkerDao {
    @Query("SELECT * FROM v2_store_marker WHERE id = 1")
    suspend fun get(): V2StoreMarkerEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(marker: V2StoreMarkerEntity)
}

/**
 * The per-user/account v2 business database (`accounts/<account>/<user>/v2/avago_v2.db`).
 *
 * Scaffold only: the entity set is the [V2StoreMarkerEntity] placeholder until the contract
 * generator emits the 75 business tables (plan §15.6). Opened exclusively through
 * [AvagoV2DatabaseFactory], which refuses unless v2 is enabled and the legacy reset completed.
 */
@Database(
    entities = [V2StoreMarkerEntity::class],
    version = V2Contract.V2_DB_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AvagoV2Database : RoomDatabase() {
    abstract fun markerDao(): V2StoreMarkerDao
}
