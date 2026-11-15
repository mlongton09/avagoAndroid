package com.avago.feature.crew.nav

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
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
import com.avago.feature.crew.R
import com.avago.feature.crew.data.PassportLink
import com.avago.feature.crew.ui.CoGroupScreen
import com.avago.feature.crew.ui.CoOwnershipsScreen
import com.avago.feature.crew.ui.CrewHomeScreen
import com.avago.feature.crew.ui.FaultScreen
import com.avago.feature.crew.ui.InspectionScreen
import com.avago.feature.crew.ui.LoanDetailScreen
import com.avago.feature.crew.ui.LoansScreen
import com.avago.feature.crew.ui.MachineScreen
import com.avago.feature.crew.ui.OperatorJobScreen
import com.avago.feature.crew.ui.OperatorJobsScreen
import com.avago.feature.crew.ui.OperatorRecordScreen
import com.avago.feature.crew.ui.PassportScanScreen
import com.avago.feature.crew.ui.PassportScreen
import com.avago.feature.crew.ui.TransferClaimScreen

/** Crew, operator, loan, co-ownership and passport destinations; every screen draws its own top bar. */
object CrewRoutes {
    const val PREFIX = "crew/"
    const val HOME = "crew/home"
    const val MACHINE = "crew/machine/{assetId}"
    const val INSPECT = "crew/machine/{assetId}/inspect"
    const val FAULT = "crew/machine/{assetId}/fault"
    const val RECORD = "crew/record"
    const val LOANS = "crew/loans"
    const val LOAN = "crew/loans/{loanId}"
    const val COOWN = "crew/coownership"
    const val COGROUP = "crew/coownership/{groupId}"
    const val JOBS = "crew/operator"
    const val JOB = "crew/operator/{accountId}/{assignmentId}"
    const val SCAN = "crew/passport/scan"
    const val PASSPORT = "crew/passport/{tokenId}"
    const val CLAIM = "crew/passport-claim/{token}"

    fun machine(assetId: String) = "crew/machine/${Uri.encode(assetId)}"
    fun inspect(assetId: String) = "crew/machine/${Uri.encode(assetId)}/inspect"
    fun fault(assetId: String) = "crew/machine/${Uri.encode(assetId)}/fault"
    fun loan(id: String) = "crew/loans/${Uri.encode(id)}"
    fun group(id: String) = "crew/coownership/${Uri.encode(id)}"
    fun job(accountId: String, assignmentId: String) = "crew/operator/${Uri.encode(accountId)}/${Uri.encode(assignmentId)}"
    fun passport(tokenId: String) = "crew/passport/${Uri.encode(tokenId)}"
    fun claim(token: String) = "crew/passport-claim/${Uri.encode(token)}"

    /** Route for a scanned QR or an opened passport / transfer link, or null when it is not one. */
    fun forLink(raw: String?): String? = when (val l = PassportLink.parse(raw)) {
        is PassportLink.Passport -> passport(l.tokenId)
        is PassportLink.Claim -> claim(l.token)
        null -> null
    }
}

fun NavGraphBuilder.crewNavGraph(navController: NavHostController, onOpenAsset: (String) -> Unit = {}) {
    val back: () -> Unit = { navController.popBackStack() }
    val go: (String) -> Unit = { navController.navigate(it) { launchSingleTop = true } }
    fun arg(name: String) = navArgument(name) { type = NavType.StringType }

    composable(CrewRoutes.HOME) {
        CrewHomeScreen(
            onBack = back,
            onMachine = { go(CrewRoutes.machine(it)) },
            onScan = { go(CrewRoutes.SCAN) },
            onOperatorJobs = { go(CrewRoutes.JOBS) },
            onLoans = { go(CrewRoutes.LOANS) },
            onCoOwnership = { go(CrewRoutes.COOWN) },
            onRecord = { go(CrewRoutes.RECORD) },
        )
    }
    composable(CrewRoutes.MACHINE, arguments = listOf(arg("assetId"))) {
        MachineScreen(
            onBack = back,
            onInspect = { go(CrewRoutes.inspect(it)) },
            onFault = { go(CrewRoutes.fault(it)) },
            onPassport = { go(CrewRoutes.SCAN) },
        )
    }
    composable(CrewRoutes.INSPECT, arguments = listOf(arg("assetId"))) { InspectionScreen(onBack = back, onDone = back) }
    composable(CrewRoutes.FAULT, arguments = listOf(arg("assetId"))) { FaultScreen(onBack = back, onDone = back) }
    composable(CrewRoutes.RECORD) { OperatorRecordScreen(onBack = back) }
    composable(CrewRoutes.LOANS) { LoansScreen(onBack = back, onLoan = { go(CrewRoutes.loan(it)) }) }
    composable(CrewRoutes.LOAN, arguments = listOf(arg("loanId"))) { LoanDetailScreen(onBack = back) }
    composable(CrewRoutes.COOWN) { CoOwnershipsScreen(onBack = back, onGroup = { go(CrewRoutes.group(it)) }) }
    composable(CrewRoutes.COGROUP, arguments = listOf(arg("groupId"))) { CoGroupScreen(onBack = back) }
    composable(CrewRoutes.JOBS) { OperatorJobsScreen(onBack = back, onJob = { a, j -> go(CrewRoutes.job(a, j)) }) }
    composable(CrewRoutes.JOB, arguments = listOf(arg("accountId"), arg("assignmentId"))) { OperatorJobScreen(onBack = back) }
    composable(CrewRoutes.SCAN) {
        PassportScanScreen(
            onBack = back,
            onPassport = { navController.navigate(CrewRoutes.passport(it)) { popUpTo(CrewRoutes.SCAN) { inclusive = true } } },
            onClaim = { navController.navigate(CrewRoutes.claim(it)) { popUpTo(CrewRoutes.SCAN) { inclusive = true } } },
        )
    }
    composable(CrewRoutes.PASSPORT, arguments = listOf(arg("tokenId"))) { PassportScreen(onBack = back) }
    composable(CrewRoutes.CLAIM, arguments = listOf(arg("token"))) {
        TransferClaimScreen(onBack = back, onClaimed = { assetId ->
            back()
            assetId?.let(onOpenAsset)
        })
    }
}

/** Drawer entry for the host's side menu. */
@Composable
fun CrewDrawerItem(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.crew_title)) },
        icon = { Icon(Icons.Filled.Engineering, contentDescription = null) },
        selected = currentRoute?.startsWith(CrewRoutes.PREFIX) == true,
        onClick = { onNavigate(CrewRoutes.HOME) },
        modifier = modifier,
    )
}
