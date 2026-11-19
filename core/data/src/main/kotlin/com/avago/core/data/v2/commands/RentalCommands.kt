package com.avago.core.data.v2.commands

import com.avago.core.data.domain.BulkCountsDraft
import com.avago.core.data.domain.CustomerDocumentDraft
import com.avago.core.data.domain.HandoverDamageDraft
import com.avago.core.data.domain.HandoverDraft
import com.avago.core.data.domain.HandoverOverride
import com.avago.core.data.domain.PaymentDraft
import com.avago.core.data.domain.ReadinessCheck
import com.avago.core.data.domain.ReadinessCheckItem
import com.avago.core.data.domain.RentalBookingDraft
import com.avago.core.data.domain.RentalCustomer
import com.avago.core.data.domain.RentalCustomerDraft
import com.avago.core.data.domain.RentalDeliveryTask
import com.avago.core.data.domain.RentalOrder
import com.avago.core.data.domain.RentalOrderAction
import com.avago.core.data.domain.RentalOrderLine
import com.avago.core.data.domain.RentalRates
import com.avago.core.data.domain.RentalTransfer
import com.avago.core.data.domain.SwapDraft
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import com.avago.core.data.v2.V2StagedFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.math.BigDecimal
import java.time.Instant

/**
 * Builders for the rental catalog commands (orders, lines, hand-overs, readiness checks, bulk
 * returns, payments, invoices, signatures, customers, delivery tasks and transfers) with the
 * optimistic overlay each one shows until the server's rows arrive.
 */
object RentalCommands {

    private const val ONE_MICROS = 1_000_000L

    /** What a line hand-over touches locally: the order, the line and the line's allocations. */
    data class LineContext(
        val order: RentalOrder,
        val line: RentalOrderLine,
        /** Active allocations of the line (ids). */
        val allocationIds: List<String>,
        /** Rental lines of the order still open or out once this line is handled (this line excluded). */
        val otherOpenLines: Int,
    )

    // -------------------------------------------------------------------------------- customers

