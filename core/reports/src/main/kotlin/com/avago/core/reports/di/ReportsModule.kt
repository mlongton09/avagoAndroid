package com.avago.core.reports.di

import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.reports.ReportAggregator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReportsModule {

    @Provides
    @Singleton
    fun provideReportAggregator(
        dbFactory: DatabaseFactory,
        identity: IdentityManager,
    ): ReportAggregator = ReportAggregator(dbFactory, identity)
}
