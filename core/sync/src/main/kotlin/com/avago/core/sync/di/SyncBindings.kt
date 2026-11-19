package com.avago.core.sync.di

import com.avago.core.data.domain.SyncRepository
import com.avago.core.sync.EngineSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindings {
    @Binds
    abstract fun syncRepository(impl: EngineSyncRepository): SyncRepository
}
