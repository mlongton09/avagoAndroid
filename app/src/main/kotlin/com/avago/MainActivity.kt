package com.avago

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.sync.SyncConflictCoordinator
import com.avago.core.sync.SyncEngine
import com.avago.nav.MainScaffold
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var conflictCoordinator: SyncConflictCoordinator
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        setContent {
            val themePreference by userPreferencesRepository.themeFlow.collectAsState(initial = "system")
            val isDark = when (themePreference) {
                "dark"  -> true
                "light" -> false
                else    -> isSystemInDarkTheme()
            }

            AvagoTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val syncState by syncEngine.state.collectAsState()
                    val conflicts by conflictCoordinator.conflicts.collectAsState()

                    MainScaffold(
                        syncState = syncState,
                        conflicts = conflicts,
                    )
                }
            }
        }
    }
}
