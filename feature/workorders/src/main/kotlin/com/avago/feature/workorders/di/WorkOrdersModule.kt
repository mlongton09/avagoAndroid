package com.avago.feature.workorders.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Hilt module for the workorders feature.
 *
 * [WorkOrderRepository] is @Singleton and constructed automatically by Hilt.
 * This module exists as a hook for any future feature-local bindings.
 */
@Module
@InstallIn(ViewModelComponent::class)
object WorkOrdersModule
