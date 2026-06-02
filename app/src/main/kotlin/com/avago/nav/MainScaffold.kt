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
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.avago.R
import com.avago.core.ai.ScoutViewModel
import com.avago.core.ai.R as AiR
import com.avago.core.ai.ui.ScoutPaletteSheet
import com.avago.core.ai.ui.VoiceInputSheet
import com.avago.core.auth.PermissionStore
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
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val route: String,
    val permission: String,
)

private val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_assets,      Icons.AutoMirrored.Filled.MenuBook, "assets_graph",      "assets.view"),
    BottomNavItem(R.string.nav_work_orders, Icons.Default.CalendarToday,        "workorders_graph",  "work_orders.view"),
    BottomNavItem(R.string.nav_chat,        Icons.AutoMirrored.Filled.Chat,     "chat",              "chat.view"),
)

// ---------------------------------------------------------------------------
// Route → display title (matches iOS per-tab title behaviour)
// ---------------------------------------------------------------------------

private fun titleForRoute(route: String?): Int = when {
    route == null                                -> R.string.app_name
    route.startsWith("workorders/dispatch")      -> R.string.nav_dispatch
    route.startsWith("workorders/calendar")      -> R.string.nav_calendar
    route.startsWith("workorders/available")     -> R.string.nav_available_jobs
    route.startsWith("workorders/tech_profile")  -> R.string.nav_my_tech_profile
    route.startsWith("workorders")               -> R.string.nav_work_orders
    route.startsWith("assets")                   -> R.string.nav_assets
    route == "gallery"                           -> R.string.nav_gallery
    route.startsWith("inventory")                -> R.string.nav_inventory
    route == "reports/cost"                      -> R.string.nav_cost_report
    route == "reports"                           -> R.string.nav_reports
    route == "category_report"                   -> R.string.nav_by_category
    route.startsWith("chat") ||
        route.startsWith("thread")               -> R.string.nav_chat
    route.startsWith("settings")                 -> R.string.nav_settings
    route.startsWith("schedule")                 -> R.string.nav_schedule
    else                                         -> R.string.app_name
}

