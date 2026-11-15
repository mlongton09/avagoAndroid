package com.avago.feature.crew.data

import com.avago.core.network.json.bool
import com.avago.core.network.json.double
import com.avago.core.network.json.int
import com.avago.core.network.json.obj
import com.avago.core.network.json.objs
import com.avago.core.network.json.str
import com.avago.core.network.json.strs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Operated rentals, operator side (feature J, AvagoSvc docs/rentals-api-operated.md §4.4).

data class OperatorJob(
    val id: String,
    val accountId: String,
    val accountName: String?,
    val orderNumber: String?,
    val orderStatus: String?,
    val locationName: String?,
    val startAt: String?,
    val endAt: String?,
    val active: Boolean,
    val checklistCompletedAt: String?,
    val openTimesheets: Int,
) {
    companion object {
        fun from(o: JsonObject): OperatorJob? {
            val id = o.str("id") ?: return null
            val acc = o.str("account_id") ?: return null
            return OperatorJob(
                id, acc, o.str("account_name"), o.str("order_number"), o.str("order_status"), o.str("location_name"),
                o.str("start_at"), o.str("end_at"), o.bool("active") != false, o.str("checklist_completed_at"), o.int("open_timesheets") ?: 0,
            )
        }
    }
}

data class OperatorJobCard(
    val orderNumber: String?,
    val orderStatus: String?,
    val customerName: String?,
    val locationName: String?,
    val startAt: String?,
    val endAt: String?,
    val liftPlanRequired: Boolean,
    val requiredCertTypes: List<String>,
    val checklistCompletedAt: String?,
    val isOperator: Boolean,
) {
    companion object {
        fun from(o: JsonObject): OperatorJobCard {
            val order = o.obj("order") ?: JsonObject(emptyMap())
            val a = o.obj("assignment") ?: JsonObject(emptyMap())
            return OperatorJobCard(
                orderNumber = order.str("number"),
                orderStatus = order.str("status"),
                customerName = order.str("customer_name"),
                locationName = order.str("location_name"),
                startAt = a.str("start_at"),
                endAt = a.str("end_at"),
                liftPlanRequired = o.bool("lift_plan_required") == true,
                requiredCertTypes = o.strs("required_cert_types"),
                checklistCompletedAt = o.str("checklist_completed_at") ?: a.str("checklist_completed_at"),
                isOperator = o.bool("is_operator") != false,
            )
        }
    }
}

data class SiteAnswer(val ok: Boolean?, val note: String = "")

/** Pre-start site checklist: [answers] keyed by item; `lift_plan_attached` when the profile needs a lift plan. */
data class SiteChecklist(
    val keys: List<String>,
    val liftPlanRequired: Boolean,
    val answers: Map<String, SiteAnswer>,
    val liftPlanAttached: Boolean,
    val completedAt: String?,
    val problems: List<String>,
) {
    /** Client-side mirror of the server's problem codes, so the user sees what is missing before submitting. */
    fun localProblems(): List<String> = buildList {
        for (k in keys) {
            val a = answers[k]
            when {
                a?.ok == null -> add("${k}_unanswered")
                a.ok == false && a.note.isBlank() -> add("${k}_failed_without_note")
            }
        }
        if (liftPlanRequired && !liftPlanAttached) add("lift_plan_missing")
    }

    companion object {
        val DEFAULT_KEYS = listOf("site_access", "ground_conditions", "overhead_hazards", "ppe")

        fun from(o: JsonObject): SiteChecklist {
            val row = o.obj("checklist")
            val ans = row?.obj("answers") ?: JsonObject(emptyMap())
            val keys = o.strs("items").ifEmpty { DEFAULT_KEYS }
            return SiteChecklist(
                keys = keys,
                liftPlanRequired = o.bool("lift_plan_required") == true,
                answers = keys.associateWith { k -> answerOf(ans[k]) },
                liftPlanAttached = ans.bool("lift_plan_attached") == true || ans.str("lift_plan_file_id") != null,
                completedAt = row?.str("completed_at"),
                problems = o.strs("problems"),
            )
        }

        /** `{ok, note?}`, a bare bool, or `"ok"`/`"yes"`/`"pass"` (as the server accepts). */
        internal fun answerOf(el: kotlinx.serialization.json.JsonElement?): SiteAnswer = when (el) {
            is JsonObject -> SiteAnswer(el.bool("ok"), el.str("note").orEmpty())
            is JsonPrimitive -> when (el.content.lowercase()) {
                "true", "ok", "yes", "pass" -> SiteAnswer(true)
                "false", "no", "fail" -> SiteAnswer(false)
                else -> SiteAnswer(null)
            }
            else -> SiteAnswer(null)
        }
    }
}

data class Timesheet(
    val id: String,
    val workDate: String?,
    val startedAt: String?,
    val endedAt: String?,
    val breakMinutes: Int,
    val notes: String?,
    val status: String,
    val signerName: String?,
    val rejectReason: String?,
    val workedHours: Double?,
) {
    val canStop: Boolean get() = status == "open" && startedAt != null && endedAt == null
    val canSubmit: Boolean get() = status == "open" && endedAt != null
    val canSign: Boolean get() = status == "submitted"

    companion object {
        fun from(o: JsonObject): Timesheet? {
            val id = o.str("id") ?: return null
            return Timesheet(
                id, o.str("work_date"), o.str("started_at"), o.str("ended_at"), o.int("break_minutes") ?: 0, o.str("notes"),
                o.str("status") ?: "open", o.str("signer_name"), o.str("reject_reason"), o.double("worked_hours"),
            )
        }

        fun list(o: JsonObject) = o.objs("items").mapNotNull(::from)
    }
}
