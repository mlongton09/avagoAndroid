package com.avago.core.seed.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * AppLimits is @Singleton with @Inject constructor — Hilt auto-binds it.
 */
@Module
@InstallIn(SingletonComponent::class)
object SeedModule
