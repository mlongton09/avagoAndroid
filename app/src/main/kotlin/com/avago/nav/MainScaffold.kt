package com.avago.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.avago.core.ai.ScoutViewModel
import com.avago.core.ai.ui.ScoutPaletteSheet
import com.avago.core.ai.ui.VoiceInputSheet
import com.avago.core.push.SyncStatusBanner
import com.avago.core.sync.SyncState
import com.avago.core.ui.AvagoToast
import com.avago.core.ui.AvagoToastHost
import com.avago.core.ui.OfflineBanner
import com.avago.feature.auth.nav.AuthRoute
import com.avago.feature.inventory.nav.InventoryRoute
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
    BottomNavItem("Assets",      Icons.AutoMirrored.Filled.MenuBook, "assets_graph"),
    BottomNavItem("Work Orders", Icons.Default.CalendarToday,        "workorders_graph"),
    BottomNavItem("Chat",        Icons.AutoMirrored.Filled.Chat,     "chat"),
)

// ---------------------------------------------------------------------------
// Route → display title (matches iOS per-tab title behaviour)
// ---------------------------------------------------------------------------

private fun titleForRoute(route: String?): String = when {
    route == null                                -> "Avago"
    route.startsWith("workorders/dispatch")      -> "Dispatch"
    route.startsWith("workorders/calendar")      -> "Calendar"
    route.startsWith("workorders/available")     -> "Available Jobs"
    route.startsWith("workorders/tech_profile")  -> "My Tech Profile"
    route.startsWith("workorders")               -> "Work Orders"
    route.startsWith("assets")                   -> "Assets"
    route.startsWith("inventory")                -> "Inventory"
    route == "reports/cost"                      -> "Cost Report"
    route == "reports"                           -> "Reports"
    route == "category_report"                   -> "By Category"
    route.startsWith("chat") ||
        route.startsWith("thread")               -> "Chat"
    route.startsWith("settings")                 -> "Settings"
    route.startsWith("docs")                     -> "Docs"
    route.startsWith("schedule")                 -> "Schedule"
    else                                         -> "Avago"
}

