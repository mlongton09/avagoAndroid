package com.avago.feature.settings.nav

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.avago.core.ui.LocationPickerScreen
import com.avago.feature.settings.BuildConfig
import com.avago.feature.settings.AboutScreen
import com.avago.feature.settings.DeveloperScreen
import com.avago.feature.settings.LegalTextScreen
import com.avago.feature.settings.LegalTextType
import com.avago.feature.settings.InviteUsersScreen
import com.avago.feature.settings.MyTechProfileScreen
import com.avago.feature.settings.CustomFieldDefsScreen
import com.avago.feature.settings.PermissionSetsScreen
import com.avago.feature.settings.SettingsScreen
import com.avago.feature.settings.SyncConflictsScreen

/**
 * Route constants for the settings nested graph.
 */
object SettingsRoute {
    const val GRAPH  = "settings_graph"
    const val Main   = "settings"
    const val InviteUsers = "settings/invite"
    const val Developer = "settings/developer"
    const val About = "settings/about"
    const val TechProfile = "settings/tech_profile"
    const val TechProfileLocationPicker = "settings/tech_profile/location_picker?currentId={currentId}"
    const val SyncConflicts = "settings/sync_conflicts"
    const val PrivacyPolicy = "settings/legal/privacy"
    const val TermsOfService = "settings/legal/terms"
    const val PermissionSets = "settings/permission_sets"
    const val CustomFields = "settings/custom_fields"
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
                onNavigateToInvite        = { navController.navigate(SettingsRoute.InviteUsers) },
                onNavigateToDeveloper     = { navController.navigate(SettingsRoute.Developer) },
                onNavigateToAbout         = { navController.navigate(SettingsRoute.About) },
                onNavigateToTechProfile   = { navController.navigate(SettingsRoute.TechProfile) },
                onNavigateToSyncConflicts = { navController.navigate(SettingsRoute.SyncConflicts) },
                onNavigateToPermissionSets = { navController.navigate(SettingsRoute.PermissionSets) },
                onNavigateToCustomFields  = { navController.navigate(SettingsRoute.CustomFields) },
            )
        }

        composable(SettingsRoute.InviteUsers) {
            InviteUsersScreen(onBack = { navController.popBackStack() })
        }

        if (BuildConfig.DEBUG) {
            composable(SettingsRoute.Developer) {
                DeveloperScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(SettingsRoute.About) {
            AboutScreen(
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

        composable(SettingsRoute.TechProfile) { backStackEntry ->
            val selectedLocationId = backStackEntry.savedStateHandle.get<String>("selected_location_id")
            val selectedLocationName = backStackEntry.savedStateHandle.get<String>("selected_location_name")
            MyTechProfileScreen(
                onBack = { navController.popBackStack() },
                onPickLocation = { currentId ->
                    val route = SettingsRoute.TechProfileLocationPicker
                        .replace("{currentId}", currentId ?: "")
                    navController.navigate(route)
                },
                selectedLocationId = selectedLocationId,
                selectedLocationName = selectedLocationName,
            )
        }

        composable(
            route = SettingsRoute.TechProfileLocationPicker,
            arguments = listOf(
                navArgument("currentId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val currentId = entry.arguments?.getString("currentId")?.takeIf { it.isNotBlank() }
            LocationPickerScreen(
                currentLocationId = currentId,
                onLocationSelected = { id, name ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_location_id", id)
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_location_name", name)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(SettingsRoute.SyncConflicts) {
            SyncConflictsScreen(onBack = { navController.popBackStack() })
        }

        composable(SettingsRoute.PermissionSets) {
            PermissionSetsScreen(onBack = { navController.popBackStack() })
        }

        composable(SettingsRoute.CustomFields) {
            CustomFieldDefsScreen(onBack = { navController.popBackStack() })
        }
    }
}
