package com.avago.feature.crew.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.jsonBody
import com.avago.core.network.json.map
import com.avago.core.network.json.objs
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Crew daily work, operator record, loans and co-ownership (feature D). */
interface CrewService {
    suspend fun machinesToday(date: LocalDate? = null): ApiResult<MachinesToday>
    suspend fun openClockSession(): ApiResult<ClockSession?>
    suspend fun checklist(assetId: String): ApiResult<Checklist>
    suspend fun uploadPhoto(assetId: String, jpeg: ByteArray): ApiResult<String>
    suspend fun submitInspection(assetId: String, answers: List<InspectionAnswer>, notes: String?, meterValue: Double?): ApiResult<InspectionResult>
    suspend fun recordMeter(assetId: String, input: MeterInput): ApiResult<MeterResult>
    suspend fun reportFault(assetId: String, title: String, note: String?, severity: String, photoIds: List<String>): ApiResult<FaultResult>
    suspend fun clockIn(assetId: String, notes: String? = null): ApiResult<ClockSession?>
    suspend fun clockOut(sessionId: String, notes: String? = null): ApiResult<ClockSession?>

    suspend fun operatorRecord(): ApiResult<OperatorRecord>
    suspend fun recordEvents(before: String? = null): ApiResult<EventPage>
    suspend fun shares(): ApiResult<List<RecordShare>>
    suspend fun createShare(label: String?, expiresInDays: Int, showEmployers: Boolean): ApiResult<RecordShare>
    suspend fun revokeShare(id: String): ApiResult<Unit>

    suspend fun myLoans(): ApiResult<MyLoans>
    suspend fun loan(id: String): ApiResult<Loan>
    suspend fun acceptLoan(loan: Loan, signerName: String): ApiResult<Loan?>
    suspend fun loanAction(id: String, action: String): ApiResult<Loan?>
    suspend fun uploadLoanPhoto(id: String, jpeg: ByteArray): ApiResult<String>
    suspend fun handover(id: String, input: HandoverInput, isReturn: Boolean): ApiResult<Loan?>

    suspend fun coOwnerships(): ApiResult<List<CoOwnership>>
    suspend fun coGroup(groupId: String): ApiResult<CoGroup>
    suspend fun coBookings(groupId: String, from: String, to: String): ApiResult<CoBookings>
    suspend fun createCoBooking(groupId: String, startsAt: String, endsAt: String, notes: String?): ApiResult<Unit>
    suspend fun cancelCoBooking(groupId: String, bookingId: String): ApiResult<Unit>
    suspend fun coBalances(groupId: String): ApiResult<CoBalances>
}

internal fun noAccount(): ApiResult.Err = ApiResult.Err(ApiError(-1, "no_account", "No active account"))

@Singleton
class KtorCrewService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : CrewService {
    private fun acc(): String? = identity.getActiveAccountId()
    private fun crew(a: String, p: String) = "/accounts/$a/crew$p"

    override suspend fun machinesToday(date: LocalDate?): ApiResult<MachinesToday> {
        val a = acc() ?: return noAccount()
        return api.get(crew(a, "/me/machines"), mapOf("date" to date?.toString())).map(MachinesToday::from)
    }

    override suspend fun openClockSession(): ApiResult<ClockSession?> =
        api.get("/users/me/crew/clock").map { ClockSession.from(it["open_clock_session"] as? JsonObject) }

    override suspend fun checklist(assetId: String): ApiResult<Checklist> {
        val a = acc() ?: return noAccount()
        return api.get(crew(a, "/assets/$assetId/checklist")).map(Checklist::from)
    }

    override suspend fun uploadPhoto(assetId: String, jpeg: ByteArray): ApiResult<String> {
        val a = acc() ?: return noAccount()
        return reserveAndPut(api.post(crew(a, "/photos"), jsonBody("asset_id" to assetId, "content_type" to JPEG)), jpeg)
    }

