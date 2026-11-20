package com.avago.feature.assets.di

import com.avago.feature.assets.data.AssetExtrasService
import com.avago.feature.assets.data.AssetQrService
import com.avago.feature.assets.data.AssetScopeRepository
import com.avago.feature.assets.data.KtorAssetExtrasService
import com.avago.feature.assets.data.KtorAssetQrService
import com.avago.feature.assets.data.KtorAssetScopeRepository
import com.avago.feature.assets.data.KtorSampleDataService
import com.avago.feature.assets.data.SampleDataService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt bindings for the assets feature's online endpoints (scope, VIN decode, geocode, recalls, QR labels, sample data). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssetsModule {
    @Binds
    abstract fun assetScope(impl: KtorAssetScopeRepository): AssetScopeRepository

    @Binds
    abstract fun assetExtras(impl: KtorAssetExtrasService): AssetExtrasService

    @Binds
    abstract fun assetQr(impl: KtorAssetQrService): AssetQrService

    @Binds
    abstract fun sampleData(impl: KtorSampleDataService): SampleDataService
}
