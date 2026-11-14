package com.avago.core.network.rentals

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Thin client for the Rentals API (`/accounts/{account_id}/rentals/...`).
 *
 * Contract: stage A routes in AvagoSvc `src/routes/rentals_api` (docs/rentals-api.md, validated
 * by feature/rentals RentalsContractTest). Responses are raw JSON (no envelope) and are
 * returned as [JsonObject] so the offline store can keep the server row verbatim; typed views
 * are built in feature/rentals.
 *
 * Mutations accept an [idempotencyKey] (the client_uuid of the queued action) which is sent
 * as `Idempotency-Key`; creates also carry `client_uuid` in the body (server replay).
 *
 * Non-2xx responses throw [RentalsApiException] with the server's stable `code`; transport
 * failures propagate as IOExceptions so callers can tell "retry later" from "rejected".
 */
@Singleton
class RentalsApi @Inject constructor(
    private val client: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
) {
    private fun r(accountId: String, path: String) = "$baseUrl/accounts/$accountId/rentals$path"

    // ---- Offline sync feeds (§15) ------------------------------------------------------

    /** GET /rentals/sync/{entity}?updated_since&cursor&limit → `{entity, items, next_cursor, has_more, server_time}`. */
    suspend fun syncFeed(
        accountId: String,
        entity: String,
        cursor: String?,
        updatedSince: String?,
        limit: Int = 500,
    ): JsonObject = call {
        client.get(r(accountId, "/sync/$entity")) {
            cursor?.let { parameter("cursor", it) }
            updatedSince?.let { parameter("updated_since", it) }
            parameter("limit", limit)
        }.body()
    }

    // ---- Fleet visibility (§11.1) -----------------------------------------------------

    /** GET /rentals/fleet (filters optional; `updated_since` for incremental refresh). */
    suspend fun fleet(
        accountId: String,
        status: List<String> = emptyList(),
        locationId: String? = null,
        classId: String? = null,
        q: String? = null,
        updatedSince: String? = null,
    ): JsonObject = call {
        client.get(r(accountId, "/fleet")) {
            if (status.isNotEmpty()) parameter("status", status.joinToString(","))
            locationId?.let { parameter("location_id", it) }
            classId?.let { parameter("class_id", it) }
            q?.let { parameter("q", it) }
            updatedSince?.let { parameter("updated_since", it) }
        }.body()
    }

    /** GET /rentals/availability/search?from&to&class_id&location_id&qty&currency&customer_id. */
    suspend fun availabilitySearch(
        accountId: String,
        from: String,
        to: String,
        classId: String? = null,
        locationId: String? = null,
        qty: Int = 1,
        currency: String? = null,
        customerId: String? = null,
    ): JsonObject = call {
        client.get(r(accountId, "/availability/search")) {
            parameter("from", from)
            parameter("to", to)
            classId?.let { parameter("class_id", it) }
            locationId?.let { parameter("location_id", it) }
            parameter("qty", qty)
            currency?.let { parameter("currency", it) }
            customerId?.let { parameter("customer_id", it) }
        }.body()
    }

    /** GET /rentals/schedule?from&to (≤ 92 days) → `{events:[…]}`. */
    suspend fun schedule(
        accountId: String,
        from: String,
        to: String,
        locationId: String? = null,
        classId: String? = null,
    ): JsonObject = call {
        client.get(r(accountId, "/schedule")) {
            parameter("from", from)
            parameter("to", to)
            locationId?.let { parameter("location_id", it) }
            classId?.let { parameter("class_id", it) }
        }.body()
    }

    /** GET /accounts/{id}/assets/{asset_id}/rental-readiness. */
    suspend fun assetReadiness(accountId: String, assetId: String): JsonObject = call {
        client.get("$baseUrl/accounts/$accountId/assets/$assetId/rental-readiness").body()
    }

    // ---- Pricing ----------------------------------------------------------------------

    /** POST /rentals/pricing/preview — authoritative price (offline previews are "not confirmed"). */
    suspend fun pricingPreview(accountId: String, body: JsonObject): JsonObject = call {
        client.post(r(accountId, "/pricing/preview")) { json(body) }.body()
    }

    // ---- Orders -----------------------------------------------------------------------

    suspend fun getOrder(accountId: String, orderId: String): JsonObject = call {
        client.get(r(accountId, "/orders/$orderId")).body()
    }

    /** POST /rentals/orders — body carries `client_uuid` (replay returns the same order). */
    suspend fun createOrder(accountId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/orders")) { json(body); idem(idempotencyKey) }.body()
    }

    /** PATCH /rentals/orders/{id} — body carries `version` (409 version_conflict). */
    suspend fun updateOrder(accountId: String, orderId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.patch(r(accountId, "/orders/$orderId")) { json(body); idem(idempotencyKey) }.body()
    }

    suspend fun addLine(accountId: String, orderId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/orders/$orderId/lines")) { json(body); idem(idempotencyKey) }.body()
    }

    /**
     * POST /rentals/orders/{id}/{action} — quote|hold|reserve|cancel|no-show|checkout|extend|
     * swap|off-rent|checkin|close. Body: TransitionBody `{reason, end_at, at, by,
     * override_ready, version}`. Stage A answers 501 `not_implemented` for checkout/checkin/
     * swap/close until stage B lands.
     */
    suspend fun orderAction(
        accountId: String,
        orderId: String,
        action: String,
        body: JsonObject,
        idempotencyKey: String,
    ): JsonObject = call {
        client.post(r(accountId, "/orders/$orderId/$action")) { json(body); idem(idempotencyKey) }.body()
    }

    suspend fun orderEvents(accountId: String, orderId: String): JsonElement = call {
        client.get(r(accountId, "/orders/$orderId/events")).body()
    }

    // ---- Customers --------------------------------------------------------------------

    suspend fun createCustomer(accountId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/customers")) { json(body); idem(idempotencyKey) }.body()
    }

    suspend fun updateCustomer(accountId: String, customerId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.patch(r(accountId, "/customers/$customerId")) { json(body); idem(idempotencyKey) }.body()
    }

    /** POST /rentals/customers/{id}/{contacts|documents|job-sites}. */
    suspend fun createCustomerChild(
        accountId: String,
        customerId: String,
        kind: String,
        body: JsonObject,
        idempotencyKey: String,
    ): JsonObject = call {
        client.post(r(accountId, "/customers/$customerId/$kind")) { json(body); idem(idempotencyKey) }.body()
    }

    // ---- Assets -----------------------------------------------------------------------

    suspend fun enableAsset(accountId: String, assetId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/assets/$assetId/enable")) { json(body); idem(idempotencyKey) }.body()
    }

    suspend fun disableAsset(accountId: String, assetId: String, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/assets/$assetId/disable")) { json(JsonObject(emptyMap())); idem(idempotencyKey) }.body()
    }

    // ---- Stage B (AvagoSvc origin/master c562904: src/routes/rentals_api/stage_b.rs) -------
    // Aligned to docs/rentals-api-stage-b.md and the serde structs in handovers.rs /
    // documents.rs / ledger.rs / invoices.rs (validated by RentalsContractTest).

    /**
     * POST /rentals/orders/{o}/lines/{l}/checkout|checkin — `HandoverBody` (handovers.rs):
     * `{client_uuid, at, asset_id, location_id, inspection_log_id, meter_value, fuel_level_pct,
     * charge_level_pct, accessories:[String], photos, damage:[DamageIn], charges:[LineIn],
     * customer_signature_id, staff_signature_id, notes, override_ready, bill_late}`.
     * → 201 `{handovers:[…], order, warnings:[{code,…}], replayed}`; 409 `already_checked_out|in`.
     */
    suspend fun lineHandover(
        accountId: String,
        orderId: String,
        lineId: String,
        kind: String,
        body: JsonObject,
        idempotencyKey: String,
    ): JsonObject = call {
        require(kind == HANDOVER_CHECKOUT || kind == HANDOVER_CHECKIN) { "kind must be checkout|checkin" }
        client.post(r(accountId, "/orders/$orderId/lines/$lineId/$kind")) { json(body); idem(idempotencyKey) }.body()
    }

    /**
     * POST /rentals/orders/{o}/lines/{l}/swap — `SwapBody` (handovers.rs): `{client_uuid, at,
     * new_asset_id, reason, checkin:HandoverBody?, checkout:HandoverBody?}` → 201 `{handovers,
     * order, old_line_id, new_line_id, warnings, replayed}`; 409 `allocation_conflict` when the
     * replacement is not free. Needs `rental.checkout`. (The order-level `/swap` action is 501.)
     */
    suspend fun lineSwap(
        accountId: String,
        orderId: String,
        lineId: String,
        body: JsonObject,
        idempotencyKey: String,
    ): JsonObject = call {
        client.post(r(accountId, "/orders/$orderId/lines/$lineId/swap")) { json(body); idem(idempotencyKey) }.body()
    }

    /**
     * GET /rentals/orders/{o}/lines/{l}/baseline → `{order_id, order_line_id, asset_id, line_status,
     * checkout{id, at, meter_value, fuel_level_pct, accessories, photos, inspection, inspection_title}?,
     * defects{at_checkout[], since_checkout[]}}` (check-in comparison).
     */
    suspend fun lineBaseline(accountId: String, orderId: String, lineId: String): JsonObject = call {
        client.get(r(accountId, "/orders/$orderId/lines/$lineId/baseline")).body()
    }

    /**
     * POST /rentals/orders/{o}/signatures — `SignatureBody` (documents.rs): `{client_uuid, document_id,
     * signer_name, signer_email, signer_role customer|staff, image_png_base64 | image_file_id,
     * signed_at, device_id, method in_person|remote, lat, lng}` → 201 the signature row (no image).
     */
    suspend fun createSignature(accountId: String, orderId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/orders/$orderId/signatures")) { json(body); idem(idempotencyKey) }.body()
    }

    /**
     * POST /rentals/orders/{o}/ledger — `LedgerBody` (ledger.rs) → `{entry, order, warnings, replayed}`.
     */
    suspend fun createLedgerEntry(accountId: String, orderId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/orders/$orderId/ledger")) { json(body); idem(idempotencyKey) }.body()
    }

    /**
     * POST /rentals/orders/{o}/invoices — `DraftBody` (invoices.rs) `{client_uuid, line_ids?, series?,
     * notes?, due_at?}` → `{invoice, warnings, replayed}`.
     */
    suspend fun createInvoice(accountId: String, orderId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/orders/$orderId/invoices")) { json(body); idem(idempotencyKey) }.body()
    }

    /** GET /rentals/documents/{id}/pdf — the rendered document as raw `application/pdf` bytes. */
    suspend fun documentPdf(accountId: String, documentId: String): ByteArray = call {
        client.get(r(accountId, "/documents/$documentId/pdf")) {
            header(HttpHeaders.Accept, ContentType.Application.Pdf.toString())
        }.bodyAsBytes()
    }

    // ---- plumbing ---------------------------------------------------------------------

    companion object {
        const val HANDOVER_CHECKOUT = "checkout"
        const val HANDOVER_CHECKIN = "checkin"
    }

    private fun HttpRequestBuilder.json(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun HttpRequestBuilder.idem(key: String) {
        header("Idempotency-Key", key)
    }

    private suspend inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        val text = runCatching { e.response.bodyAsText() }.getOrDefault("")
        throw RentalsApiException.from(e.response.status.value, text, e)
    }
}

