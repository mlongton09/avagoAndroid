package com.avago.feature.assets.nav

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import java.net.URLDecoder
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.avago.core.ui.NotesFullScreenScreen
import com.avago.feature.assets.ui.AddEditAssetScreen
import com.avago.feature.assets.ui.OnboardingScreen
import com.avago.feature.assets.ui.OnboardingViewModel
import com.avago.feature.assets.ui.AssetBarcodeScannerScreen
import com.avago.feature.assets.ui.AssetDetailScreen
import com.avago.feature.assets.ui.AssetListScreen
import com.avago.feature.assets.ui.AssetPdfViewerScreen
import com.avago.feature.assets.ui.AssetPhotoGalleryScreen
import com.avago.feature.assets.ui.AssetPickerScreen
import com.avago.feature.assets.ui.AssetTypePickerScreen
import com.avago.feature.assets.ui.AssetWorkOrdersScreen
import com.avago.feature.assets.ui.CategoryPickerScreen
import com.avago.feature.assets.ui.ColorPickerScreen
import com.avago.feature.assets.ui.DocScanPipelineScreen
import com.avago.feature.assets.ui.DocTypePickerScreen
import com.avago.feature.assets.ui.AssetRentalsScreen
import com.avago.feature.assets.ui.RentalCustomersScreen
import com.avago.feature.assets.ui.RentalCustomerFormScreen
import com.avago.feature.assets.ui.RentalBookingScreen
import com.avago.feature.assets.ui.RentalInvoiceScreen
import com.avago.feature.assets.ui.CategoryReportScreen
import com.avago.feature.assets.ui.CostReportScreen
import com.avago.feature.assets.ui.WheelConfigBuilderScreen
import com.avago.feature.assets.ui.WheelConfigScreen
import com.avago.feature.assets.ui.WheelDataInputScreen
import com.avago.feature.assets.viewmodel.WheelSaveViewModel

/**
 * Type-safe route constants for the assets feature.
 */
object AssetsRoute {
    const val GRAPH = "assets_graph"
    const val LIST = "assets/list"
    const val DETAIL = "assets/detail/{assetId}"
    const val ADD_EDIT = "assets/add_edit?assetId={assetId}&initialAssetType={initialAssetType}"
    const val TYPE_PICKER = "assets/type_picker"
    const val CATEGORY_PICKER = "assets/category_picker"
    const val DOC_TYPE_PICKER = "assets/doc_type_picker"
    const val PICKER = "assets/picker"
    const val BARCODE_SCANNER = "assets/barcode_scanner"
    const val DOC_SCANNER = "assets/doc_scanner?entityId={entityId}&entityType={entityType}"
    const val PHOTO_GALLERY = "assets/gallery/{assetId}?initialIndex={initialIndex}"
    const val WORK_ORDERS = "assets/work_orders/{assetId}"
    const val PDF_VIEWER = "assets/pdf_viewer?pdfUrl={pdfUrl}&title={title}"
    const val NOTES_FULL_SCREEN = "assets/notes?entityId={entityId}&entityType={entityType}&initialText={initialText}"
    const val COLOR_PICKER = "assets/color_picker?currentColor={currentColor}"
    const val WHEEL_CONFIG = "assets/wheel_config/{assetId}"
    const val WHEEL_CONFIG_BUILDER = "assets/wheel_config_builder/{assetId}"
    const val WHEEL_DATA_INPUT = "assets/wheel_data_input/{assetId}"
    const val RENTALS = "assets/rentals/{assetId}"
    const val RENTAL_CUSTOMERS = "assets/rental-customers"
    const val RENTAL_CUSTOMER_NEW = "assets/rental-customers/new"
    const val RENTAL_CUSTOMER_EDIT = "assets/rental-customers/{customerId}/edit"
    const val RENTAL_BOOKING = "assets/rental-booking/{assetId}"
    const val RENTAL_INVOICE = "assets/rental-invoices/{invoiceId}"
    const val ONBOARDING = "assets/onboarding"
    const val COST_REPORT = "assets/cost_report"
    const val CATEGORY_REPORT = "assets/category_report/{assetId}"

