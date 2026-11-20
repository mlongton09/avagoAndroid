package com.avago.feature.assets.model

import com.avago.core.data.domain.AssetToolDetails
import com.avago.core.data.domain.AssetVehicleDetails
import com.avago.core.data.domain.ToolDetailsDraft
import com.avago.core.data.domain.VehicleDetailsDraft
import com.avago.core.network.json.bool
import com.avago.core.network.json.int
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.LocalDate

// Feature I (scope) per-asset extras. Tool and vehicle details live in the v2 store and are edited with
// `maint.asset_tool_details.update` / `maint.asset_vehicle_details.update`; NHTSA recalls are a server
// query with REST remedy actions (AvagoSvc `routes/scope.rs`).

/** Calibration / registration / insurance expiry state (`ok | due_soon | overdue`). */
enum class ExpiryStatus {
    Ok, DueSoon, Overdue;

    companion object {
        fun from(s: String?): ExpiryStatus? = when (s) {
            "ok" -> Ok
            "due_soon" -> DueSoon
            "overdue" -> Overdue
            else -> null
        }

        /** Local expiry state of an ISO date: overdue before [today], due soon within [soonDays]. */
        fun of(date: String?, today: LocalDate = LocalDate.now(), soonDays: Long = 30): ExpiryStatus? {
            val d = date?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return null
            return when {
                d.isBefore(today) -> Overdue
                !d.isAfter(today.plusDays(soonDays)) -> DueSoon
                else -> Ok
            }
        }
    }
}

private fun JsonObject.data(): JsonObject = obj("data") ?: this

object AssetForms {
    /** Blank or an ISO `yyyy-MM-dd` date. */
    fun validDate(s: String): Boolean = s.isBlank() || parseDate(s) != null

    fun parseDate(s: String): LocalDate? = s.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun parseDecimal(s: String): BigDecimal? = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
}

/**
 * Editable tool-details text fields. A blank due date means "derived from calibrated-on + interval";
 * [of] shows a stored due date that equals the derived one as blank, so editing the calibration date or
 * interval moves it.
 */
data class ToolForm(
    val calibratedOn: String = "",
    val intervalDays: String = "",
    val dueOn: String = "",
    /** `days`, `cycles`, or empty for the asset type's usage meter. */
    val usageUnit: String = "",
    val usageValue: String = "",
    val replacementValue: String = "",
    val currency: String = "",
    val trackerTagId: String = "",
    val trackerKind: String = "",
) {
    /** Field key -> error code; empty when the form is valid. */
    fun errors(): Map<String, String> = buildMap {
        if (!AssetForms.validDate(calibratedOn)) put("calibrated_on", "date")
        if (!AssetForms.validDate(dueOn)) put("calibration_due_on", "date")
        if (intervalDays.isNotBlank() && intervalDays.trim().toIntOrNull()?.takeIf { it in 1..3650 } == null) {
            put("calibration_interval_days", "range")
        }
        if (usageValue.isNotBlank() && AssetForms.parseDecimal(usageValue)?.takeIf { it.signum() >= 0 } == null) put("usage_value", "number")
        if (replacementValue.isNotBlank() && AssetForms.parseDecimal(replacementValue)?.takeIf { it.signum() >= 0 } == null) {
            put("replacement_value", "number")
        }
        when {
            currency.isNotBlank() && !currency.trim().matches(Regex("[A-Za-z]{3}")) -> put("currency", "currency")
            currency.isBlank() && replacementValue.isNotBlank() -> put("currency", "currency")
        }
        if (trackerTagId.trim().length > 128) put("tracker_tag_id", "length")
    }

    /** The calibration due date the server derives for a blank [dueOn]; null when it cannot. */
    val derivedDueOn: LocalDate?
        get() {
            val cal = AssetForms.parseDate(calibratedOn) ?: return null
            val days = intervalDays.trim().toIntOrNull()?.takeIf { it in 1..3650 } ?: return null
            return cal.plusDays(days.toLong())
        }

    /** Call only when [errors] is empty. */
    fun toDraft(): ToolDetailsDraft = ToolDetailsDraft(
        calibratedOn = AssetForms.parseDate(calibratedOn),
        calibrationIntervalDays = intervalDays.trim().toIntOrNull(),
        calibrationDueOn = AssetForms.parseDate(dueOn),
        usageUnit = usageUnit.ifEmpty { null },
        usageValue = AssetForms.parseDecimal(usageValue),
        replacementValue = AssetForms.parseDecimal(replacementValue),
        currency = currency.trim().uppercase().ifEmpty { null },
        trackerTagId = trackerTagId.trim().ifEmpty { null },
        trackerKind = trackerKind.ifEmpty { null },
    )

    companion object {
        fun of(t: AssetToolDetails?, defaultCurrency: String): ToolForm {
            if (t == null) return ToolForm(currency = defaultCurrency)
            val form = ToolForm(
                calibratedOn = t.calibratedOn.orEmpty(),
                intervalDays = t.calibrationIntervalDays?.toString().orEmpty(),
                usageUnit = t.usageUnit.orEmpty(),
                usageValue = t.usageValue?.stripTrailingZeros()?.toPlainString().orEmpty(),
                replacementValue = t.replacementValue?.toPlainString().orEmpty(),
                currency = t.currency ?: defaultCurrency,
                trackerTagId = t.trackerTagId.orEmpty(),
                trackerKind = t.trackerKind.orEmpty(),
            )
            val derived = form.derivedDueOn?.toString()
            return form.copy(dueOn = t.calibrationDueOn?.takeIf { it != derived }.orEmpty())
        }

        val TRACKER_KINDS = listOf("gps", "bluetooth", "uwb", "other")
    }
}

