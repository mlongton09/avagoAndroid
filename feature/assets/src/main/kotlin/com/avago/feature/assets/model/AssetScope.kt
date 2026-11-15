package com.avago.feature.assets.model

import com.avago.core.network.json.bool
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import com.avago.core.network.json.strs
import kotlinx.serialization.json.JsonObject

/**
 * Product scope (feature I): which asset types an account may pick, mirroring
 * `GET /accounts/{a}/asset-types` (AvagoSvc `domain/scope.rs`). Real estate is
 * hidden unless the platform or an account override enables it.
 */
data class AssetTypeCatalog(
    val realEstateEnabled: Boolean,
    /** Type ids in picker order; empty when the server list is unavailable. */
    val types: List<String>,
    val hidden: Set<String>,
    /** id → usage meter (hours | distance | days | cycles | none). */
    val usageMeters: Map<String, String> = emptyMap(),
) {
    fun isVisible(key: String): Boolean =
        key !in hidden && (realEstateEnabled || !AssetScope.isRealEstate(key))

    companion object {
        /** Offline/unknown default: the platform flag defaults to off. */
        val Default = AssetTypeCatalog(realEstateEnabled = false, types = emptyList(), hidden = AssetScope.REAL_ESTATE_TYPES)

        fun from(root: JsonObject): AssetTypeCatalog {
            val o = root.obj("data") ?: root
            val enabled = o.bool("real_estate_enabled") ?: false
            val types = o.objs("types")
            return AssetTypeCatalog(
                realEstateEnabled = enabled,
                types = types.mapNotNull { it.str("id") },
                hidden = o.strs("hidden_types").toSet(),
                usageMeters = types.mapNotNull { t -> t.str("id")?.let { id -> t.str("usage_meter")?.let { id to it } } }.toMap(),
            )
        }
    }
}

object AssetScope {
    val REAL_ESTATE_TYPES = setOf("healthcare", "multifamily", "office", "residential", "restaurant")

    val TOOL_TYPES = setOf(
        "survey_layout", "drone", "welding_cutting", "concrete_compaction", "test_measurement", "power_tools",
    )

    val ROAD_VEHICLE_TYPES = setOf(
        "vehicle", "light_vehicle", "car", "ev", "commercial_vehicle", "truck", "motorcycle",
        "recreational_vehicle", "trailer", "atv_utv", "golf_cart", "snowmobile",
    )

    /** Road types whose usage meter is distance (trailers have none; ATVs and golf carts use hours). */
    private val DISTANCE_TYPES = ROAD_VEHICLE_TYPES - setOf("trailer", "atv_utv", "golf_cart")

    fun isRealEstate(type: String?): Boolean = type?.lowercase() in REAL_ESTATE_TYPES
    fun isTool(type: String?): Boolean = type?.lowercase() in TOOL_TYPES
    fun isRoadVehicle(type: String?): Boolean = type?.lowercase() in ROAD_VEHICLE_TYPES

    /** VIN applies to road vehicles and to untyped assets; tools carry a serial number instead. */
    fun showsVin(type: String?): Boolean = type == null || isRoadVehicle(type)

    fun showsLicensePlate(type: String?): Boolean = !isTool(type) && !isRealEstate(type)

    fun tracksMileage(type: String?, catalog: AssetTypeCatalog? = null): Boolean {
        val k = type?.lowercase() ?: return false
        catalog?.usageMeters?.get(k)?.let { return it == "distance" }
        return k in DISTANCE_TYPES
    }

    /**
     * Picker groups for [catalog]. Hidden types are dropped, except [currentType]
     * which stays visible so an existing asset keeps its type.
     */
    fun visibleGroups(
        groups: List<AssetTypeGroup>,
        catalog: AssetTypeCatalog,
        currentType: String? = null,
    ): List<AssetTypeGroup> = groups
        .map { g -> g.copy(types = g.types.filter { it.key == currentType || catalog.isVisible(it.key) }) }
        .filter { it.types.isNotEmpty() }
}
