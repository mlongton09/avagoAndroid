package com.avago.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.avago.core.ai.ScoutViewModel
import com.avago.core.ai.ui.ScoutPaletteSheet
import com.avago.core.ai.ui.VoiceInputSheet
import com.avago.core.push.SyncStatusBanner
import com.avago.core.sync.SyncConflict
import com.avago.core.sync.SyncState
import com.avago.core.ui.AvagoToast
import com.avago.core.ui.AvagoToastHost
import com.avago.core.ui.OfflineBanner
import com.avago.feature.settings.AccountSwitcherViewModel
import com.avago.feature.settings.nav.SettingsRoute
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Bottom nav descriptor
// ---------------------------------------------------------------------------

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

private val bottomNavItems = listOf(
    BottomNavItem("Assets",      Icons.Default.Inventory2,  "assets_graph"),
    BottomNavItem("Work Orders", Icons.Default.Assignment,  "workorders_graph"),
    BottomNavItem("Inventory",   Icons.Default.Widgets,     "inventory_graph"),
    BottomNavItem("Reports",     Icons.Default.BarChart,    "reports"),
    BottomNavItem("Chat",        Icons.Default.Chat,        "chat"),
)

// ---------------------------------------------------------------------------
// Root scaffold
// ---------------------------------------------------------------------------

/**
 * Root Compose scaffold: ModalNavigationDrawer (with [AccountDrawerContent]) +
 * [Scaffold] (TopAppBar + [BottomNavBar] + [AvagoNavHost] body).
 *
 * Sync feedback surfaces inside this composable:
 * - [SyncStatusBanner]  — floating pill when sync is in progress
 * - [ConflictBottomSheet] is delegated to AvagoNavHost for now so it can live
 *   close to the content that owns resolution context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    syncState: SyncState,
    conflicts: List<SyncConflict>,
    isOffline: Boolean = false,
    toast: AvagoToast,
    onAddAccount: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    scoutViewModel: ScoutViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Scout sheet visibility state
    var scoutPaletteVisible by remember { mutableStateOf(false) }
    var voiceSheetVisible by remember { mutableStateOf(false) }

    AvagoToastHost(toastManager = toast) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AccountDrawerContent(
                    onSwitchAccount = {
                        scope.launch { drawerState.close() }
                    },
                    onAddAccount = {
                        scope.launch { drawerState.close() }
                        onAddAccount()
                    },
                    onSignOut = {
                        scope.launch { drawerState.close() }
                    },
                    navController = navController,
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Avago") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                BottomNavBar(navController = navController)
            },
            floatingActionButton = {
                // Scout FAB:
                //   • Single tap  → ScoutPaletteSheet
                //   • Long press  → VoiceInputSheet (voice transcription)
                //
                // The FAB is global — it lives at the MainScaffold level so
                // it is always available regardless of which tab is active,
                // matching the iOS ScoutFAB that floats above the tab bar.
                //
                // pointerInput(detectTapGestures) is used instead of FAB's
                // built-in onClick so we can intercept the long-press gesture
                // before the FAB's ripple consumes it.
                FloatingActionButton(
                    onClick = {},   // gesture handled by pointerInput below
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { scoutPaletteVisible = true },
                            onLongPress = { voiceSheetVisible = true },
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Scout AI — tap to open, hold for voice",
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                OfflineBanner(isOffline = isOffline)
                Box(modifier = Modifier.weight(1f)) {
                    AvagoNavHost(navController = navController)

                    // Floating sync banner — sits above content, top-center.
                    SyncStatusBanner(
                        syncState = syncState,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Scout sheets (outside the Scaffold so they overlay everything)
    // ---------------------------------------------------------------------------

    ScoutPaletteSheet(
        visible = scoutPaletteVisible,
        onDismiss = { scoutPaletteVisible = false },
        onNavigate = { targetScreen, fields ->
            // Stash Scout's pre-fill fields in the back-stack entry that owns
            // the destination so the target form can read them in onResume /
            // LaunchedEffect without needing a separate shared ViewModel.
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set("scout_fields", HashMap(fields))
            navController.navigate(targetScreen) { launchSingleTop = true }
        },
        viewModel = scoutViewModel,
    )

    VoiceInputSheet(
        visible = voiceSheetVisible,
        onDismiss = { voiceSheetVisible = false },
        onTranscript = { transcript ->
            // Funnel the voice transcript into Scout as if the user had typed it.
            scoutViewModel.query(transcript)
            scoutPaletteVisible = true   // show the palette so the result is visible
        },
    )
    } // AvagoToastHost
}

// ---------------------------------------------------------------------------
// Bottom navigation bar
// ---------------------------------------------------------------------------

@Composable
fun BottomNavBar(navController: NavHostController) {
    NavigationBar {
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute?.startsWith(item.route) == true,
                onClick = {
                    navController.navigate(item.route) {
                        // Avoid accumulating a large back stack on repeated taps.
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Account drawer content
// ---------------------------------------------------------------------------

@Composable
fun AccountDrawerContent(
    viewModel: AccountSwitcherViewModel = hiltViewModel(),
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: (String) -> Unit,
    navController: NavHostController,
) {
    val accounts by viewModel.accounts.collectAsState()
    val activeId  by viewModel.activeAccountId.collectAsState()

    Column(modifier = Modifier.fillMaxHeight()) {

        // App branding header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Column {
                Text(
                    text = "Avago",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val active = accounts.firstOrNull { it.accountId == activeId }
                if (active != null) {
                    val subtitle = active.email
                        ?: active.displayName
                        ?: active.accountId
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // Accounts section header
        Text(
            text = "ACCOUNTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )

        // Account list
        accounts.forEach { account ->
            val isActive = account.accountId == activeId
            val label    = account.displayName ?: account.email ?: account.accountId

            NavigationDrawerItem(
                label = { Text(label) },
                selected = isActive,
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                badge = if (isActive) {
                    {
                        Text(
                            text = account.role?.uppercase() ?: "Active",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else if (account.role != null) {
                    val role = account.role ?: ""
                    {
                        Text(
                            text = role.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    if (!isActive) {
                        viewModel.switchTo(account.accountId)
                        onSwitchAccount(account.accountId)
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Add account
        NavigationDrawerItem(
            label = { Text("Add account") },
            selected = false,
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
            onClick = onAddAccount,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Settings link
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = navController.currentBackStackEntry?.destination?.route == SettingsRoute.Main,
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            onClick = {
                navController.navigate(SettingsRoute.GRAPH) { launchSingleTop = true }
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // Sign out active account
        NavigationDrawerItem(
            label = {
                Text(
                    text = "Sign out",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            selected = false,
            icon = {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                activeId?.let { id ->
                    viewModel.signOut(id)
                    onSignOut(id)
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
