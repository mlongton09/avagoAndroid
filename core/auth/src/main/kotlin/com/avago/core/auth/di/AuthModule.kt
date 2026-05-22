package com.avago.core.auth.di

import android.content.Context
import com.avago.core.auth.IdentityManager
import com.avago.core.auth.SecureTokenStore
import com.avago.core.network.RefreshFailedHandler
import com.avago.core.network.TokenProvider
import com.avago.core.network.TokenStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindTokenProvider(store: SecureTokenStore): TokenProvider

    @Binds
    @Singleton
    abstract fun bindTokenStorage(store: SecureTokenStore): TokenStorage

    @Binds
    @Singleton
    abstract fun bindRefreshFailedHandler(identityManager: IdentityManager): RefreshFailedHandler

    companion object {

        @Provides
        @Singleton
        fun provideSecureTokenStore(
            @ApplicationContext context: Context,
        ): SecureTokenStore = SecureTokenStore(context)

        @Provides
        @Named("deviceId")
        @Singleton
        fun provideDeviceId(tokenStore: SecureTokenStore): String =
            tokenStore.getOrCreateDeviceId()
    }
}
