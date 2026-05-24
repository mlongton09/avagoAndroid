package com.avago.core.network.di

import com.avago.core.network.AvagoHttpClient
import com.avago.core.network.BuildConfig
import com.avago.core.network.RefreshFailedHandler
import com.avago.core.network.TokenProvider
import com.avago.core.network.TokenStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String =
        BuildConfig.BASE_URL.takeIf { it.isNotBlank() }
            ?: "https://api.avagomate.com"

    @Provides
    @Singleton
    fun provideHttpClient(
        tokenProvider: TokenProvider,
        tokenStorage: TokenStorage,
        @Named("baseUrl") baseUrl: String,
        refreshFailedHandler: RefreshFailedHandler,
    ): HttpClient = AvagoHttpClient.create(
        baseUrl = baseUrl,
        tokenProvider = tokenProvider,
        tokenStorage = tokenStorage,
        isDebug = BuildConfig.DEBUG,
        refreshFailedHandler = refreshFailedHandler,
    )
}
