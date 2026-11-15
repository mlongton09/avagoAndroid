package com.avago.feature.crew.data

import com.avago.core.network.json.bool
import com.avago.core.network.json.double
import com.avago.core.network.json.int
import com.avago.core.network.json.long
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import com.avago.core.network.json.strs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Crew daily work (feature D, AvagoSvc docs/crew-lending-coownership.md §3–4).

data class Machine(
    val assignmentId: String?,
    val assetId: String,
    val name: String,
    val makeModel: String?,
    val assetType: String?,
    val inspectedTodayResult: String?,
    val inspectedTodayAt: String?,
    val openDefects: Int,
    val openClockSessionId: String?,
    val lastMeter: String?,
) {
    val inspectedToday: Boolean get() = inspectedTodayResult != null

    companion object {
        fun from(o: JsonObject): Machine? {
            val assetId = o.str("asset_id") ?: return null
            val makeModel = listOfNotNull(o.str("make"), o.str("model")).joinToString(" ").ifBlank { null }
            return Machine(
                assignmentId = o.str("assignment_id"),
                assetId = assetId,
                name = o.str("name") ?: makeModel ?: assetId,
                makeModel = makeModel,
                assetType = o.str("asset_type"),
                inspectedTodayResult = o.str("inspected_today_result"),
                inspectedTodayAt = o.str("inspected_today_at"),
                openDefects = o.int("open_defects") ?: 0,
                openClockSessionId = o.str("open_clock_session_id"),
                lastMeter = meterText(o["last_meter"]),
            )
        }

        /** `last_meter` is a number, a string or `{value, unit}` depending on the asset's meter history. */
        internal fun meterText(el: kotlinx.serialization.json.JsonElement?): String? = when (el) {
            is JsonObject -> listOfNotNull(el.str("value") ?: el.str("meter_value"), el.str("unit") ?: el.str("meter_unit"))
                .joinToString(" ").ifBlank { null }
            is JsonPrimitive -> el.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.ifBlank { null }
            else -> null
        }
    }
}

data class MachinesToday(val date: String?, val role: String?, val inspectionsPending: Int, val items: List<Machine>) {
    companion object {
        fun from(o: JsonObject) = MachinesToday(
            date = o.str("date"),
            role = o.str("role"),
            inspectionsPending = o.int("inspections_pending") ?: 0,
            items = o.objs("items").mapNotNull(Machine::from),
        )
    }
}

data class ChecklistItem(val key: String, val label: String, val section: String?)

data class Checklist(val assetName: String?, val source: String?, val items: List<ChecklistItem>, val severities: List<String>) {
    companion object {
        fun from(o: JsonObject) = Checklist(
            assetName = o.obj("asset")?.str("name"),
            source = o.str("source"),
            items = o.objs("items").mapNotNull { i ->
                val key = i.str("key") ?: return@mapNotNull null
                ChecklistItem(key, i.str("label") ?: key, i.str("section"))
            },
            severities = o.strs("severities").ifEmpty { listOf("minor", "major", "out_of_service") },
        )
    }
}

/** One answer; [pass] null = not applicable. A fail may carry a [note], [severity] and photos. */
data class InspectionAnswer(
    val key: String,
    val pass: Boolean?,
    val note: String? = null,
    val severity: String? = null,
    val photoIds: List<String> = emptyList(),
)

data class InspectionResult(val logId: String?, val result: String?, val itemsCount: Int, val failedCount: Int, val defects: Int) {
    companion object {
        fun from(o: JsonObject) = InspectionResult(
            logId = o.str("log_id"),
            result = o.str("result"),
            itemsCount = o.int("items_count") ?: 0,
            failedCount = o.int("failed_count") ?: 0,
            defects = o.objs("defects").size,
        )
    }
}

data class PhotoSlot(val photoId: String, val uploadUrl: String?) {
    companion object {
        fun from(o: JsonObject): PhotoSlot? = o.str("photo_id")?.let { PhotoSlot(it, o.str("upload_url")) }
    }
}

data class MeterInput(
    val meterValue: Double? = null,
    val meterUnit: String? = null,
    val fuelQty: Double? = null,
    val fuelUnit: String? = null,
    val chargePct: Int? = null,
    val notes: String? = null,
) {
    val hasValue: Boolean get() = meterValue != null || fuelQty != null || chargePct != null
}

