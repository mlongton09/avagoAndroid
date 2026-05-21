package com.avago.feature.assets.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Fireplace
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.ui.graphics.vector.ImageVector

data class AssetTypeItem(
    val key: String,
    val labelResId: Int,
    val icon: ImageVector,
)

/**
 * Canonical list of 26 asset types matching the iOS app palette.
 * Labels reference string resource IDs from strings.xml.
 */
object AssetTypes {
    val all: List<AssetTypeItem> = listOf(
        AssetTypeItem("vehicle", com.avago.feature.assets.R.string.asset_type_vehicle, Icons.Default.DirectionsCar),
        AssetTypeItem("light_vehicle", com.avago.feature.assets.R.string.asset_type_light_vehicle, Icons.Default.DirectionsCar),
        AssetTypeItem("motorcycle", com.avago.feature.assets.R.string.asset_type_motorcycle, Icons.Default.DirectionsBike),
        AssetTypeItem("ev", com.avago.feature.assets.R.string.asset_type_ev, Icons.Default.ElectricCar),
        AssetTypeItem("truck", com.avago.feature.assets.R.string.asset_type_truck, Icons.Default.LocalShipping),
        AssetTypeItem("commercial_vehicle", com.avago.feature.assets.R.string.asset_type_commercial_vehicle, Icons.Default.DirectionsBus),
        AssetTypeItem("recreational_vehicle", com.avago.feature.assets.R.string.asset_type_recreational_vehicle, Icons.Default.AirportShuttle),
        AssetTypeItem("trailer", com.avago.feature.assets.R.string.asset_type_trailer, Icons.Default.LocalShipping),
        AssetTypeItem("atv_utv", com.avago.feature.assets.R.string.asset_type_atv_utv, Icons.Default.Agriculture),
        AssetTypeItem("snowmobile", com.avago.feature.assets.R.string.asset_type_snowmobile, Icons.Default.Speed),
        AssetTypeItem("golf_cart", com.avago.feature.assets.R.string.asset_type_golf_cart, Icons.Default.GolfCourse),
        AssetTypeItem("equipment", com.avago.feature.assets.R.string.asset_type_equipment, Icons.Default.Construction),
        AssetTypeItem("heavy_equipment", com.avago.feature.assets.R.string.asset_type_heavy_equipment, Icons.Default.Construction),
        AssetTypeItem("generator", com.avago.feature.assets.R.string.asset_type_generator, Icons.Default.EvStation),
        AssetTypeItem("lawn_equipment", com.avago.feature.assets.R.string.asset_type_lawn_equipment, Icons.Default.EnergySavingsLeaf),
        AssetTypeItem("compressor", com.avago.feature.assets.R.string.asset_type_compressor, Icons.Default.Fireplace),
        AssetTypeItem("forklift", com.avago.feature.assets.R.string.asset_type_forklift, Icons.Default.Warehouse),
        AssetTypeItem("crane", com.avago.feature.assets.R.string.asset_type_crane, Icons.Default.Handyman),
        AssetTypeItem("personal_watercraft", com.avago.feature.assets.R.string.asset_type_personal_watercraft, Icons.Default.Sailing),
        AssetTypeItem("pleasure_craft", com.avago.feature.assets.R.string.asset_type_pleasure_craft, Icons.Default.Sailing),
        AssetTypeItem("commercial_vessel", com.avago.feature.assets.R.string.asset_type_commercial_vessel, Icons.Default.Anchor),
        AssetTypeItem("residential", com.avago.feature.assets.R.string.asset_type_residential, Icons.Default.Home),
        AssetTypeItem("multifamily", com.avago.feature.assets.R.string.asset_type_multifamily, Icons.Default.HomeWork),
        AssetTypeItem("office", com.avago.feature.assets.R.string.asset_type_office, Icons.Default.Business),
        AssetTypeItem("industrial", com.avago.feature.assets.R.string.asset_type_industrial, Icons.Default.Warehouse),
        AssetTypeItem("healthcare", com.avago.feature.assets.R.string.asset_type_healthcare, Icons.Default.LocalHospital),
    )

    fun iconFor(key: String?): ImageVector =
        all.firstOrNull { it.key == key }?.icon ?: Icons.Default.DirectionsCar

    fun labelResIdFor(key: String?): Int? =
        all.firstOrNull { it.key == key }?.labelResId
}

/**
 * Preset avatar color palette (20 colors, matching iOS palette).
 */
val AssetColorPalette: List<Long> = listOf(
    0xFFE57373L,
    0xFFFF8A65L,
    0xFFFFD54FL,
    0xFFAED581L,
    0xFF4DB6ACL,
    0xFF4FC3F7L,
    0xFF7986CBL,
    0xFFF06292L,
    0xFFA1887FL,
    0xFF90A4AEL,
    0xFFEF5350L,
    0xFFFF7043L,
    0xFFFFA726L,
    0xFF66BB6AL,
    0xFF26C6DAL,
    0xFF29B6F6L,
    0xFF5C6BC0L,
    0xFFEC407AL,
    0xFF8D6E63L,
    0xFF78909CL,
)