// ---------------------------------------------------------------------------
// Root scaffold
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    syncState: SyncState,
    isOffline: Boolean = false,
    toast: AvagoToast,
    onAddAccount: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    scoutViewModel: ScoutViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var scoutPaletteVisible by remember { mutableStateOf(false) }
    var voiceSheetVisible by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isAuthDestination = currentRoute == null ||
        currentRoute == AuthRoute.SignIn ||
        currentRoute == AuthRoute.EmailSignIn ||
        currentRoute == AuthRoute.GRAPH

    AvagoToastHost(toastManager = toast) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isAuthDestination,
        drawerContent = {
            ModalDrawerSheet {
                SideMenuContent(
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    onAddAccount = {
                        scope.launch { drawerState.close() }
                        onAddAccount()
                    },
                    onSignOut = { _ ->
                        scope.launch { drawerState.close() }
                    },
                    onSignIn = {
                        scope.launch { drawerState.close() }
                        navController.navigate(AuthRoute.GRAPH) { launchSingleTop = true }
                    },
                    navController = navController,
                )
            }
        },
    ) {
        Scaffold(
            floatingActionButtonPosition = FabPosition.Start,
            topBar = {
                if (!isAuthDestination) {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(titleForRoute(currentRoute))
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            },
            bottomBar = {
                if (!isAuthDestination) {
                    BottomNavBar(navController = navController)
                }
            },
            floatingActionButton = {
                if (!isAuthDestination) {
                    FloatingActionButton(
                        onClick = {},
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { scoutPaletteVisible = true },
                                onLongPress = { voiceSheetVisible = true },
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Scout AI — tap to open, hold for voice",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (!isAuthDestination) {
                    OfflineBanner(isOffline = isOffline)
                }
                Box(modifier = Modifier.weight(1f)) {
                    AvagoNavHost(navController = navController)
                    if (!isAuthDestination) {
                        SyncStatusBanner(
                            syncState = syncState,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
    }

    if (!isAuthDestination) {
        ScoutPaletteSheet(
            visible = scoutPaletteVisible,
            onDismiss = { scoutPaletteVisible = false },
            onNavigate = { targetScreen, fields ->
                scoutViewModel.dispatchFormFill(targetScreen, fields)
                scoutPaletteVisible = false
                navController.navigate(targetScreen) { launchSingleTop = true }
            },
            viewModel = scoutViewModel,
        )

        VoiceInputSheet(
            visible = voiceSheetVisible,
            onDismiss = { voiceSheetVisible = false },
            onTranscript = { transcript ->
                scoutViewModel.query(transcript)
                scoutPaletteVisible = true
            },
        )
    }
    } // AvagoToastHost
}

// ---------------------------------------------------------------------------
// Bottom navigation bar
// ---------------------------------------------------------------------------

@Composable
fun BottomNavBar(navController: NavHostController) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route || currentRoute?.startsWith("${item.route}/") == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = null,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Side menu drawer content — matches iOS SideMenuViewController structure
// ---------------------------------------------------------------------------

@Composable
fun SideMenuContent(
    viewModel: AccountSwitcherViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: (String) -> Unit,
    onSignIn: () -> Unit = {},
    navController: NavHostController,
) {
    val accounts by viewModel.accounts.collectAsState()
    val activeId  by viewModel.activeAccountId.collectAsState()
    val active = accounts.firstOrNull { it.accountId == activeId }

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    var showAccountSwitcher by remember { mutableStateOf(false) }

    if (showAccountSwitcher) {
        AlertDialog(
            onDismissRequest = { showAccountSwitcher = false },
            title = { Text("Switch Account") },
            text = {
                Column {
                    accounts.forEach { account ->
                        val label = when {
                            account.isAnonymous -> "Guest"
                            !account.accountName.isNullOrBlank() -> account.accountName!!
                            !account.email.isNullOrBlank() -> account.email!!
                            !account.displayName.isNullOrBlank() -> account.displayName!!
                            else -> "Unknown Account"
                        }
                        val isActive = account.accountId == activeId
                        TextButton(
                            onClick = {
                                if (!isActive) viewModel.switchTo(account.accountId)
                                showAccountSwitcher = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (isActive) "✓ $label" else label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    TextButton(
                        onClick = {
                            showAccountSwitcher = false
                            onAddAccount()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add Account")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountSwitcher = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = "Avago",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = when {
                active == null || active.isAnonymous -> "Not signed in"
                !active.email.isNullOrBlank() -> active.email!!
                !active.displayName.isNullOrBlank() -> active.displayName!!
                else -> "Not signed in"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        HorizontalDivider()

        // ── ACCOUNT ──────────────────────────────────────────────────────────
        DrawerSectionHeader("ACCOUNT")

        // Show Sign In when anonymous or not signed in (mirrors iOS SideMenuViewController)
        if (active == null || active.isAnonymous) {
            NavigationDrawerItem(
                label = { Text("Sign In") },
                selected = false,
                icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                onClick = onSignIn,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        NavigationDrawerItem(
            label = { Text("Switch Account") },
            selected = false,
            icon = { Icon(Icons.Default.ManageAccounts, contentDescription = null) },
            onClick = { showAccountSwitcher = true },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        if (active != null && !active.isAnonymous) {
            NavigationDrawerItem(
                label = { Text("Invite Users") },
                selected = false,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                onClick = { onNavigate(SettingsRoute.GRAPH) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            val signOutLabel = "Sign Out of ${
                when {
                    !active.accountName.isNullOrBlank() -> active.accountName!!
                    !active.displayName.isNullOrBlank() -> active.displayName!!
                    !active.email.isNullOrBlank() -> active.email!!
                    else -> "this account"
                }
            }"
            NavigationDrawerItem(
                label = { Text(signOutLabel, color = MaterialTheme.colorScheme.error) },
                selected = false,
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
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
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── REPORTS ──────────────────────────────────────────────────────────
        DrawerSectionHeader("REPORTS")

        NavigationDrawerItem(
            label = { Text("Cost Report") },
            selected = currentRoute == "reports/cost",
            icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
            onClick = { onNavigate("reports/cost") },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        NavigationDrawerItem(
            label = { Text("By Category") },
            selected = currentRoute == "category_report",
            icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
            onClick = { onNavigate("category_report") },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── INVENTORY ────────────────────────────────────────────────────────
        DrawerSectionHeader("INVENTORY")

        NavigationDrawerItem(
            label = { Text("Inventory") },
            selected = currentRoute?.startsWith("inventory") == true &&
                currentRoute?.startsWith("inventory/purchase-orders") == false,
            icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
            onClick = { onNavigate(InventoryRoute.List.route) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        NavigationDrawerItem(
            label = { Text("Purchase Orders") },
            selected = currentRoute?.startsWith("inventory/purchase-orders") == true,
            icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
            onClick = { onNavigate(InventoryRoute.PurchaseOrderList.route) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── Settings ─────────────────────────────────────────────────────────
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = currentRoute?.startsWith("settings") == true,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = { onNavigate(SettingsRoute.GRAPH) },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
    )
}
