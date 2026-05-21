package com.avago.feature.log.nav

import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.feature.log.ui.AddEditLogScreen
import com.avago.feature.log.ui.LogDetailScreen
import com.avago.feature.log.ui.LogListScreen
import com.avago.feature.log.ui.PerformedByPickerScreen

/**
 * Type-safe route constants for the log feature.
 *
 * Routes:
 *   log/list?assetId={}                            — all logs or filtered by asset
 *   log/detail/{entryId}                           — full detail screen
 *   log/add_edit?entryId={entryId}&assetId={id}   — create or edit a log entry
 *   log/performed_by_picker                        — member picker
 */
object LogRoute {
    const val GRAPH = "log_graph"
    const val LIST = "log/list?assetId={assetId}"
    const val DETAIL = "log/detail/{entryId}"
    const val ADD_EDIT = "log/add_edit?entryId={entryId}&assetId={assetId}"
    const val PERFORMED_BY_PICKER = "log/performed_by_picker"

    fun list(assetId: String? = null) =
        "log/list?assetId=${assetId ?: ""}"

    fun detail(entryId: String) = "log/detail/$entryId"

    fun addEdit(entryId: String? = null, assetId: String? = null) =
        "log/add_edit?entryId=${entryId ?: ""}&assetId=${assetId ?: ""}"
}

/**
 * Registers the full log navigation sub-graph.
 *
 * [onOpenAssetPicker] — caller opens the global asset picker
 * and writes "selected_asset_id" to the AddEdit entry's SavedStateHandle.
 */
fun NavGraphBuilder.logNavGraph(
    navController: NavHostController,
    onOpenAssetPicker: () -> Unit = {},
) {
    navigation(
        startDestination = LogRoute.LIST,
        route = LogRoute.GRAPH,
    ) {
        // ------------------------------------------------------------------
        // Log list
        // ------------------------------------------------------------------
        composable(
            route = LogRoute.LIST,
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
            LogListScreen(
                assetId = assetId,
                onLogClick = { entryId -> navController.navigate(LogRoute.detail(entryId)) },
                onAddLog = { navController.navigate(LogRoute.addEdit(assetId = assetId)) },
            )
        }

        // ------------------------------------------------------------------
        // Log detail
        // ------------------------------------------------------------------
        composable(
            route = LogRoute.DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = requireNotNull(backStackEntry.arguments?.getString("entryId"))
            LogDetailScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(LogRoute.addEdit(entryId = id)) },
                onDeleted = { navController.popBackStack() },
            )
        }

        // ------------------------------------------------------------------
        // Add / Edit
        // ------------------------------------------------------------------
        composable(
            route = LogRoute.ADD_EDIT,
            arguments = listOf(
                navArgument("entryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("assetId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId")
                ?.takeIf { it.isNotBlank() }
            val preselectedAssetId = backStackEntry.arguments?.getString("assetId")
                ?.takeIf { it.isNotBlank() }

            // Observe results written back from sub-screens via SavedStateHandle
            val savedStateHandle = backStackEntry.savedStateHandle
            val pickedAssetId: State<String?> =
                savedStateHandle.getStateFlow<String?>("selected_asset_id", null)
                    .collectAsStateWithLifecycle()
            val pickedUserId: State<String?> =
                savedStateHandle.getStateFlow<String?>("performed_by_user_id", null)
                    .collectAsStateWithLifecycle()
            val pickedUserName: State<String?> =
                savedStateHandle.getStateFlow<String?>("performed_by_name", null)
                    .collectAsStateWithLifecycle()

            AddEditLogScreen(
                entryId = entryId,
                preselectedAssetId = preselectedAssetId ?: pickedAssetId.value,
                performedByUserId = pickedUserId.value,
                performedByName = pickedUserName.value,
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    // Navigate to detail, removing AddEdit from back-stack
                    navController.navigate(LogRoute.detail(savedId)) {
                        popUpTo(LogRoute.ADD_EDIT) { inclusive = true }
                    }
                },
                onOpenAssetPicker = onOpenAssetPicker,
                onOpenPerformedByPicker = {
                    navController.navigate(LogRoute.PERFORMED_BY_PICKER)
                },
            )
        }

        // ------------------------------------------------------------------
        // Performed By picker
        // ------------------------------------------------------------------
        composable(LogRoute.PERFORMED_BY_PICKER) {
            PerformedByPickerScreen(
                currentUserId = null,
                currentUserName = null,
                onSelected = { userId, name ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle["performed_by_user_id"] = userId
                        handle["performed_by_name"] = name
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