/** Editable vehicle-details text fields. */
data class VehicleForm(
    val bodyClass: String = "",
    val fuelType: String = "",
    val gvwr: String = "",
    val registrationNumber: String = "",
    val registrationExpiresOn: String = "",
    val insurancePolicyNumber: String = "",
    val insuranceExpiresOn: String = "",
) {
    fun errors(): Map<String, String> = buildMap {
        if (!AssetForms.validDate(registrationExpiresOn)) put("registration_expires_on", "date")
        if (!AssetForms.validDate(insuranceExpiresOn)) put("insurance_expires_on", "date")
    }

    /** Call only when [errors] is empty. */
    fun toDraft(): VehicleDetailsDraft = VehicleDetailsDraft(
        bodyClass = bodyClass.trim().ifEmpty { null },
        fuelType = fuelType.trim().ifEmpty { null },
        gvwr = gvwr.trim().ifEmpty { null },
        registrationNumber = registrationNumber.trim().ifEmpty { null },
        registrationExpiresOn = AssetForms.parseDate(registrationExpiresOn),
        insurancePolicyNumber = insurancePolicyNumber.trim().ifEmpty { null },
        insuranceExpiresOn = AssetForms.parseDate(insuranceExpiresOn),
    )

    companion object {
        fun of(v: AssetVehicleDetails?) = VehicleForm(
            bodyClass = v?.bodyClass.orEmpty(),
            fuelType = v?.fuelType.orEmpty(),
            gvwr = v?.gvwr.orEmpty(),
            registrationNumber = v?.registrationNumber.orEmpty(),
            registrationExpiresOn = v?.registrationExpiresOn.orEmpty(),
            insurancePolicyNumber = v?.insurancePolicyNumber.orEmpty(),
            insuranceExpiresOn = v?.insuranceExpiresOn.orEmpty(),
        )
    }
}

data class Recall(
    val campaignNumber: String,
    val component: String?,
    val summary: String?,
    val consequence: String?,
    val remedy: String?,
    val reportReceivedDate: String?,
    val parkIt: Boolean,
    val parkOutside: Boolean,
    val remediedAt: String?,
    val remedyNote: String?,
) {
    val open: Boolean get() = remediedAt == null

    companion object {
        fun from(o: JsonObject): Recall? = o.str("campaign_number")?.let {
            Recall(
                campaignNumber = it,
                component = o.str("component"),
                summary = o.str("summary"),
                consequence = o.str("consequence"),
                remedy = o.str("remedy"),
                reportReceivedDate = o.str("report_received_date"),
                parkIt = o.bool("park_it") ?: false,
                parkOutside = o.bool("park_outside") ?: false,
                remediedAt = o.str("remedied_at"),
                remedyNote = o.str("remedy_note"),
            )
        }
    }
}

data class RecallList(
    /** False when the asset is not a road vehicle or lacks make/model/year. */
    val supported: Boolean,
    val stale: Boolean,
    val unavailable: Boolean,
    val fetchedAt: String?,
    val openCount: Int,
    val blocksRentals: Boolean,
    val recalls: List<Recall>,
) {
    companion object {
        fun from(root: JsonObject): RecallList {
            val o = root.data()
            val recalls = o.objs("recalls").mapNotNull { Recall.from(it) }
            return RecallList(
                supported = o.bool("supported") ?: false,
                stale = o.bool("stale") ?: false,
                unavailable = o.bool("unavailable") ?: false,
                fetchedAt = o.str("fetched_at"),
                openCount = o.int("open_count") ?: recalls.count { it.open },
                blocksRentals = o.bool("block_unremedied_recalls") ?: false,
                recalls = recalls,
            )
        }
    }
}
