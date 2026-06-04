package com.avago.feature.workorders.nav

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.core.ui.LocationPickerScreen
import com.avago.feature.workorders.WorkOrderListScreen
import com.avago.feature.workorders.ui.AssetGroupPickerScreen
import com.avago.feature.workorders.ui.AvailableJobsScreen
import com.avago.feature.workorders.ui.CostLinesEditorScreen
import com.avago.feature.workorders.ui.DispatchBoardScreen
import com.avago.feature.workorders.ui.GLAccountPickerScreen
import com.avago.feature.workorders.ui.JobPickerScreen
import com.avago.feature.workorders.ui.TechProfileScreen
import com.avago.feature.workorders.ui.WoTemplateListScreen
import com.avago.feature.workorders.ui.WorkOrderCalendarScreen
import com.avago.feature.workorders.ui.WorkOrderCreateScreen
import com.avago.feature.workorders.ui.WorkOrderDetailScreen
import com.avago.feature.workorders.ui.WorkOrderLogScreen

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
    const val TECH_PROFILE = "workorders/tech/{techId}"
    const val JOB_PICKER = "workorders/job_picker"
    const val TEMPLATE_LIST = "workorders/templates"
    const val COST_LINES_EDITOR = "workorders/cost_lines/{woId}"
    const val GL_ACCOUNT_PICKER = "workorders/gl_account_picker"
    const val ASSET_GROUP_PICKER = "workorders/asset_group_picker"
    const val LOCATION_PICKER = "workorders/location_picker"
    const val LOG_WORK = "workorders/log/{woId}"

    fun detail(woId: String) = "workorders/detail/$woId"
    fun logWork(woId: String) = "workorders/log/$woId"
    fun createEdit(woId: String? = null) =
        if (woId != null) "workorders/create_edit?woId=$woId"
        else "workorders/create_edit?woId="
    fun techProfile(techId: String) = "workorders/tech/$techId"
    fun costLinesEditor(woId: String) = "workorders/cost_lines/$woId"
}

/**
 * Registers the full work orders navigation sub-graph.
 *
 * Call from the app-level NavHost:
 * ```
 * workOrderNavGraph(navController)
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.workOrderNavGraph(
    navController: NavHostController,
    onNavigateToAssetPicker: (returnRoute: String) -> Unit = {},
    onNavigateToInventoryPicker: (returnRoute: String) -> Unit = {},
    onNavigateToLogWork: ((assetId: String?) -> Unit)? = null,
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
                onOpenCalendar = {
                    navController.navigate(WorkOrderRoute.CALENDAR)
                },
                onOpenDispatchBoard = {
                    navController.navigate(WorkOrderRoute.DISPATCH_BOARD)
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
                onTechClick = { techId -> navController.navigate(WorkOrderRoute.techProfile(techId)) },
                onAddPart = { onNavigateToInventoryPicker(WorkOrderRoute.detail(woId)) },
                onManageCostLines = { navController.navigate(WorkOrderRoute.costLinesEditor(woId)) },
                onLogWork = onNavigateToLogWork,
                onNavigateToLogScreen = {
                    // Replace detail with the merged screen (matches iOS nav stack replacement)
                    navController.navigate(WorkOrderRoute.logWork(woId)) {
                        popUpTo(WorkOrderRoute.detail(woId)) { inclusive = true }
                    }
                },
            )
        }

        // ── Merged Work Order + Log Entry screen ──────────────────────────────
        composable(
            route = WorkOrderRoute.LOG_WORK,
            arguments = listOf(navArgument("woId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val logWoId = requireNotNull(backStackEntry.arguments?.getString("woId"))
            WorkOrderLogScreen(
                woId = logWoId,
                onBack = { navController.popBackStack() },
                onCompleted = {
                    navController.navigate(WorkOrderRoute.LIST) {
                        popUpTo(WorkOrderRoute.GRAPH) { inclusive = false }
                    }
                },
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

            // Observe job selection returned from JobPickerScreen
            val selectedJobId = backStackEntry.savedStateHandle
                .get<String>("selected_job_id")
            val selectedLocationId = backStackEntry.savedStateHandle
                .get<String>("selected_location_id")
            val selectedLocationName = backStackEntry.savedStateHandle
                .get<String>("selected_location_name")

            WorkOrderCreateScreen(
                woId = woId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onPickAsset = {
                    onNavigateToAssetPicker(WorkOrderRoute.createEdit(woId))
                },
                onPickLocation = {
                    navController.navigate(WorkOrderRoute.LOCATION_PICKER)
                },
                onPickAssetGroup = {
                    navController.navigate(WorkOrderRoute.ASSET_GROUP_PICKER)
                },
                onPickJob = {
                    navController.navigate(WorkOrderRoute.JOB_PICKER)
                },
                selectedJobId = selectedJobId,
                selectedLocationId = selectedLocationId,
                selectedLocationName = selectedLocationName,
                selectedAssetId = selectedAssetId,
            )
        }

        composable(WorkOrderRoute.LOCATION_PICKER) {
            LocationPickerScreen(
                onLocationSelected = { id, name ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_location_id", id)
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_location_name", name)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
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

        // ── Tech Profile ──────────────────────────────────────────────────────
        composable(
            route = WorkOrderRoute.TECH_PROFILE,
            arguments = listOf(navArgument("techId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val techId = requireNotNull(backStackEntry.arguments?.getString("techId"))
            TechProfileScreen(
                techId = techId,
                onBack = { navController.popBackStack() },
                onWoClick = { woId ->
                    navController.navigate(WorkOrderRoute.detail(woId))
                },
            )
        }

        // ── Job Picker ────────────────────────────────────────────────────────────
        composable(WorkOrderRoute.JOB_PICKER) {
            JobPickerScreen(
                onBack = { navController.popBackStack() },
                onJobSelected = { jobId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_job_id", jobId)
                    navController.popBackStack()
                },
            )
        }

        composable(WorkOrderRoute.TEMPLATE_LIST) {
            WoTemplateListScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── Cost Lines Editor ─────────────────────────────────────────────────────
        composable(
            route = WorkOrderRoute.COST_LINES_EDITOR,
            arguments = listOf(navArgument("woId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val woId = requireNotNull(backStackEntry.arguments?.getString("woId"))
            val pendingGlAccount = backStackEntry.savedStateHandle
                .get<String>("selected_gl_account")
            CostLinesEditorScreen(
                woId = woId,
                onBack = { navController.popBackStack() },
                onNavigateToGlPicker = {
                    navController.navigate(WorkOrderRoute.GL_ACCOUNT_PICKER)
                },
                pendingGlAccount = pendingGlAccount,
                onGlAccountConsumed = {
                    backStackEntry.savedStateHandle.remove<String>("selected_gl_account")
                },
            )
        }

        // ── GL Account Picker ─────────────────────────────────────────────────────
        composable(WorkOrderRoute.GL_ACCOUNT_PICKER) {
            GLAccountPickerScreen(
                onSelected = { account ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_gl_account", account)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Asset Group Picker ────────────────────────────────────────────────────
        composable(WorkOrderRoute.ASSET_GROUP_PICKER) {
            AssetGroupPickerScreen(
                onGroupSelected = { group ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_asset_group", group)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
