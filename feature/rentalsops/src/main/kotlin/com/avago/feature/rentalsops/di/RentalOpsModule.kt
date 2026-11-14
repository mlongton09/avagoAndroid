package com.avago.feature.rentalsops.di

import com.avago.core.sync.ExternalOutbox
import com.avago.feature.rentalsops.sync.RentalOpsOutbox
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class RentalOpsModule {
    /** SyncEngine drains rentals P2 queue rows and pulls P2 feeds through this outbox. */
    @Binds
    @IntoSet
    abstract fun bindOutbox(impl: RentalOpsOutbox): ExternalOutbox
}
