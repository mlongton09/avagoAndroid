package com.avago.feature.workorders.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.feature.workorders.WorkOrderListScreen
import com.avago.feature.workorders.ui.AvailableJobsScreen
import com.avago.feature.workorders.ui.DispatchBoardScreen
import com.avago.feature.workorders.ui.WorkOrderCalendarScreen
import com.avago.feature.workorders.ui.WorkOrderCreateScreen
import com.avago.feature.workorders.ui.WorkOrderDetailScreen

/**
 * Type-safe route constants for the workorders feature.
 */
object WorkOrderRoute {
    const val GRAPH = "workorders_graph"
    const val LIST = "workorders/list"
    const val DETAIL = "workorders/detail/{woId}"
    const val CREATE_EDIT = "workorders/create_edit?woId={woId}"
    const val DISPATCH_BOARD = "workorders/dispatch_board"
    const val CALENDAR = "workorders/calendar"
    const val AVAILABLE_JOBS = "workorders/available_jobs"

    fun detail(woId: String) = "workorders/detail/$woId"
    fun createEdit(woId: String? = null) =
        if (woId != null) "workorders/create_edit?woId=$woId"
        else "workorders/create_edit?woId="
}

/**
 * Registers the full work orders navigation sub-graph.
 *
 * Call from the app-level NavHost:
 * ```
 * workOrderNavGraph(navController)
 * ```
 */
fun NavGraphBuilder.workOrderNavGraph(
    navController: NavHostController,
    onNavigateToAssetPicker: (returnRoute: String) -> Unit = {},
) {
    navigation(
        startDestination = WorkOrderRoute.LIST,
        route = WorkOrderRoute.GRAPH,
    ) {
        // ── List ──────────────────────────────────────────────────────────────
        composable(WorkOrderRoute.LIST) {
            WorkOrderListScreen(
                onWoClick = { woId ->
                    navController.navigate(WorkOrderRoute.detail(woId))
                },
                onCreateWo = {
                    navController.navigate(WorkOrderRoute.createEdit())
                },
            )
        }

        // ── Detail ────────────────────────────────────────────────────────────
        composable(
            route = WorkOrderRoute.DETAIL,
            arguments = listOf(navArgument("woId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val woId = requireNotNull(backStackEntry.arguments?.getString("woId"))
            WorkOrderDetailScreen(
                woId = woId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(WorkOrderRoute.createEdit(id)) },
            )
        }

        // ── Create / Edit ─────────────────────────────────────────────────────
        composable(
            route = WorkOrderRoute.CREATE_EDIT,
            arguments = listOf(
                navArgument("woId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val woId = backStackEntry.arguments?.getString("woId")
                ?.takeIf { it.isNotBlank() }

            // Observe asset selection returned from AssetPickerScreen
            val selectedAssetId = backStackEntry.savedStateHandle
                .get<String>("selected_asset_id")

            WorkOrderCreateScreen(
                woId = woId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onPickAsset = {
                    onNavigateToAssetPicker(WorkOrderRoute.createEdit(woId))
                },
            )
        }

        // ── Dispatch Board ────────────────────────────────────────────────────
        composable(WorkOrderRoute.DISPATCH_BOARD) {
            DispatchBoardScreen(
                onWoClick = { woId ->
                    navController.navigate(WorkOrderRoute.detail(woId))
                },
            )
        }

        // ── Calendar ──────────────────────────────────────────────────────────
        composable(WorkOrderRoute.CALENDAR) {
            WorkOrderCalendarScreen(
                onWoClick = { woId ->
                    navController.navigate(WorkOrderRoute.detail(woId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Available Jobs ────────────────────────────────────────────────────
        composable(WorkOrderRoute.AVAILABLE_JOBS) {
            AvailableJobsScreen(
                onBack = { navController.popBackStack() },
                onWoClick = { woId ->
                    navController.navigate(WorkOrderRoute.detail(woId))
                },
            )
        }
    }
}
