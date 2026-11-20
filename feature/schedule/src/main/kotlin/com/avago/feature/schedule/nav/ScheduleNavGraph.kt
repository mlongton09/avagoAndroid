package com.avago.feature.schedule.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.feature.schedule.ui.GlobalCalendarScreen
import com.avago.feature.schedule.ui.PmPlanEditScreen
import com.avago.feature.schedule.ui.PmPlanListScreen
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

    /** PM plans of the account, or of one asset when `assetId` is given. */
    const val PLANS = "schedule/plans?assetId={assetId}"

    /** New PM plan, optionally for an asset. */
    const val PLAN_NEW = "schedule/plan/new?assetId={assetId}"

    /** Edit one PM plan. */
    const val PLAN_EDIT = "schedule/plan/edit/{planId}"

    fun assetSchedules(assetId: String) = "schedule/asset/$assetId"

    fun plans(assetId: String? = null) = if (assetId == null) "schedule/plans" else "schedule/plans?assetId=$assetId"

    fun newPlan(assetId: String? = null) = if (assetId == null) "schedule/plan/new" else "schedule/plan/new?assetId=$assetId"

    fun editPlan(planId: String) = "schedule/plan/edit/$planId"

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
    val openPlans: (String?) -> Unit = { navController.navigate(ScheduleRoute.plans(it)) }
    val editPlan: (String) -> Unit = { navController.navigate(ScheduleRoute.editPlan(it)) }
    val optionalAsset = navArgument("assetId") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }
    navigation(startDestination = ScheduleRoute.LIST, route = ScheduleRoute.GRAPH) {
        composable(ScheduleRoute.LIST) {
            ScheduleListScreen(assetId = null, onScheduleClick = openDetail, onOpenCalendar = openCalendar, onOpenPlans = openPlans)
        }

        composable(
            route = ScheduleRoute.ASSET_SCHEDULES,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            ScheduleListScreen(
                assetId = requireNotNull(backStackEntry.arguments?.getString("assetId")),
                onScheduleClick = openDetail,
                onOpenCalendar = openCalendar,
                onOpenPlans = openPlans,
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
                onEditPlan = editPlan,
            )
        }

        composable(ScheduleRoute.CALENDAR) {
            GlobalCalendarScreen(onScheduleClick = openDetail, onBack = { navController.popBackStack() })
        }

        composable(route = ScheduleRoute.PLANS, arguments = listOf(optionalAsset)) {
            PmPlanListScreen(
                onBack = { navController.popBackStack() },
                onOpenPlan = editPlan,
                onNewPlan = { navController.navigate(ScheduleRoute.newPlan(it)) },
            )
        }

        composable(route = ScheduleRoute.PLAN_NEW, arguments = listOf(optionalAsset)) {
            PmPlanEditScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
        }

        composable(
            route = ScheduleRoute.PLAN_EDIT,
            arguments = listOf(navArgument("planId") { type = NavType.StringType }),
        ) {
            PmPlanEditScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
        }
    }
}
