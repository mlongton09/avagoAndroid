package com.avago.feature.reports.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.avago.feature.reports.ui.CostReportScreen
import com.avago.feature.reports.ui.FinancialReportsScreen
import com.avago.feature.reports.ui.MaintenanceReportsScreen
import com.avago.feature.reports.ui.ReportsListScreen
import com.avago.feature.reports.ui.SystemReportsScreen
import com.avago.feature.reports.ui.WorkOrderReportsScreen

sealed class ReportsRoute(val route: String) {
    data object List : ReportsRoute("reports/list")
    data object CostReport : ReportsRoute("reports/cost")
    data object WorkOrderReports : ReportsRoute("reports/work-orders")
    data object MaintenanceReports : ReportsRoute("reports/maintenance")
    data object FinancialReports : ReportsRoute("reports/financial")
    data object SystemReports : ReportsRoute("reports/system")
}

fun NavGraphBuilder.reportsNavGraph(navController: NavHostController) {
    navigation(
        startDestination = ReportsRoute.List.route,
        route = "reports",
    ) {
        composable(ReportsRoute.List.route) {
            ReportsListScreen(
                onNavigateToWorkOrders = { navController.navigate(ReportsRoute.WorkOrderReports.route) },
                onNavigateToMaintenance = { navController.navigate(ReportsRoute.MaintenanceReports.route) },
                onNavigateToFinancial = { navController.navigate(ReportsRoute.FinancialReports.route) },
                onNavigateToSystem = { navController.navigate(ReportsRoute.SystemReports.route) },
            )
        }

        composable(ReportsRoute.CostReport.route) {
            CostReportScreen(onBack = { navController.popBackStack() })
        }

        composable(ReportsRoute.WorkOrderReports.route) {
            WorkOrderReportsScreen(onBack = { navController.popBackStack() })
        }

        composable(ReportsRoute.MaintenanceReports.route) {
            MaintenanceReportsScreen(onBack = { navController.popBackStack() })
        }

        composable(ReportsRoute.FinancialReports.route) {
            FinancialReportsScreen(onBack = { navController.popBackStack() })
        }

        composable(ReportsRoute.SystemReports.route) {
            SystemReportsScreen(onBack = { navController.popBackStack() })
        }
    }
}
