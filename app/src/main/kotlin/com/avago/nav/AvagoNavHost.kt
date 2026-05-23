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
import com.avago.feature.assets.nav.assetsNavGraph
import com.avago.feature.auth.nav.AuthRoute
import com.avago.feature.auth.nav.authNavGraph
import com.avago.feature.chat.nav.chatNavGraph
import com.avago.feature.chat.nav.ChatRoute
import com.avago.feature.docs.nav.docsNavGraph
import com.avago.feature.inventory.nav.inventoryNavGraph
import com.avago.feature.reports.ReportsScreen
import com.avago.feature.schedule.nav.scheduleNavGraph
import com.avago.feature.settings.nav.settingsNavGraph
import com.avago.feature.workorders.nav.workOrderNavGraph

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

    // ── Bootstrap: skip sign-in for returning users ───────────────────────────
    // When the active account is set (restored from disk by initOnLaunch) and the
    // current destination is still inside the auth graph, navigate to the main app.
    LaunchedEffect(activeAccountId) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val inAuthGraph = currentRoute == null ||
            currentRoute == AuthRoute.GRAPH ||
            currentRoute == AuthRoute.SignIn ||
            currentRoute == AuthRoute.EmailSignIn
        if (activeAccountId != null && inAuthGraph) {
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
            assetsNavGraph(navController = navController)

            // ── Work Orders ───────────────────────────────────────────────────────
            workOrderNavGraph(navController = navController)

            // ── Inventory ─────────────────────────────────────────────────────────
            inventoryNavGraph(navController = navController)

            // ── Reports ───────────────────────────────────────────────────────────
            composable("reports") {
                ReportsScreen()
            }

            // ── Schedule ──────────────────────────────────────────────────────────
            scheduleNavGraph(navController = navController)

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
                onKeepLocal = { conflictViewModel.keepLocal(conflict) },
                onUseServer = { conflictViewModel.acceptServer(conflict) },
                onDismiss = { conflictViewModel.dismiss(conflict) },
            )
        }
    }
}
