package com.avago.feature.growth.nav

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.avago.feature.growth.R
import com.avago.feature.growth.data.PortalLink
import com.avago.feature.growth.ui.CaseDetailScreen
import com.avago.feature.growth.ui.CasesScreen
import com.avago.feature.growth.ui.GrowthHomeScreen
import com.avago.feature.growth.ui.NetworkInboxScreen
import com.avago.feature.growth.ui.PayoutsScreen
import com.avago.feature.growth.ui.PortalScreen
import com.avago.feature.growth.ui.ReferralsScreen
import com.avago.feature.growth.ui.ReviewsScreen

/** Trust, network, payouts, referrals and renter-portal destinations; every screen draws its own top bar. */
object GrowthRoutes {
    const val PREFIX = "growth/"
    const val HOME = "growth/home"
    const val REVIEWS = "growth/reviews"
    const val CASES = "growth/cases"
    const val CASE = "growth/cases/{caseId}"
    const val NETWORK = "growth/network"
    const val PAYOUTS = "growth/payouts"
    const val REFERRALS = "growth/referrals"
    const val PORTAL = "growth/portal/{slug}?token={token}"

    fun case(id: String) = "growth/cases/${Uri.encode(id)}"
    fun portal(slug: String, token: String) = "growth/portal/${Uri.encode(slug)}?token=${Uri.encode(token)}"

    /** Route for an opened renter-portal magic link, or null when it is not one. */
    fun forLink(raw: String?): String? = PortalLink.parse(raw)?.let { portal(it.slug, it.token) }
}

fun NavGraphBuilder.growthNavGraph(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    val go: (String) -> Unit = { navController.navigate(it) { launchSingleTop = true } }
    fun arg(name: String) = navArgument(name) { type = NavType.StringType }

    composable(GrowthRoutes.HOME) {
        GrowthHomeScreen(
            onBack = back,
            onReviews = { go(GrowthRoutes.REVIEWS) },
            onCases = { go(GrowthRoutes.CASES) },
            onNetwork = { go(GrowthRoutes.NETWORK) },
            onPayouts = { go(GrowthRoutes.PAYOUTS) },
            onReferrals = { go(GrowthRoutes.REFERRALS) },
        )
    }
    composable(GrowthRoutes.REVIEWS) { ReviewsScreen(onBack = back) }
    composable(GrowthRoutes.CASES) { CasesScreen(onBack = back, onCase = { go(GrowthRoutes.case(it)) }) }
    composable(GrowthRoutes.CASE, arguments = listOf(arg("caseId"))) { CaseDetailScreen(onBack = back) }
    composable(GrowthRoutes.NETWORK) { NetworkInboxScreen(onBack = back) }
    composable(GrowthRoutes.PAYOUTS) { PayoutsScreen(onBack = back) }
    composable(GrowthRoutes.REFERRALS) { ReferralsScreen(onBack = back) }
    composable(
        GrowthRoutes.PORTAL,
        arguments = listOf(
            arg("slug"),
            navArgument("token") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { PortalScreen(onBack = back) }
}

/** Drawer entry for the host's side menu. */
@Composable
fun GrowthDrawerItem(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.growth_title)) },
        icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null) },
        selected = currentRoute?.startsWith(GrowthRoutes.PREFIX) == true && !currentRoute.startsWith("growth/portal"),
        onClick = { onNavigate(GrowthRoutes.HOME) },
        modifier = modifier,
    )
}