    fun detail(assetId: String) = "assets/detail/$assetId"
    fun workOrders(assetId: String) = "assets/work_orders/$assetId"
    fun rentals(assetId: String) = "assets/rentals/$assetId"
    fun rentalCustomerEdit(customerId: String) = "assets/rental-customers/$customerId/edit"
    fun rentalBooking(assetId: String) = "assets/rental-booking/$assetId"
    fun rentalInvoice(invoiceId: String) = "assets/rental-invoices/$invoiceId"
    fun addEdit(assetId: String? = null, initialAssetType: String? = null) =
        "assets/add_edit?assetId=${Uri.encode(assetId ?: "")}&initialAssetType=${Uri.encode(initialAssetType ?: "")}"
    fun photoGallery(assetId: String, initialIndex: Int = 0) =
        "assets/gallery/$assetId?initialIndex=$initialIndex"
    fun pdfViewer(pdfUrl: String, title: String = "Document") =
        "assets/pdf_viewer?pdfUrl=${Uri.encode(pdfUrl)}&title=${Uri.encode(title)}"
    fun docScanner(entityId: String, entityType: String) =
        "assets/doc_scanner?entityId=${Uri.encode(entityId)}&entityType=${Uri.encode(entityType)}"
    fun notesFullScreen(entityId: String, entityType: String, initialText: String) =
        "assets/notes?entityId=${Uri.encode(entityId)}&entityType=${Uri.encode(entityType)}&initialText=${Uri.encode(initialText)}"
    fun colorPicker(currentColor: String?) =
        "assets/color_picker?currentColor=${Uri.encode(currentColor ?: "")}"
    fun wheelConfig(assetId: String) = "assets/wheel_config/$assetId"
    fun wheelConfigBuilder(assetId: String) = "assets/wheel_config_builder/$assetId"
    fun wheelDataInput(assetId: String) = "assets/wheel_data_input/$assetId"
    fun categoryReport(assetId: String) = "assets/category_report/$assetId"
}

/**
 * Registers the full assets navigation sub-graph.
 *
 * Call this from the app-level NavHost:
 * ```
 * assetsNavGraph(navController, onNavigateToLogEntry = { ... })
 * ```
 */
