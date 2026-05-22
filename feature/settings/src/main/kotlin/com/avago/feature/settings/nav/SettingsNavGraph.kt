package com.avago.feature.settings.nav

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.avago.feature.settings.DeveloperScreen
import com.avago.feature.settings.InviteUsersScreen
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
    const val InviteUsers = "settings/invite"
    const val Developer = "settings/developer"
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
                onNavigateToInvite   = { navController.navigate(SettingsRoute.InviteUsers) },
                onNavigateToDeveloper = { navController.navigate(SettingsRoute.Developer) },
            )
        }

        composable(SettingsRoute.Members) {
            MembersListScreen()
        }

        composable(SettingsRoute.Licenses) {
            LicensesScreen()
        }

        composable(SettingsRoute.InviteUsers) {
            InviteUsersScreen(onBack = { navController.popBackStack() })
        }

        composable(SettingsRoute.Developer) {
            DeveloperScreen(onBack = { navController.popBackStack() })
        }
    }
}
