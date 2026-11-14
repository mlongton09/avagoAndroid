package com.avago.feature.rentalsops.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Rentals P2 local store — a small per-account Room database (accounts/{id}/rentalops.db)
 * instead of new tables in AvagoDatabase.
 *
 * Why separate: P1 (feature/rentals) is adding its rental tables to AvagoDatabase with a new
 * schema version on an unmerged branch. Adding P2 tables there too would collide on the
 * version number and on MigrationRentals. Everything here is a re-pullable cache of server
 * feeds (plus local proof media), so a destructive fallback is safe; the durable outbox stays
 * in the shared `sync_queue` table (AvagoDatabase) and is drained by SyncEngine through
 * [com.avago.core.sync.ExternalOutbox].
 *
 * TODO(P1 reconcile): once P1's rental tables exist, the fleet/order-line/allocation feeds
 * cached here duplicate P1's cache and should be read from P1's DAOs instead.
 */

@Entity(tableName = "rentalops_records", primaryKeys = ["entity", "id"], indices = [Index("entity")])
data class RecordEntity(
    val entity: String,
    val id: String,
    /** Verbatim server row. */
    val json: String,
    val seq: Long?,
    val deleted: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "rentalops_cursors")
data class CursorEntity(
    @PrimaryKey val entity: String,
    val cursor: String?,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long?,
    /** Server answered 404/405/501 for this feed (stage not deployed): keep last-known-good cache. */
    val unsupported: Boolean = false,
)

/** Proof-of-delivery media captured offline; uploaded before the task event that cites it. */
@Entity(tableName = "rentalops_media", indices = [Index("task_id"), Index("event_queue_id")])
data class MediaEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    /** Queue row of the task event this media belongs to (null while still a draft). */
    @ColumnInfo(name = "event_queue_id") val eventQueueId: String?,
    /** `photo` or `signature`. */
    val kind: String,
    @ColumnInfo(name = "local_path") val localPath: String,
    val uploaded: Boolean = false,
    /** Server `file_upload_id` once uploaded + finalized (kept so retries never re-upload). */
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface RentalOpsDao {
    @Query("SELECT * FROM rentalops_records WHERE entity = :entity AND deleted = 0")
    fun observe(entity: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM rentalops_records WHERE entity = :entity AND id = :id")
    fun observeOne(entity: String, id: String): Flow<RecordEntity?>

    @Query("SELECT * FROM rentalops_records WHERE entity = :entity AND deleted = 0")
    suspend fun all(entity: String): List<RecordEntity>

    @Query("SELECT * FROM rentalops_records WHERE entity = :entity AND id = :id")
    suspend fun get(entity: String, id: String): RecordEntity?

    @Upsert
    suspend fun upsertAll(rows: List<RecordEntity>)

    @Query("DELETE FROM rentalops_records WHERE entity = :entity")
    suspend fun clearEntity(entity: String)

    @Query("DELETE FROM rentalops_records WHERE entity = :entity AND id = :id")
    suspend fun deleteRecord(entity: String, id: String)

    /** Atomically swaps a snapshot entity (observers never see the empty in-between state). */
    @Transaction
    suspend fun replaceEntity(entity: String, rows: List<RecordEntity>) {
        clearEntity(entity)
        if (rows.isNotEmpty()) upsertAll(rows)
    }

    @Query("SELECT * FROM rentalops_cursors WHERE entity = :entity")
    suspend fun cursor(entity: String): CursorEntity?

    @Upsert
    suspend fun upsertCursor(c: CursorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(m: MediaEntity)

    @Query("SELECT * FROM rentalops_media WHERE task_id = :taskId ORDER BY created_at")
    fun observeMedia(taskId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM rentalops_media WHERE event_queue_id = :queueId ORDER BY created_at")
    suspend fun mediaForEvent(queueId: String): List<MediaEntity>

    @Query("SELECT * FROM rentalops_media WHERE task_id = :taskId AND event_queue_id IS NULL ORDER BY created_at")
    suspend fun draftMedia(taskId: String): List<MediaEntity>

    @Query("UPDATE rentalops_media SET event_queue_id = :queueId WHERE task_id = :taskId AND event_queue_id IS NULL")
    suspend fun attachDrafts(taskId: String, queueId: String)

    @Query("UPDATE rentalops_media SET uploaded = 1, remote_id = :remoteId WHERE id = :id")
    suspend fun markUploaded(id: String, remoteId: String)

    @Query("SELECT * FROM rentalops_media WHERE id = :id")
    suspend fun media(id: String): MediaEntity?

    @Query("DELETE FROM rentalops_media WHERE id = :id")
    suspend fun deleteMedia(id: String)
}

@Database(
    entities = [RecordEntity::class, CursorEntity::class, MediaEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RentalOpsDatabase : RoomDatabase() {
    abstract fun dao(): RentalOpsDao
}

@Singleton
class RentalOpsDatabaseFactory @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val open = mutableMapOf<String, RentalOpsDatabase>()
    private val lock = Mutex()

    suspend fun get(accountId: String): RentalOpsDatabase = lock.withLock {
        open.getOrPut(accountId) {
            val dir = File(ctx.filesDir, "accounts/$accountId").apply { mkdirs() }
            Room.databaseBuilder(ctx, RentalOpsDatabase::class.java, File(dir, "rentalops.db").path)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }

    suspend fun dao(accountId: String): RentalOpsDao = get(accountId).dao()

    /** Directory for proof media of [accountId] (inside app-private files; FileProvider `files` path). */
    fun mediaDir(accountId: String): File =
        File(ctx.filesDir, "accounts/$accountId/rentalops_media").apply { mkdirs() }
}

/**
 * Entities cached by this module (P2-only data). Fleet, orders, lines, allocations and
 * customers are NOT duplicated here: they live in P1's shared `rental_records` store
 * (core/data RentalDao), filled by feature:rentals' sync.
 */
object RentalOpsEntities {
    const val TASK = "rental_delivery_task"
    const val TRANSFER = "rental_transfer"
    /** Snapshot of stage C `GET …/unassigned-lines` (replaced on every pull). */
    const val UNASSIGNED_LINE = "rentalops_unassigned_line"

    /** Primary-key field per cached entity. */
    fun pkField(entity: String): String = when (entity) {
        UNASSIGNED_LINE -> "line_id"
        else -> "id"
    }
}
