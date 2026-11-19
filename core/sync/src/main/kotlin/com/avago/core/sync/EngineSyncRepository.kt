package com.avago.core.sync

import com.avago.core.data.domain.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/** [SyncRepository] over the app's sync cycle ([SyncEngine.sync], a v2 pull + outbox flush). */
@Singleton
class EngineSyncRepository @Inject constructor(private val engine: SyncEngine) : SyncRepository {
    override suspend fun syncNow(): Boolean = engine.sync() is SyncResult.Success
}
