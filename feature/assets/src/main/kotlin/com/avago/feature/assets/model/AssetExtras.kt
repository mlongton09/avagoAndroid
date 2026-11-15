package com.avago.feature.assets.model

import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.network.json.bool
import com.avago.core.network.json.int
import com.avago.core.network.json.long
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

// Feature I (scope) per-asset extras: tool details, vehicle details and NHTSA recalls
// (AvagoSvc `routes/scope.rs`).

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
    }
}

private fun JsonObject.data(): JsonObject = obj("data") ?: this

data class ToolDetails(
    val calibrationDocId: String?,
    val calibratedOn: String?,
    val calibrationIntervalDays: Int?,
    val calibrationDueOn: String?,
    /** days | cycles */
    val usageUnit: String,
    val usageValue: Long,
    val replacementValueMinor: Long?,
    val currency: String?,
    val trackerTagId: String?,
    /** gps | bluetooth | uwb | other */
    val trackerKind: String?,
    val isTool: Boolean,
    val calibrationStatus: ExpiryStatus?,
    val verifiedRenterRequired: Boolean,
) {
    companion object {
        fun from(root: JsonObject): ToolDetails {
            val o = root.data()
            return ToolDetails(
                calibrationDocId = o.str("calibration_doc_id"),
                calibratedOn = o.str("calibrated_on"),
                calibrationIntervalDays = o.int("calibration_interval_days"),
                calibrationDueOn = o.str("calibration_due_on"),
                usageUnit = o.str("usage_unit") ?: "days",
                usageValue = o.long("usage_value") ?: 0,
                replacementValueMinor = o.long("replacement_value_minor"),
                currency = o.str("currency"),
                trackerTagId = o.str("tracker_tag_id"),
                trackerKind = o.str("tracker_kind"),
                isTool = o.bool("is_tool") ?: true,
                calibrationStatus = ExpiryStatus.from(o.str("calibration_status")),
                verifiedRenterRequired = o.bool("verified_renter_required") ?: false,
            )
        }
    }
}

/** Editable tool-details text fields; [toBody] validates and builds the PUT body. */
data class ToolForm(
    val calibratedOn: String = "",
    val intervalDays: String = "",
    val dueOn: String = "",
    val usageUnit: String = "days",
    val usageValue: String = "",
    val replacementValue: String = "",
    val currency: String = "",
    val trackerTagId: String = "",
    val trackerKind: String = "",
) {
    /** Field key → error code; empty when the form is valid. */
    fun errors(): Map<String, String> = buildMap {
        if (!AssetForms.validDate(calibratedOn)) put("calibrated_on", "date")
        if (!AssetForms.validDate(dueOn)) put("calibration_due_on", "date")
        if (intervalDays.isNotBlank() && intervalDays.trim().toIntOrNull()?.takeIf { it in 1..3650 } == null) {
            put("calibration_interval_days", "range")
        }
        if (usageValue.isNotBlank() && usageValue.trim().toLongOrNull()?.takeIf { it >= 0 } == null) put("usage_value", "number")
        if (replacementValue.isNotBlank() && MoneyFormatter.parsePlainMinor(replacementValue, currency) == null) {
            put("replacement_value_minor", "number")
        }
        if (currency.isNotBlank() && !currency.trim().matches(Regex("[A-Za-z]{3}"))) put("currency", "currency")
        if (trackerTagId.trim().length > 128) put("tracker_tag_id", "length")
    }

    fun toBody(): JsonObject {
        fun orNull(s: String): Any = s.trim().ifEmpty { null } ?: JsonNull
        return com.avago.core.network.json.jsonBody(
            "calibrated_on" to orNull(calibratedOn),
            "calibration_interval_days" to (intervalDays.trim().toIntOrNull() ?: JsonNull),
            // Blank due date lets the server derive it from calibrated_on + interval.
            "calibration_due_on" to dueOn.trim().ifEmpty { null },
            "usage_unit" to usageUnit,
            "usage_value" to (usageValue.trim().toLongOrNull() ?: 0L),
            "replacement_value_minor" to (MoneyFormatter.parsePlainMinor(replacementValue, currency) ?: JsonNull),
            "currency" to (currency.trim().uppercase().ifEmpty { null } ?: JsonNull),
            "tracker_tag_id" to orNull(trackerTagId),
            "tracker_kind" to orNull(trackerKind),
        )
    }

    companion object {
        fun of(t: ToolDetails) = ToolForm(
            calibratedOn = t.calibratedOn.orEmpty(),
            intervalDays = t.calibrationIntervalDays?.toString().orEmpty(),
            dueOn = t.calibrationDueOn.orEmpty(),
            usageUnit = t.usageUnit,
            usageValue = t.usageValue.toString(),
            replacementValue = t.replacementValueMinor?.let { MoneyFormatter.toPlain(it, t.currency.orEmpty()) }.orEmpty(),
            currency = t.currency.orEmpty(),
            trackerTagId = t.trackerTagId.orEmpty(),
            trackerKind = t.trackerKind.orEmpty(),
        )

        val TRACKER_KINDS = listOf("gps", "bluetooth", "uwb", "other")
    }
}

