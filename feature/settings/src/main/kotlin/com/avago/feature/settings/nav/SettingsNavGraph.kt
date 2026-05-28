package com.avago.feature.settings.nav

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.avago.feature.settings.AboutScreen
import com.avago.feature.settings.DeveloperScreen
import com.avago.feature.settings.LegalTextScreen
import com.avago.feature.settings.LegalTextType
import com.avago.feature.settings.InviteUsersScreen
import com.avago.feature.settings.LicensesScreen
import com.avago.feature.settings.MembersListScreen
import com.avago.feature.settings.MyTechProfileScreen
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
    const val About = "settings/about"
    const val TechProfile = "settings/tech_profile"
    const val PrivacyPolicy = "settings/legal/privacy"
    const val TermsOfService = "settings/legal/terms"
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
                onNavigateToMembers    = { navController.navigate(SettingsRoute.Members) },
                onNavigateToLicenses   = { navController.navigate(SettingsRoute.Licenses) },
                onNavigateToInvite     = { navController.navigate(SettingsRoute.InviteUsers) },
                onNavigateToDeveloper  = { navController.navigate(SettingsRoute.Developer) },
                onNavigateToAbout      = { navController.navigate(SettingsRoute.About) },
                onNavigateToTechProfile = { navController.navigate(SettingsRoute.TechProfile) },
            )
        }

        composable(SettingsRoute.Members) {
            MembersListScreen(onBack = { navController.popBackStack() })
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

        composable(SettingsRoute.About) {
            AboutScreen(
                onNavigateToLicenses = { navController.navigate(SettingsRoute.Licenses) },
                onNavigateToPrivacyPolicy = { navController.navigate(SettingsRoute.PrivacyPolicy) },
                onNavigateToTerms = { navController.navigate(SettingsRoute.TermsOfService) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(SettingsRoute.PrivacyPolicy) {
            LegalTextScreen(
                type = LegalTextType.PRIVACY_POLICY,
                onBack = { navController.popBackStack() },
            )
        }

        composable(SettingsRoute.TermsOfService) {
            LegalTextScreen(
                type = LegalTextType.TERMS_OF_SERVICE,
                onBack = { navController.popBackStack() },
            )
        }

        composable(SettingsRoute.TechProfile) {
            MyTechProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
