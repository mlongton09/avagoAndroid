package com.avago.core.seed.di

import com.avago.core.seed.AppLimits
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SeedModule {

    @Provides
    @Singleton
    fun provideAppLimits(): AppLimits = AppLimits()
}
