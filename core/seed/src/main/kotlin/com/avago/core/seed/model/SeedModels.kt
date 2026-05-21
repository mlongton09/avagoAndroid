package com.avago.core.seed.model

import kotlinx.serialization.Serializable

@Serializable
data class AssetTypeSeed(
    val id: String,
    val name: String,
    val icon: String? = null,
    val meter_types: List<String> = emptyList(),
)

@Serializable
data class LogCategorySeed(
    val id: String,
    val name: String,
    val icon: String? = null,
    val applies_to: List<String> = emptyList(),
)

@Serializable
data class LogCategoryGroupSeed(
    val asset_type: String,
    val categories: List<LogCategorySeed>,
)

@Serializable
data class InspectionTypeSeed(
    val id: String,
    val asset_type: String,
    val sub_category: String,
)

@Serializable
data class DocTypeSeed(
    val id: String,
    val name: String,
)

@Serializable
data class InventoryCategorySeed(
    val id: String,
    val name: String,
)

@Serializable
data class AppLimitsSeed(
    val max_assets: Int = 25,
    val max_log_entries: Int = 100,
    val max_work_orders: Int = 25,
    val max_inventory_parts: Int = 50,
    val max_docs: Int = 25,
    val max_photos_per_entry: Int = 4,
    val max_team_members: Int = 1,
)
