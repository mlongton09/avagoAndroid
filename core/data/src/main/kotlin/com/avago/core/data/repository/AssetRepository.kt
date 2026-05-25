package com.avago.core.data.repository

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.AvagoDatabase
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.ConfigEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepository @Inject constructor(
    private val dbFactory: DatabaseFactory,
) {

    // ---------------------------------------------------------------------------
    // Asset queries
    // ---------------------------------------------------------------------------

    /**
     * Returns a cold Flow of all non-deleted assets for the given account.
     * The accountId must be the active account ID at observation time.
     */
    suspend fun observeAssets(accountId: String): Flow<List<AssetEntity>> {
        val db = dbFactory.get(accountId)
        return db.assetDao().observeAll(accountId)
    }

    suspend fun getAssetById(accountId: String, assetId: String): AssetEntity? {
        val db = dbFactory.get(accountId)
        return db.assetDao().getById(assetId)
    }

    /**
     * Upserts the asset entity into the local DB and enqueues a sync-push operation.
     */
    suspend fun upsertAsset(accountId: String, entity: AssetEntity) {
        val db = dbFactory.get(accountId)
        db.assetDao().upsert(entity)
        enqueueSyncPush(
            db = db,
            entityType = "asset",
            entityId = entity.assetId,
            serverVersion = entity.serverVersion,
            operation = "update",
        )
        Timber.d("[AssetRepository] Upserted asset ${entity.assetId} and enqueued sync")
    }

    /**
     * Soft-deletes the asset and enqueues a delete sync operation.
     */
    suspend fun softDeleteAsset(accountId: String, assetId: String) {
        val db = dbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.assetDao().softDelete(assetId, now)
        enqueueSyncPush(
            db = db,
            entityType = "asset",
            entityId = assetId,
            serverVersion = 0L,
            operation = "delete",
        )
        Timber.d("[AssetRepository] Soft-deleted asset $assetId")
    }

    // ---------------------------------------------------------------------------
    // Log queries (for detail screen — returns all logs for the asset/account)
    // ---------------------------------------------------------------------------

    suspend fun observeLogsForAsset(accountId: String, assetId: String): Flow<List<LogEntity>> {
        val db = dbFactory.get(accountId)
        // LogDao.observeAll returns all non-deleted for account; filter by assetId in memory.
        return db.logDao().observeAll(accountId).map { entries ->
            entries.filter { it.assetId == assetId }
        }
    }

    // ---------------------------------------------------------------------------
    // Config queries (for asset types seeded by Phase 4)
    // ---------------------------------------------------------------------------

    suspend fun getAssetTypesConfig(accountId: String): ConfigEntity? {
        val db = dbFactory.get(accountId)
        return db.configDao().getByKey(scope = "system", key = "asset_types")
    }

    // ---------------------------------------------------------------------------
    // Sync queue helpers
    // ---------------------------------------------------------------------------

    private suspend fun enqueueSyncPush(
        db: AvagoDatabase,
        entityType: String,
        entityId: String,
        serverVersion: Long,
        operation: String = "update",
    ) {
        val now = System.currentTimeMillis()
        db.syncQueueDao().enqueueWithDedup(
            SyncQueueEntity(
                queueId = "${entityType}_${entityId}",
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                serverVersion = serverVersion,
                payload = null,
                syncStatus = "pending",
                attempts = 0L,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}
