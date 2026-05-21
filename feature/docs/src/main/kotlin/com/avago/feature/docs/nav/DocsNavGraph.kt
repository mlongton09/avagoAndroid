package com.avago.feature.docs.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.feature.docs.ui.DocAddScreen
import com.avago.feature.docs.ui.DocDetailScreen
import com.avago.feature.docs.ui.DocListScreen

/**
 * Type-safe route constants for the docs feature.
 */
object DocsRoute {
    const val GRAPH = "docs_graph"
    const val LIST = "docs/list"
    const val DETAIL = "docs/detail/{docId}"
    const val ADD = "docs/add"

    fun detail(docId: String) = "docs/detail/$docId"
}

/**
 * Registers the full docs navigation sub-graph.
 *
 * Call from the app-level NavHost:
 * ```
 * docsNavGraph(navController)
 * ```
 */
fun NavGraphBuilder.docsNavGraph(
    navController: NavHostController,
) {
    navigation(
        startDestination = DocsRoute.LIST,
        route = DocsRoute.GRAPH,
    ) {
        composable(DocsRoute.LIST) {
            DocListScreen(
                onDocClick = { docId ->
                    navController.navigate(DocsRoute.detail(docId))
                },
                onAddDoc = {
                    navController.navigate(DocsRoute.ADD)
                },
            )
        }

        composable(
            route = DocsRoute.DETAIL,
            arguments = listOf(navArgument("docId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val docId = requireNotNull(backStackEntry.arguments?.getString("docId"))
            DocDetailScreen(
                docId = docId,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }

        composable(DocsRoute.ADD) {
            DocAddScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