    private suspend fun reserveAndPut(reserve: ApiResult<JsonObject>, jpeg: ByteArray): ApiResult<String> {
        val slot = when (reserve) {
            is ApiResult.Err -> return reserve
            is ApiResult.Ok -> PhotoSlot.from(reserve.value) ?: return ApiResult.Err(ApiError(-1, "parse_error", "no photo_id"))
        }
        val url = slot.uploadUrl ?: return ApiResult.Ok(slot.photoId)
        return when (val put = api.upload(url, jpeg, JPEG)) {
            is ApiResult.Ok -> ApiResult.Ok(slot.photoId)
            is ApiResult.Err -> put
        }
    }

    override suspend fun submitInspection(assetId: String, answers: List<InspectionAnswer>, notes: String?, meterValue: Double?): ApiResult<InspectionResult> {
        val a = acc() ?: return noAccount()
        return api.post(crew(a, "/assets/$assetId/inspections"), inspectionBody(answers, notes, meterValue)).map(InspectionResult::from)
    }

    override suspend fun recordMeter(assetId: String, input: MeterInput): ApiResult<MeterResult> {
        val a = acc() ?: return noAccount()
        val body = jsonBody(
            "meter_value" to input.meterValue,
            "meter_unit" to input.meterUnit.takeIf { input.meterValue != null },
            "fuel_qty" to input.fuelQty,
            "fuel_unit" to input.fuelUnit.takeIf { input.fuelQty != null },
            "charge_pct" to input.chargePct,
            "notes" to input.notes?.ifBlank { null },
        )
        return api.post(crew(a, "/assets/$assetId/meter"), body).map(MeterResult::from)
    }

    override suspend fun reportFault(assetId: String, title: String, note: String?, severity: String, photoIds: List<String>): ApiResult<FaultResult> {
        val a = acc() ?: return noAccount()
        val body = jsonBody("title" to title, "note" to note?.ifBlank { null }, "severity" to severity, "photo_ids" to photoIds.ifEmpty { null })
        return api.post(crew(a, "/assets/$assetId/faults"), body).map(FaultResult::from)
    }

    override suspend fun clockIn(assetId: String, notes: String?): ApiResult<ClockSession?> {
        val a = acc() ?: return noAccount()
        return api.post(crew(a, "/assets/$assetId/clock-in"), jsonBody("notes" to notes)).map { ClockSession.from(it) }
    }

    override suspend fun clockOut(sessionId: String, notes: String?): ApiResult<ClockSession?> {
        val a = acc() ?: return noAccount()
        return api.post(crew(a, "/clock/$sessionId/clock-out"), jsonBody("notes" to notes)).map { ClockSession.from(it) }
    }

    override suspend fun operatorRecord(): ApiResult<OperatorRecord> = api.get("/users/me/operator-record").map(OperatorRecord::from)

    override suspend fun recordEvents(before: String?): ApiResult<EventPage> =
        api.get("/users/me/operator-record/events", mapOf("limit" to 50, "before" to before)).map(EventPage::from)

    override suspend fun shares(): ApiResult<List<RecordShare>> =
        api.get("/users/me/operator-record/shares").map { o -> o.objs("items").ifEmpty { o.objs("shares") }.mapNotNull(RecordShare::from) }

    override suspend fun createShare(label: String?, expiresInDays: Int, showEmployers: Boolean): ApiResult<RecordShare> =
        api.post(
            "/users/me/operator-record/shares",
            jsonBody("label" to label?.ifBlank { null }, "expires_in_days" to expiresInDays, "show_employers" to showEmployers),
        ).map { RecordShare.from(it) ?: error("no share id") }

    override suspend fun revokeShare(id: String): ApiResult<Unit> = api.delete("/users/me/operator-record/shares/$id").map { }

    override suspend fun myLoans(): ApiResult<MyLoans> = api.get("/users/me/loans").map(MyLoans::from)

    override suspend fun loan(id: String): ApiResult<Loan> = api.get("/loans/$id").map { Loan.from(it.loanBody()) ?: error("no loan") }

