package com.avago.feature.log.model

import com.avago.feature.log.model.LogPacks.readBool
import com.avago.feature.log.model.LogPacks.readDouble
import com.avago.feature.log.model.LogPacks.readString
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs

/**
 * Pure per-asset log analytics, ported from web `src/lib/log/analytics.ts` (L7):
 * fuel economy series (full-to-full fills, partials carried forward, 25 % median
 * anomaly flag), cost per meter unit and spend by category. No Android / I/O.
 */
object LogAnalytics {

    enum class VolumeUnit(val label: String) { GAL("gal"), L("L"), KWH("kWh") }
    enum class DistanceUnit(val label: String) { MI("mi"), KM("km"), H("h") }

    private const val L_PER_GAL = 3.785411784
    private const val KM_PER_MI = 1.609344
    const val ECONOMY_ANOMALY_FRACTION = 0.25

    /** Minimal entry view so the math is testable without Room entities. */
    data class Entry(
        val id: String,
        val dateMs: Long,
        val category: String?,
        val meter: Double?,
        val cost: Double?,
        val attributes: JsonObject = JsonObject(emptyMap()),
    )

    /** Accepts both the canonical Android (`gallon` / `liter`) and the web (`gal` / `L`) spellings. */
    fun volumeUnitOf(raw: String?): VolumeUnit? = when (LogPacks.normalizeVolumeUnit(raw)) {
        "gallon" -> VolumeUnit.GAL
        "liter" -> VolumeUnit.L
        "kWh" -> VolumeUnit.KWH
        else -> null
    }

    /** Asset meter type wins for hour meters; otherwise the user distance preference. */
    fun distanceUnitFor(meterType: String?, preferredDistance: String?): DistanceUnit {
        val mt = meterType?.trim()?.lowercase()
        if (mt == "engine_hours" || mt == "hours" || mt == "hrs" || mt == "h") return DistanceUnit.H
        if (mt == "km" || mt == "kilometers") return DistanceUnit.KM
        val pref = preferredDistance?.trim()?.lowercase()
        return when {
            pref == "hours" || pref == "hrs" || pref == "h" -> DistanceUnit.H
            pref?.startsWith("km") == true -> DistanceUnit.KM
            else -> DistanceUnit.MI
        }
    }

    fun economyUnitFor(distance: DistanceUnit, volume: VolumeUnit): String = when {
        volume == VolumeUnit.KWH -> when (distance) {
            DistanceUnit.H -> "kWh/h"
            DistanceUnit.KM -> "km/kWh"
            DistanceUnit.MI -> "mi/kWh"
        }
        distance == DistanceUnit.H -> if (volume == VolumeUnit.L) "L/h" else "gal/h"
        distance == DistanceUnit.KM -> "km/L"
        else -> "mpg"
    }

    /** Converts a stored economy value into [to], or null when the units are incompatible. */
    fun convertEconomy(value: Double, from: String, to: String): Double? {
        if (from == to) return value
        val kmPerL: Double? = when (from) {
            "mpg" -> value * KM_PER_MI / L_PER_GAL
            "km/L" -> value
            "L/100km" -> if (value > 0) 100.0 / value else null
            else -> null
        }
        if (kmPerL == null) {
            return when {
                from == "mi/kWh" && to == "km/kWh" -> value * KM_PER_MI
                from == "km/kWh" && to == "mi/kWh" -> value / KM_PER_MI
                from == "gal/h" && to == "L/h" -> value * L_PER_GAL
                from == "L/h" && to == "gal/h" -> value / L_PER_GAL
                else -> null
            }
        }
        return when (to) {
            "km/L" -> kmPerL
            "mpg" -> kmPerL * L_PER_GAL / KM_PER_MI
            "L/100km" -> if (kmPerL > 0) 100.0 / kmPerL else null
            else -> null
        }
    }

    private fun convertVolume(v: Double, from: VolumeUnit, to: VolumeUnit): Double {
        if (from == to || from == VolumeUnit.KWH || to == VolumeUnit.KWH) return v
        return if (from == VolumeUnit.GAL) v * L_PER_GAL else v / L_PER_GAL
    }

    fun median(values: List<Double>): Double? {
        val xs = values.filter { it.isFinite() }.sorted()
        if (xs.isEmpty()) return null
        val mid = xs.size / 2
        return if (xs.size % 2 == 1) xs[mid] else (xs[mid - 1] + xs[mid]) / 2
    }

    fun fuelVolume(e: Entry): Double? = e.attributes.readDouble("fuel_volume")?.takeIf { it > 0 }

    private fun isFullFill(e: Entry): Boolean = e.attributes.readBool("fuel_full_fill") != false

    data class EconomyPoint(
        val id: String,
        val dateMs: Long,
        val meter: Double?,
        val volume: Double,
        val distance: Double?,
        val economy: Double?,
        val stored: Boolean,
        val anomaly: Boolean,
        val partial: Boolean,
    )

    data class EconomySeries(
        val unit: String,
        val volumeUnit: VolumeUnit,
        val distanceUnit: DistanceUnit,
        val points: List<EconomyPoint>,
        val median: Double?,
        val average: Double?,
        val totalVolume: Double,
        val totalDistance: Double,
        val anomalies: Int,
    ) {
        val withEconomy: List<EconomyPoint> get() = points.filter { it.economy != null }
        val last: Double? get() = withEconomy.lastOrNull()?.economy
    }

