package com.avago

import android.app.Application
import android.content.Context
import android.os.Trace
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.avago.core.auth.IdentityManager
import com.avago.core.data.CrashDiagnostics
import com.avago.core.data.ExchangeRateService
import com.avago.core.network.AvagoServiceClient
import com.avago.core.seed.ConfigSeeder
import com.avago.core.sync.ConnectivityMonitor
import com.avago.core.sync.DeltaPushApplier
import com.avago.core.sync.PhotoCacheSweeper
import com.avago.core.sync.SyncEngine
import com.avago.core.sync.SyncGate
import com.avago.core.sync.SyncWorker
import com.avago.core.sync.TechLocationService
import com.avago.feature.chat.realtime.BackgroundSyncCoordinator
import com.avago.feature.chat.realtime.OutboxRetryCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AvagoApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()



    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var identityManager: IdentityManager
    @Inject lateinit var crashDiagnostics: CrashDiagnostics
    @Inject lateinit var serviceClient: AvagoServiceClient
    @Inject lateinit var configSeeder: ConfigSeeder
    @Inject lateinit var connectivityMonitor: ConnectivityMonitor
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var syncGate: SyncGate
    @Inject lateinit var exchangeRateService: ExchangeRateService
    @Inject lateinit var techLocationService: TechLocationService
    @Inject lateinit var outboxRetryCoordinator: OutboxRetryCoordinator
    @Inject lateinit var photoCacheSweeper: PhotoCacheSweeper
    @Inject lateinit var deltaApplier: DeltaPushApplier
    @Inject lateinit var chatBackgroundSync: BackgroundSyncCoordinator

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
                crashDiagnostics.setUserContext()
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

        appScope.launch {
            try {
                exchangeRateService.refreshIfNeeded()
            } catch (e: Exception) {
                Timber.e(e, "AvagoApplication: exchange rate refresh failed")
            }
        }

        appScope.launch {
            while (true) {
                delay(5 * 60 * 1000L)
                val accountId = identityManager.getActiveAccountId() ?: continue
                try {
                    deltaApplier.flushMetrics(accountId)
                } catch (e: Exception) {
                    Timber.e(e, "AvagoApplication: periodic metrics flush failed")
                }
            }
        }

        identityManager.registerSyncWorker(SyncWorker::class.java)
        schedulePeriodicSync()
        observeConnectivityForSync()
        observeSignOutForWatermarkReset()
        observeSignInForRateLimitClear()
        observePermissionsStaleness()
        observeAppForeground()

        Trace.endSection() // AvagoApplication.initCoroutines
        Trace.endSection() // AvagoApplication.onCreate
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "avago_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Timber.d("AvagoApplication: periodic sync scheduled")
    }

    private fun observeConnectivityForSync() {
        appScope.launch {
            // Emit pairs of (previous, current) to detect false → true transitions.
            connectivityMonitor.networkStatus
                .scan(Pair(true, true)) { acc, current -> Pair(acc.second, current) }
                .distinctUntilChanged()
                .collect { (previous, current) ->
                    if (!previous && current) {
                        Timber.d("AvagoApplication: connectivity restored — triggering immediate sync")
                        syncEngine.handleConnectivityLost() // resets any stale in-flight items
                        triggerImmediateSync()
                    } else if (!current) {
                        syncEngine.handleConnectivityLost()
                    }
                }
        }
    }

    private fun observeSignOutForWatermarkReset() {
        appScope.launch {
            identityManager.signOutEvents.collect { accountId ->
                runCatching { syncEngine.resetAllWatermarks(accountId) }
                    .onFailure { Timber.e(it, "AvagoApplication: failed to reset watermarks for $accountId") }
                syncGate.reset()
            }
        }
    }

    private fun observeSignInForRateLimitClear() {
        appScope.launch {
            identityManager.signInEvents.collect {
                syncEngine.clearRateLimitBackoff()
            }
        }
    }

    private fun observePermissionsStaleness() {
        appScope.launch {
            serviceClient.permissionsStaleEvents.collect { accountId ->
                Timber.w("AvagoApplication: permissions stale for $accountId — triggering sync")
                val request = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(this@AvagoApplication)
                    .enqueueUniqueWork(
                        "sync_permissions_$accountId",
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
            }
        }
    }

    private fun observeAppForeground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Timber.d("AvagoApplication: app foregrounded — triggering foreground sync")
                val request = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(this@AvagoApplication).enqueueUniqueWork(
                    "avago_sync",
                    ExistingWorkPolicy.KEEP,
                    request,
                )
                techLocationService.startMonitoring()
                outboxRetryCoordinator.startPeriodicFlush()
                // Catch up on chat messages that arrived while backgrounded (mirrors iOS
                // AppBootstrapCoordinator.handleAppForeground → BackgroundSyncCoordinator.runDelta).
                // WebSocket onOpen already calls runDelta on reconnect; this covers the case
                // where the socket stayed alive across a short background/foreground cycle.
                appScope.launch {
                    try {
                        chatBackgroundSync.runDelta()
                    } catch (e: Exception) {
                        Timber.e(e, "AvagoApplication: chat delta sync on foreground failed")
                    }
                }

                // Deferred cache sweep — give the app 30 s to settle before evicting local photos
                appScope.launch {
                    delay(30_000)
                    val accountId = identityManager.getActiveAccountId() ?: return@launch
                    try {
                        photoCacheSweeper.runIfNeeded(accountId)
                    } catch (e: Exception) {
                        Timber.e(e, "AvagoApplication: PhotoCacheSweeper failed")
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                Timber.d("AvagoApplication: app backgrounded")
                techLocationService.stopMonitoring()
                outboxRetryCoordinator.stopPeriodicFlush()
                appScope.launch {
                    val accountId = identityManager.getActiveAccountId() ?: return@launch
                    try {
                        deltaApplier.flushMetrics(accountId)
                    } catch (e: Exception) {
                        Timber.e(e, "AvagoApplication: onStop metrics flush failed")
                    }
                }
            }
        })
    }

    private fun triggerImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "avago_sync",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
