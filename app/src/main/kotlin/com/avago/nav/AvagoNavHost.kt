package com.avago.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.avago.app.MainViewModel
import com.avago.core.sync.ui.SyncConflictSheet
import com.avago.core.sync.ui.SyncConflictViewModel
import com.avago.feature.assets.nav.AssetsRoute
import com.avago.feature.assets.nav.assetsNavGraph
import com.avago.feature.auth.nav.AuthRoute
import com.avago.feature.auth.nav.authNavGraph
import com.avago.feature.chat.nav.chatNavGraph
import com.avago.feature.chat.nav.ChatRoute
import com.avago.feature.docs.nav.docsNavGraph
import com.avago.feature.inventory.nav.inventoryNavGraph
import com.avago.feature.log.nav.LogRoute
import com.avago.feature.log.nav.logNavGraph
import com.avago.feature.assets.ui.GlobalCategoryReportScreen
import com.avago.feature.reports.ui.CostReportScreen
import com.avago.feature.schedule.nav.scheduleNavGraph
import com.avago.feature.settings.nav.settingsNavGraph
import com.avago.feature.workorders.nav.workOrderNavGraph
import com.avago.core.ai.ui.ScoutHistoryScreen

/**
 * App-level [NavHost].
 *
 * Embedded inside [MainScaffold]'s content slot.  The [navController] is created
 * and owned by [MainScaffold] so the drawer, bottom bar, and all feature screens
 * share the same back-stack.
 *
 * Bootstrap logic:
 * - If [IdentityManager] already has an active account when this composable first
 *   runs (i.e. the user was signed in on a previous session), the nav host
 *   immediately navigates to "assets_graph" and removes the auth graph from the
 *   back stack.
 * - If [IdentityManager.activeAccountId] becomes null while the user is inside the
 *   main app (sign-out), the nav host navigates back to the auth graph.
 */
@Composable
fun AvagoNavHost(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel(),
    conflictViewModel: SyncConflictViewModel = hiltViewModel(),
) {
    val conflicts by conflictViewModel.conflicts.collectAsStateWithLifecycle()
    val activeAccountId by mainViewModel.activeAccountId.collectAsStateWithLifecycle()
    val activeAccountIsAnonymous by mainViewModel.activeAccountIsAnonymous.collectAsStateWithLifecycle()

    // ── Bootstrap: skip sign-in for returning users ───────────────────────────
    // When the active account is set (restored from disk by initOnLaunch) and the
    // current destination is still inside the auth graph, navigate to the main app.
    // Anonymous accounts are intentionally excluded — they allow the auth screen to
    // remain visible so the user can complete a real sign-in.
    LaunchedEffect(activeAccountId, activeAccountIsAnonymous) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val inAuthGraph = currentRoute == null ||
            currentRoute == AuthRoute.GRAPH ||
            currentRoute == AuthRoute.SignIn ||
            currentRoute == AuthRoute.EmailSignIn
        if (activeAccountId != null && !activeAccountIsAnonymous && inAuthGraph) {
            navController.navigate("assets_graph") {
                popUpTo(AuthRoute.GRAPH) { inclusive = true }
            }
        } else if (activeAccountId == null && !inAuthGraph) {
            // Sign-out: navigate back to auth and clear the entire back stack.
            navController.navigate(AuthRoute.GRAPH) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = AuthRoute.GRAPH,
        ) {

            // ── Auth ──────────────────────────────────────────────────────────────
            authNavGraph(
                navController = navController,
                onSignedIn = {
                    navController.navigate("assets_graph") {
                        popUpTo(AuthRoute.GRAPH) { inclusive = true }
                    }
                },
            )

            // ── Assets ────────────────────────────────────────────────────────────
            assetsNavGraph(
                navController = navController,
                onNavigateToAddLogEntry = { assetId ->
                    navController.navigate(LogRoute.addEdit(assetId = assetId))
                },
                onNavigateToLogDetail = { entryId ->
                    navController.navigate(LogRoute.detail(entryId))
                },
                onNavigateToWorkOrder = { woId ->
                    navController.navigate("workorders/detail/$woId")
                },
                onAssetPicked = { assetId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_asset_id", assetId)
                    navController.popBackStack()
                },
            )

            // ── Log ───────────────────────────────────────────────────────────────
            logNavGraph(
                navController = navController,
                onOpenAssetPicker = {
                    navController.navigate(AssetsRoute.PICKER)
                },
            )

            // ── Work Orders ───────────────────────────────────────────────────────
            workOrderNavGraph(
                navController = navController,
                onNavigateToAssetPicker = {
                    navController.navigate(AssetsRoute.PICKER)
                },
                onNavigateToInventoryPicker = {
                    navController.navigate("inventory/picker")
                },
                onNavigateToLogWork = { assetId ->
                    navController.navigate(LogRoute.addEdit(assetId = assetId))
                },
            )

            // ── Inventory ─────────────────────────────────────────────────────────
            inventoryNavGraph(navController = navController)

            // ── Reports ───────────────────────────────────────────────────────────
            composable("reports/cost") {
                CostReportScreen(onBack = { navController.popBackStack() })
            }
            composable("category_report") {
                GlobalCategoryReportScreen(onBack = { navController.popBackStack() })
            }
            composable("scout/history") {
                ScoutHistoryScreen(onBack = { navController.popBackStack() })
            }

            // ── Schedule ──────────────────────────────────────────────────────────
            scheduleNavGraph(
                navController = navController,
                onNavigateToAssetPicker = {
                    navController.navigate(AssetsRoute.PICKER)
                },
            )

            // ── Docs ──────────────────────────────────────────────────────────────
            docsNavGraph(navController = navController)

            // ── Chat ──────────────────────────────────────────────────────────────
            // Wrap in a nested nav graph so the bottom nav item "chat" resolves to the
            // graph entry point rather than a non-existent flat route.
            navigation(startDestination = ChatRoute.List.route, route = "chat") {
                chatNavGraph(navController = navController)
            }

            // ── Settings ──────────────────────────────────────────────────────────
            settingsNavGraph(navController = navController)
        }

        // ── Sync conflict resolution overlay ─────────────────────────────────────
        // Shows a bottom sheet for the first pending conflict; user resolves one at a time.
        conflicts.firstOrNull()?.let { conflict ->
            SyncConflictSheet(
                conflict = conflict,
                conflictCount = conflicts.size,
                onKeepLocal = { conflictViewModel.keepLocal(conflict) },
                onUseServer = { conflictViewModel.acceptServer(conflict) },
                onKeepAllLocal = { conflictViewModel.keepAllLocal() },
                onUseServerAll = { conflictViewModel.acceptAllServer() },
                onDismiss = { conflictViewModel.dismiss(conflict) },
            )
        }
    }
}