    fun createCustomer(id: String, draft: RentalCustomerDraft, newId: () -> String, nowMillis: Long, userId: String?): V2NewCommand {
        val name = requireNotNull(draft.name.clean()) { "a customer needs a name" }
        val kind = PartyKind.entries.first { it.wire == draft.kind }
        val payload = PlacesPartiesCustomersCreate(
            id = id,
            name = name,
            kind = kind,
            email = draft.email.clean(),
            phone = draft.phone.clean(),
            company = draft.company.clean(),
            address = draft.address.clean(),
            notes = draft.notes.clean(),
            preferredLocale = draft.preferredLocale.clean(),
            preferredCurrency = draft.preferredCurrency.clean()?.uppercase(),
            dateOfBirth = draft.dateOfBirth.takeIf { kind == PartyKind.PERSON },
        )
        val ops = mutableListOf(
            V2RowOp.upsert(
                "parties", id,
                mapOf(
                    "kind" to kind.wire, "display_name" to name, "organization_name" to payload.company,
                    "is_account_self" to 0L, "origin" to "manual", "needs_review" to 0L, "notes" to payload.notes,
                ) + created(nowMillis, userId),
            ),
            V2RowOp.upsert(
                "customer_profiles", mapOf("party_id" to id),
                mapOf(
                    "status" to "active", "po_required" to 0L, "do_not_rent" to 0L, "is_tax_exempt" to 0L,
                    "is_reverse_charge" to 0L, "locale" to payload.preferredLocale, "currency_code" to payload.preferredCurrency,
                ) + created(nowMillis, userId),
            ),
        )
        listOf("email" to payload.email, "phone" to payload.phone).forEach { (k, value) ->
            if (value != null) {
                ops += V2RowOp.upsert(
                    "party_contact_points", newId(),
                    mapOf("party_id" to id, "kind" to k, "value" to value, "is_primary" to 1L) + created(nowMillis, userId),
                )
            }
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /** Null when nothing changed. [pointIdsByKind]: the customer's primary email / phone point ids. */
    fun updateCustomer(
        stored: RentalCustomer,
        draft: RentalCustomerDraft,
        pointIdsByKind: Map<String, String>,
        newId: () -> String,
        nowMillis: Long,
    ): V2NewCommand? {
        val d = V2UpdateDiff(PlacesPartiesCustomersUpdate.Clear.entries)
        val name = d.text("name", stored.name, draft.name)
        val kind = d.field("kind", stored.kind, draft.kind)
        val email = d.text("email", stored.email, draft.email)
        val phone = d.text("phone", stored.phone, draft.phone)
        val company = d.text("company", stored.company, draft.company)
        val address = d.text("address", stored.address, draft.address)
        val notes = d.text("notes", stored.notes, draft.notes)
        val locale = d.text("preferred_locale", stored.locale, draft.preferredLocale)
        val currency = d.text("preferred_currency", stored.currency, draft.preferredCurrency?.uppercase())
        draft.dateOfBirth?.let { d.field("date_of_birth", null, it, column = null) }
        d.check()
        if (d.isEmpty) return null
        val payload = PlacesPartiesCustomersUpdate(
            id = stored.partyId,
            name = name,
            kind = kind?.let { k -> PartyKind.entries.first { it.wire == k } },
            email = email, phone = phone, company = company, address = address, notes = notes,
            preferredLocale = locale, preferredCurrency = currency, dateOfBirth = draft.dateOfBirth,
            clear = d.clear,
        )
        val cleared = d.clear.map { it.wire }.toSet()
        fun change(value: Any?, wire: String): Pair<Boolean, Any?> = when {
            value != null -> true to value
            wire in cleared -> true to null
            else -> false to null
        }
        val party = buildMap<String, Any?> {
            name?.let { put("display_name", it) }
            kind?.let { put("kind", it) }
            change(company, "company").let { (c, v) -> if (c) put("organization_name", v) }
            change(notes, "notes").let { (c, v) -> if (c) put("notes", v) }
        }
        val profile = buildMap<String, Any?> {
            change(locale, "preferred_locale").let { (c, v) -> if (c) put("locale", v) }
            change(currency, "preferred_currency").let { (c, v) -> if (c) put("currency_code", v) }
        }
        val ops = mutableListOf<V2RowOp>()
        if (party.isNotEmpty()) ops += V2RowOp.upsert("parties", stored.partyId, party + updated(nowMillis))
        if (profile.isNotEmpty()) ops += V2RowOp.upsert("customer_profiles", mapOf("party_id" to stored.partyId), profile + updated(nowMillis))
        listOf("email" to change(email, "email"), "phone" to change(phone, "phone")).forEach { (k, c) ->
            if (!c.first) return@forEach
            val existing = pointIdsByKind[k]
            val value = c.second as String?
            ops += when {
                value == null && existing != null -> V2RowOp.delete("party_contact_points", existing)
                value == null -> return@forEach
                existing != null -> V2RowOp.upsert("party_contact_points", existing, mapOf("value" to value) + updated(nowMillis))
                else -> V2RowOp.upsert(
                    "party_contact_points", newId(),
                    mapOf("party_id" to stored.partyId, "kind" to k, "value" to value, "is_primary" to 1L) + created(nowMillis, null),
                )
            }
        }
        return payload.toNewCommand(baseVersion = stored.version, overlay = ops.takeIf { it.isNotEmpty() }?.let(::V2OverlayPatch))
    }

    /** Customer documents are read through REST (no projection), so there is no overlay. */
    fun addCustomerDocument(partyId: String, documentId: String, draft: CustomerDocumentDraft, scans: List<V2StagedFile>): V2NewCommand =
        PlacesPartiesCustomersAddDocument(
            id = partyId,
            documentId = documentId,
            kind = PartyDocumentKind.entries.first { it.wire == draft.kind },
            number = draft.number.clean(),
            issuer = draft.issuer.clean(),
            issuingCountry = draft.issuingCountry.clean(),
            issuingRegion = draft.issuingRegion.clean(),
            classCodes = draft.classCodes.clean(),
            issuedOn = draft.issuedOn,
            expiresOn = draft.expiresOn,
            notes = draft.notes.clean(),
            fileObjectIds = scans.map { it.fileId }.ifEmpty { null },
        ).toNewCommand(uploads = scans.map { it.toUpload() })

    // ----------------------------------------------------------------------------------- orders

    /** The order with one rental line; the overlay shows a draft until the server prices it. */
    fun createOrder(
        id: String,
        lineId: String,
        draft: RentalBookingDraft,
        currency: String,
        customerName: String?,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(draft.customerPartyId != null || !draft.walkInName.isNullOrBlank()) { "a booking needs a customer" }
        require(draft.classId != null || draft.assetId != null) { "a booking needs a class or a unit" }
        require(draft.endAt.isAfter(draft.startAt)) { "the booking must end after it starts" }
        val line = RentalOrderLineIn(
            id = lineId,
            kind = RentalLineKind.RENTAL,
            classId = draft.classId,
            assetId = draft.assetId,
            locationId = draft.locationId,
            description = draft.description.clean(),
            startAt = draft.startAt,
            endAt = draft.endAt,
        )
        val payload = RentalRentalOrdersCreate(
            id = id,
            customerPartyId = draft.customerPartyId,
            walkInName = draft.walkInName.clean().takeIf { draft.customerPartyId == null },
            locationId = draft.locationId,
            currency = draft.currency.clean()?.uppercase(),
            startAt = draft.startAt,
            endAt = draft.endAt,
            notes = draft.notes.clean(),
            lines = listOf(line),
        )
        val ops = listOf(
            V2RowOp.upsert(
                "rental_orders", id,
                mapOf(
                    "status" to "draft", "walk_in_name" to payload.walkInName, "billable" to 1L, "notes" to payload.notes,
                    "customer_party_id" to draft.customerPartyId, "location_id" to draft.locationId, "currency" to currency,
                    "locale" to "", "start_at" to draft.startAt.toEpochMilli(), "end_at" to draft.endAt.toEpochMilli(),
                    "fulfilment" to "pickup", "subtotal_minor" to 0L, "discount_minor" to 0L, "tax_minor" to 0L,
                    "total_minor" to 0L, "deposit_required_minor" to 0L, "tax_summary" to "{}", "source" to "staff",
                    "needs_review" to 0L, "kind" to "standard",
                ) + created(nowMillis, userId),
            ),
            V2RowOp.upsert(
                "rental_order_lines", lineId,
                mapOf(
                    "order_id" to id, "line_no" to 1L, "kind" to "rental", "class_id" to draft.classId,
                    "asset_id" to draft.assetId, "location_id" to draft.locationId,
                    "description" to (line.description ?: customerName.orEmpty()), "qty_micros" to ONE_MICROS,
                    "start_at" to draft.startAt.toEpochMilli(), "end_at" to draft.endAt.toEpochMilli(),
                    "amount_minor" to 0L, "discount_minor" to 0L, "taxable" to 1L, "tax_snapshot" to "{}", "tax_minor" to 0L,
                    "status" to "open", "assign_unit_later" to 0L,
                ) + created(nowMillis, userId),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /**
     * One order transition. [allocationIds]: the order's active allocations (their purpose follows
     * the order); [lines] the order's lines (an extension moves their ends, a booking of a unit
     * line without an allocation gets one locally until the server's arrives).
     */
    fun act(
        order: RentalOrder,
        action: RentalOrderAction,
        allocationIds: List<String>,
        lines: List<RentalOrderLine>,
        newId: () -> String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val id = order.id
        val ops = mutableListOf<V2RowOp>()
        fun status(s: String, extra: Map<String, Any?> = emptyMap()) {
            ops += V2RowOp.upsert("rental_orders", id, mapOf("status" to s) + extra + updated(nowMillis))
        }
        fun allocations(purpose: String?, active: Boolean = true) {
            allocationIds.forEach { a ->
                ops += V2RowOp.upsert(
                    "resource_allocations", a,
                    buildMap {
                        purpose?.let { put("purpose", it) }
                        put("active", if (active) 1L else 0L)
                        putAll(updated(nowMillis))
                    },
                )
            }
        }
        fun book(purpose: String) {
            allocations(purpose)
            if (allocationIds.isEmpty()) {
                lines.filter { it.assetId != null && it.status == "open" && it.kind == "rental" }.forEach { l ->
                    ops += V2RowOp.upsert(
                        "resource_allocations", newId(),
                        mapOf(
                            "during_start" to (l.startAt ?: order.startAt)?.toEpochMilli(),
                            "during_end" to (l.endAt ?: order.endAt)?.toEpochMilli(),
                            "asset_id" to l.assetId, "order_id" to id, "order_line_id" to l.id, "purpose" to purpose,
                            "active" to 1L, "blocking" to 1L, "needs_review" to 0L, "conflict" to 0L,
                            "qty_micros" to ONE_MICROS, "resource_kind" to "unit",
                        ) + created(nowMillis, userId),
                    )
                }
            }
        }
        val payload: V2CommandPayload = when (action) {
            RentalOrderAction.Quote -> {
                status("quoted")
                RentalRentalOrdersQuote(id)
            }
            is RentalOrderAction.Hold -> {
                status("held")
                book("hold")
                RentalRentalOrdersHold(id, action.reason.clean())
            }
            is RentalOrderAction.Reserve -> {
                status("reserved")
                book("reserved")
                RentalRentalOrdersReserve(
                    id,
                    overrideReady = action.overrideReady.takeIf { it },
                    overrideCredit = action.overrideCredit.takeIf { it },
                    overrideReason = action.overrideReason.clean(),
                )
            }
            is RentalOrderAction.Cancel -> {
                val reason = requireNotNull(action.reason.clean()) { "a cancellation needs a reason" }
                status("cancelled", mapOf("cancel_reason" to reason))
                allocations(null, active = false)
                RentalRentalOrdersCancel(id, reason)
            }
            is RentalOrderAction.NoShow -> {
                status("cancelled", mapOf("cancel_reason" to (action.reason.clean() ?: "no_show")))
                allocations(null, active = false)
                RentalRentalOrdersNoShow(id, action.reason.clean())
            }
            is RentalOrderAction.Extend -> {
                val end = action.endAt.toEpochMilli()
                ops += V2RowOp.upsert("rental_orders", id, mapOf("end_at" to end) + updated(nowMillis))
                lines.filter { it.status == "open" || it.status == "out" }.forEach { l ->
                    ops += V2RowOp.upsert("rental_order_lines", l.id, mapOf("end_at" to end) + updated(nowMillis))
                }
                allocationIds.forEach { a -> ops += V2RowOp.upsert("resource_allocations", a, mapOf("during_end" to end) + updated(nowMillis)) }
                RentalRentalOrdersExtend(id, action.endAt, action.reason.clean())
            }
            is RentalOrderAction.OffRent -> {
                status("off_rent_requested", mapOf("off_rent_at" to action.at.toEpochMilli(), "off_rent_by" to action.by.clean()))
                RentalRentalOrdersOffRent(id, action.at, action.by.clean(), action.reason.clean())
            }
            RentalOrderAction.Close -> {
                status("closed")
                RentalRentalOrdersClose(id)
            }
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    fun assignUnit(line: RentalOrderLine, assetId: String, overrideReady: Boolean, allocationIds: List<String>, nowMillis: Long): V2NewCommand {
        val ops = mutableListOf(V2RowOp.upsert("rental_order_lines", line.id, mapOf("asset_id" to assetId) + updated(nowMillis)))
        allocationIds.forEach { a ->
            ops += V2RowOp.upsert("resource_allocations", a, mapOf("asset_id" to assetId, "resource_kind" to "unit") + updated(nowMillis))
        }
        return RentalRentalOrderLinesAssignUnit(line.id, assetId, overrideReady.takeIf { it })
            .toNewCommand(overlay = V2OverlayPatch(ops))
    }

    // -------------------------------------------------------------------------------- hand-overs

    /** A drawn signature (PNG staged for upload) recorded against the order. */
    fun captureSignature(
        id: String,
        orderId: String,
        signerName: String,
        role: SignerRole,
        png: V2StagedFile,
        signedAt: Instant,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val name = requireNotNull(signerName.clean()) { "a signature needs the signer's name" }
        return RentalSignaturesCapture(
            id = id, orderId = orderId, signerName = name, signerRole = role, method = SignatureMethod.IN_PERSON,
            imageFileObjectId = png.fileId, signedAt = signedAt,
        ).toNewCommand(
            overlay = V2OverlayPatch(
                listOf(
                    MaintCommands.pendingFileRow(png, nowMillis, userId),
                    signatureRow(id, orderId, name, role.wire, png.fileId, signedAt, nowMillis, userId, null),
                ),
            ),
            uploads = listOf(png.toUpload()),
        )
    }

    private fun signatureRow(
        id: String,
        orderId: String,
        name: String,
        role: String,
        fileId: String?,
        signedAt: Instant,
        nowMillis: Long,
        userId: String?,
        requestId: String?,
    ) = V2RowOp.upsert(
        "signatures", id,
        mapOf(
            "order_id" to orderId, "signer_name" to name, "signer_role" to role, "image_file_object_id" to fileId,
            "signed_at" to signedAt.toEpochMilli(), "received_at" to nowMillis, "method" to "in_person",
            "recorded_by" to userId, "request_id" to requestId,
        ),
    )

    /**
     * Check-out or check-in of one line. [photos] / [meterPhoto] are staged for upload with the
     * command; [signatureId] is the customer signature captured first (the command depends on it).
     */
    fun handover(
        id: String,
        draft: HandoverDraft,
        ctx: LineContext,
        photos: List<V2StagedFile>,
        meterPhoto: V2StagedFile?,
        signatureId: String?,
        dependsOn: List<String>,
        readinessId: String,
        newId: () -> String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(draft.lineId == ctx.line.id) { "the hand-over is for line ${ctx.line.id}" }
        val handover = HandoverIn(
            id = id,
            at = draft.at,
            assetId = draft.assetId.takeIf { draft.checkout && ctx.line.assetId == null },
            inspectionLogId = draft.inspectionActivityId,
            meterValue = draft.meterValue,
            meterOverride = draft.meterOverrideReason.clean()?.let { true },
            meterOverrideReason = draft.meterOverrideReason.clean(),
            meterPhotoFileObjectId = meterPhoto?.fileId,
            fuelLevelPct = draft.fuelLevelPct,
            photoFileObjectIds = photos.map { it.fileId }.ifEmpty { null },
            damage = draft.damage.map(::damageIn).ifEmpty { null },
            customerSignatureId = signatureId,
            notes = draft.notes.clean(),
        )
        val creditReason = draft.creditOverrideReason.clean()
        val payload: V2CommandPayload = if (draft.checkout) {
            RentalRentalHandoversCheckout(
                id = id, orderId = draft.orderId, lineId = draft.lineId, handover = handover,
                overrideReady = draft.overrideReady.takeIf { it },
                overrideCredit = creditReason?.let { true },
                overrideDocs = creditReason?.let { true },
                overrideSignature = draft.overrideSignature.takeIf { it },
                overrideReason = creditReason,
            )
        } else {
            RentalRentalHandoversCheckin(
                id = id, orderId = draft.orderId, lineId = draft.lineId, handover = handover,
                bulk = draft.bulk?.let(::bulkCounts),
            )
        }
        val uploads = (photos + listOfNotNull(meterPhoto))
        val files = uploads.map { MaintCommands.pendingFileRow(it, nowMillis, userId) }
        val overlay = handoverOverlay(payload.toJson(), draft.checkout, ctx, readinessId, newId, nowMillis, userId)
        return payload.toNewCommand(
            overlay = V2OverlayPatch(files + overlay),
            dependsOn = dependsOn,
            uploads = uploads.map { it.toUpload() },
        )
    }

    private fun damageIn(d: HandoverDamageDraft) = HandoverDamage(
        label = d.label.clean() ?: d.zone,
        severity = d.severity,
        decision = when {
            !d.isNew -> "pre_existing"
            (d.chargeMinor ?: 0L) > 0L -> "charge"
            else -> "waive"
        },
        chargeMinor = d.chargeMinor?.takeIf { d.isNew && it > 0 },
        reason = d.reason.clean(),
        location = d.zone,
    )

    private fun bulkCounts(c: BulkCountsDraft) = BulkCounts(
        returned = c.returned, damaged = c.damaged, lost = c.lost, damageChargeMinor = c.damageChargeMinor, notes = c.notes.clean(),
    )

    /**
     * The local effect of a check-out / check-in command, read from its JSON [payload] so a refused
     * hand-over resent with an override gets the same overlay: the hand-over row, the line out /
     * returned, the allocation on rent / released, the order on rent / returned and, on check-in,
     * the pending readiness checklist the server opens for the unit.
     */
    fun handoverOverlay(
        payload: JsonObject,
        checkout: Boolean,
        ctx: LineContext,
        readinessId: String,
        newId: () -> String,
        nowMillis: Long,
        userId: String?,
    ): List<V2RowOp> {
        val h = payload["handover"] as JsonObject
        val handoverId = h.text("id") ?: payload.text("id")!!
        val at = h.text("at")?.let(Instant::parse)?.toEpochMilli() ?: nowMillis
        val assetId = h.text("asset_id") ?: ctx.line.assetId
        val line = ctx.line
        val ops = mutableListOf(
            V2RowOp.upsert(
                "rental_handovers", handoverId,
                mapOf(
                    "order_id" to ctx.order.id, "order_line_id" to line.id, "asset_id" to assetId,
                    "kind" to if (checkout) "checkout" else "checkin", "at" to at, "received_at" to nowMillis,
                    "by_user_id" to userId, "inspection_activity_id" to h.text("inspection_log_id"),
                    "fuel_level_pct" to (h["fuel_level_pct"] as? JsonPrimitive)?.intOrNull?.toLong(),
                    "charge_level_pct" to (h["charge_level_pct"] as? JsonPrimitive)?.intOrNull?.toLong(),
                    "accessories" to (h["accessories"] ?: JsonArray(emptyList())).toString(),
                    "damage_summary" to (h["damage"] ?: JsonArray(emptyList())).toString(),
                    "charges" to (h["charges"] ?: JsonArray(emptyList())).toString(),
                    "customer_signature_id" to h.text("customer_signature_id"), "notes" to h.text("notes"),
                    "late_minutes" to 0L, "readiness_items" to "[]",
                ) + created(nowMillis, userId),
            ),
        )
        if (checkout) {
            ops += V2RowOp.upsert(
                "rental_order_lines", line.id,
                mapOf("status" to "out", "actual_out_at" to at, "asset_id" to assetId) + updated(nowMillis),
            )
            if (ctx.allocationIds.isEmpty() && assetId != null) {
                ops += V2RowOp.upsert(
                    "resource_allocations", newId(),
                    mapOf(
                        "during_start" to at, "during_end" to (line.endAt ?: ctx.order.endAt)?.toEpochMilli(),
                        "asset_id" to assetId, "order_id" to ctx.order.id, "order_line_id" to line.id, "purpose" to "on_rent",
                        "active" to 1L, "blocking" to 1L, "needs_review" to 0L, "conflict" to 0L,
                        "qty_micros" to ONE_MICROS, "resource_kind" to "unit",
                    ) + created(nowMillis, userId),
                )
            }
            ctx.allocationIds.forEach { a ->
                ops += V2RowOp.upsert(
                    "resource_allocations", a,
                    mapOf("purpose" to "on_rent", "asset_id" to assetId, "resource_kind" to "unit") + updated(nowMillis),
                )
            }
            ops += V2RowOp.upsert("rental_orders", ctx.order.id, mapOf("status" to "on_rent") + updated(nowMillis))
        } else {
            ops += V2RowOp.upsert("rental_order_lines", line.id, mapOf("status" to "returned", "actual_in_at" to at) + updated(nowMillis))
            ctx.allocationIds.forEach { a ->
                ops += V2RowOp.upsert("resource_allocations", a, mapOf("active" to 0L, "during_end" to at) + updated(nowMillis))
            }
            if (ctx.otherOpenLines == 0) {
                ops += V2RowOp.upsert("rental_orders", ctx.order.id, mapOf("status" to "returned") + updated(nowMillis))
            }
            if (assetId != null && payload["bulk"] == null) {
                ops += readinessRow(readinessId, assetId, ctx.order.id, "pending", DEFAULT_CHECKLIST, null, at, nowMillis, userId)
            }
        }
        return ops
    }

    /** Same handover payload with [fix] applied (the refused command is resent with it). */
    fun withOverride(payload: JsonObject, checkout: Boolean, fix: HandoverOverride): JsonObject {
        val m = payload.toMutableMap()
        fun reason(r: String) {
            require(r.isNotBlank()) { "an override needs a reason" }
            m["override_reason"] = JsonPrimitive(r.trim())
        }
        when (fix) {
            HandoverOverride.Signature -> {
                require(checkout) { "only a check-out has a signature gate" }
                m["override_signature"] = JsonPrimitive(true)
            }
            is HandoverOverride.Credit -> {
                require(checkout) { "only a check-out has a credit gate" }
                m["override_credit"] = JsonPrimitive(true)
                m["override_docs"] = JsonPrimitive(true)
                reason(fix.reason)
            }
            is HandoverOverride.Ready -> {
                require(checkout) { "only a check-out has a readiness gate" }
                m["override_ready"] = JsonPrimitive(true)
                reason(fix.reason)
            }
            is HandoverOverride.Meter -> {
                val h = (m["handover"] as JsonObject).toMutableMap()
                require(fix.reason.isNotBlank()) { "a meter override needs a reason" }
                h["meter_override"] = JsonPrimitive(true)
                h["meter_override_reason"] = JsonPrimitive(fix.reason.trim())
                fix.value?.let { h["meter_value"] = V2PayloadJson.value(it) }
                m["handover"] = JsonObject(h)
            }
        }
        return JsonObject(m)
    }

    /** Swap of an on-rent line's unit: the old line is swapped, a new line continues the rental. */
    fun swap(
        draft: SwapDraft,
        ctx: LineContext,
        newLineId: String,
        checkinId: String,
        checkoutId: String,
        newId: () -> String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val old = ctx.line
        require(old.status == "out") { "only an on-rent line can be swapped" }
        require(draft.newAssetId != old.assetId) { "pick a different unit" }
        val payload = RentalRentalOrderLinesSwap(
            id = old.id, newLineId = newLineId, newAssetId = draft.newAssetId, at = draft.at, reason = draft.reason.clean(),
            overrideReady = draft.overrideReady.takeIf { it },
            checkin = HandoverIn(id = checkinId, at = draft.at, meterValue = draft.inMeter, fuelLevelPct = draft.inFuelPct),
            checkout = HandoverIn(id = checkoutId, at = draft.at, assetId = draft.newAssetId, meterValue = draft.outMeter, fuelLevelPct = draft.outFuelPct),
        )
        val at = draft.at.toEpochMilli()
        val ops = mutableListOf(
            V2RowOp.upsert("rental_order_lines", old.id, mapOf("status" to "swapped", "actual_in_at" to at) + updated(nowMillis)),
            V2RowOp.upsert(
                "rental_order_lines", newLineId,
                mapOf(
                    "order_id" to old.orderId, "line_no" to (old.lineNo + 1000L), "kind" to old.kind, "class_id" to old.classId,
                    "asset_id" to draft.newAssetId, "product_id" to old.productId, "parent_line_id" to old.parentLineId,
                    "description" to old.description, "qty_micros" to V2Micros.fromDecimal(old.qty),
                    "start_at" to at, "end_at" to old.endAt?.toEpochMilli(), "actual_out_at" to at,
                    "unit_price_minor" to old.unitPriceMinor, "amount_minor" to 0L, "discount_minor" to 0L, "taxable" to 1L,
                    "tax_snapshot" to "{}", "tax_minor" to 0L, "status" to "out", "assign_unit_later" to 0L,
                ) + created(nowMillis, userId),
            ),
            V2RowOp.upsert(
                "resource_allocations", newId(),
                mapOf(
                    "during_start" to at, "during_end" to (old.endAt ?: ctx.order.endAt)?.toEpochMilli(),
                    "asset_id" to draft.newAssetId, "order_id" to old.orderId, "order_line_id" to newLineId, "purpose" to "on_rent",
                    "active" to 1L, "blocking" to 1L, "needs_review" to 0L, "conflict" to 0L,
                    "qty_micros" to ONE_MICROS, "resource_kind" to "unit",
                ) + created(nowMillis, userId),
            ),
        )
        ctx.allocationIds.forEach { a ->
            ops += V2RowOp.upsert("resource_allocations", a, mapOf("active" to 0L, "during_end" to at) + updated(nowMillis))
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    fun bulkReturn(
        id: String,
        line: RentalOrderLine,
        counts: BulkCountsDraft,
        at: Instant,
        locationId: String?,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val productId = requireNotNull(line.productId) { "only a bulk line takes a bulk return" }
        listOf(counts.returned, counts.damaged, counts.lost).forEach { require(it.signum() >= 0) { "counts cannot be negative" } }
        require(listOf(counts.returned, counts.damaged, counts.lost).any { it.signum() > 0 }) { "a return needs a quantity" }
        val payload = RentalRentalBulkReturnsCreate(
            id = id, orderId = line.orderId, lineId = line.id, returned = counts.returned, damaged = counts.damaged, lost = counts.lost,
            damageChargeMinor = counts.damageChargeMinor, notes = counts.notes.clean(), at = at, locationId = locationId,
        )
        val ops = listOf(
            V2RowOp.upsert(
                "rental_bulk_returns", id,
                mapOf(
                    "order_id" to line.orderId, "order_line_id" to line.id, "product_id" to productId, "location_id" to locationId,
                    "returned_at" to at.toEpochMilli(), "returned_qty_micros" to V2Micros.fromDecimal(counts.returned),
                    "damaged_qty_micros" to V2Micros.fromDecimal(counts.damaged), "lost_qty_micros" to V2Micros.fromDecimal(counts.lost),
                    "final" to 0L, "charge_minor" to (counts.damageChargeMinor ?: 0L), "notes" to payload.notes, "recorded_by" to userId,
                ) + created(nowMillis, userId),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    // ------------------------------------------------------------------------- money & documents

    /** Ledger entries are not projected, so a recorded payment shows as pending on the order. */
    fun recordPayment(entryId: String, draft: PaymentDraft): V2NewCommand {
        require(draft.amountMinor > 0) { "the amount must be positive" }
        return RentalRentalOrdersRecordPayment(
            id = draft.orderId,
            entryId = entryId,
            kind = LedgerKind.entries.first { it.wire == draft.kind },
            method = PaymentMethod.entries.first { it.wire == draft.method },
            amountMinor = draft.amountMinor,
            currency = draft.currency.uppercase(),
            reference = draft.reference.clean(),
            notes = draft.notes.clean(),
            isAuthorization = draft.isAuthorization.takeIf { it },
            cardBrand = draft.cardBrand.clean(),
            cardLast4 = draft.cardLast4.clean(),
            payerName = draft.payerName.clean(),
            receivedAt = draft.receivedAt,
        ).toNewCommand()
    }

    fun createInvoiceDraft(id: String, order: RentalOrder, nowMillis: Long, userId: String?): V2NewCommand {
        val customer = requireNotNull(order.customerPartyId) { "a walk-in order cannot be invoiced; add a customer first" }
        return RentalInvoicesCreateDraft(id = id, orderId = order.id).toNewCommand(
            overlay = V2OverlayPatch(
                listOf(
                    V2RowOp.upsert(
                        "invoices", id,
                        mapOf(
                            "customer_party_id" to customer, "order_id" to order.id, "status" to "draft", "kind" to "invoice",
                            "currency" to order.currency, "subtotal_minor" to 0L, "tax_minor" to 0L, "total_minor" to 0L,
                            "discount_minor" to 0L, "paid_minor" to 0L, "credited_minor" to 0L, "series" to "", "consolidated" to 0L,
                            "workflow" to "rental",
                        ) + created(nowMillis, userId),
                    ),
                ),
            ),
        )
    }

    /** Opens an in-person agreement signature request for the customer (the signing depends on it). */
    fun requestAgreementSignature(orderId: String, requestId: String, signerName: String, locale: String?): V2NewCommand =
        RentalRentalOrdersRequestSignatures(
            id = orderId,
            requestId = requestId,
            purpose = SignaturePurpose.AGREEMENT,
            locale = locale.clean(),
            signers = listOf(SignerIn(name = requireNotNull(signerName.clean()) { "the signer needs a name" }, role = SignerRole.CUSTOMER.wire, method = SignatureMethod.IN_PERSON)),
        ).toNewCommand()

    fun signRequest(
        id: String,
        orderId: String,
        requestId: String,
        signerName: String,
        png: V2StagedFile,
        consentTextVersion: String,
        signedAt: Instant,
        dependsOn: String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand = RentalSignaturesSignRequest(
        id = id, requestId = requestId, imageFileObjectId = png.fileId, typedName = signerName.clean(), consent = true,
        consentTextVersion = consentTextVersion, signedAt = signedAt,
    ).toNewCommand(
        overlay = V2OverlayPatch(
            listOf(
                MaintCommands.pendingFileRow(png, nowMillis, userId),
                signatureRow(id, orderId, signerName.trim(), "customer", png.fileId, signedAt, nowMillis, userId, requestId),
            ),
        ),
        dependsOn = listOf(dependsOn),
        uploads = listOf(png.toUpload()),
    )

    // -------------------------------------------------------------------------------- readiness

    val DEFAULT_CHECKLIST = listOf(
        ReadinessCheckItem("inspected", "Inspected", false),
        ReadinessCheckItem("cleaned", "Cleaned", false),
        ReadinessCheckItem("refuelled", "Refuelled / charged", false),
        ReadinessCheckItem("accessories", "Accessories complete", false),
    )

    private fun itemsIn(items: List<ReadinessCheckItem>) = items.map { ReadinessItem(it.key, it.label, it.done, it.note.clean()) }

    private fun itemsJson(items: List<ReadinessCheckItem>): String = JsonArray(itemsIn(items).map { it.toJson() }).toString()

    private fun readinessRow(
        id: String,
        assetId: String,
        orderId: String?,
        status: String,
        items: List<ReadinessCheckItem>,
        notes: String?,
        at: Long,
        nowMillis: Long,
        userId: String?,
    ) = V2RowOp.upsert(
        "rental_handovers", id,
        mapOf(
            "order_id" to orderId, "asset_id" to assetId, "kind" to "readiness", "at" to at, "received_at" to nowMillis,
            "by_user_id" to userId, "accessories" to "[]", "damage_summary" to "[]", "charges" to "[]", "late_minutes" to 0L,
            "readiness_status" to status, "readiness_items" to itemsJson(items), "notes" to notes,
        ) + created(nowMillis, userId),
    )

    fun createCheck(id: String, assetId: String, items: List<ReadinessCheckItem>, notes: String?, nowMillis: Long, userId: String?): V2NewCommand {
        val list = items.ifEmpty { DEFAULT_CHECKLIST }
        return RentalRentalHandoversCreateCheck(id = id, assetId = assetId, items = itemsIn(list), notes = notes.clean())
            .toNewCommand(overlay = V2OverlayPatch(listOf(readinessRow(id, assetId, null, "pending", list, notes.clean(), nowMillis, nowMillis, userId))))
    }

    /** Readiness rule (server `check_status_ok`): only an open check changes; passing needs every item done. */
    fun updateCheck(
        stored: ReadinessCheck,
        status: String,
        items: List<ReadinessCheckItem>,
        notes: String?,
        nowMillis: Long,
    ): V2NewCommand {
        require(stored.open) { "a closed checklist cannot change" }
        val target = ReadinessStatus.entries.first { it.wire == status }
        val reason = notes.clean()
        when (target) {
            ReadinessStatus.PASSED -> require(items.all { it.done }) { "every item must be done to pass" }
            ReadinessStatus.WAIVED -> require(reason != null) { "waiving needs a reason" }
            ReadinessStatus.FAILED -> require(reason != null || items.any { !it.done }) { "failing needs a reason" }
            ReadinessStatus.PENDING -> Unit
        }
        return RentalRentalHandoversUpdateCheck(
            id = stored.id,
            status = target.takeIf { it.wire != stored.status },
            items = itemsIn(items),
            notes = reason,
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(
                listOf(
                    V2RowOp.upsert(
                        "rental_handovers", stored.id,
                        mapOf("readiness_status" to target.wire, "readiness_items" to itemsJson(items), "notes" to reason) + updated(nowMillis),
                    ),
                ),
            ),
        )
    }

    // ------------------------------------------------------------------------------ asset rental

    fun enableRental(assetId: String, rates: RentalRates, nowMillis: Long): V2NewCommand {
        require(rates.dayMinor > 0) { "a day rate is required" }
        return MaintAssetsEnableRental(
            id = assetId, rateDayMinor = rates.dayMinor, rateWeekMinor = rates.weekMinor, depositMinor = rates.depositMinor,
            currency = rates.currency.clean()?.uppercase(),
        ).toNewCommand(overlay = V2OverlayPatch.of(V2RowOp.upsert("assets", assetId, mapOf("rentable" to 1L) + updated(nowMillis))))
    }

    fun disableRental(assetId: String, nowMillis: Long): V2NewCommand =
        MaintAssetsDisableRental(assetId).toNewCommand(
            overlay = V2OverlayPatch.of(V2RowOp.upsert("assets", assetId, mapOf("rentable" to 0L) + updated(nowMillis))),
        )

    // ---------------------------------------------------------------------------- delivery tasks

    private fun task(id: String, nowMillis: Long, vararg set: Pair<String, Any?>) =
        V2OverlayPatch.of(V2RowOp.upsert("rental_delivery_tasks", id, mapOf(*set) + updated(nowMillis)))

    fun assignTask(task: RentalDeliveryTask, driverUserId: String?, nowMillis: Long): V2NewCommand = if (driverUserId == null) {
        RentalRentalDeliveryTasksUnassign(task.id).toNewCommand(
            baseVersion = task.version,
            overlay = task(task.id, nowMillis, "driver_user_id" to null, "status" to "planned"),
        )
    } else {
        RentalRentalDeliveryTasksAssign(task.id, driverUserId).toNewCommand(
            baseVersion = task.version,
            overlay = task(task.id, nowMillis, "driver_user_id" to driverUserId, "status" to "assigned"),
        )
    }

    fun startTask(id: String, at: Instant, lat: BigDecimal?, lng: BigDecimal?, nowMillis: Long): V2NewCommand =
        RentalRentalDeliveryTasksStart(id, at, lat, lng).toNewCommand(
            overlay = task(id, nowMillis, "status" to "en_route", "started_at" to at.toEpochMilli()),
        )

    fun arriveTask(id: String, at: Instant, lat: BigDecimal?, lng: BigDecimal?, nowMillis: Long): V2NewCommand =
        RentalRentalDeliveryTasksArrive(id, at, lat, lng).toNewCommand(
            overlay = task(id, nowMillis, "status" to "arrived", "arrived_at" to at.toEpochMilli()),
        )

    fun completeTask(
        id: String,
        at: Instant,
        photos: List<V2StagedFile>,
        signature: V2StagedFile?,
        signedName: String?,
        notes: String?,
        lat: BigDecimal?,
        lng: BigDecimal?,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(signature == null || !signedName.isNullOrBlank()) { "a signature needs the signer's name" }
        val files = photos + listOfNotNull(signature)
        val ops = files.map { MaintCommands.pendingFileRow(it, nowMillis, userId) } + V2RowOp.upsert(
            "rental_delivery_tasks", id,
            mapOf(
                "status" to "completed", "completed_at" to at.toEpochMilli(), "proof_signed_name" to signedName.clean(),
                "proof_notes" to notes.clean(), "proof_lat" to lat?.toPlainString(), "proof_lng" to lng?.toPlainString(),
            ) + updated(nowMillis),
        )
        return RentalRentalDeliveryTasksComplete(
            id = id, at = at, photoFileObjectIds = photos.map { it.fileId }.ifEmpty { null },
            signatureFileObjectId = signature?.fileId, signedName = signedName.clean(), notes = notes.clean(), lat = lat, lng = lng,
        ).toNewCommand(overlay = V2OverlayPatch(ops), uploads = files.map { it.toUpload() })
    }

    fun failTask(id: String, reason: String, at: Instant, notes: String?, lat: BigDecimal?, lng: BigDecimal?, nowMillis: Long): V2NewCommand {
        val why = requireNotNull(reason.clean()) { "a failed stop needs a reason" }
        return RentalRentalDeliveryTasksFail(id = id, reason = why, at = at, notes = notes.clean(), lat = lat, lng = lng).toNewCommand(
            overlay = task(id, nowMillis, "status" to "failed", "failed_reason" to why),
        )
    }

    fun cancelTask(id: String, reason: String?, nowMillis: Long): V2NewCommand =
        RentalRentalDeliveryTasksCancel(id, reason.clean()).toNewCommand(overlay = task(id, nowMillis, "status" to "cancelled"))

    fun reopenTask(task: RentalDeliveryTask, reason: String?, nowMillis: Long): V2NewCommand =
        RentalRentalDeliveryTasksReopen(task.id, reason.clean()).toNewCommand(
            overlay = task(task.id, nowMillis, "status" to if (task.driverUserId != null) "assigned" else "planned", "failed_reason" to null),
        )

    // --------------------------------------------------------------------------------- transfers

    fun createTransfer(
        id: String,
        assetId: String,
        fromLocationId: String?,
        toLocationId: String,
        arriveBy: Instant,
        reason: String?,
        orderId: String?,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(toLocationId != fromLocationId) { "the unit is already at that branch" }
        require(arriveBy.toEpochMilli() > nowMillis) { "arrive-by must be in the future" }
        val payload = RentalRentalTransfersCreate(
            id = id, assetId = assetId, toLocationId = toLocationId, shipAt = Instant.ofEpochMilli(nowMillis), arriveBy = arriveBy,
            reason = reason.clean(), orderId = orderId,
        )
        return payload.toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    "rental_transfers", id,
                    mapOf(
                        "asset_id" to assetId, "from_location_id" to fromLocationId, "to_location_id" to toLocationId,
                        "status" to "planned", "planned_during_start" to nowMillis, "planned_during_end" to arriveBy.toEpochMilli(),
                        "reason" to payload.reason, "order_id" to orderId,
                    ) + created(nowMillis, userId),
                ),
            ),
        )
    }

    fun shipTransfer(t: RentalTransfer, at: Instant, nowMillis: Long): V2NewCommand {
        require(t.status == "planned") { "only a planned transfer ships" }
        return RentalRentalTransfersShip(t.id, at).toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert("rental_transfers", t.id, mapOf("status" to "in_transit", "shipped_at" to at.toEpochMilli()) + updated(nowMillis)),
            ),
        )
    }

    fun receiveTransfer(t: RentalTransfer, at: Instant, nowMillis: Long): V2NewCommand {
        require(t.status == "planned" || t.status == "in_transit") { "the transfer is ${t.status}" }
        return RentalRentalTransfersReceive(t.id, at).toNewCommand(
            overlay = V2OverlayPatch(
                listOf(
                    V2RowOp.upsert("rental_transfers", t.id, mapOf("status" to "received", "received_at" to at.toEpochMilli()) + updated(nowMillis)),
                    V2RowOp.upsert("assets", t.assetId, mapOf("location_id" to t.toLocationId) + updated(nowMillis)),
                ),
            ),
        )
    }

    fun cancelTransfer(t: RentalTransfer, reason: String?, nowMillis: Long): V2NewCommand {
        require(t.status == "planned") { "only a planned transfer can be cancelled" }
        return RentalRentalTransfersCancel(t.id, reason.clean()).toNewCommand(
            overlay = V2OverlayPatch.of(V2RowOp.upsert("rental_transfers", t.id, mapOf("status" to "cancelled") + updated(nowMillis))),
        )
    }

    private fun JsonObject.text(key: String): String? = when (val v = this[key]) {
        null, is JsonNull -> null
        is JsonPrimitive -> v.contentOrNull
        else -> null
    }
}
