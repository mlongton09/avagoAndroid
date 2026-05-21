package com.avago.feature.schedule.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Hilt module for the schedule feature.
 *
 * [ScheduleRepository] is [@Singleton][javax.inject.Singleton] and is constructed
 * automatically by Hilt — no explicit binding required. This module acts as a hook
 * for any future feature-local bindings.
 */
@Module
@InstallIn(ViewModelComponent::class)
object ScheduleModule