// ---------------------------------------------------------------------------
// Root scaffold
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    syncState: SyncState,
    isOffline: Boolean = false,
    syncReady: Boolean = true,
    toast: AvagoToast,
    onAddAccount: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    scoutViewModel: ScoutViewModel = hiltViewModel(),
    navFlagsViewModel: NavFlagsViewModel = hiltViewModel(),
    pendingNavRoute: kotlinx.coroutines.flow.Flow<String>? = null,
    permissionStore: PermissionStore,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var scoutPaletteVisible by remember { mutableStateOf(false) }
    var voiceSheetVisible by remember { mutableStateOf(false) }

    // Consume push-notification deep links: when AvagoFcmService taps
    // arrive, MainViewModel posts a route on this flow, and we navigate
    // through the same NavController used by the rest of the app.
    androidx.compose.runtime.LaunchedEffect(pendingNavRoute, navController) {
        pendingNavRoute?.collect { route ->
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    val chatEnabled by navFlagsViewModel.chatEnabled.collectAsState()
    val workOrdersEnabled by navFlagsViewModel.workOrdersEnabled.collectAsState()
    val unreadChatMentionCount by navFlagsViewModel.unreadChatMentionCount.collectAsState()
    val upcomingMineWorkOrderCount by navFlagsViewModel.upcomingMineWorkOrderCount.collectAsState()
    val permissions by permissionStore.permissions.collectAsState()
    val isRoot by permissionStore.isRoot.collectAsState()
    fun can(permission: String) = isRoot || permissions.contains(permission)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isAuthDestination = currentRoute == null ||
        currentRoute == AuthRoute.SignIn ||
        currentRoute == AuthRoute.EmailSignIn ||
        currentRoute == AuthRoute.GRAPH
    // iOS parity: the Scout FAB is anchored to the tab bar, which UIKit hides
    // on pushed detail VCs like ThreadViewController/SubthreadViewController.
    // On Android we mirror that by suppressing the FAB on chat thread + subthread
    // routes (anything matching "chat/thread/...").
    val isChatThreadDestination = currentRoute?.startsWith("chat/thread/") == true
    // iOS parity: AssetDetailViewController hides the navigation bar
    // (setNavigationBarHidden) and the tab bar (hidesBottomBarWhenPushed)
    // and provides its own custom 44pt nav row above the photo banner.
    // The log list, when launched from a specific asset, is the Android
    // equivalent — suppress the outer chrome so its own custom header sits
    // flush against the photo.
    val isAssetLogDestination = currentRoute?.startsWith("log/list") == true

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
                    canNavigate = ::can,
                )
            }
        },
    ) {
        Scaffold(
            floatingActionButtonPosition = FabPosition.Start,
            topBar = {
                if (!isAuthDestination && !isAssetLogDestination) {
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
                                Text(stringResource(titleForRoute(currentRoute)))
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = null)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            },
            bottomBar = {
                if (!isAuthDestination && !isAssetLogDestination) {
                    BottomNavBar(
                        navController = navController,
                        workOrdersEnabled = workOrdersEnabled,
                        chatEnabled = chatEnabled,
                        unreadChatMentionCount = unreadChatMentionCount,
                        upcomingMineWorkOrderCount = upcomingMineWorkOrderCount,
                        canNavigate = ::can,
                    )
                }
            },
            floatingActionButton = {
                if (!isAuthDestination && !isChatThreadDestination && can("scout.view")) {
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
                            contentDescription = stringResource(AiR.string.scout_open),
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
                    if (!isAuthDestination && !syncReady) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AvagoNavHost(navController = navController)
                    }
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
fun BottomNavBar(
    navController: NavHostController,
    workOrdersEnabled: Boolean = true,
    chatEnabled: Boolean = true,
    unreadChatMentionCount: Int = 0,
    upcomingMineWorkOrderCount: Int = 0,
    canNavigate: (String) -> Boolean = { true },
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        val visibleItems = bottomNavItems.filter { item ->
            when (item.route) {
                "workorders_graph" -> workOrdersEnabled && canNavigate(item.permission)
                "chat"            -> chatEnabled && canNavigate(item.permission)
                else              -> canNavigate(item.permission)
            }
        }

        visibleItems.forEach { item ->
            val itemLabel = stringResource(item.labelRes)
            val badgeCount = when (item.route) {
                "chat"             -> unreadChatMentionCount
                "workorders_graph" -> upcomingMineWorkOrderCount
                else               -> 0
            }
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
                icon = {
                    if (badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    val label = if (badgeCount > 99) "99+" else badgeCount.toString()
                                    Text(label)
                                }
                            },
                        ) {
                            Icon(item.icon, contentDescription = itemLabel)
                        }
                    } else {
                        Icon(item.icon, contentDescription = itemLabel)
                    }
                },
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
    canNavigate: (String) -> Boolean = { true },
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
            title = { Text(stringResource(R.string.drawer_switch_account)) },
            text = {
                Column {
                    accounts.forEach { account ->
                        val label = when {
                            account.isAnonymous -> stringResource(R.string.drawer_guest)
                            !account.accountName.isNullOrBlank() -> account.accountName!!
                            !account.email.isNullOrBlank() -> account.email!!
                            !account.displayName.isNullOrBlank() -> account.displayName!!
                            else -> stringResource(R.string.drawer_unknown_account)
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
                                text = if (isActive) stringResource(R.string.drawer_current_account, label) else label,
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
                        Text(stringResource(R.string.drawer_add_account))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountSwitcher = false }) { Text(stringResource(R.string.drawer_cancel)) }
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
                text = stringResource(R.string.sidemenu_app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = when {
                active == null || active.isAnonymous -> stringResource(R.string.drawer_not_signed_in)
                !active.email.isNullOrBlank() -> active.email!!
                !active.displayName.isNullOrBlank() -> active.displayName!!
                else -> stringResource(R.string.drawer_not_signed_in)
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
        DrawerSectionHeader(stringResource(R.string.drawer_header_account))

        // Show Sign In when anonymous or not signed in (mirrors iOS SideMenuViewController)
        if (active == null || active.isAnonymous) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_sign_in)) },
                selected = false,
                icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                onClick = onSignIn,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_switch_account)) },
            selected = false,
            icon = { Icon(Icons.Default.ManageAccounts, contentDescription = null) },
            onClick = { showAccountSwitcher = true },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        if (active != null && !active.isAnonymous) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_invite_users)) },
                selected = false,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                onClick = { onNavigate(SettingsRoute.GRAPH) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            val signOutAccountLabel = when {
                    !active.accountName.isNullOrBlank() -> active.accountName!!
                    !active.displayName.isNullOrBlank() -> active.displayName!!
                    !active.email.isNullOrBlank() -> active.email!!
                    else -> stringResource(R.string.drawer_this_account)
                }
            val signOutLabel = stringResource(R.string.drawer_sign_out_of, signOutAccountLabel)
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
        DrawerSectionHeader(stringResource(R.string.drawer_header_reports))

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_cost_report)) },
            selected = currentRoute == "reports/cost",
            icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
            onClick = { onNavigate("reports/cost") },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_by_category)) },
            selected = currentRoute == "category_report",
            icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
            onClick = { onNavigate("category_report") },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        if (canNavigate("assets.view")) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_gallery)) },
                selected = currentRoute == "gallery",
                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                onClick = { onNavigate("gallery") },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (canNavigate("inventory.view")) {
            // ── INVENTORY ────────────────────────────────────────────────────────
            DrawerSectionHeader(stringResource(R.string.drawer_header_inventory))

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_inventory)) },
                selected = currentRoute?.startsWith("inventory") == true &&
                    currentRoute?.startsWith("inventory/purchase-orders") == false,
                icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                onClick = { onNavigate(InventoryRoute.List.route) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_purchase_orders)) },
                selected = currentRoute?.startsWith("inventory/purchase-orders") == true,
                icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                onClick = { onNavigate(InventoryRoute.PurchaseOrderList.route) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        // ── Settings ─────────────────────────────────────────────────────────
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_settings)) },
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
