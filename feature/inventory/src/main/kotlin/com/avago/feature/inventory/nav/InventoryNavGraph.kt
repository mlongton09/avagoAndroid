package com.avago.feature.inventory.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.avago.feature.inventory.cyclecounts.CycleCountDetailScreen
import com.avago.feature.inventory.cyclecounts.CycleCountListScreen
import com.avago.feature.inventory.parts.AddEditPartScreen
import com.avago.feature.inventory.parts.InventoryListScreen
import com.avago.feature.inventory.parts.PartDetailScreen
import com.avago.feature.inventory.purchaseorders.PurchaseOrderDetailScreen
import com.avago.feature.inventory.purchaseorders.PurchaseOrderListScreen
import com.avago.feature.inventory.purchaseorders.PurchaseOrderCreateScreen
import com.avago.feature.inventory.vendors.AddEditVendorScreen
import com.avago.feature.inventory.vendors.VendorDetailScreen
import com.avago.feature.inventory.vendors.VendorListScreen
import com.avago.feature.inventory.warehouse.WarehouseIssueScreen
import com.avago.feature.inventory.warehouse.WarehouseMenuScreen
import com.avago.feature.inventory.warehouse.WarehouseMoveScreen
import com.avago.feature.inventory.warehouse.WarehouseReceiveScreen

// ---------------------------------------------------------------------------
// Type-safe route descriptors
// ---------------------------------------------------------------------------

sealed class InventoryRoute(val route: String) {
    object List : InventoryRoute("inventory/list")
    object VendorList : InventoryRoute("inventory/vendors")
    object PurchaseOrderList : InventoryRoute("inventory/purchase-orders")
    object WarehouseMenu : InventoryRoute("inventory/warehouse")
    object WarehouseReceive : InventoryRoute("inventory/warehouse/receive")
    object WarehouseIssue : InventoryRoute("inventory/warehouse/issue")
    object WarehouseMove : InventoryRoute("inventory/warehouse/move")
    object CycleCountList : InventoryRoute("inventory/cycle-counts")

    object PartDetail : InventoryRoute("inventory/parts/{partId}") {
        fun build(partId: String) = "inventory/parts/$partId"
    }

    object AddEditPart : InventoryRoute("inventory/parts/edit?partId={partId}") {
        fun build(partId: String? = null) =
            if (partId != null) "inventory/parts/edit?partId=$partId"
            else "inventory/parts/edit"
    }

    object VendorDetail : InventoryRoute("inventory/vendors/{vendorId}") {
        fun build(vendorId: String) = "inventory/vendors/$vendorId"
    }

    object AddEditVendor : InventoryRoute("inventory/vendors/edit?vendorId={vendorId}") {
        fun build(vendorId: String? = null) =
            if (vendorId != null) "inventory/vendors/edit?vendorId=$vendorId"
            else "inventory/vendors/edit"
    }

    object PurchaseOrderDetail : InventoryRoute("inventory/purchase-orders/{poId}") {
        fun build(poId: String) = "inventory/purchase-orders/$poId"
    }

    object CreateEditPurchaseOrder : InventoryRoute("inventory/purchase-orders/edit?poId={poId}") {
        fun build(poId: String? = null) =
            if (poId != null) "inventory/purchase-orders/edit?poId=$poId"
            else "inventory/purchase-orders/edit"
    }

    object CycleCountDetail : InventoryRoute("inventory/cycle-counts/{countId}") {
        fun build(countId: String) = "inventory/cycle-counts/$countId"
    }
}

// ---------------------------------------------------------------------------
// NavGraph wiring
// ---------------------------------------------------------------------------

