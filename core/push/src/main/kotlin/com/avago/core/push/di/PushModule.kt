package com.avago.core.push.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the push notification subsystem.
 *
 * [AvagoFcmService] uses Hilt field injection via [@AndroidEntryPoint] and lives
 * in the `:app` module, so no explicit `@Provides` methods are needed here.
 * This module is the correct extension point for any future push-scoped bindings
 * (e.g. a custom notification repository or sound-preference provider).
 */
@Module
@InstallIn(SingletonComponent::class)
object PushModule
