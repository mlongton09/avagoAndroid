package com.avago.feature.rentals.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.core.data.rental.FleetStatus
import com.avago.feature.rentals.domain.HandoverTarget
import com.avago.feature.rentals.ui.AssetRentalScreen
import com.avago.feature.rentals.ui.DocumentCaptureScreen
import com.avago.feature.rentals.ui.HandoverScreen
import com.avago.feature.rentals.ui.QuickBookScreen
import com.avago.feature.rentals.ui.RentalCustomerScreen
import com.avago.feature.rentals.ui.RentalCustomersScreen
import com.avago.feature.rentals.ui.RentalFleetScreen
import com.avago.feature.rentals.ui.RentalOrderScreen
import com.avago.feature.rentals.ui.RentalOrdersScreen
import com.avago.feature.rentals.ui.RentalScanScreen
import com.avago.feature.rentals.ui.RentalScheduleScreen
import com.avago.feature.rentals.ui.RentalsHomeActions
import com.avago.feature.rentals.ui.RentalsHomeScreen
import com.avago.feature.rentals.ui.ReadinessScreen
import com.avago.feature.rentals.ui.SignAgreementScreen
import com.avago.feature.rentals.ui.SwapScreen
import java.time.Instant

/** Routes of the rentals feature (§14). All screens own their top bar. */
object RentalsRoute {
    const val PREFIX = "rentals/"
    const val HOME = "rentals/home"
    const val FLEET = "rentals/fleet?status={status}&find={find}"
    const val SCHEDULE = "rentals/schedule"
    const val ORDERS = "rentals/orders"
    const val ORDER = "rentals/order/{orderId}"
    const val QUICK_BOOK = "rentals/quickbook?assetId={assetId}&start={start}&end={end}&customerId={customerId}"
    const val CUSTOMERS = "rentals/customers"
    const val CUSTOMER = "rentals/customer/{customerId}"
    const val DOCUMENT = "rentals/document/{customerId}"
    const val HANDOVER = "rentals/handover/{orderId}/{direction}"
    const val SCAN = "rentals/scan?mode={mode}"
    const val ASSET = "rentals/asset/{assetId}"
    const val READINESS = "rentals/readiness/{assetId}"
    const val SWAP = "rentals/swap/{orderId}"
    const val SIGN = "rentals/order/{orderId}/sign"


    fun fleet(status: FleetStatus? = null) = "rentals/fleet?status=${status?.wire.orEmpty()}"
    /** Fleet board with the "find free units" sheet open. */
    fun findFree() = "rentals/fleet?status=&find=1"
    /** Scan; [mode] is `out` / `in` when started as check-out / check-in. */
    fun scan(mode: String? = null) = if (mode == null) "rentals/scan" else "rentals/scan?mode=$mode"
    fun order(orderId: String) = "rentals/order/${Uri.encode(orderId)}"
    fun quickBook(assetId: String? = null, start: Instant? = null, end: Instant? = null, customerId: String? = null) =
        "rentals/quickbook?assetId=${Uri.encode(assetId.orEmpty())}&start=${start?.toEpochMilli() ?: ""}&end=${end?.toEpochMilli() ?: ""}" +
            "&customerId=${Uri.encode(customerId.orEmpty())}"
    fun customer(customerId: String?) = "rentals/customer/${Uri.encode(customerId ?: "new")}"
    fun document(customerId: String?) = "rentals/document/${Uri.encode(customerId ?: "new")}"
    fun handover(orderId: String, direction: String) = "rentals/handover/${Uri.encode(orderId)}/$direction"
    fun asset(assetId: String) = "rentals/asset/${Uri.encode(assetId)}"
    fun readiness(assetId: String) = "rentals/readiness/${Uri.encode(assetId)}"
    fun swap(orderId: String) = "rentals/swap/${Uri.encode(orderId)}"
    fun sign(orderId: String) = "rentals/order/${Uri.encode(orderId)}/sign"
}

private fun optString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
private fun reqString(name: String) = navArgument(name) { type = NavType.StringType }

/**
 * Registers the rentals screens. [onOpenAsset] opens the asset detail owned by feature/assets;
 * [onStartInspection] opens an inspection-v2 log entry for the asset (feature/log).
 * [operationsEntries] renders the permission-gated operations links (feature/rentalsops) on the
 * rentals home; it receives a navigate callback.
 */
