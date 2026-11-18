package com.avago.core.data.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.data.v2.LegacyStoreReset
import com.avago.core.data.v2.SharedPreferencesV2FlagStorage
import com.avago.core.data.v2.V2Flag
import com.avago.core.data.v2.V2FlagStorage
import com.avago.core.data.v2.V2ResetMarker
import com.avago.core.data.v2.V2StoreGate
import com.avago.core.data.v2.V2StorePaths
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabaseFactory(
        @ApplicationContext context: Context,
    ): DatabaseFactory = DatabaseFactory(context)

    // ── Schema v2 (inert unless V2Flag is enabled) ───────────────────────────

    @Provides
    @Singleton
    fun provideV2FlagStorage(@ApplicationContext context: Context): V2FlagStorage =
        SharedPreferencesV2FlagStorage(context)

    @Provides
    @Singleton
    fun provideV2ResetMarker(@ApplicationContext context: Context): V2ResetMarker =
        V2ResetMarker(File(context.noBackupFilesDir, V2StorePaths.RESET_MARKER_FILE))

    @Provides
    @Singleton
    fun provideV2Flag(
        @ApplicationContext context: Context,
        storage: V2FlagStorage,
        marker: V2ResetMarker,
    ): V2Flag = V2Flag(
        storage = storage,
        marker = marker,
        isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
    )

    @Provides
    @Singleton
    fun provideV2StoreGate(flag: V2Flag, marker: V2ResetMarker): V2StoreGate = V2StoreGate(flag, marker)

    @Provides
    @Singleton
    fun provideLegacyStoreReset(
        @ApplicationContext context: Context,
        marker: V2ResetMarker,
        databaseFactory: dagger.Lazy<DatabaseFactory>,
        userPreferences: dagger.Lazy<UserPreferencesRepository>,
    ): LegacyStoreReset = LegacyStoreReset(
        marker = marker,
        dirs = LegacyStoreReset.Dirs(
            filesDir = context.filesDir,
            sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs"),
            dataStoreDir = File(context.filesDir, "datastore"),
            cacheDir = context.cacheDir,
        ),
        clearDrafts = { userPreferences.get().clearChatDrafts() },
        beforeWipe = { databaseFactory.get().closeAll() },
    )
}
