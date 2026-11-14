package com.avago.feature.rentals.data

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.db.rental.RentalSyncStateEntity
import com.avago.core.data.rental.LocalFleet
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.network.rentals.RentalsApiException
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.RentalJson
import com.avago.feature.rentals.domain.bool
import com.avago.feature.rentals.domain.long
import com.avago.feature.rentals.domain.objs
import com.avago.feature.rentals.domain.parseObj
import com.avago.feature.rentals.domain.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Incremental pull of the rentals sync feeds into Room (spec §15):
 * `GET /rentals/sync/{entity}?cursor` per feed (tombstones delete, local unsynced rows are
 * never overwritten), then `GET /rentals/fleet?updated_since` for unit status inputs.
 * Bounded: [MAX_PAGES] pages per feed per run.
 */
@Singleton
class RentalSync @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val api: RentalsApi,
) {
    private val mutex = Mutex()

    sealed interface Outcome {
        data object Ok : Outcome
        /** Rentals not enabled / not permitted for this account (403/404) — nothing to pull. */
        data object Disabled : Outcome
        data class Failed(val error: Throwable, val transient: Boolean) : Outcome
    }

    suspend fun pull(accountId: String): Outcome = mutex.withLock {
        val dao = dbFactory.get(accountId).rentalDao()
        var failure: Outcome.Failed? = null
        // API contract §11 rule 6: a periodic full resync (cursor 0) recovers rows skipped by
        // long-running transactions. Rows are re-upserted; local unsynced rows stay untouched.
        val lastFull = dao.get(RentalEntity.META, META_FULL_RESYNC)?.let { parseObj(it.json)?.long("at") } ?: 0L
        val fullResync = System.currentTimeMillis() - lastFull > FULL_RESYNC_MS
        for (entity in RentalEntity.FEEDS) {
            try {
                pullFeed(accountId, entity, fromStart = fullResync)
                if (entity == RentalEntity.ALLOCATION) {
                    dao.upsert(meta("allocations_synced", buildJsonObject { put("at", System.currentTimeMillis()) }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RentalsApiException) {
                if (e.status == 401 || e.status == 403 || (e.status == 404 && entity == RentalEntity.FEEDS.first())) {
                    return@withLock Outcome.Disabled
                }
                // Feed not deployed yet (stage B entity on a stage A server).
                if (e.status == 404 || e.isNotImplemented || (e.status == 400 && e.code == "unknown_entity")) continue
                Timber.w(e, "[RentalSync] feed $entity failed")
                recordError(accountId, entity, e.message)
                failure = Outcome.Failed(e, e.isTransient)
            } catch (e: Exception) {
                Timber.w(e, "[RentalSync] feed $entity failed")
                recordError(accountId, entity, e.message)
                failure = Outcome.Failed(e, true)
            }
        }
        try {
            pullFleet(accountId, fromStart = fullResync)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[RentalSync] fleet failed")
            recordError(accountId, RentalEntity.FLEET, e.message)
            failure = failure ?: Outcome.Failed(e, (e as? RentalsApiException)?.isTransient ?: true)
        }
        failure ?: run {
            if (fullResync) dao.upsert(meta(META_FULL_RESYNC, buildJsonObject { put("at", System.currentTimeMillis()) }))
            Outcome.Ok
        }
    }

    private suspend fun recordError(accountId: String, entity: String, message: String?) {
        val dao = dbFactory.get(accountId).rentalDao()
        val s = dao.syncState(entity) ?: RentalSyncStateEntity(entity)
        dao.upsertSyncState(s.copy(lastError = message ?: "error"))
    }

    private fun meta(id: String, body: JsonObject) = RentalRecordEntity(
        entity = RentalEntity.META,
        id = id,
        json = RentalJson.encodeToString(JsonObject.serializer(), body),
        updatedAt = System.currentTimeMillis(),
    )

    private suspend fun pullFeed(accountId: String, entity: String, fromStart: Boolean = false) {
        val dao = dbFactory.get(accountId).rentalDao()
        var state = (dao.syncState(entity) ?: RentalSyncStateEntity(entity)).let { if (fromStart) it.copy(cursor = null) else it }
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = api.syncFeed(accountId, entity, cursor = state.cursor, updatedSince = null)
            // Local unsynced rows are never overwritten by a pull (read once per page, not per item).
            val localChanges = dao.unsyncedIds(entity).toHashSet()
            val upserts = mutableListOf<RentalRecordEntity>()
            val tombstones = mutableListOf<String>()
            for (item in page.objs("items")) {
                val id = RentalRecords.idOf(entity, item) ?: continue
                if (RentalRecords.isTombstone(item)) {
                    tombstones += id
                    if (entity == RentalEntity.ASSET) dao.deleteSynced(RentalEntity.FLEET, listOf(id))
                    continue
                }
                if (id in localChanges) continue
                RentalRecords.record(entity, item)?.let { upserts += it }
            }
            val hasMore = page.bool("has_more") == true
            val next = page.str("next_cursor") ?: state.cursor
            state = state.copy(
                cursor = next,
                serverTime = page.str("server_time") ?: state.serverTime,
                lastSyncedAt = if (hasMore) state.lastSyncedAt else System.currentTimeMillis(),
                lastError = null,
            )
            dao.applyPage(entity, upserts, tombstones, state)
            if (!hasMore || next == null) break
        }
    }

    private suspend fun pullFleet(accountId: String, fromStart: Boolean = false) {
        val dao = dbFactory.get(accountId).rentalDao()
        val entity = RentalEntity.FLEET
        val state = dao.syncState(entity) ?: RentalSyncStateEntity(entity)
        val resp = api.fleet(accountId, updatedSince = state.cursor.takeUnless { fromStart })
        val items = resp.objs("items")
        // Units in the change set that are no longer rentable come back as `{asset_id, _deleted:true}`.
        val tombstones = items.filter(RentalRecords::isTombstone).mapNotNull { it.str("asset_id") }
        val localChanges = dao.unsyncedIds(entity).toHashSet()
        val upserts = items.filterNot(RentalRecords::isTombstone).mapNotNull { item ->
            val id = item.str("asset_id") ?: return@mapNotNull null
            if (id in localChanges) null else RentalRecords.record(entity, item)
        }
        resp.long("reserved_window_minutes")?.let { w ->
            dao.upsert(
                meta(
                    LocalFleet.META_FLEET_ID,
                    buildJsonObject {
                        put("reserved_window_minutes", JsonPrimitive(w))
                        resp["counts"]?.let { put("counts", it) }
                    },
                ),
            )
        }
        dao.applyPage(
            entity,
            upserts,
            tombstones,
            state.copy(
                cursor = resp.str("next_cursor") ?: resp.str("server_time") ?: state.cursor,
                serverTime = resp.str("server_time") ?: state.serverTime,
                lastSyncedAt = System.currentTimeMillis(),
                lastError = null,
            ),
        )
    }

    companion object {
        const val MAX_PAGES = 40
        const val META_FULL_RESYNC = "full_resync"
        const val FULL_RESYNC_MS = 7L * 24 * 60 * 60 * 1000
    }
}