fun NavGraphBuilder.rentalsNavGraph(
    navController: NavHostController,
    onOpenAsset: (assetId: String) -> Unit,
    onStartInspection: (assetId: String) -> Unit,
    operationsEntries: (@Composable (navigate: (String) -> Unit) -> Unit)? = null,
) {
    val back: () -> Unit = { navController.popBackStack() }
    fun go(route: String) = navController.navigate(route) { launchSingleTop = true }
    val handover: (String, String) -> Unit = { orderId, direction -> go(RentalsRoute.handover(orderId, direction)) }
    composable(RentalsRoute.HOME) {
        RentalsHomeScreen(
            onBack = null,
            actions = RentalsHomeActions(
                onOpenFleet = { go(RentalsRoute.fleet(it)) },
                onFindFree = { go(RentalsRoute.findFree()) },
                onOpenSchedule = { go(RentalsRoute.SCHEDULE) },
                onOpenOrders = { go(RentalsRoute.ORDERS) },
                onOpenOrder = { go(RentalsRoute.order(it)) },
                onOpenCustomers = { go(RentalsRoute.CUSTOMERS) },
                onNewBooking = { go(RentalsRoute.quickBook()) },
                onScanCheckOut = { go(RentalsRoute.scan(HandoverTarget.OUT)) },
                onScanCheckIn = { go(RentalsRoute.scan(HandoverTarget.IN)) },
                onHandover = handover,
            ),
            operations = {
                operationsEntries?.invoke { route -> go(route) }
            },
        )
    }
    composable(RentalsRoute.FLEET, arguments = listOf(optString("status"), optString("find"))) { entry ->
        RentalFleetScreen(
            initialStatus = FleetStatus.fromWire(entry.arguments?.getString("status")),
            onBack = back,
            onOpenAsset = { go(RentalsRoute.asset(it)) },
            onBook = { assetId, start, end -> go(RentalsRoute.quickBook(assetId, start, end)) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onReadiness = { go(RentalsRoute.readiness(it)) },
            onHandover = handover,
            openFindFree = entry.arguments?.getString("find") == "1",
        )
    }
    composable(RentalsRoute.SCHEDULE) {
        RentalScheduleScreen(onBack = back, onOpenOrder = { go(RentalsRoute.order(it)) })
    }
    composable(RentalsRoute.ORDERS) {
        RentalOrdersScreen(
            onBack = back,
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onQuickBook = { go(RentalsRoute.quickBook()) },
        )
    }
    composable(RentalsRoute.ORDER, arguments = listOf(reqString("orderId"))) {
        RentalOrderScreen(
            onBack = back,
            onHandover = { orderId, direction -> go(RentalsRoute.handover(orderId, direction)) },
            onOpenCustomer = { go(RentalsRoute.customer(it)) },
            onSwap = { go(RentalsRoute.swap(it)) },
            onSign = { go(RentalsRoute.sign(it)) },
            onAddDocument = { go(RentalsRoute.document(it)) },
        )
    }
    composable(
        RentalsRoute.QUICK_BOOK,
        arguments = listOf(optString("assetId"), optString("start"), optString("end"), optString("customerId")),
    ) {
        QuickBookScreen(
            onBack = back,
            onBooked = { orderId ->
                navController.navigate(RentalsRoute.order(orderId)) {
                    popUpTo(RentalsRoute.QUICK_BOOK) { inclusive = true }
                }
            },
            onNewCustomer = { go(RentalsRoute.customer(null)) },
        )
    }
    composable(RentalsRoute.CUSTOMERS) {
        RentalCustomersScreen(
            onBack = back,
            onOpen = { go(RentalsRoute.customer(it)) },
            onNew = { go(RentalsRoute.customer(null)) },
            onBook = { go(RentalsRoute.quickBook(customerId = it)) },
        )
    }
    composable(RentalsRoute.CUSTOMER, arguments = listOf(reqString("customerId"))) {
        RentalCustomerScreen(
            onBack = back,
            onSaved = { back() },
            onScanLicence = { go(RentalsRoute.document(null)) },
            onAddDocument = { go(RentalsRoute.document(it)) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onBook = { go(RentalsRoute.quickBook(customerId = it)) },
        )
    }
    composable(RentalsRoute.DOCUMENT, arguments = listOf(reqString("customerId"))) {
        DocumentCaptureScreen(onBack = back, onDone = back)
    }
    composable(RentalsRoute.HANDOVER, arguments = listOf(reqString("orderId"), reqString("direction"))) {
        HandoverScreen(
            onBack = back,
            onDone = back,
            onStartInspection = onStartInspection,
            onCheckedIn = { assetId ->
                navController.navigate(RentalsRoute.readiness(assetId)) {
                    popUpTo(RentalsRoute.HANDOVER) { inclusive = true }
                }
            },
            onSign = { go(RentalsRoute.sign(it)) },
            onAddDocument = { go(RentalsRoute.document(it)) },
        )
    }
    composable(RentalsRoute.SIGN, arguments = listOf(reqString("orderId"))) {
        SignAgreementScreen(onBack = back)
    }
    composable(RentalsRoute.READINESS, arguments = listOf(reqString("assetId"))) {
        ReadinessScreen(onBack = back, onDone = back)
    }
    composable(RentalsRoute.SWAP, arguments = listOf(reqString("orderId"))) {
        SwapScreen(
            onBack = back,
            onSwapped = { returned ->
                navController.navigate(RentalsRoute.readiness(returned)) {
                    popUpTo(RentalsRoute.SWAP) { inclusive = true }
                }
            },
        )
    }
    composable(RentalsRoute.SCAN, arguments = listOf(optString("mode"))) { entry ->
        RentalScanScreen(
            onBack = back,
            onOpenAsset = { go(RentalsRoute.asset(it)) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onLicence = { go(RentalsRoute.customer(null)) },
            onHandover = handover,
            mode = entry.arguments?.getString("mode"),
        )
    }
    composable(RentalsRoute.ASSET, arguments = listOf(reqString("assetId"))) {
        AssetRentalScreen(
            onBack = back,
            onBook = { go(RentalsRoute.quickBook(it)) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onReadiness = { go(RentalsRoute.readiness(it)) },
            onHandover = handover,
        )
    }

}