data class MeterResult(val warnings: List<String>) {
    companion object {
        fun from(o: JsonObject) = MeterResult(o.strs("warnings"))
    }
}

data class FaultResult(val defectId: String?, val severity: String?) {
    companion object {
        fun from(o: JsonObject) = FaultResult(o.str("defect_id"), o.str("severity"))
    }
}

data class ClockSession(
    val id: String,
    val assetId: String?,
    val assetName: String?,
    val clockInAt: String?,
    val clockOutAt: String?,
    val hours: Double?,
) {
    val open: Boolean get() = clockOutAt == null

    companion object {
        fun from(o: JsonObject?): ClockSession? {
            val id = o?.str("id") ?: return null
            return ClockSession(id, o.str("asset_id"), o.str("asset_name"), o.str("clock_in_at"), o.str("clock_out_at"), o.double("hours"))
        }
    }
}

// ---- Operator record (§4) -------------------------------------------------------------

data class Employer(val accountId: String?, val name: String, val events: Int, val current: Boolean)

data class OperatorRecord(
    val displayName: String?,
    val inspections: Int,
    val inspectionsFailed: Int,
    val hoursLogged: Double,
    val incidents: Int,
    val meterEntries: Int,
    val firstActivityAt: String?,
    val lastActivityAt: String?,
    val employers: List<Employer>,
    val certifications: List<Certification>,
) {
    companion object {
        fun from(o: JsonObject): OperatorRecord {
            val s = o.obj("summary") ?: JsonObject(emptyMap())
            return OperatorRecord(
                displayName = o.obj("profile")?.let { it.str("display_name") ?: it.str("name") },
                inspections = s.int("inspections") ?: 0,
                inspectionsFailed = s.int("inspections_failed") ?: 0,
                hoursLogged = s.double("hours_logged") ?: 0.0,
                incidents = s.int("incidents") ?: 0,
                meterEntries = s.int("meter_entries") ?: 0,
                firstActivityAt = s.str("first_activity_at"),
                lastActivityAt = s.str("last_activity_at"),
                employers = s.objs("employers").map {
                    Employer(it.str("account_id"), it.str("name") ?: "—", it.int("events") ?: 0, it.bool("current") == true)
                },
                certifications = o.objs("certifications").map(Certification::from),
            )
        }
    }
}

data class Certification(val certType: String, val issuer: String?, val expiresOn: String?, val expiryStatus: String?) {
    companion object {
        fun from(o: JsonObject) = Certification(o.str("cert_type") ?: "—", o.str("issuer"), o.str("expires_on"), o.str("expiry_status"))
    }
}

data class RecordEvent(
    val id: String,
    val kind: String,
    val occurredAt: String?,
    val accountName: String?,
    val assetName: String?,
    val result: String?,
    val hours: Double?,
    val meterValue: String?,
    val meterUnit: String?,
) {
    companion object {
        fun from(o: JsonObject): RecordEvent? {
            val id = o.str("id") ?: return null
            return RecordEvent(
                id, o.str("kind") ?: "event", o.str("occurred_at") ?: o.str("work_date"), o.str("account_name"),
                o.str("asset_name"), o.str("result"), o.double("hours"), o.str("meter_value"), o.str("meter_unit"),
            )
        }
    }
}

data class EventPage(val items: List<RecordEvent>, val nextBefore: String?) {
    companion object {
        fun from(o: JsonObject) = EventPage(o.objs("items").mapNotNull(RecordEvent::from), o.str("next_before"))
    }
}

data class RecordShare(
    val id: String,
    val label: String?,
    val expiresAt: String?,
    val revoked: Boolean,
    val showEmployers: Boolean,
    val url: String?,
) {
    companion object {
        fun from(o: JsonObject): RecordShare? {
            val id = o.str("id") ?: o.obj("share")?.str("id") ?: return null
            val src = o.obj("share") ?: o
            return RecordShare(
                id = id,
                label = src.str("label"),
                expiresAt = src.str("expires_at"),
                revoked = src.str("revoked_at") != null,
                showEmployers = src.bool("show_employers") != false,
                url = o.str("url"),
            )
        }
    }
}

internal fun JsonObject.longOrZero(key: String): Long = long(key) ?: 0L
