package com.avago.feature.assets.model

import androidx.compose.ui.graphics.Color
import com.avago.core.network.json.bool
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import com.avago.core.network.json.strs
import com.avago.feature.assets.R
import kotlinx.serialization.json.JsonObject

/** One pickable type from `GET /accounts/{a}/asset-types`. */
data class AssetTypeInfo(
    val id: String,
    /** road | marine | equipment | tools | real_estate */
    val group: String,
    /** vehicle | equipment | tool | real_estate */
    val kind: String,
    /** hours | distance | days | cycles | none */
    val usageMeter: String,
    val serialRequired: Boolean,
    val vinSupported: Boolean,
) {
    companion object {
        fun from(o: JsonObject): AssetTypeInfo? {
            val id = o.str("id")?.lowercase() ?: return null
            return AssetTypeInfo(
                id = id,
                group = o.str("group") ?: "other",
                kind = o.str("kind") ?: "equipment",
                usageMeter = o.str("usage_meter") ?: "none",
                serialRequired = o.bool("serial_required") ?: false,
                vinSupported = o.bool("vin_supported") ?: false,
            )
        }
    }
}

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
    /** Full server entries in picker order; empty offline. */
    val entries: List<AssetTypeInfo> = emptyList(),
) {
    fun isVisible(key: String): Boolean =
        key !in hidden && (realEstateEnabled || !AssetScope.isRealEstate(key))

    fun info(type: String?): AssetTypeInfo? = type?.lowercase()?.let { t -> entries.firstOrNull { it.id == t } }

    companion object {
        /** Offline/unknown default: the platform flag defaults to off. */
        val Default = AssetTypeCatalog(realEstateEnabled = false, types = emptyList(), hidden = AssetScope.REAL_ESTATE_TYPES)

        fun from(root: JsonObject): AssetTypeCatalog {
            val o = root.obj("data") ?: root
            val enabled = o.bool("real_estate_enabled") ?: false
            val types = o.objs("types")
            val entries = types.mapNotNull { AssetTypeInfo.from(it) }
            return AssetTypeCatalog(
                realEstateEnabled = enabled,
                types = types.mapNotNull { it.str("id") },
                hidden = o.strs("hidden_types").toSet(),
                usageMeters = types.mapNotNull { t -> t.str("id")?.let { id -> t.str("usage_meter")?.let { id to it } } }.toMap(),
                entries = entries,
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

    fun isTool(type: String?, catalog: AssetTypeCatalog? = null): Boolean =
        catalog?.info(type)?.let { it.kind == "tool" } ?: (type?.lowercase() in TOOL_TYPES)

    fun isRoadVehicle(type: String?, catalog: AssetTypeCatalog? = null): Boolean =
        catalog?.info(type)?.let { it.group == "road" } ?: (type?.lowercase() in ROAD_VEHICLE_TYPES)

    /** VIN applies to road vehicles and to untyped assets; tools carry a serial number instead. */
    fun showsVin(type: String?, catalog: AssetTypeCatalog? = null): Boolean =
        type == null || (catalog?.info(type)?.vinSupported ?: isRoadVehicle(type))

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

    private data class GroupMeta(val titleRes: Int, val color: Color)

    /** Server group ids in display order. */
    private val SERVER_GROUPS = linkedMapOf(
        "road" to GroupMeta(R.string.asset_type_group_vehicles, Color(0xFF0A84FF)),
        "equipment" to GroupMeta(R.string.asset_type_group_equipment, Color(0xFFF5A020)),
        "tools" to GroupMeta(R.string.asset_type_group_tools, Color(0xFF5856D6)),
        "marine" to GroupMeta(R.string.asset_type_group_marine, Color(0xFF0099CC)),
        "real_estate" to GroupMeta(R.string.asset_type_group_real_estate, Color(0xFF34C759)),
    )
    private val OTHER = GroupMeta(R.string.asset_type_group_other, Color(0xFF8E8E93))

    /** Local picker group → server group, for keeping a current type the server no longer lists. */
    private fun serverGroupOf(type: String): String =
        when (AssetTypes.grouped.firstOrNull { g -> g.types.any { it.key == type } }?.id) {
            "vehicles", "powersports" -> "road"
            "marine" -> "marine"
            "equipment" -> "equipment"
            "tools" -> "tools"
            "real_estate" -> "real_estate"
            else -> "other"
        }

    /** "test_measurement" → "Test measurement" for ids the app has no label for. */
    fun humanize(id: String): String =
        id.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

    private fun item(id: String): AssetTypeItem =
        AssetTypes.all.firstOrNull { it.key == id }
            ?: AssetTypeItem(id, 0, AssetTypes.iconResFor(null), fallbackLabel = humanize(id))

    /**
     * Picker groups from the server `types` list (already scoped to the account);
     * falls back to the built-in catalog when the list is unavailable.
     */
    fun pickerGroups(catalog: AssetTypeCatalog, currentType: String? = null): List<AssetTypeGroup> {
        if (catalog.entries.isEmpty()) return visibleGroups(AssetTypes.grouped, catalog, currentType)
        val byGroup = LinkedHashMap<String, MutableList<String>>()
        for (e in catalog.entries) byGroup.getOrPut(e.group) { mutableListOf() }.add(e.id)
        val current = currentType?.lowercase()?.takeIf { it.isNotBlank() }
        if (current != null && catalog.entries.none { it.id == current }) {
            byGroup.getOrPut(serverGroupOf(current)) { mutableListOf() }.add(current)
        }
        val order = SERVER_GROUPS.keys.toList()
        return byGroup.entries
            .sortedBy { (g, _) -> order.indexOf(g).let { if (it < 0) order.size else it } }
            .map { (g, ids) ->
                val meta = SERVER_GROUPS[g] ?: OTHER
                AssetTypeGroup(id = g, titleResId = meta.titleRes, color = meta.color, types = ids.distinct().map(::item))
            }
    }
}