package com.avago.core.sync.v2

import com.avago.core.data.v2.V2SyncResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets the legacy [com.avago.core.sync.SyncEngine] hand a cycle to the v2 engine. While v2 is
 * active (gate open) every existing trigger — the periodic worker, connectivity, foreground,
 * DAO nudges — runs a v2 pull + outbox flush instead of the legacy push/pull.
 */
fun interface V2SyncHook {
    /** Runs one v2 cycle, or returns null (without I/O) when v2 is not active. */
    suspend fun runIfActive(): V2SyncResult?

    companion object {
        val Inactive = V2SyncHook { null }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class V2SyncModule {
    @Binds
    abstract fun bindV2SyncHook(coordinator: V2SyncCoordinator): V2SyncHook
}
