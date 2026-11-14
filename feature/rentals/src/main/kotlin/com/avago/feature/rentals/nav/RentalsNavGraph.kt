package com.avago.feature.rentals.nav

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.core.data.rental.FleetStatus
import com.avago.feature.rentals.legacy.AssetRentalsScreen
import com.avago.feature.rentals.legacy.RentalBookingScreen
import com.avago.feature.rentals.legacy.RentalInvoiceScreen
import com.avago.feature.rentals.legacy.RentalCustomerFormScreen as LegacyCustomerFormScreen
import com.avago.feature.rentals.legacy.RentalCustomersScreen as LegacyCustomersScreen
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
import com.avago.feature.rentals.ui.RentalsHomeScreen
import com.avago.feature.rentals.ui.ReadinessScreen
import com.avago.feature.rentals.ui.SwapScreen
import java.time.Instant

/** Routes of the rentals feature (§14). All screens own their top bar. */
object RentalsRoute {
    const val PREFIX = "rentals/"
    const val HOME = "rentals/home"
    const val FLEET = "rentals/fleet?status={status}"
    const val SCHEDULE = "rentals/schedule"
    const val ORDERS = "rentals/orders"
    const val ORDER = "rentals/order/{orderId}"
    const val QUICK_BOOK = "rentals/quickbook?assetId={assetId}&start={start}&end={end}"
    const val CUSTOMERS = "rentals/customers"
    const val CUSTOMER = "rentals/customer/{customerId}"
    const val DOCUMENT = "rentals/document/{customerId}"
    const val HANDOVER = "rentals/handover/{orderId}/{direction}"
    const val SCAN = "rentals/scan"
    const val ASSET = "rentals/asset/{assetId}"
    const val READINESS = "rentals/readiness/{assetId}"
    const val SWAP = "rentals/swap/{orderId}"

    // Legacy (pre-v2) rental screens kept reachable from the asset Rental tab.
    const val LEGACY_ASSET = "rentals/legacy/asset/{assetId}"
    const val LEGACY_BOOKING = "rentals/legacy/booking/{assetId}"
    const val LEGACY_CUSTOMERS = "rentals/legacy/customers"
    const val LEGACY_CUSTOMER_FORM = "rentals/legacy/customer_form?customerId={customerId}"
    const val LEGACY_INVOICE = "rentals/legacy/invoice/{invoiceId}"

    fun fleet(status: FleetStatus? = null) = "rentals/fleet?status=${status?.wire.orEmpty()}"
    fun order(orderId: String) = "rentals/order/${Uri.encode(orderId)}"
    fun quickBook(assetId: String? = null, start: Instant? = null, end: Instant? = null) =
        "rentals/quickbook?assetId=${Uri.encode(assetId.orEmpty())}&start=${start?.toEpochMilli() ?: ""}&end=${end?.toEpochMilli() ?: ""}"
    fun customer(customerId: String?) = "rentals/customer/${Uri.encode(customerId ?: "new")}"
    fun document(customerId: String?) = "rentals/document/${Uri.encode(customerId ?: "new")}"
    fun handover(orderId: String, direction: String) = "rentals/handover/${Uri.encode(orderId)}/$direction"
    fun asset(assetId: String) = "rentals/asset/${Uri.encode(assetId)}"
    fun readiness(assetId: String) = "rentals/readiness/${Uri.encode(assetId)}"
    fun swap(orderId: String) = "rentals/swap/${Uri.encode(orderId)}"
    fun legacyAsset(assetId: String) = "rentals/legacy/asset/${Uri.encode(assetId)}"
    fun legacyBooking(assetId: String) = "rentals/legacy/booking/${Uri.encode(assetId)}"
    fun legacyCustomerForm(customerId: String?) = "rentals/legacy/customer_form?customerId=${Uri.encode(customerId.orEmpty())}"
    fun legacyInvoice(invoiceId: String) = "rentals/legacy/invoice/${Uri.encode(invoiceId)}"
}

private fun optString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
private fun reqString(name: String) = navArgument(name) { type = NavType.StringType }

/**
 * Registers the rentals screens. [onOpenAsset] opens the asset detail owned by feature/assets;
 * [onStartInspection] opens an inspection-v2 log entry for the asset (feature/log).
 */