fun NavGraphBuilder.inventoryNavGraph(navController: NavHostController) {
    navigation(
        startDestination = InventoryRoute.List.route,
        route = "inventory_graph",
    ) {
        // Parts
        composable(InventoryRoute.List.route) {
            InventoryListScreen(
                onPartClick = { partId -> navController.navigate(InventoryRoute.PartDetail.build(partId)) },
                onAddPart = { navController.navigate(InventoryRoute.AddEditPart.build()) },
            )
        }
        composable(
            route = InventoryRoute.PartDetail.route,
            arguments = listOf(navArgument("partId") { type = NavType.StringType }),
        ) { back ->
            val partId = back.arguments?.getString("partId") ?: return@composable
            PartDetailScreen(
                partId = partId,
                onEdit = { navController.navigate(InventoryRoute.AddEditPart.build(partId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = InventoryRoute.AddEditPart.route,
            arguments = listOf(navArgument("partId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { back ->
            val partId = back.arguments?.getString("partId")
            AddEditPartScreen(
                partId = partId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        // Vendors
        composable(InventoryRoute.VendorList.route) {
            VendorListScreen(
                onVendorClick = { vendorId -> navController.navigate(InventoryRoute.VendorDetail.build(vendorId)) },
                onAddVendor = { navController.navigate(InventoryRoute.AddEditVendor.build()) },
            )
        }
        composable(
            route = InventoryRoute.VendorDetail.route,
            arguments = listOf(navArgument("vendorId") { type = NavType.StringType }),
        ) { back ->
            val vendorId = back.arguments?.getString("vendorId") ?: return@composable
            VendorDetailScreen(
                vendorId = vendorId,
                onEdit = { navController.navigate(InventoryRoute.AddEditVendor.build(vendorId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = InventoryRoute.AddEditVendor.route,
            arguments = listOf(navArgument("vendorId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { back ->
            val vendorId = back.arguments?.getString("vendorId")
            AddEditVendorScreen(
                vendorId = vendorId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        // Purchase Orders
        composable(InventoryRoute.PurchaseOrderList.route) {
            PurchaseOrderListScreen(
                onPoClick = { poId -> navController.navigate(InventoryRoute.PurchaseOrderDetail.build(poId)) },
                onCreatePo = { navController.navigate(InventoryRoute.CreateEditPurchaseOrder.build()) },
            )
        }
        composable(
            route = InventoryRoute.PurchaseOrderDetail.route,
            arguments = listOf(navArgument("poId") { type = NavType.StringType }),
        ) { back ->
            val poId = back.arguments?.getString("poId") ?: return@composable
            PurchaseOrderDetailScreen(
                poId = poId,
                onEdit = { navController.navigate(InventoryRoute.CreateEditPurchaseOrder.build(poId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = InventoryRoute.CreateEditPurchaseOrder.route,
            arguments = listOf(navArgument("poId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { back ->
            val poId = back.arguments?.getString("poId")
            PurchaseOrderCreateScreen(
                poId = poId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        // Warehouse
        composable(InventoryRoute.WarehouseMenu.route) {
            WarehouseMenuScreen(
                onReceive = { navController.navigate(InventoryRoute.WarehouseReceive.route) },
                onIssue = { navController.navigate(InventoryRoute.WarehouseIssue.route) },
                onMove = { navController.navigate(InventoryRoute.WarehouseMove.route) },
            )
        }
        composable(InventoryRoute.WarehouseReceive.route) {
            WarehouseReceiveScreen(onBack = { navController.popBackStack() })
        }
        composable(InventoryRoute.WarehouseIssue.route) {
            WarehouseIssueScreen(onBack = { navController.popBackStack() })
        }
        composable(InventoryRoute.WarehouseMove.route) {
            WarehouseMoveScreen(onBack = { navController.popBackStack() })
        }

        // Cycle counts
        composable(InventoryRoute.CycleCountList.route) {
            CycleCountListScreen(
                onCountClick = { countId -> navController.navigate(InventoryRoute.CycleCountDetail.build(countId)) },
            )
        }
        composable(
            route = InventoryRoute.CycleCountDetail.route,
            arguments = listOf(navArgument("countId") { type = NavType.StringType }),
        ) { back ->
            val countId = back.arguments?.getString("countId") ?: return@composable
            CycleCountDetailScreen(
                countId = countId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
