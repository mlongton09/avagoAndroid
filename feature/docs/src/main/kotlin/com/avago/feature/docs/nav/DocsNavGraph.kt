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
    /** [OWNER_ASSET] or [OWNER_ACTIVITY], then the owner's id. */
    const val ADD = "docs/add/{ownerType}/{ownerId}"
    const val DETAIL = "docs/detail/{docId}"
    const val OWNER_ASSET = "asset"
    const val OWNER_ACTIVITY = "activity"

    fun addForAsset(assetId: String) = "docs/add/$OWNER_ASSET/$assetId"

    /** Capture for a service record (ctivity_records). */
    fun addForActivity(activityId: String) = "docs/add/$OWNER_ACTIVITY/$activityId"

    fun detail(docId: String) = "docs/detail/$docId"
}

/** Maintenance documents: the account list (drawer), one document, and capture for an asset or service record. */
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
        arguments = listOf(
            navArgument("ownerType") { type = NavType.StringType },
            navArgument("ownerId") { type = NavType.StringType },
        ),
    ) {
        DocAddScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }
}
