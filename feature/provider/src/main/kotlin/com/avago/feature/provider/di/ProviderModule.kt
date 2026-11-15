package com.avago.feature.provider.di

import com.avago.feature.provider.data.KtorProviderService
import com.avago.feature.provider.data.ProviderService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {
    @Binds
    abstract fun bindProviderService(impl: KtorProviderService): ProviderService
}
