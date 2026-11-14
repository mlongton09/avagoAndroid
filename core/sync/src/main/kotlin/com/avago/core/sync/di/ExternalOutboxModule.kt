package com.avago.core.sync.di

import com.avago.core.sync.ExternalOutbox
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declares the (possibly empty) set of feature outboxes drained by SyncEngine. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalOutboxModule {
    @Multibinds
    abstract fun externalOutboxes(): Set<ExternalOutbox>
}
