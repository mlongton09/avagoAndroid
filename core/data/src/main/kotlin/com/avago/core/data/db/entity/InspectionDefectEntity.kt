package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local copy of one AvagoSvc `inspection_defects` register row (inspection-v2 contract §3.2).
 *
 * Filled by `DefectRegisterSync.pull` during every sync cycle (the register is not a
 * `/sync/pull` entity, so it is fetched from `GET /defects` and stored as an account-wide
 * snapshot). [status] mirrors `row_json.status` so local optimistic edits (queued in
 * `sync_queue` as `inspection_defect`) can be applied without rewriting the server row.
 */
@Entity(
    tableName = "inspection_defects",
    indices = [Index(value = ["asset_id"])],
)
data class InspectionDefectEntity(
    @PrimaryKey
    @ColumnInfo(name = "defect_id")
    val defectId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "item_key")
    val itemKey: String,

    @ColumnInfo(name = "status")
    val status: String,

    /** Server row as returned by `GET/PATCH /defects` (source of every other field). */
    @ColumnInfo(name = "row_json")
    val rowJson: String,

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: String?,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long,
)