fun NavGraphBuilder.assetsNavGraph(
    navController: NavHostController,
    onNavigateToAddLogEntry: (assetId: String, categoryKey: String?) -> Unit = { _, _ -> },
    onNavigateToLogDetail: (entryId: String) -> Unit = {},
    onAssetPicked: (assetId: String) -> Unit = {},
    onNavigateToWorkOrder: (woId: String) -> Unit = {},
    /** Called when the user taps X in the asset picker to cancel. The host
     *  decides how far back to pop (e.g. all the way to the WO list). */
    onPickerCancel: () -> Unit = { navController.popBackStack() },
    rentalsEnabled: Boolean = true,
) {
    navigation(
        startDestination = AssetsRoute.LIST,
        route = AssetsRoute.GRAPH,
    ) {
        composable(AssetsRoute.ONBOARDING) {
            val onboardingVm: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                onComplete = {
                    onboardingVm.completeOnboarding()
                    navController.navigate(AssetsRoute.LIST) {
                        popUpTo(AssetsRoute.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(AssetsRoute.LIST) {
            val onboardingVm: OnboardingViewModel = hiltViewModel()
            val showOnboardingScreen by onboardingVm.showOnboardingScreen.collectAsStateWithLifecycle()
            LaunchedEffect(showOnboardingScreen) {
                if (showOnboardingScreen) {
                    navController.navigate(AssetsRoute.ONBOARDING) {
                        popUpTo(AssetsRoute.LIST) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
            AssetListScreen(
                onAssetClick = { assetId ->
                    navController.navigate(AssetsRoute.detail(assetId))
                },
                onAssetLongPress = { assetId ->
                    navController.navigate(AssetsRoute.addEdit(assetId))
                },
                onAddAsset = {
                    navController.navigate(AssetsRoute.TYPE_PICKER)
                },
                onScanBarcode = {
                    navController.navigate(AssetsRoute.BARCODE_SCANNER)
                },
            )
        }

        composable(
            route = AssetsRoute.DETAIL,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assetId = requireNotNull(backStackEntry.arguments?.getString("assetId"))
            AssetDetailScreen(
                assetId = assetId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AssetsRoute.addEdit(assetId)) },
                onAddLogEntry = { categoryKey -> onNavigateToAddLogEntry(assetId, categoryKey) },
                onLogEntryClick = { entryId -> onNavigateToLogDetail(entryId) },
                onOpenPhotoGallery = { index ->
                    navController.navigate(AssetsRoute.photoGallery(assetId, index))
                },
                onOpenWorkOrders = {
                    navController.navigate(AssetsRoute.workOrders(assetId))
                },
                onOpenNotes = { initialText ->
                    navController.navigate(AssetsRoute.notesFullScreen(assetId, "asset", initialText))
                },
                onOpenWheelConfig = {
                    navController.navigate(AssetsRoute.wheelConfig(assetId))
                },
                onOpenWheelDataInput = {
                    navController.navigate(AssetsRoute.wheelDataInput(assetId))
                },
                onOpenRentals = {
                    navController.navigate(AssetsRoute.rentals(assetId))
                },
                rentalsEnabled = rentalsEnabled,
                onOpenAssetChat = {
                    // No asset->thread mapping yet on Android; route to the chat list.
                    navController.navigate("chat/list")
                },
            )
        }

        composable(
            route = AssetsRoute.ADD_EDIT,
            arguments = listOf(
                navArgument("assetId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("initialAssetType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getString("assetId")
                ?.takeIf { it.isNotBlank() }
            val initialAssetType = backStackEntry.arguments?.getString("initialAssetType")
                ?.takeIf { it.isNotBlank() }
            AddEditAssetScreen(
                assetId = assetId,
                initialAssetType = initialAssetType,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onOpenTypePicker = { navController.navigate(AssetsRoute.TYPE_PICKER) },
                navController = navController,
                onOpenWheelConfig = {
                    navController.navigate(AssetsRoute.wheelConfigBuilder(assetId ?: "new"))
                },
            )
        }

        composable(AssetsRoute.TYPE_PICKER) {
            AssetTypePickerScreen(
                onTypeSelected = { typeKey ->
                    val previousRoute = navController.previousBackStackEntry?.destination?.route
                    if (previousRoute?.startsWith("assets/add_edit") == true) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("selected_asset_type", typeKey)
                        navController.popBackStack()
                    } else {
                        navController.navigate(AssetsRoute.addEdit(initialAssetType = typeKey)) {
                            popUpTo(AssetsRoute.TYPE_PICKER) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(AssetsRoute.PICKER) {
            AssetPickerScreen(
                onAssetSelected = { assetId ->
                    // onAssetPicked already calls popBackStack() — don't double-pop
                    onAssetPicked(assetId)
                },
                onBack = { navController.popBackStack() },
                onCancel = onPickerCancel,
            )
        }

        composable(AssetsRoute.BARCODE_SCANNER) {
            AssetBarcodeScannerScreen(
                onAssetFound = { assetId ->
                    // Pop the scanner and navigate to the matched asset detail
                    navController.popBackStack()
                    navController.navigate(AssetsRoute.detail(assetId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.PHOTO_GALLERY,
            arguments = listOf(
                navArgument("assetId") { type = NavType.StringType },
                navArgument("initialIndex") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { backStackEntry ->
            val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
            AssetPhotoGalleryScreen(
                initialIndex = initialIndex,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.WORK_ORDERS,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assetId = requireNotNull(backStackEntry.arguments?.getString("assetId"))
            AssetWorkOrdersScreen(
                assetId = assetId,
                onBack = { navController.popBackStack() },
                onOpenWorkOrder = { woId -> onNavigateToWorkOrder(woId) },
            )
        }

        composable(AssetsRoute.CATEGORY_PICKER) {
            CategoryPickerScreen(
                selectedCategory = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<String>("selected_category"),
                onCategorySelected = { name ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_category", name)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(AssetsRoute.DOC_TYPE_PICKER) {
            DocTypePickerScreen(
                onDocTypeSelected = { type ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_doc_type", type)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.PDF_VIEWER,
            arguments = listOf(
                navArgument("pdfUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = "Document"
                },
            ),
        ) { backStackEntry ->
            val pdfUrl = URLDecoder.decode(backStackEntry.arguments?.getString("pdfUrl") ?: "", "UTF-8")
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "Document", "UTF-8")
            AssetPdfViewerScreen(
                pdfUrl = pdfUrl,
                title = title,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.DOC_SCANNER,
            arguments = listOf(
                navArgument("entityId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("entityType") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val entityId = backStackEntry.arguments?.getString("entityId") ?: ""
            val entityType = backStackEntry.arguments?.getString("entityType") ?: ""
            DocScanPipelineScreen(
                entityId = entityId,
                entityType = entityType,
                onBack = { navController.popBackStack() },
                onDocSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.NOTES_FULL_SCREEN,
            arguments = listOf(
                navArgument("entityId") { type = NavType.StringType; defaultValue = "" },
                navArgument("entityType") { type = NavType.StringType; defaultValue = "" },
                navArgument("initialText") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val initialText = URLDecoder.decode(backStackEntry.arguments?.getString("initialText") ?: "", "UTF-8")
            NotesFullScreenScreen(
                initialText = initialText,
                onBack = { navController.popBackStack() },
                onSave = { text ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("saved_notes", text)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = AssetsRoute.COLOR_PICKER,
            arguments = listOf(
                navArgument("currentColor") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val currentColor = backStackEntry.arguments?.getString("currentColor")
                ?.takeIf { it.isNotBlank() }
            ColorPickerScreen(
                currentColor = currentColor,
                onColorSelected = { hex ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_color", hex)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.WHEEL_CONFIG,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) {
            val viewModel: WheelSaveViewModel = hiltViewModel()
            WheelConfigScreen(
                onSave = { position, tireSize, rimSize, brand, notes ->
                    viewModel.saveWheelConfig(position, tireSize, rimSize, brand, notes) {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.WHEEL_CONFIG_BUILDER,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { back ->
            val assetId = requireNotNull(back.arguments?.getString("assetId"))
            WheelConfigBuilderScreen(
                assetId = assetId,
                onSave = { config ->
                    // Serialize config and pass back to the previous screen (add/edit or detail)
                    val axlesJson = config.axles.joinToString(",") { axle ->
                        "{\"role\":\"${axle.role.name}\",\"tireType\":\"${axle.tireType.name}\"}"
                    }
                    val json = "{\"category\":\"${config.category.name}\",\"axles\":[$axlesJson],\"totalWheels\":${config.totalWheels}}"
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("wheel_config", json)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.WHEEL_DATA_INPUT,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) {
            val viewModel: WheelSaveViewModel = hiltViewModel()
            WheelDataInputScreen(
                onSave = { treadDepthMm, tirePressurePsi, lastInspectionMs, nextInspectionMs, condition ->
                    viewModel.saveWheelData(
                        treadDepthMm = treadDepthMm,
                        tirePressurePsi = tirePressurePsi,
                        lastInspectionMs = lastInspectionMs,
                        nextInspectionMs = nextInspectionMs,
                        condition = condition,
                    ) {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.RENTALS,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assetId = requireNotNull(backStackEntry.arguments?.getString("assetId"))
            AssetRentalsScreen(
                assetId = assetId,
                onBack = { navController.popBackStack() },
                onOpenBooking = { navController.navigate(AssetsRoute.rentalBooking(assetId)) },
                onOpenCustomers = { navController.navigate(AssetsRoute.RENTAL_CUSTOMERS) },
                onOpenInvoice = { invoiceId -> navController.navigate(AssetsRoute.rentalInvoice(invoiceId)) },
            )
        }

        composable(route = AssetsRoute.RENTAL_CUSTOMERS) {
            RentalCustomersScreen(
                onBack = { navController.popBackStack() },
                onAddCustomer = { navController.navigate(AssetsRoute.RENTAL_CUSTOMER_NEW) },
                onEditCustomer = { customerId ->
                    navController.navigate(AssetsRoute.rentalCustomerEdit(customerId))
                },
            )
        }

        composable(route = AssetsRoute.RENTAL_CUSTOMER_NEW) {
            RentalCustomerFormScreen(
                customerId = null,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.RENTAL_CUSTOMER_EDIT,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val customerId = requireNotNull(backStackEntry.arguments?.getString("customerId"))
            RentalCustomerFormScreen(
                customerId = customerId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.RENTAL_BOOKING,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assetId = requireNotNull(backStackEntry.arguments?.getString("assetId"))
            RentalBookingScreen(
                assetId = assetId,
                onBack = { navController.popBackStack() },
                onNavigateToNewCustomer = {
                    navController.navigate(AssetsRoute.RENTAL_CUSTOMER_NEW)
                },
                onBooked = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = AssetsRoute.RENTAL_INVOICE,
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val invoiceId = requireNotNull(backStackEntry.arguments?.getString("invoiceId"))
            RentalInvoiceScreen(
                invoiceId = invoiceId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = AssetsRoute.COST_REPORT) {
            CostReportScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AssetsRoute.CATEGORY_REPORT,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val assetId = requireNotNull(backStackEntry.arguments?.getString("assetId"))
            CategoryReportScreen(
                assetId = assetId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
