package com.avago.feature.rentals.data

import com.avago.core.data.rental.Money
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Request bodies of the Rentals API, built to the stage A serde structs in AvagoSvc
 * `src/routes/rentals_api` (docs/rentals-api.md). Pure so the contract tests can check the
 * exact wire shape. `client_uuid` is added by the outbox when the item is queued.
 */
internal object RentalRequests {

    /** `LineIn` (pricing.rs). */
    fun rentalLine(classId: String?, assetId: String?, description: String?, start: Instant, end: Instant, clientUuid: String) =
        buildJsonObject {
            put("kind", "rental")
            classId?.let { put("class_id", it) }
            assetId?.let { put("asset_id", it) }
            put("qty", 1)
            put("start_at", start.toString())
            put("end_at", end.toString())
            description?.let { put("description", it) }
            put("client_uuid", clientUuid)
        }

    /** `OrderBody` (orders.rs). `source` must be `staff` or `api` (400 invalid_value otherwise). */
    fun orderCreate(input: RentalRepository.BookingInput, line: JsonObject) = buildJsonObject {
        input.customerId?.let { put("customer_id", it) }
        input.customerName?.let { put("customer_name", it) }
        input.locationId?.let { put("location_id", it) }
        put("currency", input.currency)
        put("start_at", input.start.toString())
        put("end_at", input.end.toString())
        put("source", "staff")
        input.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
        put("lines", JsonArray(listOf(line)))
    }

