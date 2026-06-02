package com.avago

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.avago.app.MainViewModel
import com.avago.core.auth.IdentityManager
import com.avago.core.auth.PermissionStore
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.push.NotificationPermissionHelper
import com.avago.core.sync.SyncEngine
import com.avago.core.sync.SyncGate
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

        // Trigger a full sync every time the activity resumes (foreground).
        lifecycleScope.launch {
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
            val themePreference by userPreferencesRepository.themeFlow.collectAsState(initial = "system")
            val isDark = when (themePreference) {
                "dark"  -> true
                "light" -> false
                else    -> isSystemInDarkTheme()
            }

            AvagoTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val identityReady by identityManager.isInitialized.collectAsState()
                    if (!identityReady) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val syncState by syncEngine.state.collectAsState()
                        val syncReady by syncGate.isOpen.collectAsState()
                        val isOffline by mainViewModel.isOffline.collectAsState()

                        MainScaffold(
                            syncState = syncState,
                            isOffline = isOffline,
                            syncReady = syncReady,
                            toast = mainViewModel.toast,
                            pendingNavRoute = mainViewModel.pendingNavRoute,
                            permissionStore = permissionStore,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The activity is launched singleTop by AvagoFcmService — a second
        // notification tap arrives here instead of onCreate, so we need
        // to re-forward the deep link.
        forwardChatDeepLink(intent)
    }

    private fun forwardChatDeepLink(intent: android.content.Intent?) {
        val threadId = intent?.getStringExtra("thread_id") ?: return
        if (threadId.isBlank()) return
        Timber.i("MainActivity: deep-link to chat thread $threadId")
        mainViewModel.openChatThread(threadId)
    }
}
