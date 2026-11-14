package com.avago.feature.rentals.data

import com.avago.core.sync.ExternalOutbox
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class RentalsModule {
    /** Lets the main [com.avago.core.sync.SyncEngine] drain/pull rentals with every sync. */
    @Binds
    @IntoSet
    abstract fun rentalOutbox(impl: RentalOutbox): ExternalOutbox
}