    override suspend fun acceptLoan(loan: Loan, signerName: String): ApiResult<Loan?> =
        api.post(
            "/loans/${loan.id}/accept",
            jsonBody(
                "method" to "click",
                "consent" to true,
                "agreement_sha256" to loan.agreementSha256,
                "signer_name" to signerName,
                "typed_name" to signerName,
                "consent_text_version" to loan.consentVersion,
            ),
        ).map { Loan.from(it.loanBody()) }

    override suspend fun loanAction(id: String, action: String): ApiResult<Loan?> =
        api.post("/loans/$id/$action").map { Loan.from(it.loanBody()) }

    override suspend fun uploadLoanPhoto(id: String, jpeg: ByteArray): ApiResult<String> =
        reserveAndPut(api.post("/loans/$id/photos", jsonBody("content_type" to JPEG)), jpeg)

    override suspend fun handover(id: String, input: HandoverInput, isReturn: Boolean): ApiResult<Loan?> =
        api.post("/loans/$id/${if (isReturn) "return" else "handover-out"}", handoverBody(input, isReturn)).map { Loan.from(it.loanBody()) }

    override suspend fun coOwnerships(): ApiResult<List<CoOwnership>> =
        api.get("/users/me/coownerships").map { it.objs("items").mapNotNull(CoOwnership::from) }

    override suspend fun coGroup(groupId: String): ApiResult<CoGroup> = api.get("/coownerships/$groupId").map(CoGroup::from)

    override suspend fun coBookings(groupId: String, from: String, to: String): ApiResult<CoBookings> =
        api.get("/coownerships/$groupId/bookings", mapOf("from" to from, "to" to to)).map(CoBookings::from)

    override suspend fun createCoBooking(groupId: String, startsAt: String, endsAt: String, notes: String?): ApiResult<Unit> =
        api.post("/coownerships/$groupId/bookings", jsonBody("starts_at" to startsAt, "ends_at" to endsAt, "notes" to notes?.ifBlank { null })).map { }

    override suspend fun cancelCoBooking(groupId: String, bookingId: String): ApiResult<Unit> =
        api.delete("/coownerships/$groupId/bookings/$bookingId").map { }

    override suspend fun coBalances(groupId: String): ApiResult<CoBalances> = api.get("/coownerships/$groupId/balances").map(CoBalances::from)

    companion object {
        fun inspectionBody(answers: List<InspectionAnswer>, notes: String?, meterValue: Double?): JsonObject = jsonBody(
            "answers" to answers.map { a ->
                jsonBody(
                    "key" to a.key,
                    "pass" to (a.pass ?: JsonNull),
                    "note" to a.note?.ifBlank { null },
                    "severity" to a.severity.takeIf { a.pass == false },
                    "photo_ids" to a.photoIds.ifEmpty { null },
                )
            },
            "notes" to notes?.ifBlank { null },
            "meter_value" to meterValue,
        )

        fun handoverBody(input: HandoverInput, isReturn: Boolean): JsonObject = jsonBody(
            "checklist" to input.checklist.map { jsonBody("key" to it.key, "label" to it.label, "ok" to it.ok, "note" to it.note?.ifBlank { null }) },
            "photo_ids" to input.photoIds,
            "meter_value" to input.meterValue,
            "fuel_level" to input.fuelLevel?.ifBlank { null },
            "notes" to input.notes?.ifBlank { null },
            "damage" to if (isReturn) input.damage.map { jsonBody("label" to it.label, "note" to it.note?.ifBlank { null }, "severity" to it.severity) } else null,
        )

        /** Loan actions return the loan bare or as `{loan: …}`. */
        private fun JsonObject.loanBody(): JsonObject = (this["loan"] as? JsonObject) ?: this
    }
}

/** Photos are JPEG-encoded before upload (ui/Common.kt); registered and PUT with this type. */
private const val JPEG = "image/jpeg"
