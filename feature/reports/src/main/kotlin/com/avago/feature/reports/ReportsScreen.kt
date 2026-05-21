package com.avago.feature.reports

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.avago.feature.reports.nav.ReportsRoute
import com.avago.feature.reports.nav.reportsNavGraph

/**
 * Entry point composable for the Reports feature.
 * Hosts an internal NavHost so reports can navigate between list → section screens
 * without coupling to the app-level nav graph structure.
 */
@Composable
fun ReportsScreen() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "reports",
    ) {
        reportsNavGraph(navController)
    }
}
