package com.avago.feature.assets.model

import androidx.annotation.DrawableRes
import com.avago.feature.assets.R

data class AssetTypeItem(
    val key: String,
    val labelResId: Int,
    @DrawableRes val iconRes: Int,
)

object AssetTypes {
    val all: List<AssetTypeItem> = listOf(
        AssetTypeItem("vehicle",              R.string.asset_type_vehicle,              R.drawable.ic_asset_light_vehicle),
        AssetTypeItem("light_vehicle",        R.string.asset_type_light_vehicle,        R.drawable.ic_asset_light_vehicle),
        AssetTypeItem("motorcycle",           R.string.asset_type_motorcycle,           R.drawable.ic_asset_motorcycle),
        AssetTypeItem("ev",                   R.string.asset_type_ev,                   R.drawable.ic_asset_light_vehicle),
        AssetTypeItem("truck",                R.string.asset_type_truck,                R.drawable.ic_asset_commercial_vehicle),
        AssetTypeItem("commercial_vehicle",   R.string.asset_type_commercial_vehicle,   R.drawable.ic_asset_commercial_vehicle),
        AssetTypeItem("recreational_vehicle", R.string.asset_type_recreational_vehicle, R.drawable.ic_asset_recreational_vehicle),
        AssetTypeItem("trailer",              R.string.asset_type_trailer,              R.drawable.ic_asset_trailer),
        AssetTypeItem("atv_utv",              R.string.asset_type_atv_utv,              R.drawable.ic_asset_atv_utv),
        AssetTypeItem("snowmobile",           R.string.asset_type_snowmobile,           R.drawable.ic_asset_snowmobile),
        AssetTypeItem("golf_cart",            R.string.asset_type_golf_cart,            R.drawable.ic_asset_golf_cart),
        AssetTypeItem("equipment",            R.string.asset_type_equipment,            R.drawable.ic_asset_heavy_equipment),
        AssetTypeItem("heavy_equipment",      R.string.asset_type_heavy_equipment,      R.drawable.ic_asset_heavy_equipment),
        AssetTypeItem("generator",            R.string.asset_type_generator,            R.drawable.ic_asset_generator),
        AssetTypeItem("lawn_equipment",       R.string.asset_type_lawn_equipment,       R.drawable.ic_asset_lawn_equipment),
        AssetTypeItem("compressor",           R.string.asset_type_compressor,           R.drawable.ic_asset_generator),
        AssetTypeItem("forklift",             R.string.asset_type_forklift,             R.drawable.ic_asset_heavy_equipment),
        AssetTypeItem("crane",                R.string.asset_type_crane,                R.drawable.ic_asset_heavy_equipment),
        AssetTypeItem("personal_watercraft",  R.string.asset_type_personal_watercraft,  R.drawable.ic_asset_personal_watercraft),
        AssetTypeItem("pleasure_craft",       R.string.asset_type_pleasure_craft,       R.drawable.ic_asset_pleasure_craft),
        AssetTypeItem("commercial_vessel",    R.string.asset_type_commercial_vessel,    R.drawable.ic_asset_commercial_vessel),
        AssetTypeItem("residential",          R.string.asset_type_residential,          R.drawable.ic_asset_residential),
        AssetTypeItem("multifamily",          R.string.asset_type_multifamily,          R.drawable.ic_asset_multifamily),
        AssetTypeItem("office",               R.string.asset_type_office,               R.drawable.ic_asset_office),
        AssetTypeItem("industrial",           R.string.asset_type_industrial,           R.drawable.ic_asset_industrial),
        AssetTypeItem("healthcare",           R.string.asset_type_healthcare,           R.drawable.ic_asset_healthcare),
    )

    private val keySet = all.map { it.key }.toHashSet()

    @DrawableRes
    fun iconResFor(key: String?): Int =
        all.firstOrNull { it.key == key }?.iconRes ?: R.drawable.ic_asset_light_vehicle

    fun labelResIdFor(key: String?): Int? =
        all.firstOrNull { it.key == key }?.labelResId

    fun labelFor(key: String?): Int? = labelResIdFor(key)
}

val AssetColorPalette: List<Long> = listOf(
    0xFFE57373L, 0xFFFF8A65L, 0xFFFFD54FL, 0xFFAED581L,
    0xFF4DB6ACL, 0xFF4FC3F7L, 0xFF7986CBL, 0xFFF06292L,
    0xFFA1887FL, 0xFF90A4AEL, 0xFFEF5350L, 0xFFFF7043L,
    0xFFFFA726L, 0xFF66BB6AL, 0xFF26C6DAL, 0xFF29B6F6L,
    0xFF5C6BC0L, 0xFFEC407AL, 0xFF8D6E63L, 0xFF78909CL,
)
