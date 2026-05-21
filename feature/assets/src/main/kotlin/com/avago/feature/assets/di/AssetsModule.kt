package com.avago.feature.assets.di

import com.avago.core.data.repository.AssetRepository
import com.avago.core.sync.SyncEngine
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Hilt module for the assets feature.
 *
 * [AssetRepository] and [SyncEngine] are both @Singleton and provided by their
 * respective core modules — no explicit @Provides needed here. This module exists
 * as a placeholder for any feature-local bindings added in future phases.
 */
@Module
@InstallIn(ViewModelComponent::class)
object AssetsModule
