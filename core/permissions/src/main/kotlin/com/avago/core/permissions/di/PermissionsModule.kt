package com.avago.core.permissions.di

import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.permissions.PermissionsManager
import com.avago.core.sync.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PermissionsModule {

    @Provides
    @Singleton
    fun providePermissionsManager(
        dbFactory: DatabaseFactory,
        identity: IdentityManager,
        @ApplicationScope scope: CoroutineScope,
    ): PermissionsManager = PermissionsManager(
        dbFactory = dbFactory,
        identity = identity,
        scope = scope,
    )
}
