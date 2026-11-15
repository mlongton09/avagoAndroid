package com.avago.feature.crew.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.map
import com.avago.core.network.json.objs
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Operated rentals for the assigned operator (J): job cards, site checklist and timesheets. */
interface OperatorService {
    suspend fun myJobs(includeReleased: Boolean = false): ApiResult<List<OperatorJob>>
    suspend fun job(accountId: String, assignmentId: String): ApiResult<OperatorJobCard>
    suspend fun checklist(accountId: String, assignmentId: String): ApiResult<SiteChecklist>
    suspend fun saveChecklist(accountId: String, assignmentId: String, c: SiteChecklist): ApiResult<Unit>
    suspend fun completeChecklist(accountId: String, assignmentId: String): ApiResult<Unit>
    suspend fun timesheets(accountId: String, assignmentId: String): ApiResult<List<Timesheet>>
    suspend fun startTimesheet(accountId: String, assignmentId: String): ApiResult<Unit>
    suspend fun stopTimesheet(accountId: String, timesheetId: String, breakMinutes: Int, notes: String?): ApiResult<Unit>
    suspend fun submitTimesheet(accountId: String, timesheetId: String): ApiResult<Unit>
    suspend fun signTimesheet(accountId: String, timesheetId: String, signerName: String): ApiResult<Unit>
}

@Singleton
class KtorOperatorService @Inject constructor(private val api: JsonApi) : OperatorService {
    private fun r(a: String, p: String) = "/accounts/$a/rentals$p"

    override suspend fun myJobs(includeReleased: Boolean): ApiResult<List<OperatorJob>> =
        api.get("/users/me/operator-assignments", mapOf("include_released" to includeReleased.takeIf { it }))
            .map { it.objs("items").mapNotNull(OperatorJob::from) }

    override suspend fun job(accountId: String, assignmentId: String) =
        api.get(r(accountId, "/operator-assignments/$assignmentId")).map(OperatorJobCard::from)

    override suspend fun checklist(accountId: String, assignmentId: String) =
        api.get(r(accountId, "/operator-assignments/$assignmentId/checklist")).map(SiteChecklist::from)

    override suspend fun saveChecklist(accountId: String, assignmentId: String, c: SiteChecklist): ApiResult<Unit> =
        api.put(r(accountId, "/operator-assignments/$assignmentId/checklist"), checklistBody(c)).map { }

    override suspend fun completeChecklist(accountId: String, assignmentId: String): ApiResult<Unit> =
        api.post(r(accountId, "/operator-assignments/$assignmentId/checklist/complete")).map { }

    override suspend fun timesheets(accountId: String, assignmentId: String) =
        api.get(r(accountId, "/operator-assignments/$assignmentId/timesheets")).map(Timesheet::list)

    override suspend fun startTimesheet(accountId: String, assignmentId: String): ApiResult<Unit> =
        api.post(r(accountId, "/operator-assignments/$assignmentId/timesheets/start")).map { }

    override suspend fun stopTimesheet(accountId: String, timesheetId: String, breakMinutes: Int, notes: String?): ApiResult<Unit> =
        api.post(r(accountId, "/timesheets/$timesheetId/stop"), jsonBody("break_minutes" to breakMinutes, "notes" to notes?.ifBlank { null })).map { }

    override suspend fun submitTimesheet(accountId: String, timesheetId: String): ApiResult<Unit> =
        api.post(r(accountId, "/timesheets/$timesheetId/submit")).map { }

    override suspend fun signTimesheet(accountId: String, timesheetId: String, signerName: String): ApiResult<Unit> =
        api.post(
            r(accountId, "/timesheets/$timesheetId/sign"),
            jsonBody("signer_name" to signerName, "typed_name" to signerName, "consent" to true, "consent_text_version" to "v1"),
        ).map { }

    companion object {
        fun checklistBody(c: SiteChecklist): JsonObject = jsonBody(
            "answers" to buildMap<String, Any?> {
                c.answers.forEach { (k, a) -> if (a.ok != null) put(k, jsonBody("ok" to a.ok, "note" to a.note.ifBlank { null })) }
                if (c.liftPlanRequired) put("lift_plan_attached", c.liftPlanAttached)
            },
            "photo_file_ids" to emptyList<String>(),
        )
    }
}

/** Public QR passport and transfer claim (B). Public routes need no account. */
interface PassportService {
    suspend fun passport(tokenId: String): ApiResult<Passport>
    suspend fun submitInspection(tokenId: String, name: String?, answers: Map<String, String>, notes: Map<String, String>): ApiResult<String?>
    suspend fun reportFault(tokenId: String, name: String?, description: String, severity: String, photoBase64: String?): ApiResult<String?>
    suspend fun transferPreview(token: String): ApiResult<TransferPreview>
    suspend fun claimTransfer(token: String): ApiResult<ClaimResult>
}

@Singleton
class KtorPassportService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : PassportService {
    override suspend fun passport(tokenId: String) = api.get("/public/passport/$tokenId").map { Passport.from(it, tokenId) }

    override suspend fun submitInspection(tokenId: String, name: String?, answers: Map<String, String>, notes: Map<String, String>): ApiResult<String?> =
        api.post(
            "/public/passport/$tokenId/inspections",
            jsonBody(
                "name" to name?.ifBlank { null },
                "answers" to answers,
                "notes" to notes.filterValues { it.isNotBlank() },
                "website" to "",
            ),
        ).map { it["result"]?.toString()?.trim('"') }

    override suspend fun reportFault(tokenId: String, name: String?, description: String, severity: String, photoBase64: String?): ApiResult<String?> =
        api.post(
            "/public/passport/$tokenId/fault-reports",
            jsonBody(
                "name" to name?.ifBlank { null },
                "description" to description,
                "severity" to severity,
                "photo" to photoBase64,
                "website" to "",
            ),
        ).map { it["id"]?.toString()?.trim('"') }

    override suspend fun transferPreview(token: String) = api.get("/public/passport-transfers/$token").map(TransferPreview::from)

    override suspend fun claimTransfer(token: String): ApiResult<ClaimResult> {
        val a = identity.getActiveAccountId() ?: return noAccount()
        return api.post("/passport-transfers/$token/claim", jsonBody("account_id" to a)).map(ClaimResult::from)
    }
}
