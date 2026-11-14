package com.avago.feature.rentalsops.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.RentalOpsAccess
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.push.RentalOpsRoutes
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
import javax.inject.Inject

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

/** Drawer entry for the host's side menu; hidden when the user has no rental-ops permission. */
@Composable
fun RentalOpsDrawerItem(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: RentalOpsDrawerViewModel = hiltViewModel(),
) {
    val access by vm.access.collectAsStateWithLifecycle()
    if (!access.any) return
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.rentalops_title)) },
        icon = { Icon(Icons.Filled.LocalShipping, contentDescription = null) },
        selected = currentRoute?.startsWith(RentalOpsRoutes.PREFIX) == true,
        onClick = { onNavigate(RentalOpsRoutes.HOME) },
        modifier = modifier,
    )
}
