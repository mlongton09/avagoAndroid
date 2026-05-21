package com.avago.feature.settings.nav

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.avago.feature.settings.LicensesScreen
import com.avago.feature.settings.MembersListScreen
import com.avago.feature.settings.SettingsScreen

/**
 * Route constants for the settings nested graph.
 */
object SettingsRoute {
    const val GRAPH  = "settings_graph"
    const val Main   = "settings"
    const val Members = "settings/members"
    const val Licenses = "settings/licenses"
}

/**
 * Registers the Settings nested nav graph with [navController] as the back-stack owner.
 *
 * Usage in [AvagoNavHost]:
 * ```
 * settingsNavGraph(navController = navController)
 * ```
 */
fun NavGraphBuilder.settingsNavGraph(navController: NavController) {
    navigation(
        startDestination = SettingsRoute.Main,
        route = SettingsRoute.GRAPH,
    ) {
        composable(SettingsRoute.Main) {
            SettingsScreen(
                onNavigateToMembers  = { navController.navigate(SettingsRoute.Members) },
                onNavigateToLicenses = { navController.navigate(SettingsRoute.Licenses) },
            )
        }

        composable(SettingsRoute.Members) {
            MembersListScreen()
        }

        composable(SettingsRoute.Licenses) {
            LicensesScreen()
        }
    }
}
