package com.avago.feature.crew.di

import com.avago.feature.crew.data.CrewService
import com.avago.feature.crew.data.KtorCrewService
import com.avago.feature.crew.data.KtorOperatorService
import com.avago.feature.crew.data.KtorPassportService
import com.avago.feature.crew.data.OperatorService
import com.avago.feature.crew.data.PassportService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CrewModule {
    @Binds abstract fun crew(impl: KtorCrewService): CrewService
    @Binds abstract fun operator(impl: KtorOperatorService): OperatorService
    @Binds abstract fun passport(impl: KtorPassportService): PassportService
}
