package com.avago.feature.docs.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.feature.docs.ui.DocAddScreen

object DocsRoute {
    const val ADD = "docs/add/{assetId}"

    fun add(assetId: String) = "docs/add/$assetId"
}

/**
 * Document capture. Documents are listed on their asset (the asset Documents tab reads the
 * attachment links); this graph only adds new ones.
 */
fun NavGraphBuilder.docsNavGraph(navController: NavHostController) {
    composable(
        route = DocsRoute.ADD,
        arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
    ) {
        DocAddScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }
}
