package com.avago.feature.assets.di

import com.avago.feature.assets.data.AssetExtrasService
import com.avago.feature.assets.data.AssetScopeRepository
import com.avago.feature.assets.data.KtorAssetExtrasService
import com.avago.feature.assets.data.KtorAssetScopeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt bindings for the assets feature's server compute endpoints (scope, VIN decode, geocode). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssetsModule {
    @Binds
    abstract fun assetScope(impl: KtorAssetScopeRepository): AssetScopeRepository

    @Binds
    abstract fun assetExtras(impl: KtorAssetExtrasService): AssetExtrasService
}