data class VehicleDetails(
    val bodyClass: String?,
    val fuelType: String?,
    val gvwr: String?,
    val registrationNumber: String?,
    val registrationExpiresOn: String?,
    val registrationDocId: String?,
    val insurancePolicyNumber: String?,
    val insuranceExpiresOn: String?,
    val insuranceDocId: String?,
    val registrationStatus: ExpiryStatus?,
    val insuranceStatus: ExpiryStatus?,
    val vinSupported: Boolean,
    /** Odometer reading when the server's vehicle details carry one; null otherwise. */
    val odometer: Long?,
    /** True when the response has an `odometer` key, so mileage can be saved here. */
    val odometerSupported: Boolean,
) {
    companion object {
        fun from(root: JsonObject): VehicleDetails {
            val o = root.data()
            return VehicleDetails(
                bodyClass = o.str("body_class"),
                fuelType = o.str("fuel_type"),
                gvwr = o.str("gvwr"),
                registrationNumber = o.str("registration_number"),
                registrationExpiresOn = o.str("registration_expires_on"),
                registrationDocId = o.str("registration_doc_id"),
                insurancePolicyNumber = o.str("insurance_policy_number"),
                insuranceExpiresOn = o.str("insurance_expires_on"),
                insuranceDocId = o.str("insurance_doc_id"),
                registrationStatus = ExpiryStatus.from(o.str("registration_status")),
                insuranceStatus = ExpiryStatus.from(o.str("insurance_status")),
                vinSupported = o.bool("vin_supported") ?: false,
                odometer = o.long("odometer"),
                odometerSupported = o.containsKey("odometer"),
            )
        }
    }
}

data class VehicleForm(
    val bodyClass: String = "",
    val fuelType: String = "",
    val gvwr: String = "",
    val registrationNumber: String = "",
    val registrationExpiresOn: String = "",
    val insurancePolicyNumber: String = "",
    val insuranceExpiresOn: String = "",
    val odometer: String = "",
) {
    fun errors(): Map<String, String> = buildMap {
        if (!AssetForms.validDate(registrationExpiresOn)) put("registration_expires_on", "date")
        if (!AssetForms.validDate(insuranceExpiresOn)) put("insurance_expires_on", "date")
        if (odometer.isNotBlank() && odometer.trim().toLongOrNull()?.takeIf { it >= 0 } == null) put("odometer", "number")
    }

    /** PUT body; `odometer` is sent only when the server supports it. */
    fun toBody(sendOdometer: Boolean): JsonObject {
        fun orNull(s: String): Any = s.trim().ifEmpty { null } ?: JsonNull
        return com.avago.core.network.json.jsonBody(
            "body_class" to orNull(bodyClass),
            "fuel_type" to orNull(fuelType),
            "gvwr" to orNull(gvwr),
            "registration_number" to orNull(registrationNumber),
            "registration_expires_on" to orNull(registrationExpiresOn),
            "insurance_policy_number" to orNull(insurancePolicyNumber),
            "insurance_expires_on" to orNull(insuranceExpiresOn),
            "odometer" to if (sendOdometer) odometer.trim().toLongOrNull() else null,
        )
    }

    companion object {
        fun of(v: VehicleDetails) = VehicleForm(
            bodyClass = v.bodyClass.orEmpty(),
            fuelType = v.fuelType.orEmpty(),
            gvwr = v.gvwr.orEmpty(),
            registrationNumber = v.registrationNumber.orEmpty(),
            registrationExpiresOn = v.registrationExpiresOn.orEmpty(),
            insurancePolicyNumber = v.insurancePolicyNumber.orEmpty(),
            insuranceExpiresOn = v.insuranceExpiresOn.orEmpty(),
            odometer = v.odometer?.toString().orEmpty(),
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

/** Small form helpers shared by the scope screens. */
object AssetForms {
    /** Blank or an ISO `yyyy-MM-dd` date. */
    fun validDate(s: String): Boolean = s.isBlank() || runCatching { LocalDate.parse(s.trim()) }.isSuccess
}