    /** Instant-book evaluate body (instant.rs `EvaluateBody`): ids are optional, the period is required. */
    fun instantBookEvaluate(input: RentalRepository.BookingInput) = buildJsonObject {
        input.classId?.let { put("class_id", it) }
        input.assetId?.let { put("asset_id", it) }
        input.customerId?.let { put("customer_id", it) }
        put("start_at", input.start.toString())
        put("end_at", input.end.toString())
    }
    /** `PreviewBody` (pricing.rs): authoritative online price for the QuickBook live preview. */
    fun pricingPreview(input: RentalRepository.BookingInput) = buildJsonObject {
        put("currency", input.currency)
        input.customerId?.let { put("customer_id", it) }
        input.locationId?.let { put("location_id", it) }
        put("start_at", input.start.toString())
        put("end_at", input.end.toString())
        put(
            "lines",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("kind", "rental")
                        input.classId?.let { put("class_id", it) }
                        input.assetId?.let { put("asset_id", it) }
                        put("qty", 1)
                    },
                ),
            ),
        )
    }

    /** `TransitionBody` (orders.rs): reason, end_at, at, by, override_ready, version. */
    fun transition(reason: String?, endAt: Instant?, at: Instant, version: JsonElement?) = buildJsonObject {
        reason?.let { put("reason", it) }
        endAt?.let { put("end_at", it.toString()) }
        put("at", at.toString())
        version?.takeIf { it !is JsonNull }?.let { put("version", it) }
    }

    /**
     * Customer create / PATCH (customers.rs `CUSTOMERS.cols`; unknown keys are rejected with
     * 400 `unknown_field`). [serverVersion] makes a PATCH optimistic (409 on a stale copy).
     */
    fun customer(input: RentalRepository.CustomerInput, serverVersion: Long? = null) = buildJsonObject {
        put("name", input.name.trim())
        input.company?.takeIf { it.isNotBlank() }?.let { put("company", it.trim()) }
        input.email?.takeIf { it.isNotBlank() }?.let { put("email", it.trim()) }
        input.phone?.takeIf { it.isNotBlank() }?.let { put("phone", it.trim()) }
        input.address?.takeIf { it.isNotBlank() }?.let { put("address", it.trim()) }
        input.dateOfBirth?.takeIf { it.isNotBlank() }?.let { put("date_of_birth", it) }
        input.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.trim()) }
        input.preferredLocale?.let { put("preferred_locale", it) }
        input.preferredCurrency?.let { put("preferred_currency", it) }
        serverVersion?.let { put("server_version", it) }
    }

    /** ISO 3166-1 alpha-3 → alpha-2 for the AAMVA jurisdictions (`issuing_country` is CHAR(2)). */
    fun countryAlpha2(country: String?): String? = when (val c = country?.trim()?.uppercase()) {
        null, "" -> null
        "USA" -> "US"
        "CAN" -> "CA"
        "MEX" -> "MX"
        else -> c.takeIf { it.length == 2 }
    }

    /** Customer document (customers.rs `DOCUMENTS.cols`; kind ∈ licence|id|coi|tax_exempt|operator_cert|credit_app|other). */
    fun document(doc: RentalRepository.DocumentInput) = buildJsonObject {
        put("kind", doc.kind)
        doc.number?.takeIf { it.isNotBlank() }?.let { put("number", it.trim()) }
        countryAlpha2(doc.issuingCountry)?.let { put("issuing_country", it) }
        doc.issuingRegion?.takeIf { it.isNotBlank() }?.let { put("issuing_region", it.trim()) }
        doc.classCodes?.takeIf { it.isNotBlank() }?.let { put("class_codes", it.trim()) }
        doc.issuedOn?.let { put("issued_on", it) }
        doc.expiresOn?.let { put("expires_on", it) }
        doc.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.trim()) }
    }

    /** `EnableBody` (catalog.rs): `rate_day_minor` is required and > 0. */
    fun enableAsset(dayRate: Money, weekRate: Money? = null, monthRate: Money? = null, deposit: Money? = null) = buildJsonObject {
        require(dayRate.minor > 0) { "rate_day_minor must be > 0" }
        put("rate_day_minor", dayRate.minor)
        weekRate?.let { put("rate_week_minor", it.minor) }
        monthRate?.let { put("rate_month_minor", it.minor) }
        deposit?.let { put("deposit_minor", it.minor) }
        put("currency", dayRate.currency)
    }

    // ── Stage B (rentals-stage-b serde structs; pending on master) ──

    /**
     * `SignatureBody` (documents.rs) for `POST /orders/{o}/signatures`: customer, in person,
     * device time as `signed_at`. The caller passes a localised fallback for [signerName].
     */
    fun signature(signerName: String, pngDataUrl: String, at: Instant, deviceId: String? = null) = buildJsonObject {
        put("signer_name", signerName.trim())
        put("signer_role", "customer")
        put("method", "in_person")
        // The server accepts plain base64 or a `data:image/png;base64,` URL; send plain base64.
        put("image_png_base64", pngDataUrl.substringAfter("base64,", pngDataUrl))
        put("signed_at", at.toString())
        deviceId?.let { put("device_id", it) }
    }

    /** Wire `decision` of a handover damage mark (`DamageIn.decision`). */
    const val DECISION_CHARGE = "charge"
    const val DECISION_WAIVE = "waive"
    const val DECISION_PRE_EXISTING = "pre_existing"

    /**
     * `HandoverBody` (handovers.rs) for `POST /orders/{o}/lines/{l}/checkout|checkin`. The line
     * is in the path; `at` is the device time of the event (§15) and the server stamps
     * `received_at`. Sketch marks travel as `damage[]` (`DamageIn`): marks already on the
     * baseline (and every check-out mark) are `pre_existing`; new check-in damage is either
     * `charge` (with `charge_minor`) or `waive` (with a `reason`) — the server rejects a waive
     * without reason. Photos are added by the outbox once uploaded.
     */
    fun handover(input: RentalRepository.HandoverInput, signatureId: String?) = buildJsonObject {
        put("asset_id", input.assetId)
        put("at", input.occurredAt.toString())
        input.logId?.let { put("inspection_log_id", it) }
        // NUMERIC on the server (rust_decimal accepts a JSON string or number); a string keeps precision.
        input.meterReading?.let { put("meter_value", java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()) }
        input.fuelLevel?.let { put("fuel_level_pct", it.coerceIn(0, 100)) }
        input.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.trim()) }
        signatureId?.let { put("customer_signature_id", it) }
        put("damage", JsonArray(input.marks.map { damage(input.direction, it) }))
        // Wave 2 gates approved up front on the hand-over screen (all approvals travel together).
        if (input.overrideSignature) put("override_signature", true)
        input.meterOverrideReason?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put("meter_override", true)
            put("meter_override_reason", it)
        }
        input.creditOverrideReason?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put("override_credit", true)
            put("override_docs", true)
            put("override_reason", it)
        }
    }

    /** One `DamageIn`; `location` carries the inspection v2 sketch zone key. */
    fun damage(direction: String, m: RentalRepository.HandoverInput.Mark) = buildJsonObject {
        val decision = when {
            direction != "in" || !m.isNew -> DECISION_PRE_EXISTING
            m.chargeMinor != null && m.chargeMinor > 0 -> DECISION_CHARGE
            else -> DECISION_WAIVE
        }
        m.label.takeIf { it.isNotBlank() }?.let { put("label", it.trim()) }
        put("severity", m.severity)
        put("decision", decision)
        if (decision == DECISION_CHARGE) put("charge_minor", m.chargeMinor)
        m.reason?.takeIf { it.isNotBlank() }?.let { put("reason", it.trim()) }
        put("location", m.zone)
    }

    /** `LedgerBody` (ledger.rs) manual entry (§5.1: provider null = manual; never a card number). */
    fun ledgerEntry(input: RentalRepository.PaymentInput) = buildJsonObject {
        put("kind", input.kind)
        put("method", input.method)
        put("amount_minor", input.amount.minor)
        put("currency", input.amount.currency)
        input.reference?.takeIf { it.isNotBlank() }?.let { put("reference", it.trim()) }
        input.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.trim()) }
        put("received_at", input.receivedAt.toString())
        put("provider", JsonNull)
        input.cardBrand?.takeIf { it.isNotBlank() }?.let { put("card_brand", it.trim()) }
        input.last4?.let { put("card_last4", it) }
        input.payerName?.takeIf { it.isNotBlank() }?.let { put("payer_name", it.trim()) }
        input.payerEmail?.takeIf { it.isNotBlank() }?.let { put("payer_email", it.trim()) }
        if (input.isAuthorization) put("is_authorization", true)
    }

    /** `DraftBody` (invoices.rs): every uninvoiced line of the order (client_uuid added by the outbox). */
    fun invoice() = buildJsonObject { }

    /**
     * `SwapBody` (handovers.rs) for `POST /orders/{o}/lines/{l}/swap`: check the current unit in
     * ([checkin], no late billing) and [newAssetId] out ([checkout]) at device time [at]. The
     * nested bodies are `HandoverBody` without line/asset (the server fills them).
     */
    fun swap(clientUuid: String, at: Instant, newAssetId: String, reason: String?, checkin: JsonObject?, checkout: JsonObject?) =
        buildJsonObject {
            put("client_uuid", clientUuid)
            put("at", at.toString())
            put("new_asset_id", newAssetId)
            reason?.takeIf { it.isNotBlank() }?.let { put("reason", it.trim()) }
            checkin?.let { put("checkin", it) }
            checkout?.let { put("checkout", it) }
        }

    /** One side of a swap (`HandoverBody` subset): meter / fuel readings, or null when nothing was read. */
    fun swapSide(meter: Double?, fuelPct: Int?): JsonObject? {
        if (meter == null && fuelPct == null) return null
        return buildJsonObject {
            meter?.let { put("meter_value", java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()) }
            fuelPct?.let { put("fuel_level_pct", it.coerceIn(0, 100)) }
        }
    }

    /** `CheckBody` (handovers.rs) for `POST /readiness-checks` (created `pending`). */
    fun readinessCreate(assetId: String, items: JsonArray?, notes: String?) = buildJsonObject {
        put("asset_id", assetId)
        items?.let { put("items", it) }
        notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.trim()) }
    }

    /** `CheckBody` for `PATCH /readiness-checks/{id}` (stale [serverVersion] → 409 `conflict`). */
    fun readinessUpdate(status: String, items: JsonArray, notes: String?, serverVersion: Long?) = buildJsonObject {
        put("status", status)
        put("items", items)
        notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.trim()) }
        serverVersion?.let { put("server_version", it) }
    }
}
