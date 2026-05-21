package com.avago

import android.app.Application
import android.os.Trace
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.avago.core.auth.IdentityManager
import com.avago.core.seed.ConfigSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AvagoApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var identityManager: IdentityManager
    @Inject lateinit var configSeeder: ConfigSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        Trace.beginSection("AvagoApplication.onCreate")
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Trace.beginSection("AvagoApplication.initCoroutines")
        appScope.launch {
            try {
                Trace.beginSection("IdentityManager.initOnLaunch")
                identityManager.initOnLaunch()
                Trace.endSection()
                val accountId = identityManager.getActiveAccountId()
                if (accountId != null) {
                    Trace.beginSection("ConfigSeeder.seedIfNeeded")
                    configSeeder.seedIfNeeded(accountId)
                    Trace.endSection()
                } else {
                    Timber.w("AvagoApplication: no active account after init, skipping seed")
                }
            } catch (e: Exception) {
                Timber.e(e, "AvagoApplication: failed to initialize identity or seed config")
            }
        }
        Trace.endSection() // AvagoApplication.initCoroutines
        Trace.endSection() // AvagoApplication.onCreate
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
