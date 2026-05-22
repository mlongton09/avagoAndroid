package com.avago.core.permissions.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * PermissionsManager is @Singleton with @Inject constructor — Hilt auto-binds it.
 * This module is the extension point for any future permissions-local bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object PermissionsModule
