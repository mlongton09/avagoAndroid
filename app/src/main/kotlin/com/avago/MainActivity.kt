package com.avago

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.avago.app.AssetTagParser
import com.avago.app.MainViewModel
import com.avago.core.auth.IdentityManager
import com.avago.core.auth.PermissionStore
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.data.v2.V2Flag
import com.avago.core.data.v2.V2Uuid
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.push.NotificationPermissionHelper
import com.avago.core.push.WorkOrderNotifier
import com.avago.core.sync.SyncEngine
import com.avago.core.sync.SyncGate
import com.avago.core.ui.ServiceUnavailableUpdateScreen
import com.avago.nav.MainScaffold
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var syncGate: SyncGate
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var identityManager: IdentityManager
    @Inject lateinit var permissionStore: PermissionStore
    @Inject lateinit var v2Flag: V2Flag

    private val mainViewModel: MainViewModel by viewModels()

    // ─────────────────────────────────────────────────────────────────────
    // POST_NOTIFICATIONS runtime permission (Android 13+)
    // ─────────────────────────────────────────────────────────────────────
    // Android 13 (API 33) introduced a runtime permission for posting
    // notifications. Unlike iOS — where the system prompt is triggered by
    // calling UNUserNotificationCenter.requestAuthorization — Android only
    // surfaces the system dialog when the app explicitly launches a
    // RequestPermission contract. If we never launch it, FCM messages are
    // delivered to the app but never shown to the user (silent failure).
    //
    // Mirrors iOS behaviour: prompt immediately after sign-in, and also
    // prompt on launch for already-signed-in users who haven't been asked
    // yet (e.g. after upgrading the app or rolling out this feature).
    //
    // The OS itself handles the "don't ask again" rate-limit — after two
    // user denials the system silently rejects subsequent requests, so
    // calling .launch() on every sign-in is safe.
    // ─────────────────────────────────────────────────────────────────────
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.i("MainActivity: POST_NOTIFICATIONS granted=$granted")
        }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            NotificationPermissionHelper.shouldRequest(this)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the Android 12+ splash screen but DO NOT hold it on an
        // async condition. Holding the splash via setKeepOnScreenCondition
        // while enableEdgeToEdge() is in effect can leave MainActivity's
        // window stuck at mShownAlpha=0.0 after the splash dismisses
        // (reproducible on emulators and when Studio's debug agent is
        // attached) — the Compose tree draws but the surface is never
        // brought to visible state, producing a permanent black screen.
        //
        // Instead we let the splash dismiss immediately on first frame and
        // render an in-app loading overlay (below in setContent) until
        // IdentityManager.initOnLaunch() has finished restoring any
        // existing session from disk. This preserves the original UX goal
        // (no flash of the sign-in screen for returning users) without
        // triggering the splash-transition rendering bug.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Forward any thread_id extra (set by AvagoFcmService's tap
        // PendingIntent) to MainViewModel so AvagoNavHost can navigate
        // to the relevant chat thread after sign-in is restored.
        forwardChatDeepLink(intent)
        // Launcher shortcut / NFC tag; skipped on recreation so a rotation doesn't replay it.
        if (savedInstanceState == null) forwardLaunchIntent(intent)

        // Trigger a full sync every time the activity resumes (foreground).
        // Use Dispatchers.IO so network + DB work in sync() never blocks the main thread.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    syncEngine.sync()
                } catch (e: Exception) {
                    Timber.e(e, "MainActivity: background sync failed")
                }
            }
        }

        // Prompt for POST_NOTIFICATIONS after the user signs in
        // (mirrors iOS UNUserNotificationCenter.requestAuthorization).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                identityManager.signInEvents.collect {
                    maybeRequestNotificationPermission()
                }
            }
        }

        // Also prompt on launch if there's already a signed-in identity that
        // never got asked (existing users / fresh install of this build).
        lifecycleScope.launch {
            // Wait until identity restore from disk has finished before
            // checking the active account.
            identityManager.isInitialized.first { it }
            if (identityManager.getActiveAccountId() != null) {
                maybeRequestNotificationPermission()
            }
        }

        setContent {
            val themePreference by userPreferencesRepository.themeFlow.collectAsStateWithLifecycle(initialValue = "system")
            val isDark = when (themePreference) {
                "dark"  -> true
                "light" -> false
                else    -> isSystemInDarkTheme()
            }

            AvagoTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val updateRequired by v2Flag.updateRequired.collectAsStateWithLifecycle()
                    val identityReady by identityManager.isInitialized.collectAsStateWithLifecycle()
                    if (updateRequired) {
                        ServiceUnavailableUpdateScreen(onUpdate = ::openStoreListing)
                    } else if (!identityReady) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val syncState by syncEngine.state.collectAsStateWithLifecycle()
                        val syncReady by syncGate.isOpen.collectAsStateWithLifecycle()
                        val isOffline by mainViewModel.isOffline.collectAsStateWithLifecycle()
                        val activeAccountId by mainViewModel.activeAccountId.collectAsStateWithLifecycle()

                        MainScaffold(
                            syncState = syncState,
                            isOffline = isOffline,
                            syncReady = syncReady,
                            toast = mainViewModel.toast,
                            pendingNavRoute = mainViewModel.pendingNavRoute,
                            permissionStore = permissionStore,
                            signedIn = activeAccountId != null,
                        )
                    }
                }
            }
        }
    }

    /** Opens this app's Play Store listing, falling back to the web listing. */
    private fun openStoreListing() {
        val market = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            "market://details?id=$packageName".toUri(),
        )
        val web = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri(),
        )
        runCatching { startActivity(market) }
            .recoverCatching { startActivity(web) }
            .onFailure { Timber.w(it, "MainActivity: no app can open the store listing") }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The activity is launched singleTop by AvagoFcmService — a second
        // notification tap arrives here instead of onCreate, so we need
        // to re-forward the deep link.
        forwardChatDeepLink(intent)
        forwardLaunchIntent(intent)
    }

    private fun forwardLaunchIntent(intent: android.content.Intent?) {
        // Rental-ops push / widget taps carry an in-app route; only whitelisted shapes are honoured.
        intent?.getStringExtra(com.avago.feature.rentalsops.push.RentalOpsRoutes.EXTRA_ROUTE)
            ?.takeIf { com.avago.feature.rentalsops.push.RentalOpsRoutes.isSafe(it) }
            ?.let {
                intent.removeExtra(com.avago.feature.rentalsops.push.RentalOpsRoutes.EXTRA_ROUTE)
                mainViewModel.openRoute(it)
                return
            }
        // Rentals home-screen widget: opens a rentals screen (only rentals/* routes are accepted).
        intent?.getStringExtra(com.avago.feature.rentals.widget.RentalFleetWidget.EXTRA_OPEN)
            ?.takeIf { it.startsWith(com.avago.feature.rentals.nav.RentalsRoute.PREFIX) }
            ?.let { mainViewModel.openRoute(it) }
        // Work-order notifications (FCM and the local ones posted after a sync) carry the id.
        intent?.getStringExtra(WorkOrderNotifier.EXTRA_WORK_ORDER_ID)
            ?.takeIf { V2Uuid.isValid(it) }
            ?.let {
                intent.removeExtra(WorkOrderNotifier.EXTRA_WORK_ORDER_ID)
                mainViewModel.openRoute(com.avago.feature.workorders.nav.WorkOrderRoute.detail(it))
                return
            }
        when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> {
                // Marketplace web links, QR passport / transfer-claim links (crew) and
                // renter-portal magic links (growth).
                val link = intent.dataString
                (marketplaceRoute(intent.data)
                    ?: com.avago.feature.crew.nav.CrewRoutes.forLink(link)
                    ?: com.avago.feature.growth.nav.GrowthRoutes.forLink(link))
                    ?.let { mainViewModel.openRoute(it) }
            }
            ACTION_START_INSPECTION ->
                mainViewModel.openRoute(com.avago.feature.log.nav.LogRoute.addEdit(category = "inspection"))
            android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val assetId = nfcAssetId(intent)
                Timber.i("MainActivity: NFC tag -> asset ${assetId ?: "(unrecognised)"}")
                assetId?.let { mainViewModel.openRoute(com.avago.feature.assets.nav.AssetsRoute.detail(it)) }
            }
        }
    }

    /** https://(www.)avagomate.com/marketplace/… → marketplace route (null for other hosts/paths). */
    private fun marketplaceRoute(uri: android.net.Uri?): String? {
        if (uri == null || uri.scheme != "https" || uri.host !in MARKETPLACE_HOSTS) return null
        val query = listOf("token", "next").mapNotNull { k -> uri.getQueryParameter(k)?.let { k to it } }.toMap()
        return com.avago.feature.rentals.marketplace.nav.MarketplaceRoutes.fromWebPath(uri.path.orEmpty(), query)
            .also { Timber.i("MainActivity: marketplace link -> ${it ?: "(unsupported)"}") }
    }

    /** First asset id found in the tag: intent data URI, then URI / text / MIME records. */
    private fun nfcAssetId(intent: android.content.Intent): String? {
        AssetTagParser.assetId(intent.data?.toString())?.let { return it }
        val messages = androidx.core.content.IntentCompat.getParcelableArrayExtra(
            intent, android.nfc.NfcAdapter.EXTRA_NDEF_MESSAGES, android.nfc.NdefMessage::class.java,
        ) ?: return null
        for (message in messages.filterIsInstance<android.nfc.NdefMessage>()) {
            for (record in message.records) {
                val text = record.toUri()?.toString()
                    ?: when {
                        record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                            record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT) ->
                            AssetTagParser.decodeRtdText(record.payload)
                        record.tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA -> String(record.payload, Charsets.UTF_8)
                        else -> null
                    }
                AssetTagParser.assetId(text)?.let { return it }
            }
        }
        return null
    }

    private fun forwardChatDeepLink(intent: android.content.Intent?) {
        val threadId = intent?.getStringExtra("thread_id") ?: return
        if (threadId.isBlank()) return
        Timber.i("MainActivity: deep-link to chat thread $threadId")
        mainViewModel.openChatThread(threadId)
    }

    companion object {
        /** Static launcher shortcut action (res/xml/shortcuts.xml). */
        const val ACTION_START_INSPECTION = "com.avago.action.START_INSPECTION"
        /** Hosts of the marketplace App Links (AndroidManifest intent filter). */
        private val MARKETPLACE_HOSTS = setOf("www.avagomate.com", "avagomate.com")
    }
}
