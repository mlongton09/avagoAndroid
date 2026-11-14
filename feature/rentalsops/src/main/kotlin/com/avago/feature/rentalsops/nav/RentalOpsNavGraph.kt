package com.avago.feature.rentalsops.nav

import javax.inject.Inject
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentalsops.data.RentalOpsAccess
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.ui.AgingScreen
import com.avago.feature.rentalsops.ui.AssignLineScreen
import com.avago.feature.rentalsops.ui.AssignListScreen
import com.avago.feature.rentalsops.ui.RentalDeliveryScreen
import com.avago.feature.rentalsops.ui.RentalOpsHomeScreen
import com.avago.feature.rentalsops.ui.RentalOpsSettingsScreen
import com.avago.feature.rentalsops.ui.StatementScreen
import com.avago.feature.rentalsops.ui.TaskListScreen
import com.avago.feature.rentalsops.ui.TransfersScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** All rental-ops destinations; every screen draws its own top bar (host suppresses the shared one). */
fun NavGraphBuilder.rentalOpsNavGraph(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    val go: (String) -> Unit = { navController.navigate(it) { launchSingleTop = true } }
    composable(RentalOpsRoutes.HOME) { RentalOpsHomeScreen(onNavigate = go, onBack = back) }
    composable(RentalOpsRoutes.TASKS) { TaskListScreen(onNavigate = go, onBack = back) }
    composable(RentalOpsRoutes.TASK, arguments = listOf(navArgument("taskId") { type = NavType.StringType })) {
        RentalDeliveryScreen(onBack = back)
    }
    composable(RentalOpsRoutes.ASSIGN) { AssignListScreen(onNavigate = go, onBack = back) }
    composable(RentalOpsRoutes.ASSIGN_LINE, arguments = listOf(navArgument("lineId") { type = NavType.StringType })) {
        AssignLineScreen(onBack = back)
    }
    composable(RentalOpsRoutes.TRANSFERS) { TransfersScreen(onBack = back) }
    composable(RentalOpsRoutes.STATEMENT) { StatementScreen(onBack = back) }
    composable(RentalOpsRoutes.AGING) { AgingScreen(onBack = back) }
    composable(RentalOpsRoutes.SETTINGS) { RentalOpsSettingsScreen(onBack = back) }
}

@HiltViewModel
class RentalOpsDrawerViewModel @Inject constructor(repo: RentalOpsRepository) : ViewModel() {
    val access = repo.access.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RentalOpsAccess.NONE)
}

/** Drawer entry for the host's side menu; hidden when the user has no back-office rental permission. */
@Composable
fun RentalOpsDrawerItem(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: RentalOpsDrawerViewModel = hiltViewModel(),
) {
    val access by vm.access.collectAsStateWithLifecycle()
    if (!access.operations) return
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.rentalops_operations)) },
        icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
        selected = currentRoute?.startsWith(RentalOpsRoutes.PREFIX) == true,
        onClick = { onNavigate(RentalOpsRoutes.HOME) },
        modifier = modifier,
    )
}

/**
 * Where "Rentals" opens for this user: drivers without `rental.view` have no counter screens
 * and land on their delivery list; everyone else gets [home].
 */
@Composable
fun rentalsLandingRoute(home: String, vm: RentalOpsDrawerViewModel = hiltViewModel()): String {
    val access by vm.access.collectAsStateWithLifecycle()
    return if (access.driverOnly) RentalOpsRoutes.TASKS else home
}

/**
 * P2 entries on the rentals home: "My deliveries" for drivers and a single "Operations" entry
 * (dispatch, transfers, billing, statements, utilization), each hidden without its permission.
 */
@Composable
fun RentalOpsHomeEntries(onNavigate: (String) -> Unit, vm: RentalOpsDrawerViewModel = hiltViewModel()) {
    val access by vm.access.collectAsStateWithLifecycle()
    if (!access.drive && !access.operations) return
    SectionCard(title = stringResource(R.string.rentalops_section_team)) {
        if (access.drive) {
            OpsEntry(Icons.Filled.LocalShipping, StatusTone.Info, R.string.rentalops_my_deliveries, R.string.rentalops_my_deliveries_sub) {
                onNavigate(RentalOpsRoutes.TASKS)
            }
        }
        if (access.drive && access.operations) CardDivider(Modifier.padding(start = 56.dp))
        if (access.operations) {
            OpsEntry(Icons.Filled.Dashboard, StatusTone.Accent, R.string.rentalops_operations, R.string.rentalops_operations_sub) {
                onNavigate(RentalOpsRoutes.HOME)
            }
        }
    }
}

@Composable
private fun OpsEntry(icon: ImageVector, tone: StatusTone, @StringRes title: Int, @StringRes sub: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(sub)) },
        leadingContent = { LeadingBadge(icon, tone) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = cardListItemColors(),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.rentalops_open), role = Role.Button, onClick = onClick),
    )
}