    fun economySeries(entries: List<Entry>, distanceUnit: DistanceUnit = DistanceUnit.MI): EconomySeries {
        val fills = entries.filter { fuelVolume(it) != null }
            .sortedWith(compareBy<Entry> { it.dateMs }.thenBy { it.meter ?: 0.0 })
        val firstUnit = fills.firstNotNullOfOrNull { volumeUnitOf(it.attributes.readString("fuel_volume_unit")) }
        val volumeUnit = when {
            firstUnit == VolumeUnit.KWH -> VolumeUnit.KWH
            distanceUnit == DistanceUnit.KM -> VolumeUnit.L
            else -> firstUnit ?: VolumeUnit.GAL
        }
        val unit = economyUnitFor(distanceUnit, volumeUnit)
        val hours = distanceUnit == DistanceUnit.H

        val raw = mutableListOf<EconomyPoint>()
        var lastFullMeter: Double? = null
        var pending = 0.0
        var totalVolume = 0.0
        var totalDistance = 0.0
        for (f in fills) {
            val from = volumeUnitOf(f.attributes.readString("fuel_volume_unit")) ?: volumeUnit
            val vol = convertVolume(fuelVolume(f)!!, from, volumeUnit)
            val meter = f.meter?.takeIf { it > 0 }
            val full = isFullFill(f)
            pending += vol
            var economy: Double? = null
            var distance: Double? = null
            var stored = false
            if (full) {
                val lfm = lastFullMeter
                if (lfm != null && meter != null && meter > lfm && pending > 0) {
                    distance = meter - lfm
                    economy = if (hours) pending / distance else distance / pending
                    totalVolume += pending
                    totalDistance += distance
                }
                val storedVal = f.attributes.readDouble("fuel_economy")
                val storedUnit = f.attributes.readString("fuel_economy_unit")
                if (storedVal != null && storedVal > 0 && !storedUnit.isNullOrBlank()) {
                    convertEconomy(storedVal, storedUnit, unit)?.let { economy = it; stored = true }
                }
                if (meter != null) lastFullMeter = meter
                pending = 0.0
            }
            raw += EconomyPoint(f.id, f.dateMs, meter, vol, distance, economy, stored, anomaly = false, partial = !full)
        }

        val med = median(raw.mapNotNull { it.economy })
        var anomalies = 0
        val points = if (med != null && med > 0) {
            raw.map { p ->
                val e = p.economy
                if (e != null && abs(e - med) / med > ECONOMY_ANOMALY_FRACTION) {
                    anomalies++
                    p.copy(anomaly = true)
                } else p
            }
        } else raw
        val average = if (totalVolume > 0 && totalDistance > 0) {
            if (hours) totalVolume / totalDistance else totalDistance / totalVolume
        } else med
        return EconomySeries(unit, volumeUnit, distanceUnit, points, med, average, totalVolume, totalDistance, anomalies)
    }

    data class CostPerMeter(val totalCost: Double, val distance: Double, val perUnit: Double?, val unit: DistanceUnit)

    /** Spend inside the metered date window divided by the meter span (max − min). */
    fun costPerMeter(entries: List<Entry>, unit: DistanceUnit = DistanceUnit.MI): CostPerMeter {
        val metered = entries.filter { (it.meter ?: 0.0) > 0 }.sortedBy { it.dateMs }
        if (metered.size < 2) {
            return CostPerMeter(entries.sumOf { it.cost ?: 0.0 }, 0.0, null, unit)
        }
        val meters = metered.map { it.meter!! }
        val distance = meters.max() - meters.min()
        val from = dayKey(metered.first().dateMs)
        val to = dayKey(metered.last().dateMs)
        val total = entries.filter { dayKey(it.dateMs) in from..to }.sumOf { it.cost ?: 0.0 }
        return CostPerMeter(total, distance, if (distance > 0) total / distance else null, unit)
    }

    /** Day bucket (epoch day, UTC) so same-day entries share the web's YYYY-MM-DD window semantics. */
    private fun dayKey(ms: Long): Long = Math.floorDiv(ms, 86_400_000L)

    data class CategorySpend(val category: String, val total: Double, val count: Int)

    fun spendByCategory(entries: List<Entry>): List<CategorySpend> {
        val map = LinkedHashMap<String, CategorySpend>()
        for (e in entries) {
            val c = e.cost ?: continue
            if (c <= 0) continue
            val key = e.category?.takeIf { it.isNotBlank() } ?: "other"
            val prev = map[key]
            map[key] = CategorySpend(key, (prev?.total ?: 0.0) + c, (prev?.count ?: 0) + 1)
        }
        return map.values.sortedByDescending { it.total }
    }

    /** Same fallback chain as the web list rows: cost → cost_items → cost_labor. */
    fun entryCost(cost: Double?, costItems: Double?, costLabor: Double?): Double? =
        (cost ?: costItems ?: costLabor)?.takeIf { it.isFinite() }

    /** Everything the analytics sheet renders, computed once per log-list emission. */
    data class Summary(
        val totalSpend: Double,
        val entries: Int,
        val costPerMeter: CostPerMeter,
        val economy: EconomySeries,
        val spend: List<CategorySpend>,
    )

    fun summarize(entries: List<Entry>, distanceUnit: DistanceUnit): Summary = Summary(
        totalSpend = entries.sumOf { it.cost?.takeIf { c -> c > 0 } ?: 0.0 },
        entries = entries.size,
        costPerMeter = costPerMeter(entries, distanceUnit),
        economy = economySeries(entries, distanceUnit),
        spend = spendByCategory(entries),
    )
}
