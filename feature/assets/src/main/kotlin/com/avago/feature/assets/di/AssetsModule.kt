package com.avago.feature.assets.di

import com.avago.feature.assets.data.AssetExtrasService
import com.avago.feature.assets.data.AssetScopeRepository
import com.avago.feature.assets.data.KtorAssetExtrasService
import com.avago.feature.assets.data.KtorAssetScopeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the assets feature.
 *
 * [com.avago.core.data.repository.AssetRepository] and [com.avago.core.sync.SyncEngine]
 * are @Singleton and provided by their core modules.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssetsModule {
    @Binds
    abstract fun assetScope(impl: KtorAssetScopeRepository): AssetScopeRepository

    @Binds
    abstract fun assetExtras(impl: KtorAssetExtrasService): AssetExtrasService
}
