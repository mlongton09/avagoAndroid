package com.avago.feature.assets.model

import androidx.annotation.DrawableRes
import com.avago.feature.assets.R

data class AssetTypeItem(
    val key: String,
    val labelResId: Int,
    @DrawableRes val iconRes: Int,
    /** Asset name under `assets/icons/` for the hero illustration, or null. */
    val heroImageName: String? = null,
    /** Background color hex for the asset avatar, matching iOS AvatarView.colorForAssetType(). */
    val colorHex: String = "#2563EB",
)

object AssetTypes {
    val all: List<AssetTypeItem> = listOf(
        AssetTypeItem("vehicle",              R.string.asset_type_vehicle,              R.drawable.ic_asset_light_vehicle,        "hero_light_vehicle",        "#3882C8"),
        AssetTypeItem("light_vehicle",        R.string.asset_type_light_vehicle,        R.drawable.ic_asset_light_vehicle,        "hero_light_vehicle",        "#3882C8"),
        AssetTypeItem("motorcycle",           R.string.asset_type_motorcycle,           R.drawable.ic_asset_motorcycle,           "hero_motorcycle",           "#7038C0"),
        AssetTypeItem("ev",                   R.string.asset_type_ev,                   R.drawable.ic_asset_light_vehicle,        "hero_light_vehicle",        "#3882C8"),
        AssetTypeItem("truck",                R.string.asset_type_truck,                R.drawable.ic_asset_commercial_vehicle,   "hero_commercial_vehicle",   "#F07828"),
        AssetTypeItem("commercial_vehicle",   R.string.asset_type_commercial_vehicle,   R.drawable.ic_asset_commercial_vehicle,   "hero_commercial_vehicle",   "#F07828"),
        AssetTypeItem("recreational_vehicle", R.string.asset_type_recreational_vehicle, R.drawable.ic_asset_recreational_vehicle, "hero_recreational_vehicle", "#38B838"),
        AssetTypeItem("trailer",              R.string.asset_type_trailer,              R.drawable.ic_asset_trailer,              null,                        "#48B8E0"),
        AssetTypeItem("atv_utv",              R.string.asset_type_atv_utv,              R.drawable.ic_asset_atv_utv,              null,                        "#E83820"),
        AssetTypeItem("snowmobile",           R.string.asset_type_snowmobile,           R.drawable.ic_asset_snowmobile,           null,                        "#40CCE8"),
        AssetTypeItem("golf_cart",            R.string.asset_type_golf_cart,            R.drawable.ic_asset_golf_cart,            null,                        "#90CC30"),
        AssetTypeItem("equipment",            R.string.asset_type_equipment,            R.drawable.ic_asset_heavy_equipment,      "hero_heavy_equipment",      "#F5A020"),
        AssetTypeItem("heavy_equipment",      R.string.asset_type_heavy_equipment,      R.drawable.ic_asset_heavy_equipment,      "hero_heavy_equipment",      "#F5A020"),
        AssetTypeItem("generator",            R.string.asset_type_generator,            R.drawable.ic_asset_generator,            null,                        "#F0D820"),
        AssetTypeItem("lawn_equipment",       R.string.asset_type_lawn_equipment,       R.drawable.ic_asset_lawn_equipment,       null,                        "#68C020"),
        AssetTypeItem("compressor",           R.string.asset_type_compressor,           R.drawable.ic_asset_generator,            null,                        "#F0D820"),
        AssetTypeItem("forklift",             R.string.asset_type_forklift,             R.drawable.ic_asset_heavy_equipment,      "hero_heavy_equipment",      "#F5A020"),
        AssetTypeItem("crane",                R.string.asset_type_crane,                R.drawable.ic_asset_heavy_equipment,      "hero_heavy_equipment",      "#F5A020"),
        AssetTypeItem("personal_watercraft",  R.string.asset_type_personal_watercraft,  R.drawable.ic_asset_personal_watercraft,  null,                        "#2888C8"),
        AssetTypeItem("pleasure_craft",       R.string.asset_type_pleasure_craft,       R.drawable.ic_asset_pleasure_craft,       "hero_pleasure_craft",       "#2850C0"),
        AssetTypeItem("commercial_vessel",    R.string.asset_type_commercial_vessel,    R.drawable.ic_asset_commercial_vessel,    "hero_commercial_vessel",    "#2040B0"),
        AssetTypeItem("residential",          R.string.asset_type_residential,          R.drawable.ic_asset_residential,          "hero_residential",          "#D82020"),
        AssetTypeItem("multifamily",          R.string.asset_type_multifamily,          R.drawable.ic_asset_multifamily,          "hero_multifamily",          "#E04018"),
        AssetTypeItem("office",               R.string.asset_type_office,               R.drawable.ic_asset_office,               "hero_office",               "#2868C0"),
        AssetTypeItem("industrial",           R.string.asset_type_industrial,           R.drawable.ic_asset_industrial,           "hero_industrial",           "#C88018"),
        AssetTypeItem("healthcare",           R.string.asset_type_healthcare,           R.drawable.ic_asset_healthcare,           "hero_healthcare",           "#28B8C0"),
    )

    private val keySet = all.map { it.key }.toHashSet()

    @DrawableRes
    fun iconResFor(key: String?): Int =
        all.firstOrNull { it.key == key }?.iconRes ?: R.drawable.ic_asset_light_vehicle

    fun labelResIdFor(key: String?): Int? =
        all.firstOrNull { it.key == key }?.labelResId

    fun labelFor(key: String?): Int? = labelResIdFor(key)

    /** Hero illustration asset name for `assets/icons/<name>.svg`, or null when not available. */
    fun heroImageNameFor(key: String?): String? =
        all.firstOrNull { it.key == key }?.heroImageName

    /** Avatar background color matching iOS AvatarView.colorForAssetType(); `#2563EB` for unknown types. */
    fun colorHexFor(key: String?): String =
        all.firstOrNull { it.key == key }?.colorHex ?: "#2563EB"

    fun isKnownType(key: String?): Boolean = key != null && keySet.contains(key)
}

val AssetColorPalette: List<Long> = listOf(
    0xFFE57373L, 0xFFFF8A65L, 0xFFFFD54FL, 0xFFAED581L,
    0xFF4DB6ACL, 0xFF4FC3F7L, 0xFF7986CBL, 0xFFF06292L,
    0xFFA1887FL, 0xFF90A4AEL, 0xFFEF5350L, 0xFFFF7043L,
    0xFFFFA726L, 0xFF66BB6AL, 0xFF26C6DAL, 0xFF29B6F6L,
    0xFF5C6BC0L, 0xFFEC407AL, 0xFF8D6E63L, 0xFF78909CL,
)
