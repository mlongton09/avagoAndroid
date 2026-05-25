package com.avago.feature.schedule.repository

import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.ScheduleEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val dbFactory: DatabaseFactory,
) {

    // -------------------------------------------------------------------------
    // Observe
    // -------------------------------------------------------------------------

    suspend fun observeAll(accountId: String): Flow<List<ScheduleEntity>> =
        dbFactory.get(accountId).scheduleDao().observeAll(accountId)

    suspend fun getById(accountId: String, scheduleId: String): ScheduleEntity? =
        dbFactory.get(accountId).scheduleDao().getById(scheduleId)

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    suspend fun upsert(accountId: String, entity: ScheduleEntity) {
        val db = dbFactory.get(accountId)
        db.scheduleDao().upsert(entity)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "schedule",
            entityId = entity.scheduleId,
            serverVersion = entity.serverVersion,
            operation = "update",
        )
        Timber.d("[ScheduleRepo] upserted ${entity.scheduleId}")
    }

    suspend fun softDelete(accountId: String, scheduleId: String) {
        val db = dbFactory.get(accountId)
        val now = System.currentTimeMillis()
        db.scheduleDao().softDelete(scheduleId, now)
        enqueueSyncPush(
            accountId = accountId,
            entityType = "schedule",
            entityId = scheduleId,
            serverVersion = 0L,
            operation = "delete",
        )
        Timber.d("[ScheduleRepo] soft-deleted $scheduleId")
    }

    // -------------------------------------------------------------------------
    // Sync queue
    // -------------------------------------------------------------------------

    private suspend fun enqueueSyncPush(
        accountId: String,
        entityType: String,
        entityId: String,
        serverVersion: Long,
        operation: String = "update",
    ) {
        val db = dbFactory.get(accountId)
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
