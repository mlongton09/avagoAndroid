package com.avago.core.data.di

import android.content.Context
import com.avago.core.data.DatabaseFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabaseFactory(
        @ApplicationContext context: Context,
    ): DatabaseFactory = DatabaseFactory(context)
}
