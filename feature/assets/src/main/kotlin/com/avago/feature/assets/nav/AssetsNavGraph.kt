package com.avago.feature.assets.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.feature.assets.ui.AddEditAssetScreen
import com.avago.feature.assets.ui.AssetDetailScreen
import com.avago.feature.assets.ui.AssetListScreen
import com.avago.feature.assets.ui.AssetPickerScreen
import com.avago.feature.assets.ui.AssetTypePickerScreen

/**
 * Type-safe route constants for the assets feature.
 */
object AssetsRoute {
    const val GRAPH = "assets_graph"
    const val LIST = "assets/list"
    const val DETAIL = "assets/detail/{assetId}"
    const val ADD_EDIT = "assets/add_edit?assetId={assetId}"
    const val TYPE_PICKER = "assets/type_picker"
    const val PICKER = "assets/picker"

    fun detail(assetId: String) = "assets/detail/$assetId"
    fun addEdit(assetId: String? = null) =
        if (assetId != null) "assets/add_edit?assetId=$assetId" else "assets/add_edit?assetId="
}

/**
 * Registers the full assets navigation sub-graph.
 *
 * Call this from the app-level NavHost:
 * ```
 * assetsNavGraph(navController, onNavigateToLogEntry = { ... })
 * ```
 */
fun NavGraphBuilder.assetsNavGraph(
    navController: NavHostController,
    onNavigateToAddLogEntry: (assetId: String) -> Unit = {},
    onNavigateToLogDetail: (entryId: String) -> Unit = {},
    onAssetPicked: (assetId: String) -> Unit = {},
) {
    navigation(
        startDestination = AssetsRoute.LIST,
        route = AssetsRoute.GRAPH,
    ) {
        composable(AssetsRoute.LIST) {
            AssetListScreen(
                onAssetClick = { assetId ->
                    navController.navigate(AssetsRoute.detail(assetId))
                },
                onAddAsset = {
                    navController.navigate(AssetsRoute.addEdit())
                },
            )
        }

        composable(
            route = AssetsRoute.DETAIL,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assetId = requireNotNull(backStackEntry.arguments?.getString("assetId"))
            AssetDetailScreen(
                assetId = assetId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AssetsRoute.addEdit(assetId)) },
                onAddLogEntry = { onNavigateToAddLogEntry(assetId) },
                onLogEntryClick = { entryId -> onNavigateToLogDetail(entryId) },
            )
        }

        composable(
            route = AssetsRoute.ADD_EDIT,
            arguments = listOf(
                navArgument("assetId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getString("assetId")
                ?.takeIf { it.isNotBlank() }
            AddEditAssetScreen(
                assetId = assetId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onOpenTypePicker = { navController.navigate(AssetsRoute.TYPE_PICKER) },
                navController = navController,
            )
        }

        composable(AssetsRoute.TYPE_PICKER) {
            AssetTypePickerScreen(
                onTypeSelected = { typeKey ->
                    // Return the selected type back to the AddEditAsset screen via SavedStateHandle
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_asset_type", typeKey)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(AssetsRoute.PICKER) {
            AssetPickerScreen(
                onAssetSelected = { assetId ->
                    onAssetPicked(assetId)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
