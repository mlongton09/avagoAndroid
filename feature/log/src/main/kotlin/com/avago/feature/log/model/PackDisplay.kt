package com.avago.feature.log.model

import com.avago.feature.log.model.LogPacks.readBool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.NumberFormat
import java.util.Locale

/**
 * Read-only presentation of the contract §1 pack attributes (fuel, oil, tire, battery, brake
 * + common vendor / warranty keys). Port of web `components/log/extras/packDisplay.ts`.
 *
 * Pure: the caller passes a translator for the label keys (`pack_*`, `f_*`, `pos_*`,
 * `partial_fill`, `pm_plans_count`) so the same rows can be unit-tested and localized.
 */
object PackDisplay {

    enum class SectionId { FUEL, OIL, TIRE, BATTERY, BRAKE, COMMON }

    data class Row(val key: String, val label: String, val value: String)
    data class Section(val id: SectionId, val title: String, val rows: List<Row>)

    /** Translator: (key, count-or-null) → localized text. */
    fun interface Translator {
        fun t(key: String, count: Int?): String
    }

    private val POSITION_ORDER = listOf("fl", "fr", "rl", "rr", "rlo", "rli", "rri", "rro", "spare")

    val SECTION_KEYS: Map<SectionId, List<String>> = mapOf(
        SectionId.FUEL to listOf(
            "fuel_volume", "fuel_volume_unit", "fuel_price_per_unit", "fuel_grade", "fuel_station",
            "fuel_full_fill", "fuel_card_last4", "fuel_import_id", "fuel_economy", "fuel_economy_unit",
        ),
        SectionId.OIL to LogPacks.PACK_KEYS.getValue(LogPacks.Pack.OIL),
        SectionId.TIRE to LogPacks.PACK_KEYS.getValue(LogPacks.Pack.TIRE),
        SectionId.BATTERY to LogPacks.PACK_KEYS.getValue(LogPacks.Pack.BATTERY),
        SectionId.BRAKE to LogPacks.PACK_KEYS.getValue(LogPacks.Pack.BRAKE),
        SectionId.COMMON to listOf("vendor_id", "vendor_name", "pm_plan_ids", "receipt_doc_id", "warranty_until", "warranty_provider"),
    )

    /** Every key owned by a pack section (rendered there, or intentionally hidden like ids). */
    val ALL_PACK_KEYS: Set<String> = SECTION_KEYS.values.flatten().toSet()

    fun humanize(v: String): String {
        val s = v.replace('_', ' ').trim()
        return if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
    }

    private fun unitLabel(u: String?): String = when (u?.trim()) {
        "gallon", "gal" -> "gal"
        "liter", "L", "l" -> "L"
        null -> ""
        else -> u.trim()
    }

    private fun isEmpty(v: Any?): Boolean = when (v) {
        null, is JsonNull -> true
        is JsonPrimitive -> v.contentOrNull.isNullOrEmpty()
        is JsonArray -> v.isEmpty()
        is JsonObject -> v.isEmpty()
        else -> false
    }

    private fun JsonObject.rawString(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        if (!p.isString) p.doubleOrNull?.let { return LogPacks.formatNumber(it) }
        return p.contentOrNull
    }

    private fun JsonObject.numValue(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { p ->
            if (p is JsonNull) null else p.doubleOrNull ?: p.contentOrNull?.trim()?.replace(',', '.')?.toDoubleOrNull()
        }?.takeIf { it.isFinite() }

    private fun JsonObject.listValue(key: String): List<String> = when (val v = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> v.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        else -> emptyList()
    }

    private fun JsonObject.recordValue(key: String): Map<String, Double> =
        (this[key] as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }?.let { k to it }
        }?.toMap() ?: emptyMap()

