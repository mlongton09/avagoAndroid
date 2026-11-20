package com.avago.feature.workorders.model

import com.avago.feature.workorders.viewmodel.WoHorizon
import com.avago.feature.workorders.viewmodel.WoListFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The work-order list filters kept in a saved view's `spec` (dataset [DATASET]). */
@Serializable
data class WoSavedViewSpec(
    val query: String = "",
    val statuses: List<String> = emptyList(),
    val scope: String = WoListFilter.MINE.name,
    val horizon: String = WoHorizon.NOW.name,
) {
    val scopeFilter: WoListFilter get() = WoListFilter.entries.firstOrNull { it.name == scope } ?: WoListFilter.MINE
    val horizonFilter: WoHorizon get() = WoHorizon.entries.firstOrNull { it.name == horizon } ?: WoHorizon.NOW

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val DATASET = "work_orders"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun of(query: String, statuses: Set<String>, scope: WoListFilter, horizon: WoHorizon) =
            WoSavedViewSpec(query.trim(), statuses.sorted(), scope.name, horizon.name)

        /** The spec of a stored view, or null when it was written by something this list can't read. */
        fun parse(spec: String): WoSavedViewSpec? = try {
            json.decodeFromString(serializer(), spec)
        } catch (e: SerializationException) {
            null
        }
    }
}