/**
 * A rejected Rentals API request. [code] is the server's stable machine code
 * (`version_conflict`, `allocation_conflict`, `not_implemented`, `rentals_disabled`, …).
 */
class RentalsApiException(
    val status: Int,
    val code: String?,
    message: String,
    val body: JsonObject?,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** 409 — the server state moved on; the local change needs review. */
    val isConflict: Boolean get() = status == 409
    /** 501 — endpoint not shipped yet (stage B); keep the item queued. */
    val isNotImplemented: Boolean get() = status == 501 || code == "not_implemented"
    /** 408/425/429/5xx (except 501) — retry later. */
    val isTransient: Boolean get() = status == 408 || status == 425 || status == 429 || (status >= 500 && status != 501)

    companion object {
        private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

        fun from(status: Int, text: String, cause: Throwable? = null): RentalsApiException {
            val obj = runCatching { lenient.parseToJsonElement(text).jsonObject }.getOrNull()
            fun str(k: String) = obj?.get(k)?.takeIf { it !is JsonNull }?.let {
                runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
            }
            // Coded errors send `{error: message, code, message, details?}`; the documented
            // handover/version conflicts (`{error: "already_checked_out" | "conflict", message, …}`)
            // carry the code only in `error`, so fall back to it when it looks like a wire code.
            val code = str("code") ?: str("error")?.takeIf { it.matches(WIRE_CODE) }
            val msg = str("message") ?: str("error") ?: "HTTP $status"
            return RentalsApiException(status, code, msg, obj, cause)
        }

        private val WIRE_CODE = Regex("^[a-z][a-z0-9]*(_[a-z0-9]+)*$")
    }
}
