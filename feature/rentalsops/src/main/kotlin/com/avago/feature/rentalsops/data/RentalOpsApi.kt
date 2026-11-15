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
import io.ktor.client.request.put
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
 * Client for the rentals P2 ("Grow", stage C) endpoints under `/accounts/{a}/rentals`, per
 * AvagoSvc `docs/rentals-api-stage-c.md` (origin/master 6ab0092). Bare JSON bodies; collections
 * are `{items:[…]}`; errors `{error, code, message, details}`.
 *
 * Calls shared with P1 (sync feeds, availability search) delegate to [RentalsApi] in
 * core/network; this class only adds the dispatch / transfer / class-booking / billing /
 * notification-prefs endpoints and the [ApiResult] wrapping the offline outbox needs.
 */
@Singleton
class RentalOpsApi @Inject constructor(
    private val client: HttpClient,
    private val rentals: RentalsApi,
    @Named("baseUrl") private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun r(accountId: String, path: String) = "$baseUrl/accounts/$accountId/rentals$path"

    // ---- Shared with P1 (core/network RentalsApi) ----------------------------------------

    /** `GET …/sync/{entity}?cursor&limit` (`rental_delivery_task`, `rental_transfer`). */
    suspend fun syncFeed(accountId: String, entity: String, cursor: String?, limit: Int = 500): ApiResult =
        wrap { rentals.syncFeed(accountId, entity, cursor, updatedSince = null, limit = limit) }

    suspend fun availability(accountId: String, from: String, to: String, classId: String, locationId: String?): ApiResult =
        wrap { rentals.availabilitySearch(accountId, from, to, classId = classId, locationId = locationId, qty = 1) }

    // ---- §4 Delivery and dispatch ----------------------------------------------------------

    /**
     * `GET …/dispatch/my-tasks?cursor` — the `rental_delivery_task` feed wrapper scoped to the
     * caller (`rental.drive`); tasks reassigned away arrive as `{id, _deleted: true}`. A cursor
     * is always sent (`0` = from the start): without one the endpoint answers a plain list.
     */
    suspend fun myTasksFeed(accountId: String, cursor: String?, limit: Int = 500): ApiResult = call {
        client.get(r(accountId, "/dispatch/my-tasks")) {
            parameter("cursor", cursor ?: "0")
            parameter("limit", limit)
        }
    }

    /**
     * `POST …/dispatch/tasks/{id}/{start|arrive|complete|fail}` with
     * `{at, lat, lng, reason, photo_file_ids, signature_file_id, signed_name, notes}`.
     * Replaying an action whose target state is already reached returns 200 unchanged; a
     * driver acting on a reassigned task gets `403 not_assigned`.
     */
    suspend fun taskAction(accountId: String, taskId: String, action: String, body: JsonObject, idempotencyKey: String): ApiResult = call {
        client.post(r(accountId, "/dispatch/tasks/$taskId/$action")) {
            idem(idempotencyKey)
            body(body)
        }
    }

    /**
     * Wave 2 G5 `POST …/dispatch/tasks/{id}/check-in` `{client_uuid, status, at, lat, lng,
     * accuracy_m, reason, photo_file_ids, signature_file_id, signed_name, notes}` → task JSON.
     * Replaying a `client_uuid` returns the task without a second transition; the same uuid on
     * another task is 409 `client_uuid_reused`.
     */
    suspend fun checkIn(accountId: String, taskId: String, body: JsonObject): ApiResult = call {
        client.post(r(accountId, "/dispatch/tasks/$taskId/check-in")) { body(body) }
    }

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

    /**
     * `POST …/dispatch/tasks/{id}/proof-uploads` `{client_uuid, kind, filename, content_type}` →
     * `{file_upload_id, client_uuid, kind, task_id, upload_status, upload_url, storage_key,
     * content_type, replayed}`. Idempotent on [clientUuid]; `upload_url` is null once uploaded.
     */
    suspend fun proofUpload(
        accountId: String,
        taskId: String,
        clientUuid: String,
        kind: String,
        filename: String,
        contentType: String,
    ): ApiResult = call {
        client.post(r(accountId, "/dispatch/tasks/$taskId/proof-uploads")) {
            body(proofUploadBody(clientUuid, kind, filename, contentType))
        }
    }

    // ---- §3 Transfers ------------------------------------------------------------------------

    /** `POST …/transfers` `{asset_id, to_location_id, arrive_by, ship_at?, reason?, client_uuid?}` (201; replay 200). */
    suspend fun createTransfer(accountId: String, body: JsonObject, idempotencyKey: String): ApiResult = call {
        client.post(r(accountId, "/transfers")) {
            idem(idempotencyKey)
            body(body)
        }
    }

    /** `POST …/transfers/{id}/{ship|receive|cancel}` `{version?, at?, reason?}` (repeat → 409 invalid_transition). */
    suspend fun transferAction(accountId: String, transferId: String, action: String, body: JsonObject, idempotencyKey: String): ApiResult = call {
        client.post(r(accountId, "/transfers/$transferId/$action")) {
            idem(idempotencyKey)
            body(body)
        }
    }

    // ---- §2 Class booking ----------------------------------------------------------------------

    /** `POST …/orders/{o}/lines/{l}/assign-unit` `{asset_id, version?}` → the order (409 `allocation_conflict` / `version_conflict`). */
    suspend fun assignUnit(accountId: String, orderId: String, lineId: String, assetId: String, version: Long?, idempotencyKey: String): ApiResult = call {
        client.post(r(accountId, "/orders/$orderId/lines/$lineId/assign-unit")) {
            idem(idempotencyKey)
            body(assignUnitBody(assetId, version))
        }
    }

    /** `GET …/unassigned-lines?location_id&limit` → `{items:[{line_id, order_id, order_version, class_free_units, …}]}` (`rental.view`). */
    suspend fun unassignedLines(accountId: String, locationId: String? = null, limit: Int = 500): ApiResult = call {
        client.get(r(accountId, "/unassigned-lines")) {
            locationId?.let { parameter("location_id", it) }
            parameter("limit", limit)
        }
    }

    // ---- §6 Statements and AR aging -------------------------------------------------------------

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
     * `POST …/billing/customers/{id}/statement/email` `{client_uuid, recipients?, message?}` →
     * 201 `{document_id, number, email, replayed}`; 400 `invalid_value` when no recipient.
     */
    suspend fun statementEmail(accountId: String, customerId: String, clientUuid: String, recipients: List<String>, message: String?): ApiResult = call {
        client.post(r(accountId, "/billing/customers/$customerId/statement/email")) {
            body(
                JsonObject(
                    buildMap {
                        put("client_uuid", JsonPrimitive(clientUuid))
                        if (recipients.isNotEmpty()) put("recipients", JsonArray(recipients.map(::JsonPrimitive)))
                        message?.let { put("message", JsonPrimitive(it)) }
                    },
                ),
            )
        }
    }

    // ---- §7 Notification prefs -------------------------------------------------------------------

    /** `GET …/notification-prefs` → `{items:[{event, description, permission, push_enabled, email_enabled}]}`. */
    suspend fun notificationPrefs(accountId: String): ApiResult = call { client.get(r(accountId, "/notification-prefs")) }

    /** `PUT …/notification-prefs` `{items:[{event, push_enabled}]}` → same shape as GET. */
    suspend fun putNotificationPref(accountId: String, event: String, pushEnabled: Boolean): ApiResult = call {
        client.put(r(accountId, "/notification-prefs")) { body(notificationPrefBody(event, pushEnabled)) }
    }

    // ---- plumbing ---------------------------------------------------------------------

    private fun HttpRequestBuilder.idem(key: String) = header("Idempotency-Key", key)

    private fun HttpRequestBuilder.body(o: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(o)
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

    companion object {
        /** Stage C §2: `{asset_id, version?}`; `version` is the line's `order_version`. */
        fun assignUnitBody(assetId: String, version: Long?): JsonObject = JsonObject(
            buildMap {
                put("asset_id", JsonPrimitive(assetId))
                version?.let { put("version", JsonPrimitive(it)) }
            },
        )

        /** Stage C §7 `PUT …/notification-prefs` `{items:[{event, push_enabled}]}` (email is left as is). */
        fun notificationPrefBody(event: String, pushEnabled: Boolean): JsonObject = JsonObject(
            mapOf(
                "items" to JsonArray(
                    listOf(JsonObject(mapOf("event" to JsonPrimitive(event), "push_enabled" to JsonPrimitive(pushEnabled)))),
                ),
            ),
        )

        /** Stage C §4 proof reservation; `client_uuid` is the device media id, reused on retry. */
        fun proofUploadBody(clientUuid: String, kind: String, filename: String, contentType: String): JsonObject = JsonObject(
            mapOf(
                "client_uuid" to JsonPrimitive(clientUuid),
                "kind" to JsonPrimitive(kind),
                "filename" to JsonPrimitive(filename),
                "content_type" to JsonPrimitive(contentType),
            ),
        )
    }
}