fun NavGraphBuilder.rentalsNavGraph(
    navController: NavHostController,
    onOpenAsset: (assetId: String) -> Unit,
    onStartInspection: (assetId: String) -> Unit,
) {
    val back: () -> Unit = { navController.popBackStack() }
    fun go(route: String) = navController.navigate(route) { launchSingleTop = true }

    composable(RentalsRoute.HOME) {
        RentalsHomeScreen(
            onBack = null,
            onOpenFleet = { go(RentalsRoute.fleet(it)) },
            onOpenSchedule = { go(RentalsRoute.SCHEDULE) },
            onOpenOrders = { go(RentalsRoute.ORDERS) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onOpenCustomers = { go(RentalsRoute.CUSTOMERS) },
            onQuickBook = { go(RentalsRoute.quickBook()) },
            onScan = { go(RentalsRoute.SCAN) },
        )
    }
    composable(RentalsRoute.FLEET, arguments = listOf(optString("status"))) { entry ->
        RentalFleetScreen(
            initialStatus = FleetStatus.fromWire(entry.arguments?.getString("status")),
            onBack = back,
            onOpenAsset = { go(RentalsRoute.asset(it)) },
            onBook = { assetId, start, end -> go(RentalsRoute.quickBook(assetId, start, end)) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onReadiness = { go(RentalsRoute.readiness(it)) },
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
        )
    }
    composable(
        RentalsRoute.QUICK_BOOK,
        arguments = listOf(optString("assetId"), optString("start"), optString("end")),
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
        )
    }
    composable(RentalsRoute.CUSTOMER, arguments = listOf(reqString("customerId"))) {
        RentalCustomerScreen(
            onBack = back,
            onSaved = { back() },
            onScanLicence = { go(RentalsRoute.document(null)) },
            onAddDocument = { go(RentalsRoute.document(it)) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
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
        )
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
    composable(RentalsRoute.SCAN) {
        RentalScanScreen(
            onBack = back,
            onOpenAsset = { go(RentalsRoute.asset(it)) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onLicence = { go(RentalsRoute.customer(null)) },
        )
    }
    composable(RentalsRoute.ASSET, arguments = listOf(reqString("assetId"))) {
        AssetRentalScreen(
            onBack = back,
            onBook = { go(RentalsRoute.quickBook(it)) },
            onOpenOrder = { go(RentalsRoute.order(it)) },
            onOpenLegacy = { go(RentalsRoute.legacyAsset(it)) },
            onReadiness = { go(RentalsRoute.readiness(it)) },
        )
    }

    // ── Legacy screens ──
    composable(RentalsRoute.LEGACY_ASSET, arguments = listOf(reqString("assetId"))) { entry ->
        val assetId = entry.arguments?.getString("assetId").orEmpty()
        AssetRentalsScreen(
            assetId = assetId,
            onBack = back,
            onOpenBooking = { go(RentalsRoute.legacyBooking(assetId)) },
            onOpenCustomers = { go(RentalsRoute.LEGACY_CUSTOMERS) },
            onOpenInvoice = { go(RentalsRoute.legacyInvoice(it)) },
        )
    }
    composable(RentalsRoute.LEGACY_BOOKING, arguments = listOf(reqString("assetId"))) { entry ->
        RentalBookingScreen(
            assetId = entry.arguments?.getString("assetId").orEmpty(),
            onBack = back,
            onNavigateToNewCustomer = { go(RentalsRoute.legacyCustomerForm(null)) },
            onBooked = back,
        )
    }
    composable(RentalsRoute.LEGACY_CUSTOMERS) {
        LegacyCustomersScreen(
            onBack = back,
            onAddCustomer = { go(RentalsRoute.legacyCustomerForm(null)) },
            onEditCustomer = { go(RentalsRoute.legacyCustomerForm(it)) },
        )
    }
    composable(RentalsRoute.LEGACY_CUSTOMER_FORM, arguments = listOf(optString("customerId"))) { entry ->
        LegacyCustomerFormScreen(
            customerId = entry.arguments?.getString("customerId")?.takeIf { it.isNotBlank() },
            onBack = back,
            onSaved = back,
        )
    }
    composable(RentalsRoute.LEGACY_INVOICE, arguments = listOf(reqString("invoiceId"))) { entry ->
        RentalInvoiceScreen(invoiceId = entry.arguments?.getString("invoiceId").orEmpty(), onBack = back)
    }
}
