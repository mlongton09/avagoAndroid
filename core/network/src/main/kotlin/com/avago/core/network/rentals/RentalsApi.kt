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
import io.ktor.client.request.put
import io.ktor.client.request.delete
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

    /**
     * POST /rentals/instant-book/evaluate — staff preview of whether a booking would be instant
     * (AvagoSvc docs/rentals-api-instant-network.md §2.1). Body `{class_id?, asset_id?,
     * customer_id?, start_at, end_at, delivery?}` → `{data:{eligible, outcome, reasons[], …}}`.
     */
    suspend fun instantBookEvaluate(accountId: String, body: JsonObject): JsonObject = call {
        client.post(r(accountId, "/instant-book/evaluate")) { json(body) }.body()
    }

    /**
     * GET /rentals/orders/{id}/fee-breakdown — platform fee, renter fee and the owner's net payout
     * (AvagoSvc payments/connect.rs, `platform_fees::breakdown_json`), in `{data:{…}}`.
     */
    suspend fun feeBreakdown(accountId: String, orderId: String): JsonObject = call {
        client.get(r(accountId, "/orders/$orderId/fee-breakdown")).body()
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

    /**
     * GET /rentals/readiness-checks?asset_id&status → `{readiness_checks:[…]}` (stage B §3).
     * Without [status] the server lists only `pending` / `failed` checks, newest first.
     */
    suspend fun readinessChecks(accountId: String, assetId: String? = null, status: String? = null): JsonObject = call {
        client.get(r(accountId, "/readiness-checks")) {
            assetId?.let { parameter("asset_id", it) }
            status?.let { parameter("status", it) }
        }.body()
    }

    /** GET /rentals/readiness-checks/{id} → the row. */
    suspend fun readinessCheck(accountId: String, checkId: String): JsonObject = call {
        client.get(r(accountId, "/readiness-checks/$checkId")).body()
    }

    /**
     * POST /rentals/readiness-checks — `{client_uuid, asset_id, items?, notes?}` → 201 the row
     * (always created `pending`; a `client_uuid` replay returns 200 with the existing row).
     * Needs `rental.checkin`.
     */
    suspend fun createReadinessCheck(accountId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/readiness-checks")) { json(body); idem(idempotencyKey) }.body()
    }

    /**
     * PATCH /rentals/readiness-checks/{id} — `{status?, items?, notes?, server_version}` → the row
     * (`server_version + 1`). 409 on a stale version (`{error:"conflict", current}`) or
     * `invalid_transition`; `waived` needs `rental.override_ready` and non-empty notes
     * (`400 reason_required`).
     */
    suspend fun updateReadinessCheck(accountId: String, checkId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.patch(r(accountId, "/readiness-checks/$checkId")) { json(body); idem(idempotencyKey) }.body()
    }

    /** GET /rentals/documents/{id}/pdf — the rendered document as raw `application/pdf` bytes. */
    suspend fun documentPdf(accountId: String, documentId: String): ByteArray = call {
        client.get(r(accountId, "/documents/$documentId/pdf")) {
            header(HttpHeaders.Accept, ContentType.Application.Pdf.toString())
        }.bodyAsBytes()
    }

    // ---- Wave 2 / G1: e-signature, scan, meter (docs/rentals-api-wave2-esign-scan-meter.md) ----

    /**
     * GET /orders/{o}/signature-requests → `{items:[request{…, signers[]}], agreement_id,
     * agreement_signed, signed_document_id, require_signed_agreement_before_checkout}`.
     */
    suspend fun signatureRequests(accountId: String, orderId: String): JsonObject = call {
        client.get(r(accountId, "/orders/$orderId/signature-requests")).body()
    }

    /** POST /orders/{o}/signature-requests (in-person create) → 201 `{data, links}`. */
    suspend fun createSignatureRequest(accountId: String, orderId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/orders/$orderId/signature-requests")) { json(body); idem(idempotencyKey) }.body()
    }

    /** GET /signature-requests/{id}/pdf — the pinned document (hash-checked by the server). */
    suspend fun signatureRequestPdf(accountId: String, requestId: String): ByteArray = call {
        client.get(r(accountId, "/signature-requests/$requestId/pdf")) {
            header(HttpHeaders.Accept, ContentType.Application.Pdf.toString())
        }.bodyAsBytes()
    }

    /**
     * POST /signature-requests/{id}/sign — `{client_uuid, signer_id, image_png_base64 | typed_name,
     * consent:true, consent_text_version, signed_at, device_id}`; a replay answers 200 `replayed`.
     */
    suspend fun signSignatureRequest(accountId: String, requestId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/signature-requests/$requestId/sign")) { json(body); idem(idempotencyKey) }.body()
    }

    /** GET /scan/resolve?code&order_id → `{code, type: asset|part, matched_by, asset?, part?, order_lines[]}` (404 `scan_not_found`). */
    suspend fun scanResolve(accountId: String, code: String, orderId: String? = null): JsonObject = call {
        client.get(r(accountId, "/scan/resolve")) {
            parameter("code", code)
            orderId?.let { parameter("order_id", it) }
        }.body()
    }

    /** GET /assets/{id}/meter-readings → `{items, next_before, meter:{kind, requirement, unit, max_per_day}}`. */
    suspend fun meterReadings(accountId: String, assetId: String, limit: Int = 5): JsonObject = call {
        client.get(r(accountId, "/assets/$assetId/meter-readings")) { parameter("limit", limit) }.body()
    }

    // ---- Wave 2 / G3: bulk returns, contracts (docs/rentals-api-wave2-kits-subrent-longterm.md) ----

    /**
     * POST /orders/{o}/lines/{l}/bulk-return — `{returned, damaged, lost, damage_charge_minor, notes,
     * at, location_id, client_uuid}` → `{order, return, warnings}` (idempotent by client_uuid).
     */
    suspend fun bulkReturn(accountId: String, orderId: String, lineId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/orders/$orderId/lines/$lineId/bulk-return")) { json(body); idem(idempotencyKey) }.body()
    }

    /** GET /contracts/{id}/buyout-quote → `{quote_minor, equity_minor, paid_rent_minor, …, payments[]}`. */
    suspend fun buyoutQuote(accountId: String, contractId: String): JsonObject = call {
        client.get(r(accountId, "/contracts/$contractId/buyout-quote")).body()
    }

    /** GET /contracts/{id}/equity-statement?format=pdf. */
    suspend fun equityStatementPdf(accountId: String, contractId: String): ByteArray = call {
        client.get(r(accountId, "/contracts/$contractId/equity-statement")) {
            parameter("format", "pdf")
            header(HttpHeaders.Accept, ContentType.Application.Pdf.toString())
        }.bodyAsBytes()
    }

    // ---- Wave 2 / G4: KPI summary (docs/rentals-api-wave2-pricing-reports.md) ----------

    /** GET /analytics/kpi-summary?days (rental.reports). */
    suspend fun kpiSummary(accountId: String, days: Int = 30): JsonObject = call {
        client.get(r(accountId, "/analytics/kpi-summary")) { parameter("days", days) }.body()
    }

    // ---- Wave 2 / G6: online payments (docs/rentals-api-wave2-stripe.md) ---------------

    /** GET /payments/status → `{online_payments_enabled, provider, reason, …}`. */
    suspend fun paymentsStatus(accountId: String): JsonObject = call {
        client.get(r(accountId, "/payments/status")).body()
    }

    /** GET /orders/{o}/payments → `{online_payments_enabled, payment_requests[], can{charge, refund, request}, …}`. */
    suspend fun orderPayments(accountId: String, orderId: String): JsonObject = call {
        client.get(r(accountId, "/orders/$orderId/payments")).body()
    }

    /** POST /payment-requests — `{order_id, amount_minor, currency, purpose, client_uuid, …}`; online accounts get a hosted `pay_url`. */
    suspend fun createPaymentRequest(accountId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/payment-requests")) { json(body); idem(idempotencyKey) }.body()
    }

    /** POST /payment-requests/{id}/send — `{to?}` emails the pay link. */
    suspend fun sendPaymentRequest(accountId: String, requestId: String, to: String?): JsonObject = call {
        client.post(r(accountId, "/payment-requests/$requestId/send")) {
            json(JsonObject(if (to.isNullOrBlank()) emptyMap() else mapOf("to" to kotlinx.serialization.json.JsonPrimitive(to))))
        }.body()
    }

    // ---- Wave 2 / G2: credit & documents (docs/rentals-api-wave2-credit-collections-bank.md) ----

    /** GET /orders/{o}/credit-check?stage=checkout&line_ids → dry run of the credit / documents gate. */
    suspend fun creditCheck(accountId: String, orderId: String, stage: String, lineIds: List<String>): JsonObject = call {
        client.get(r(accountId, "/orders/$orderId/credit-check")) {
            parameter("stage", stage)
            if (lineIds.isNotEmpty()) parameter("line_ids", lineIds.joinToString(","))
        }.body()
    }

    /**
     * POST /customers/{c}/document-uploads `{client_uuid, filename, content_type}` →
     * `{file_upload_id, upload_url (null when already uploaded), storage_key, replayed}`.
     */
    suspend fun customerDocumentUpload(accountId: String, customerId: String, body: JsonObject, idempotencyKey: String): JsonObject = call {
        client.post(r(accountId, "/customers/$customerId/document-uploads")) { json(body); idem(idempotencyKey) }.body()
    }

    // ---- Avago Marketplace, owner side (docs/rentals-api-marketplace.md §7) -------------------

    /** GET /marketplace → `{settings, readiness, categories, public_url}` (rental.marketplace_manage). */
    suspend fun marketplaceSettings(accountId: String): JsonObject = call {
        client.get(r(accountId, "/marketplace")).body()
    }

    /** PUT /marketplace — partial settings + `server_version`; 409 version_conflict / storefront_required / location_coordinates_required. */
    suspend fun updateMarketplaceSettings(accountId: String, body: JsonObject): JsonObject = call {
        client.put(r(accountId, "/marketplace")) { json(body) }.body()
    }

    /** GET /marketplace/listings → `{items}` (per-class listing options). */
    suspend fun marketplaceListings(accountId: String): JsonObject = call {
        client.get(r(accountId, "/marketplace/listings")).body()
    }

    /** PUT /marketplace/listings/{class_id} `{listed, category, summary_i18n, offer_*, clear_overrides}`. */
    suspend fun updateMarketplaceListing(accountId: String, classId: String, body: JsonObject): JsonObject = call {
        client.put(r(accountId, "/marketplace/listings/$classId")) { json(body) }.body()
    }

    /** PUT /marketplace/locations/{location_id} `{latitude, longitude, address…}`. */
    suspend fun updateMarketplaceLocation(accountId: String, locationId: String, body: JsonObject): JsonObject = call {
        client.put(r(accountId, "/marketplace/locations/$locationId")) { json(body) }.body()
    }

    /** GET /marketplace/blocks → `{items[{renter_id, name, reason, created_at}]}`. */
    suspend fun marketplaceBlocks(accountId: String): JsonObject = call {
        client.get(r(accountId, "/marketplace/blocks")).body()
    }

    /** DELETE /marketplace/blocks/{renter_id} (404 when not blocked). */
    suspend fun unblockRenter(accountId: String, renterId: String): JsonObject = call {
        client.delete(r(accountId, "/marketplace/blocks/$renterId")).bodyOrEmpty()
    }

    /** GET /meetups?status&cursor&limit → `{items, next_cursor, pending_count}` (rental.meetup_respond). */
    suspend fun meetups(accountId: String, status: String, cursor: String?, limit: Int = 50): JsonObject = call {
        client.get(r(accountId, "/meetups")) {
            parameter("status", status)
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }.body()
    }

    suspend fun meetup(accountId: String, meetupId: String): JsonObject = call {
        client.get(r(accountId, "/meetups/$meetupId")).body()
    }

    /** POST /meetups/{id}/{action} with action in accept | counter | decline | block-renter. */
    suspend fun meetupAction(accountId: String, meetupId: String, action: String, body: JsonObject): JsonObject = call {
        require(action in MEETUP_ACTIONS) { "unknown meet-up action $action" }
        client.post(r(accountId, "/meetups/$meetupId/$action")) { json(body) }.bodyOrEmpty()
    }

    private suspend fun io.ktor.client.statement.HttpResponse.bodyOrEmpty(): JsonObject {
        val text = bodyAsText()
        if (text.isBlank()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    }

    // ---- plumbing ---------------------------------------------------------------------

    companion object {
        val MEETUP_ACTIONS = setOf("accept", "counter", "decline", "block-renter")

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
