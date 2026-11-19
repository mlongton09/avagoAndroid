package com.avago.feature.schedule.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.feature.schedule.ui.GlobalCalendarScreen
import com.avago.feature.schedule.ui.ScheduleDetailScreen
import com.avago.feature.schedule.ui.ScheduleListScreen

/** Routes of the PM schedule feature. A "schedule" is one PM occurrence (one cycle of a PM plan). */
object ScheduleRoute {
    const val GRAPH = "schedule_graph"

    /** All open occurrences of the account. */
    const val LIST = "schedule/list"

    /** Open occurrences of one asset. */
    const val ASSET_SCHEDULES = "schedule/asset/{assetId}"

    /** One occurrence; `scheduleId` is the occurrence id. */
    const val DETAIL = "schedule/detail/{scheduleId}"

    /** Month calendar of open occurrences. */
    const val CALENDAR = "schedule/calendar"

    fun assetSchedules(assetId: String) = "schedule/asset/$assetId"

    fun detail(scheduleId: String) = "schedule/detail/$scheduleId"
}

/**
 * Registers the schedule sub-graph. The host wires the cross-feature destinations:
 * [onNavigateToAddLogEntry] (log a service for an asset) and [onOpenWorkOrder] (work order detail).
 */
fun NavGraphBuilder.scheduleNavGraph(
    navController: NavHostController,
    onNavigateToAddLogEntry: (assetId: String) -> Unit = {},
    onOpenWorkOrder: (workOrderId: String) -> Unit = {},
) {
    val openDetail: (String) -> Unit = { navController.navigate(ScheduleRoute.detail(it)) }
    val openCalendar: () -> Unit = { navController.navigate(ScheduleRoute.CALENDAR) }
    navigation(startDestination = ScheduleRoute.LIST, route = ScheduleRoute.GRAPH) {
        composable(ScheduleRoute.LIST) {
            ScheduleListScreen(assetId = null, onScheduleClick = openDetail, onOpenCalendar = openCalendar)
        }

        composable(
            route = ScheduleRoute.ASSET_SCHEDULES,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            ScheduleListScreen(
                assetId = requireNotNull(backStackEntry.arguments?.getString("assetId")),
                onScheduleClick = openDetail,
                onOpenCalendar = openCalendar,
            )
        }

        composable(
            route = ScheduleRoute.DETAIL,
            arguments = listOf(navArgument("scheduleId") { type = NavType.StringType }),
        ) {
            ScheduleDetailScreen(
                onBack = { navController.popBackStack() },
                onLogService = onNavigateToAddLogEntry,
                onOpenWorkOrder = onOpenWorkOrder,
            )
        }

        composable(ScheduleRoute.CALENDAR) {
            GlobalCalendarScreen(onScheduleClick = openDetail, onBack = { navController.popBackStack() })
        }
    }
}
