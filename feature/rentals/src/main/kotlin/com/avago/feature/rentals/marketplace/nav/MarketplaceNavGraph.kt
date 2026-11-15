package com.avago.feature.rentals.marketplace.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.rentals.R
import com.avago.feature.rentals.marketplace.data.MarketplaceOwnerRepository
import com.avago.feature.rentals.marketplace.owner.MktBlocksScreen
import com.avago.feature.rentals.marketplace.owner.MktInboxFilter
import com.avago.feature.rentals.marketplace.owner.MktInboxScreen
import com.avago.feature.rentals.marketplace.owner.MktMeetupDetailScreen
import com.avago.feature.rentals.marketplace.owner.MktOwnerListingsScreen
import com.avago.feature.rentals.marketplace.owner.MktOwnerSettingsScreen
import com.avago.feature.rentals.marketplace.renter.MktListingScreen
import com.avago.feature.rentals.marketplace.renter.MktMeetScreen
import com.avago.feature.rentals.marketplace.renter.MktProfileScreen
import com.avago.feature.rentals.marketplace.renter.MktRequestsScreen
import com.avago.feature.rentals.marketplace.renter.MktReserveScreen
import com.avago.feature.rentals.marketplace.renter.MktSearchScreen
import com.avago.feature.rentals.marketplace.renter.MktSignInScreen
import com.avago.feature.rentals.marketplace.ui.PERMISSION_MARKETPLACE_MANAGE
import com.avago.feature.rentals.marketplace.ui.PERMISSION_MEETUP_RESPOND
import com.avago.feature.rentals.nav.RentalsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun optString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
private fun reqString(name: String) = navArgument(name) { type = NavType.StringType }

/**
 * Registers the Avago Marketplace destinations (renter: public; owner: permission-gated). See
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

    // ── Owner (staff) ──
    composable(MarketplaceRoutes.OWNER_SETTINGS) {
        MktOwnerSettingsScreen(onBack = back, onListings = { go(MarketplaceRoutes.OWNER_LISTINGS) }, onBlocks = { go(MarketplaceRoutes.OWNER_BLOCKS) })
    }
    composable(MarketplaceRoutes.OWNER_LISTINGS) { MktOwnerListingsScreen(onBack = back) }
    composable(MarketplaceRoutes.OWNER_MEETUPS) {
        MktInboxScreen(
            onBack = if (navController.previousBackStackEntry != null) back else null,
            onOpen = { go(MarketplaceRoutes.ownerMeetup(it)) },
            onBlocks = { go(MarketplaceRoutes.OWNER_BLOCKS) },
        )
    }
    composable(MarketplaceRoutes.OWNER_MEETUP, arguments = listOf(reqString("meetupId"))) {
        MktMeetupDetailScreen(
            onBack = { if (!navController.popBackStack()) go(MarketplaceRoutes.OWNER_MEETUPS) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
        )
    }
    composable(MarketplaceRoutes.OWNER_BLOCKS) { MktBlocksScreen(onBack = back) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MarketplaceDrawerViewModel @Inject constructor(
    private val repo: MarketplaceOwnerRepository,
    permissions: PermissionsManager,
) : ViewModel() {
    val canManage: StateFlow<Boolean> = permissions.observeCan(PERMISSION_MARKETPLACE_MANAGE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val canRespond: StateFlow<Boolean> = permissions.observeCan(PERMISSION_MEETUP_RESPOND)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** `pending_count` of the cached open inbox (§10.6 badge); 0 without permission. */
    val pending: StateFlow<Int> = permissions.observeCan(PERMISSION_MEETUP_RESPOND)
        .flatMapLatest { can -> if (can) repo.cachedInbox(MktInboxFilter.Open.status).map { it?.page?.pendingCount ?: 0 } else flowOf(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var refreshed = false

    /** One badge refresh per drawer VM (the inbox and push keep it current afterwards). */
    fun refreshBadge() {
        if (refreshed) return
        refreshed = true
        viewModelScope.launch { repo.refreshInbox(MktInboxFilter.Open.status) }
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

/** Owner entries: "Meet-ups" (badge) and "Marketplace" settings, each hidden without its permission. */
@Composable
fun MarketplaceOwnerDrawerItems(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: MarketplaceDrawerViewModel = hiltViewModel(),
) {
    val canManage by vm.canManage.collectAsStateWithLifecycle()
    val canRespond by vm.canRespond.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    LaunchedEffect(canRespond) { if (canRespond) vm.refreshBadge() }
    Column {
        if (canRespond) {
            val badgeText = pluralStringResource(R.plurals.mkt_meetups_pending, pending, pending)
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.mkt_inbox_title)) },
                icon = { Icon(Icons.Outlined.EventNote, contentDescription = null) },
                badge = if (pending > 0) ({ Badge(Modifier.semantics { contentDescription = badgeText }) { Text(pending.toString()) } }) else null,
                selected = currentRoute?.startsWith("rentals/mkt/owner/meetup") == true,
                onClick = { onNavigate(MarketplaceRoutes.OWNER_MEETUPS) },
                modifier = modifier,
            )
        }
        if (canManage) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.mkt_admin_title)) },
                icon = { Icon(Icons.Outlined.Storefront, contentDescription = null) },
                selected = currentRoute == MarketplaceRoutes.OWNER_SETTINGS || currentRoute == MarketplaceRoutes.OWNER_LISTINGS,
                onClick = { onNavigate(MarketplaceRoutes.OWNER_SETTINGS) },
                modifier = modifier,
            )
        }
    }
}
