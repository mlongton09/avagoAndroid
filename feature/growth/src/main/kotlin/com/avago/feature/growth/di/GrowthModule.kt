package com.avago.feature.growth.di

import com.avago.feature.growth.data.GrowthService
import com.avago.feature.growth.data.KtorGrowthService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GrowthModule {
    @Binds
    abstract fun bindGrowthService(impl: KtorGrowthService): GrowthService
}
