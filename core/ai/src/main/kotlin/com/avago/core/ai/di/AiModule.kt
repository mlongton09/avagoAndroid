package com.avago.core.ai.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for `:core:ai` singletons.
 *
 * [ScoutContextHost], [AiExtractor], and [ScoutViewModel] are all
 * `@Singleton` / `@HiltViewModel` with `@Inject constructor`, so Hilt
 * discovers them automatically — no explicit [dagger.Provides] bindings
 * are needed today.
 *
 * This module is kept as an empty extension point: when the on-device
 * Gemini Nano AI Edge SDK ships stable, the AiExtractor interface will
 * be split into a `CloudAiExtractor` (current) and a `NanoAiExtractor`,
 * and the binding will be declared here so the rest of the codebase
 * remains untouched.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule
