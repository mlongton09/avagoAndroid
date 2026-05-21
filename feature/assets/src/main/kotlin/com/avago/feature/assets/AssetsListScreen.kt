package com.avago.feature.assets

/**
 * Public entry point kept for backwards compatibility with AvagoNavHost.
 * The full feature is now wired via [com.avago.feature.assets.nav.assetsNavGraph].
 *
 * Delegates directly to the internal screen implementation.
 */
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.avago.feature.assets.nav.AssetsRoute
import com.avago.feature.assets.nav.assetsNavGraph

@Composable
fun AssetsListScreen() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AssetsRoute.GRAPH,
    ) {
        assetsNavGraph(navController = navController)
    }
}
