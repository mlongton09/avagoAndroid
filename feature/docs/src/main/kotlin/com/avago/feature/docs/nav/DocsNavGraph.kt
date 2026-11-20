package com.avago.feature.docs.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.feature.docs.ui.DocAddScreen
import com.avago.feature.docs.ui.DocDetailScreen
import com.avago.feature.docs.ui.DocListScreen

object DocsRoute {
    const val LIST = "docs/list"
    const val ADD = "docs/add/{assetId}"
    const val DETAIL = "docs/detail/{docId}"

    fun add(assetId: String) = "docs/add/$assetId"

    fun detail(docId: String) = "docs/detail/$docId"
}

/** Maintenance documents: the account list (drawer), one document, and capture for an asset. */
fun NavGraphBuilder.docsNavGraph(navController: NavHostController) {
    composable(DocsRoute.LIST) {
        DocListScreen(onOpen = { navController.navigate(DocsRoute.detail(it)) })
    }
    composable(
        route = DocsRoute.DETAIL,
        arguments = listOf(navArgument("docId") { type = NavType.StringType }),
    ) {
        DocDetailScreen(onBack = { navController.popBackStack() })
    }
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
