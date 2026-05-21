package com.avago.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.avago.feature.assets.nav.assetsNavGraph
import com.avago.feature.auth.SignInScreen
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
 * Start destination is "sign_in"; after a successful sign-in the host navigates to
 * "assets_graph" (the first bottom-nav tab) and pops "sign_in" off the back stack.
 */
@Composable
fun AvagoNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = "sign_in",
    ) {

        // ── Auth ──────────────────────────────────────────────────────────────
        composable("sign_in") {
            SignInScreen(
                onSignedIn = {
                    navController.navigate("assets_graph") {
                        popUpTo("sign_in") { inclusive = true }
                    }
                },
            )
        }

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
        chatNavGraph(navController = navController)

        // ── Settings ──────────────────────────────────────────────────────────
        settingsNavGraph(navController = navController)
    }
}
