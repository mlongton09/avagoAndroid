package com.avago.feature.rentals.marketplace.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.renter.MktListingScreen
import com.avago.feature.rentals.marketplace.renter.MktMeetScreen
import com.avago.feature.rentals.marketplace.renter.MktProfileScreen
import com.avago.feature.rentals.marketplace.renter.MktRequestsScreen
import com.avago.feature.rentals.marketplace.renter.MktReserveScreen
import com.avago.feature.rentals.marketplace.renter.MktSearchScreen
import com.avago.feature.rentals.marketplace.renter.MktSignInScreen

private fun optString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
private fun reqString(name: String) = navArgument(name) { type = NavType.StringType }

/**
 * Registers the Avago Marketplace destinations (renter side: the public marketplace with its own renter sign-in). See
 * docs/rentals-marketplace-android.md for entry points and deep links.
 */
fun NavGraphBuilder.marketplaceNavGraph(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    fun go(route: String) = navController.navigate(route) { launchSingleTop = true }
    val signIn: (String?) -> Unit = { next -> go(MarketplaceRoutes.signIn(next = next)) }
    /** After a sent request: back to the search when it is on the stack, else one step back. */
    val done: () -> Unit = { if (!navController.popBackStack(MarketplaceRoutes.SEARCH, inclusive = false)) navController.popBackStack() }

    composable(MarketplaceRoutes.SEARCH) {
        MktSearchScreen(
            onBack = if (navController.previousBackStackEntry != null) back else null,
            onOpenListing = { id, lat, lng -> go(MarketplaceRoutes.listing(id, lat, lng)) },
            onOpenRequests = { go(MarketplaceRoutes.REQUESTS) },
            onOpenAccount = { go(MarketplaceRoutes.PROFILE) },
        )
    }
    composable(MarketplaceRoutes.LISTING, arguments = listOf(reqString("listingId"), optString("lat"), optString("lng"), optString("loc"))) {
        MktListingScreen(
            onBack = { if (!navController.popBackStack()) go(MarketplaceRoutes.SEARCH) },
            onReserve = { go(MarketplaceRoutes.reserve(it)) },
            onMeet = { go(MarketplaceRoutes.meet(it)) },
        )
    }
    composable(MarketplaceRoutes.RESERVE, arguments = listOf(reqString("listingId"))) {
        MktReserveScreen(onBack = back, onSignIn = signIn, onRequests = { go(MarketplaceRoutes.REQUESTS) }, onDone = done)
    }
    composable(MarketplaceRoutes.MEET, arguments = listOf(reqString("listingId"))) {
        MktMeetScreen(onBack = back, onSignIn = signIn, onRequests = { go(MarketplaceRoutes.REQUESTS) }, onDone = done)
    }
    composable(MarketplaceRoutes.SIGN_IN, arguments = listOf(optString("next"), optString("token"))) {
        MktSignInScreen(
            onBack = { if (!navController.popBackStack()) go(MarketplaceRoutes.SEARCH) },
            onSignedIn = { next ->
                navController.popBackStack()
                // Started from a marketplace screen: it observes the session, so just return to it.
                // Started from a magic link (nothing public below): continue at `next` or the search.
                val below = navController.currentBackStackEntry?.destination?.route
                if (!MarketplaceRoutes.isPublic(below)) go(next?.let { MarketplaceRoutes.fromWebPath(it) } ?: MarketplaceRoutes.SEARCH)
            },
        )
    }
    composable(MarketplaceRoutes.PROFILE) {
        MktProfileScreen(onBack = back, onSignIn = { signIn(null) })
    }
    composable(MarketplaceRoutes.REQUESTS) {
        MktRequestsScreen(
            onBack = { if (!navController.popBackStack()) go(MarketplaceRoutes.SEARCH) },
            onSignIn = { signIn("/marketplace/requests") },
            onOpenListing = { go(MarketplaceRoutes.listing(it)) },
        )
    }
}

/** "Find rentals" — public renter search; shown to everyone (also signed out). */
@Composable
fun MarketplaceFindItem(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.mkt_find_rentals)) },
        icon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null) },
        selected = MarketplaceRoutes.isPublic(currentRoute),
        onClick = { onNavigate(MarketplaceRoutes.SEARCH) },
        modifier = modifier,
    )
}
