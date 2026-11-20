package com.avago.feature.rentalsops.data

import com.avago.core.network.rentals.RentalsApi
import com.avago.core.network.rentals.RentalsApiException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Outcome of a Rentals P2 call; never throws except for cancellation. */
sealed class ApiResult {
    data class Ok(val body: JsonObject) : ApiResult()

    /** Non-2xx with the server's stable `code` (e.g. `not_assigned`, `invalid_transition`). */
    data class Http(val status: Int, val code: String?, val message: String, val details: JsonObject? = null) : ApiResult()

    /**
     * No usable response. [offline] = the request never reached the server (an
     * [java.io.IOException]: offline, DNS, socket, timeout), so the outbox keeps the row pending
     * without using a retry attempt; otherwise (e.g. an unreadable 2xx body) it is a counted retry.
     */
    data class Transport(val message: String, val offline: Boolean = false) : ApiResult()

    companion object {
        /** Coded error body `{error, code, message, details}` — parsed by P1's [RentalsApiException]. */
        fun fromError(e: RentalsApiException): Http =
            Http(e.status, e.code, e.message ?: "HTTP ${e.status}", e.body?.get("details") as? JsonObject)

        fun fromErrorBody(status: Int, text: String): Http = fromError(RentalsApiException.from(status, text))
    }
}

/**
 * Read-only rentals REST calls of the operations module (`/accounts/{a}/rentals`): availability
 * search, the driver's route, statements, AR aging and the statement PDF, plus the driver
 * location pings (telemetry, not a record write). Every record write is a v2 catalog command
 * (decision L35), never a call here.
 */
@Singleton
class RentalOpsApi @Inject constructor(
    private val client: HttpClient,
    private val rentals: RentalsApi,
    @Named("baseUrl") private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun r(accountId: String, path: String) = "$baseUrl/accounts/$accountId/rentals$path"

    /** `GET …/availability/search` for one class (candidates for unit assignment). */
    suspend fun availability(accountId: String, from: String, to: String, classId: String, locationId: String?): ApiResult =
        wrap { rentals.availabilitySearch(accountId, from, to, classId = classId, locationId = locationId, qty = 1) }

    /**
     * Wave 2 G5 `GET …/routing/my-route?date=` (`rental.drive`) →
     * `{date, timezone, ping_interval_seconds, tracking, stops:[stop]}` in `route_seq` order.
     */
    suspend fun myRoute(accountId: String, date: String?): ApiResult = call {
        client.get(r(accountId, "/routing/my-route")) { date?.let { parameter("date", it) } }
    }

    /**
     * Wave 2 G5 `POST …/routing/pings` `{pings:[{lat, lng, recorded_at, accuracy_m?, speed_mps?,
     * heading?, battery_pct?, task_id?}]}` (1–50) → `{accepted, skipped, next_interval_seconds}`.
     * More than 12 batches a minute is 429 `rate_limited` with `details.retry_after`.
     */
    suspend fun postPings(accountId: String, body: JsonObject): ApiResult = call {
        client.post(r(accountId, "/routing/pings")) { body(body) }
    }

    // ---- Statements and AR aging -------------------------------------------------------------

    /** Stage B `GET …/customers/{id}/statement` (orders/invoices/entries, `rental.view`). */
    suspend fun statement(accountId: String, customerId: String, from: String?, to: String?): ApiResult = call {
        client.get(r(accountId, "/customers/$customerId/statement")) {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }
    }

    /** Stage B `GET …/reports/ar-aging` (`rental.reports`). */
    suspend fun arAging(accountId: String, asOf: String? = null): ApiResult = call {
        client.get(r(accountId, "/reports/ar-aging")) { asOf?.let { parameter("as_of", it) } }
    }

    /**
     * `GET …/billing/customers/{id}/statement/pdf` (rendered on the fly, `rental.reports`),
     * written to [dest]. Ok carries an empty body.
     */
    suspend fun statementPdf(accountId: String, customerId: String, dest: File): ApiResult = try {
        val bytes = client.get(r(accountId, "/billing/customers/$customerId/statement/pdf")) {
            parameter("format", "pdf")
            header(HttpHeaders.Accept, ContentType.Application.Pdf.toString())
        }.bodyAsBytes()
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        ApiResult.Ok(JsonObject(emptyMap()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        ApiResult.fromErrorBody(e.response.status.value, runCatching { e.response.bodyAsText() }.getOrDefault(""))
    } catch (e: Exception) {
        ApiResult.Transport(e.message ?: e.javaClass.simpleName, offline = e is java.io.IOException)
    }

    /**
     * `POST …/billing/customers/{id}/statement/email` `{client_uuid, recipients?, message?}`
     * (`rental.reports`) → 201 `{document_id, email, replayed}`; 400 `invalid_value` when neither
     * the request nor the customer has an address. Replaying [clientUuid] never mails twice.
     */
    suspend fun statementEmail(accountId: String, customerId: String, clientUuid: String, recipients: List<String>, message: String?): ApiResult = call {
        client.post(r(accountId, "/billing/customers/$customerId/statement/email")) { body(statementEmailBody(clientUuid, recipients, message)) }
    }

    // ---- plumbing ---------------------------------------------------------------------

    private fun HttpRequestBuilder.body(o: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(o)
    }

    companion object {
        fun statementEmailBody(clientUuid: String, recipients: List<String>, message: String?): JsonObject = JsonObject(
            buildMap {
                put("client_uuid", JsonPrimitive(clientUuid))
                if (recipients.isNotEmpty()) put("recipients", JsonArray(recipients.map(::JsonPrimitive)))
                message?.let { put("message", JsonPrimitive(it)) }
            },
        )
    }

    private suspend fun wrap(block: suspend () -> JsonObject): ApiResult = try {
        ApiResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: RentalsApiException) {
        ApiResult.fromError(e)
    } catch (e: Exception) {
        ApiResult.Transport(e.message ?: e.javaClass.simpleName, offline = e is java.io.IOException)
    }

    private suspend fun call(block: suspend () -> HttpResponse): ApiResult = try {
        val text = block().bodyAsText()
        ApiResult.Ok(if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        ApiResult.fromErrorBody(e.response.status.value, runCatching { e.response.bodyAsText() }.getOrDefault(""))
    } catch (e: Exception) {
        ApiResult.Transport(e.message ?: e.javaClass.simpleName, offline = e is java.io.IOException)
    }
}
