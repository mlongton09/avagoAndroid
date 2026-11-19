package com.avago.feature.rentals.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RentalsModule {
    @Binds
    abstract fun postCharges(impl: KtorPostChargeService): PostChargeService
}
