package com.avago.core.data.db.rental

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Local mirror of every rentals sync feed row (spec §15). One table keyed by
 * (`entity`, `id`) keeps the schema stable while the rentals API grows: the full server
 * JSON lives in [json] and the columns the screens filter / sort on are extracted.
 *
 * `entity` is the feed name (`rental_order`, `rental_order_line`, `rental_allocation`,
 * `rental_customer`, `rental_fleet`, …) or a local-only kind (`rental_handover`,
 * `rental_ledger`, `rental_id_map`).
 *
 * [localState]: `synced` (server copy), `pending` (local change queued in `sync_queue`),
 * `draft` (needs the server to confirm — bookings, extensions, invoices made offline),
 * `conflict` (server rejected; [notice] holds the user-facing reason).
 */
@Entity(
    tableName = "rental_records",
    primaryKeys = ["entity", "id"],
    indices = [
        Index(value = ["entity", "asset_id"]),
        Index(value = ["entity", "order_id"]),
        Index(value = ["entity", "customer_id"]),
        Index(value = ["entity", "start_at"]),
        Index(value = ["local_state"]),
    ],
)
data class RentalRecordEntity(
    @ColumnInfo(name = "entity") val entity: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "json") val json: String,
    @ColumnInfo(name = "asset_id") val assetId: String? = null,
    @ColumnInfo(name = "order_id") val orderId: String? = null,
    @ColumnInfo(name = "customer_id") val customerId: String? = null,
    @ColumnInfo(name = "class_id") val classId: String? = null,
    @ColumnInfo(name = "location_id") val locationId: String? = null,
    @ColumnInfo(name = "status") val status: String? = null,
    @ColumnInfo(name = "name") val name: String? = null,
    /** Epoch ms. */
    @ColumnInfo(name = "start_at") val startAt: Long? = null,
    /** Epoch ms; null = open-ended. */
    @ColumnInfo(name = "end_at") val endAt: Long? = null,
    @ColumnInfo(name = "seq") val seq: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "local_state") val localState: String = STATE_SYNCED,
    @ColumnInfo(name = "client_uuid") val clientUuid: String? = null,
    @ColumnInfo(name = "notice") val notice: String? = null,
) {
    companion object {
        const val STATE_SYNCED = "synced"
        const val STATE_PENDING = "pending"
        const val STATE_DRAFT = "draft"
        const val STATE_CONFLICT = "conflict"
    }
}

/** Per-feed incremental sync position (`next_cursor` / `server_time`) and last success. */
@Entity(tableName = "rental_sync_state", primaryKeys = ["entity"])
data class RentalSyncStateEntity(
    @ColumnInfo(name = "entity") val entity: String,
    @ColumnInfo(name = "cursor") val cursor: String? = null,
    @ColumnInfo(name = "server_time") val serverTime: String? = null,
    /** Epoch ms of the last complete pull of this feed. */
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
