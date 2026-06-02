package com.avago.feature.schedule.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.feature.schedule.ui.AddEditScheduleScreen
import com.avago.feature.schedule.ui.ScheduleDetailScreen
import com.avago.feature.schedule.ui.ScheduleListScreen

/**
 * Type-safe route constants for the schedule feature.
 *
 * Route patterns follow the convention already established by the workorders graph:
 * fixed segments + path / query parameters.
 */
object ScheduleRoute {
    const val GRAPH = "schedule_graph"

    /** Global list — all schedules for the active account. */
    const val LIST = "schedule/list"

    /** List scoped to a single asset. */
    const val ASSET_SCHEDULES = "schedule/asset/{assetId}"

    /** Detail view for a single schedule. */
    const val DETAIL = "schedule/detail/{scheduleId}"

    /**
     * Add-or-edit form.
     * - `scheduleId` is null for new schedules.
     * - `assetId` is non-null when creating from an asset's schedule list.
     */
    const val ADD_EDIT =
        "schedule/add_edit?scheduleId={scheduleId}&assetId={assetId}"

    /** Full-account PM calendar. */
    const val CALENDAR = "schedule/calendar"

    // ── Helpers ──────────────────────────────────────────────────────────────

    fun assetSchedules(assetId: String) = "schedule/asset/$assetId"

    fun detail(scheduleId: String) = "schedule/detail/$scheduleId"

    fun addEdit(
        scheduleId: String? = null,
        assetId: String? = null,
    ) = "schedule/add_edit?scheduleId=${scheduleId ?: ""}&assetId=${assetId ?: ""}"
}

/**
 * Registers the full schedule navigation sub-graph.
 *
 * Callers must supply [onNavigateToAssetPicker] — the schedule feature has no
 * knowledge of the asset picker's route; the host graph is responsible for wiring it up.
 *
 * ```kotlin
 * // In the app NavHost:
 * scheduleNavGraph(
 *     navController = navController,
 *     onNavigateToAssetPicker = { returnRoute ->
 *         navController.navigate("assets/picker?returnRoute=$returnRoute")
 *     },
 * )
 * ```
 */
fun NavGraphBuilder.scheduleNavGraph(
    navController: NavHostController,
    onNavigateToAssetPicker: (returnRoute: String) -> Unit = {},
    onNavigateToAddLogEntry: (assetId: String) -> Unit = {},
) {
    navigation(
        startDestination = ScheduleRoute.LIST,
        route = ScheduleRoute.GRAPH,
    ) {
        // ── Global list ───────────────────────────────────────────────────────
        composable(ScheduleRoute.LIST) {
            ScheduleListScreen(
                assetId = null,
                onScheduleClick = { scheduleId ->
                    navController.navigate(ScheduleRoute.detail(scheduleId))
                },
                onAddSchedule = {
                    navController.navigate(ScheduleRoute.addEdit())
                },
            )
        }

        // ── Asset-scoped list ─────────────────────────────────────────────────
        composable(
            route = ScheduleRoute.ASSET_SCHEDULES,
            arguments = listOf(
                navArgument("assetId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val assetId = requireNotNull(backStackEntry.arguments?.getString("assetId"))
            ScheduleListScreen(
                assetId = assetId,
                onScheduleClick = { scheduleId ->
                    navController.navigate(ScheduleRoute.detail(scheduleId))
                },
                onAddSchedule = {
                    navController.navigate(ScheduleRoute.addEdit(assetId = assetId))
                },
            )
        }

        // ── Detail ────────────────────────────────────────────────────────────
        composable(
            route = ScheduleRoute.DETAIL,
            arguments = listOf(
                navArgument("scheduleId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val scheduleId =
                requireNotNull(backStackEntry.arguments?.getString("scheduleId"))
            ScheduleDetailScreen(
                scheduleId = scheduleId,
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate(ScheduleRoute.addEdit(scheduleId = id))
                },
                onCompleteService = onNavigateToAddLogEntry,
            )
        }

        // ── Add / Edit ────────────────────────────────────────────────────────
        composable(
            route = ScheduleRoute.ADD_EDIT,
            arguments = listOf(
                navArgument("scheduleId") {
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
            val scheduleId = backStackEntry.arguments?.getString("scheduleId")
                ?.takeIf { it.isNotBlank() }
            val assetId = backStackEntry.arguments?.getString("assetId")
                ?.takeIf { it.isNotBlank() }

            // Receive asset selection returned from the picker
            val selectedAssetId = backStackEntry.savedStateHandle.get<String>("selected_asset_id")
            val selectedAssetName =
                backStackEntry.savedStateHandle.get<String>("selected_asset_name") ?: ""

            AddEditScheduleScreen(
                scheduleId = scheduleId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onPickAsset = {
                    onNavigateToAssetPicker(ScheduleRoute.addEdit(scheduleId, assetId))
                },
            )
        }

    }
}