    /**
     * @param formatCurrency formats a price-per-unit amount in the user currency.
     * @param formatDate formats an ISO `yyyy-MM-dd` date string for display.
     */
    fun build(
        attrs: JsonObject?,
        t: Translator,
        locale: Locale = Locale.getDefault(),
        formatCurrency: (Double) -> String = { String.format(locale, "%.2f", it) },
        formatDate: (String) -> String = { it },
        skipKeys: Set<String> = emptySet(),
    ): List<Section> {
        val a = attrs ?: JsonObject(emptyMap())
        fun n(v: Double, digits: Int): String = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = digits
            minimumFractionDigits = 0
        }.format(v)
        fun pos(p: String) = t.t("pos_$p", null)
        fun has(k: String) = k !in skipKeys && !isEmpty(a[k])
        fun label(k: String) = t.t("f_$k", null)
        fun simple(k: String, fmt: (String) -> String = { it }): Row? =
            if (has(k)) a.rawString(k)?.let { Row(k, label(k), fmt(it)) } else null
        fun enumVal(v: String) = humanize(v)
        fun recRow(k: String, unit: String): Row? {
            if (k in skipKeys) return null
            val r = a.recordValue(k)
            if (r.isEmpty()) return null
            val ks = r.keys.sortedBy { POSITION_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            val value = ks.joinToString(" · ") { p ->
                "${pos(p)} ${n(r.getValue(p), 1)}${if (unit.isNotEmpty()) " $unit" else ""}"
            }
            return Row(k, label(k), value)
        }

        val sections = mutableListOf<Section>()
        fun push(id: SectionId, rows: List<Row?>) {
            val r = rows.filterNotNull()
            if (r.isNotEmpty()) sections += Section(id, t.t("pack_${id.name.lowercase()}", null), r)
        }

        // Fuel
        val vol = a.numValue("fuel_volume")
        val vu = unitLabel(a.rawString("fuel_volume_unit"))
        push(SectionId.FUEL, listOf(
            if (vol != null && "fuel_volume" !in skipKeys) {
                Row("fuel_volume", label("fuel_volume"), n(vol, 3) + if (vu.isNotEmpty()) " $vu" else "")
            } else null,
            if (has("fuel_price_per_unit")) a.numValue("fuel_price_per_unit")?.let {
                Row("fuel_price_per_unit", label("fuel_price_per_unit"), formatCurrency(it) + if (vu.isNotEmpty()) " / $vu" else "")
            } else null,
            simple("fuel_grade", ::enumVal),
            simple("fuel_station"),
            if (vol != null && a.readBool("fuel_full_fill") == false) {
                Row("fuel_full_fill", label("fuel_full_fill"), t.t("partial_fill", null))
            } else null,
            if (has("fuel_economy")) a.numValue("fuel_economy")?.let {
                Row("fuel_economy", label("fuel_economy"), "${n(it, 1)} ${a.rawString("fuel_economy_unit") ?: ""}".trim())
            } else null,
            simple("fuel_card_last4") { "•••• $it" },
        ))

        // Oil
        val oq = a.numValue("oil_quantity")
        push(SectionId.OIL, listOf(
            simple("oil_type", ::enumVal),
            simple("oil_viscosity"),
            if (oq != null && "oil_quantity" !in skipKeys) {
                Row("oil_quantity", label("oil_quantity"), "${n(oq, 2)} ${unitLabel(a.rawString("oil_quantity_unit"))}".trim())
            } else null,
            simple("oil_filter_part"),
            if (has("oil_next_due_meter")) a.numValue("oil_next_due_meter")?.let {
                Row("oil_next_due_meter", label("oil_next_due_meter"), n(it, 0))
            } else null,
            simple("oil_next_due_date", formatDate),
        ))

        // Tire
        val positions = a.listValue("tire_positions")
        val treadUnit = a.rawString("tire_tread_unit") ?: "32nds"
        push(SectionId.TIRE, listOf(
            simple("tire_action", ::enumVal),
            if (positions.isNotEmpty() && "tire_positions" !in skipKeys) {
                Row("tire_positions", label("tire_positions"), positions.joinToString(", ") { pos(it) })
            } else null,
            simple("tire_brand"),
            simple("tire_size"),
            simple("tire_dot_date"),
            recRow("tire_tread", if (treadUnit == "32nds") "/32\"" else treadUnit),
            recRow("tire_psi", a.rawString("tire_pressure_unit") ?: "psi"),
        ))

        // Battery
        push(SectionId.BATTERY, listOf(
            simple("battery_action", ::enumVal),
            simple("battery_test_result", ::enumVal),
            simple("battery_rated_cca") { "$it CCA" },
            simple("battery_measured_cca") { "$it CCA" },
            if (has("battery_voltage")) {
                val v = a.numValue("battery_voltage")
                Row("battery_voltage", label("battery_voltage"), "${v?.let { n(it, 2) } ?: a.rawString("battery_voltage")} V")
            } else null,
            simple("battery_date_code"),
        ))

        // Brake
        push(SectionId.BRAKE, listOf(
            simple("brake_axle", ::enumVal),
            recRow("brake_pad_mm", "mm"),
            recRow("brake_rotor_mm", "mm"),
            simple("brake_fluid", ::enumVal),
        ))

        // Common
        push(SectionId.COMMON, listOf(
            simple("vendor_name"),
            simple("warranty_provider"),
            simple("warranty_until", formatDate),
            if (has("pm_plan_ids")) {
                Row("pm_plan_ids", label("pm_plan_ids"), t.t("pm_plans_count", a.listValue("pm_plan_ids").size))
            } else null,
        ))

        return sections
    }

    /**
     * Non-pack attributes still worth showing as generic "Item details" rows (scalar, non-empty,
     * not `false`). Pack keys are rendered by [build] instead.
     */
    fun extraRows(attrs: JsonObject?): List<Pair<String, String>> {
        val a = attrs ?: return emptyList()
        return a.mapNotNull { (k, v) ->
            if (k in ALL_PACK_KEYS) return@mapNotNull null
            val p = v as? JsonPrimitive ?: return@mapNotNull null
            if (p is JsonNull) return@mapNotNull null
            val s = if (!p.isString) p.doubleOrNull?.let { LogPacks.formatNumber(it) } ?: p.contentOrNull else p.contentOrNull
            if (s.isNullOrBlank() || s.equals("false", ignoreCase = true)) null else k to s
        }
    }
}
