package com.avago.core.data.v2.commands

import com.avago.core.data.domain.GoodsReceiptDraft
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.ProductSupplier
import com.avago.core.data.domain.ProductSupplierDraft
import com.avago.core.data.domain.PurchaseOrder
import com.avago.core.data.domain.PurchaseOrderEdit
import com.avago.core.data.domain.PurchaseOrderLine
import com.avago.core.data.domain.PurchaseOrderLineDraft
import com.avago.core.data.domain.PurchaseOrderStatus
import com.avago.core.data.domain.Supplier
import com.avago.core.data.domain.SupplierDraft
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2Money
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Builders for vendor, vendor-catalog, purchase-order and goods-receipt commands. */
object PurchasingCommands {

    // ------------------------------------------------------------------ vendors

    /** [pointIds]: client ids for the email/phone/website contact points shown optimistically. */
    fun createSupplier(
        id: String,
        draft: SupplierDraft,
        pointIds: () -> String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val name = requireNotNull(draft.name.clean()) { "a vendor needs a name" }
        val payload = PlacesPartiesVendorsCreate(
            id = id,
            displayName = name,
            supplierCode = draft.supplierCode.clean(),
            accountNumber = draft.accountNumber.clean(),
            paymentTerms = draft.paymentTerms.clean(),
            currencyCode = draft.currency.clean(),
            leadTimeDays = draft.leadTimeDays,
            isPreferred = draft.isPreferred,
            rating = draft.rating,
            contactName = draft.contactName.clean(),
            email = draft.email.clean(),
            phone = draft.phone.clean(),
            website = draft.website.clean(),
            taxId = draft.taxId.clean(),
            address = draft.address.clean(),
            notes = draft.notes.clean(),
        )
        val ops = mutableListOf(
            V2RowOp.upsert(
                "parties", id,
                mapOf(
                    "kind" to "organization", "display_name" to name, "organization_name" to name,
                    "is_account_self" to 0L, "origin" to "manual", "needs_review" to 0L, "notes" to payload.notes,
                ) + created(nowMillis, userId),
            ),
            V2RowOp.upsert(
                "supplier_profiles", mapOf("party_id" to id),
                mapOf(
                    "supplier_code" to payload.supplierCode, "account_number" to payload.accountNumber,
                    "payment_terms" to payload.paymentTerms, "currency_code" to payload.currencyCode,
                    "lead_time_days" to payload.leadTimeDays?.toLong(), "is_preferred" to if (draft.isPreferred) 1L else 0L,
                    "rating" to payload.rating?.toLong(),
                ) + created(nowMillis, userId),
            ),
        )
        payload.contactName?.let { contact ->
            ops += V2RowOp.upsert(
                "party_contacts", pointIds(),
                mapOf("party_id" to id, "name" to contact, "is_primary" to 1L) + created(nowMillis, userId),
            )
        }
        listOf("email" to payload.email, "phone" to payload.phone, "website" to payload.website).forEach { (kind, value) ->
            if (value != null) {
                ops += V2RowOp.upsert(
                    "party_contact_points", pointIds(),
                    mapOf("party_id" to id, "kind" to kind, "value" to value, "is_primary" to 1L) + created(nowMillis, userId),
                )
            }
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /**
     * Null when nothing changed. [pointIdsByKind] are the vendor's current primary contact-point
     * ids (email/phone/website) so the overlay edits them in place.
     */
    fun updateSupplier(
        stored: Supplier,
        draft: SupplierDraft,
        pointIdsByKind: Map<String, String>,
        newId: () -> String,
        nowMillis: Long,
    ): V2NewCommand? {
        val d = V2UpdateDiff(PlacesPartiesVendorsUpdate.Clear.entries)
        val name = d.text("display_name", stored.name, draft.name)
        val code = d.text("supplier_code", stored.supplierCode, draft.supplierCode)
        val account = d.text("account_number", stored.accountNumber, draft.accountNumber)
        val terms = d.text("payment_terms", stored.paymentTerms, draft.paymentTerms)
        val currency = d.text("currency_code", stored.currency, draft.currency)
        val lead = d.field("lead_time_days", stored.leadTimeDays, draft.leadTimeDays)
        val preferred = d.field("is_preferred", stored.isPreferred, draft.isPreferred)
        val rating = d.field("rating", stored.rating, draft.rating)
        val email = d.text("email", stored.email, draft.email)
        val phone = d.text("phone", stored.phone, draft.phone)
        val website = d.text("website", stored.website, draft.website)
        // Not readable on mobile: only an entered value is sent (it cannot be compared or cleared).
        val taxId = draft.taxId.clean()
        val address = d.text("address", stored.address, draft.address)
        val notes = d.text("notes", stored.notes, draft.notes)
        d.check()
        if (d.isEmpty && taxId == null) return null
        val payload = PlacesPartiesVendorsUpdate(
            id = stored.partyId, displayName = name, supplierCode = code, accountNumber = account,
            paymentTerms = terms, currencyCode = currency, leadTimeDays = lead, isPreferred = preferred, rating = rating,
            email = email, phone = phone, website = website, taxId = taxId, address = address, notes = notes,
            clear = d.clear,
        )
        val cleared = d.clear.map { it.wire }.toSet()
        fun <T> col(value: T?, wire: String): Pair<Boolean, T?> = when {
            value != null -> true to value
            wire in cleared -> true to null
            else -> false to null
        }
        val party = buildMap<String, Any?> {
            name?.let { put("display_name", it); put("organization_name", it) }
            col(notes, "notes").let { (changed, v) -> if (changed) put("notes", v) }
        }
        val profile = buildMap<String, Any?> {
            mapOf(
                "supplier_code" to col(code, "supplier_code"), "account_number" to col(account, "account_number"),
                "payment_terms" to col(terms, "payment_terms"), "currency_code" to col(currency, "currency_code"),
                "lead_time_days" to col(lead?.toLong(), "lead_time_days"), "rating" to col(rating?.toLong(), "rating"),
            ).forEach { (column, change) -> if (change.first) put(column, change.second) }
            preferred?.let { put("is_preferred", if (it) 1L else 0L) }
        }
        val ops = mutableListOf<V2RowOp>()
        if (party.isNotEmpty()) ops += V2RowOp.upsert("parties", stored.partyId, party + updated(nowMillis))
        if (profile.isNotEmpty()) {
            ops += V2RowOp.upsert("supplier_profiles", mapOf("party_id" to stored.partyId), profile + updated(nowMillis))
        }
        listOf("email" to col(email, "email"), "phone" to col(phone, "phone"), "website" to col(website, "website"))
            .forEach { (kind, change) ->
                if (!change.first) return@forEach
                val existing = pointIdsByKind[kind]
                val value = change.second
                ops += when {
                    value == null && existing != null -> V2RowOp.delete("party_contact_points", existing)
                    value == null -> return@forEach
                    existing != null -> V2RowOp.upsert("party_contact_points", existing, mapOf("value" to value) + updated(nowMillis))
                    else -> V2RowOp.upsert(
                        "party_contact_points", newId(),
                        mapOf("party_id" to stored.partyId, "kind" to kind, "value" to value, "is_primary" to 1L) +
                            created(nowMillis, null),
                    )
                }
            }
        return payload.toNewCommand(baseVersion = stored.version, overlay = V2OverlayPatch(ops))
    }

    /** The vendor's primary contact person as stored ([id] + row [version]). */
    data class ContactRef(val id: String, val name: String, val version: Long)

    /**
     * Sets the vendor's primary contact name (the vendor update has no contact field): updates,
     * creates or deletes the primary `party_contacts` row. Null when unchanged.
     */
    fun primaryContact(
        partyId: String,
        existing: ContactRef?,
        name: String?,
        newId: () -> String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand? {
        val wanted = name.clean()
        return when {
            wanted == existing?.name -> null
            existing == null -> {
                val id = newId()
                PlacesPartiesPartyContactsCreate(id = id, partyId = partyId, name = wanted!!, isPrimary = true).toNewCommand(
                    overlay = V2OverlayPatch(
                        listOf(
                            V2RowOp.upsert(
                                "party_contacts", id,
                                mapOf("party_id" to partyId, "name" to wanted, "is_primary" to 1L) + created(nowMillis, userId),
                            ),
                        ),
                    ),
                )
            }
            wanted == null -> PlacesPartiesPartyContactsDelete(existing.id).toNewCommand(
                baseVersion = existing.version,
                overlay = V2OverlayPatch(listOf(V2RowOp.delete("party_contacts", existing.id))),
            )
            else -> PlacesPartiesPartyContactsUpdate(id = existing.id, name = wanted).toNewCommand(
                baseVersion = existing.version,
                overlay = V2OverlayPatch(
                    listOf(V2RowOp.upsert("party_contacts", existing.id, mapOf("name" to wanted) + updated(nowMillis))),
                ),
            )
        }
    }

    fun setSupplierActive(stored: Supplier, active: Boolean, nowMillis: Long): V2NewCommand =
        PlacesPartiesVendorsUpdate(id = stored.partyId, active = active).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(
                listOf(
                    V2RowOp.upsert(
                        "supplier_profiles", mapOf("party_id" to stored.partyId),
                        mapOf("archived_at" to if (active) null else nowMillis) + updated(nowMillis),
                    ),
                ),
            ),
        )

    fun deleteSupplier(stored: Supplier): V2NewCommand =
        PlacesPartiesVendorsDelete(id = stored.partyId).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(
                listOf(V2RowOp.delete("supplier_profiles", mapOf("party_id" to stored.partyId)), V2RowOp.delete("parties", stored.partyId)),
            ),
        )

    // ------------------------------------------------------------------ vendor catalog

    fun createProductSupplier(id: String, draft: ProductSupplierDraft, nowMillis: Long, userId: String?): V2NewCommand {
        val payload = StockProductSuppliersCreate(
            id = id,
            productId = draft.productId,
            supplierPartyId = draft.supplierPartyId,
            supplierSku = draft.supplierSku.clean(),
            supplierDescription = draft.description.clean(),
            unitCost = draft.unitCost,
            currency = draft.currency,
            minOrderQty = draft.minOrderQty,
            packSizeQty = draft.packSizeQty,
            leadTimeDays = draft.leadTimeDays,
            isPreferred = draft.isPreferred,
            notes = draft.notes.clean(),
        )
        val row = mapOf(
            "product_id" to draft.productId, "supplier_party_id" to draft.supplierPartyId,
            "supplier_sku" to payload.supplierSku, "supplier_description" to payload.supplierDescription,
            "unit_cost" to draft.unitCost.toPlainString(), "currency" to draft.currency,
            "min_order_qty_micros" to draft.minOrderQty?.let(V2Micros::fromDecimal),
            "pack_size_qty_micros" to draft.packSizeQty?.let(V2Micros::fromDecimal),
            "lead_time_days" to draft.leadTimeDays?.toLong(), "is_preferred" to if (draft.isPreferred) 1L else 0L,
            "notes" to payload.notes,
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch(listOf(V2RowOp.upsert("product_suppliers", id, row))))
    }

    fun updateProductSupplier(stored: ProductSupplier, draft: ProductSupplierDraft, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(StockProductSuppliersUpdate.Clear.entries)
        val sku = d.text("supplier_sku", stored.supplierSku, draft.supplierSku)
        val description = d.text("supplier_description", stored.description, draft.description)
        val cost = d.decimal("unit_cost", stored.unitCost, draft.unitCost)
        val currency = d.text("currency", stored.currency, draft.currency)
        val moq = d.decimal("min_order_qty", stored.minOrderQty, draft.minOrderQty, "min_order_qty_micros", V2Micros::fromDecimal)
        val pack = d.decimal("pack_size_qty", stored.packSizeQty, draft.packSizeQty, "pack_size_qty_micros", V2Micros::fromDecimal)
        val lead = d.field("lead_time_days", stored.leadTimeDays, draft.leadTimeDays) { it.toLong() }
        val preferred = d.field("is_preferred", stored.isPreferred, draft.isPreferred) { if (it) 1L else 0L }
        val notes = d.text("notes", stored.notes, draft.notes)
        d.check()
        if (d.isEmpty) return null
        return StockProductSuppliersUpdate(
            id = stored.id, supplierSku = sku, supplierDescription = description, unitCost = cost, currency = currency,
            minOrderQty = moq, packSizeQty = pack, leadTimeDays = lead, isPreferred = preferred, notes = notes,
            clear = d.clear,
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.upsert("product_suppliers", stored.id, d.overlay + updated(nowMillis)))),
        )
    }

    fun deleteProductSupplier(stored: ProductSupplier): V2NewCommand =
        StockProductSuppliersDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.delete("product_suppliers", stored.id))),
        )

    // ------------------------------------------------------------------ purchase orders

    fun createPurchaseOrder(
        id: String,
        draft: com.avago.core.data.domain.PurchaseOrderDraft,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val payload = StockPurchaseOrdersCreate(
            id = id,
            supplierPartyId = draft.supplierPartyId,
            currency = draft.currency,
            shipToLocationId = draft.shipToLocationId,
            workOrderId = draft.workOrderId,
            assetId = draft.assetId,
            expectedOn = draft.expectedOn.clean()?.let(LocalDate::parse),
            notes = draft.notes.clean(),
        )
        val row = mapOf(
            "supplier_party_id" to draft.supplierPartyId, "status" to PurchaseOrderStatus.DRAFT.wire,
            "currency" to draft.currency, "subtotal_minor" to 0L, "tax_total_minor" to 0L, "shipping_minor" to 0L,
            "discount_minor" to 0L, "grand_total_minor" to 0L, "expected_on" to payload.expectedOn?.toString(),
            "ship_to_location_id" to draft.shipToLocationId, "work_order_id" to draft.workOrderId,
            "asset_id" to draft.assetId, "requested_by" to userId, "notes" to payload.notes,
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch(listOf(V2RowOp.upsert("purchase_orders", id, row))))
    }

    fun addLine(
        id: String,
        purchaseOrderId: String,
        lineNo: Int,
        currency: String,
        line: PurchaseOrderLineDraft,
        dependsOn: List<String>,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val description = requireNotNull(line.description.clean()) { "a line needs a description" }
        require(line.orderedQty.signum() > 0) { "quantity must be positive" }
        require(line.unitCost.signum() >= 0) { "unit cost cannot be negative" }
        val payload = StockPurchaseOrderLinesCreate(
            id = id,
            purchaseOrderId = purchaseOrderId,
            productId = line.productId,
            description = description,
            supplierSku = line.supplierSku.clean(),
            orderedQty = line.orderedQty,
            unitCost = line.unitCost,
            taxRatePct = line.taxRatePct,
            deliverToLocationId = line.deliverToLocationId,
            deliverToBinId = line.deliverToBinId,
            workOrderId = line.workOrderId,
            notes = line.notes.clean(),
        )
        val amounts = lineAmounts(line.orderedQty, line.unitCost, line.taxRatePct, currency)
        val ops = mutableListOf(
            V2RowOp.upsert(
                "purchase_order_lines", id,
                mapOf(
                    "purchase_order_id" to purchaseOrderId, "line_no" to lineNo.toLong(), "product_id" to line.productId,
                    "description" to description, "supplier_sku" to payload.supplierSku,
                    "ordered_qty_micros" to V2Micros.fromDecimal(line.orderedQty), "received_qty_micros" to 0L,
                    "unit_cost" to line.unitCost.toPlainString(), "currency" to currency,
                    "tax_rate_pct" to line.taxRatePct?.toPlainString(), "tax_minor" to amounts.taxMinor,
                    "line_total_minor" to amounts.totalMinor, "deliver_to_location_id" to line.deliverToLocationId,
                    "deliver_to_bin_id" to line.deliverToBinId, "work_order_id" to line.workOrderId, "notes" to payload.notes,
                ) + created(nowMillis, userId),
            ),
        )
        ops += totalsOp(purchaseOrderId, amounts, nowMillis)
        return payload.toNewCommand(overlay = V2OverlayPatch(ops), dependsOn = dependsOn)
    }

    fun updateLine(order: PurchaseOrder, stored: PurchaseOrderLine, line: PurchaseOrderLineDraft, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(StockPurchaseOrderLinesUpdate.Clear.entries)
        val description = d.text("description", stored.description, line.description)
        val qty = d.decimal("ordered_qty", stored.orderedQty, line.orderedQty, "ordered_qty_micros", V2Micros::fromDecimal)
        val cost = d.decimal("unit_cost", stored.unitCost, line.unitCost)
        val tax = d.decimal("tax_rate_pct", stored.taxRatePct, line.taxRatePct)
        val wo = d.text("work_order_id", stored.workOrderId, line.workOrderId)
        val notes = d.text("notes", stored.notes, line.notes)
        d.check()
        if (d.isEmpty) return null
        val before = lineAmounts(stored.orderedQty, stored.unitCost, stored.taxRatePct, stored.currency)
        val after = lineAmounts(line.orderedQty, line.unitCost, line.taxRatePct, stored.currency)
        val ops = mutableListOf(
            V2RowOp.upsert(
                "purchase_order_lines", stored.id,
                d.overlay + mapOf("tax_minor" to after.taxMinor, "line_total_minor" to after.totalMinor) + updated(nowMillis),
            ),
        )
        ops += totalsOp(order.id, after - before, nowMillis)
        return StockPurchaseOrderLinesUpdate(
            id = stored.id, description = description, orderedQty = qty, unitCost = cost, taxRatePct = tax,
            workOrderId = wo, notes = notes, clear = d.clear,
        ).toNewCommand(baseVersion = stored.version, overlay = V2OverlayPatch(ops))
    }

    fun deleteLine(order: PurchaseOrder, stored: PurchaseOrderLine, nowMillis: Long): V2NewCommand {
        val amounts = lineAmounts(stored.orderedQty, stored.unitCost, stored.taxRatePct, stored.currency)
        return StockPurchaseOrderLinesDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(
                listOf(V2RowOp.delete("purchase_order_lines", stored.id), totalsOp(order.id, -amounts, nowMillis)),
            ),
        )
    }

    fun updatePurchaseOrder(stored: PurchaseOrder, edit: PurchaseOrderEdit, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(StockPurchaseOrdersUpdate.Clear.entries)
        val supplier = d.text("supplier_party_id", stored.supplierPartyId, edit.supplierPartyId)
        val currency = d.text("currency", stored.currency, edit.currency)
        if ((supplier != null || currency != null) && !stored.isEditable) {
            throw IllegalArgumentException("The vendor and currency can only change while the order is a draft")
        }
        val shipTo = d.text("ship_to_location_id", stored.shipToLocationId, edit.shipToLocationId)
        val wo = d.text("work_order_id", stored.workOrderId, edit.workOrderId)
        val asset = d.text("asset_id", stored.assetId, edit.assetId)
        val expected = d.text("expected_on", stored.expectedOn, edit.expectedOn)
        val notes = d.text("notes", stored.notes, edit.notes)
        val internal = d.text("internal_notes", stored.internalNotes, edit.internalNotes)
        val invoice = d.text("supplier_invoice_ref", stored.supplierInvoiceRef, edit.supplierInvoiceRef)
        d.check()
        if (d.isEmpty) return null
        return StockPurchaseOrdersUpdate(
            id = stored.id, supplierPartyId = supplier, currency = currency, shipToLocationId = shipTo, workOrderId = wo,
            assetId = asset, expectedOn = expected?.let(LocalDate::parse), notes = notes, internalNotes = internal,
            supplierInvoiceRef = invoice, clear = d.clear,
        ).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.upsert("purchase_orders", stored.id, d.overlay + updated(nowMillis)))),
        )
    }

    enum class Transition { SUBMIT, APPROVE, REJECT, MARK_ORDERED, CANCEL, CLOSE }

    fun transition(stored: PurchaseOrder, transition: Transition, reason: String?, nowMillis: Long, userId: String?): V2NewCommand {
        val (payload, set) = when (transition) {
            Transition.SUBMIT -> {
                require(stored.canSubmit) { "Only a draft with lines can be submitted" }
                StockPurchaseOrdersSubmit(stored.id) to mapOf("status" to PurchaseOrderStatus.PENDING_APPROVAL.wire)
            }
            Transition.APPROVE -> {
                require(stored.canApprove) { "Only an order pending approval can be approved" }
                StockPurchaseOrdersApprove(stored.id) to
                    mapOf("status" to PurchaseOrderStatus.APPROVED.wire, "approved_by" to userId, "approved_at" to nowMillis)
            }
            Transition.REJECT -> {
                require(stored.canApprove) { "Only an order pending approval can be rejected" }
                StockPurchaseOrdersReject(stored.id, reason.clean()) to mapOf("status" to PurchaseOrderStatus.DRAFT.wire)
            }
            Transition.MARK_ORDERED -> {
                require(stored.canMarkOrdered) { "Only an approved order can be marked as ordered" }
                StockPurchaseOrdersMarkOrdered(stored.id) to
                    mapOf("status" to PurchaseOrderStatus.ORDERED.wire, "ordered_at" to nowMillis)
            }
            Transition.CANCEL -> {
                require(stored.canCancel) { "Only an open order with nothing received can be cancelled" }
                StockPurchaseOrdersCancel(stored.id, reason.clean()) to mapOf("status" to PurchaseOrderStatus.CANCELLED.wire)
            }
            Transition.CLOSE -> {
                require(stored.canClose) { "Only a (partially) received order can be closed" }
                StockPurchaseOrdersClose(stored.id) to
                    mapOf("status" to PurchaseOrderStatus.CLOSED.wire, "closed_at" to nowMillis)
            }
        }
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch(listOf(V2RowOp.upsert("purchase_orders", stored.id, set + updated(nowMillis)))),
        )
    }

    // ------------------------------------------------------------------ receiving

    /**
     * A posted receipt of PO lines. [positionFor] finds the receiving position (good condition) so
     * on-hand rises at once; [order] supplies line products and received quantities.
     */
    fun receive(
        id: String,
        lineIds: List<String>,
        draft: GoodsReceiptDraft,
        order: PurchaseOrder,
        receivingLocationId: String,
        positionFor: (productId: String, locationId: String, binId: String?) -> KnownPosition?,
        newId: () -> String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(order.canReceive) { "This order cannot be received in its current status" }
        require(draft.lines.isNotEmpty() && draft.lines.all { it.quantity.signum() > 0 }) {
            "Enter a positive quantity for at least one line"
        }
        val orderLines = order.lines.associateBy { it.id }
        val payload = StockGoodsReceiptsCreate(
            id = id,
            purchaseOrderId = order.id,
            locationId = draft.locationId,
            carrier = draft.carrier.clean(),
            trackingNumber = draft.trackingNumber.clean(),
            packingSlipNo = draft.packingSlipNo.clean(),
            notes = draft.notes.clean(),
            lines = draft.lines.mapIndexed { i, l ->
                ReceiptLine(
                    id = lineIds[i], purchaseOrderLineId = l.purchaseOrderLineId, quantity = l.quantity,
                    locationId = l.locationId, binId = l.binId, discrepancyQty = l.discrepancyQty,
                    discrepancyReason = l.discrepancyReason.clean(), notes = l.notes.clean(),
                )
            },
        )
        val ops = mutableListOf(
            V2RowOp.upsert(
                "stock_movements", id,
                mapOf(
                    "kind" to MovementKind.RECEIPT.wire, "status" to "posted", "location_id" to receivingLocationId,
                    "purchase_order_id" to order.id, "carrier" to payload.carrier, "tracking_number" to payload.trackingNumber,
                    "packing_slip_no" to payload.packingSlipNo, "notes" to payload.notes, "blind_count" to 0L,
                    "effective_at" to nowMillis, "posted_at" to nowMillis, "posted_by" to userId,
                ) + created(nowMillis, userId),
            ),
        )
        var received = BigDecimal.ZERO
        draft.lines.forEachIndexed { i, l ->
            val poLine = requireNotNull(orderLines[l.purchaseOrderLineId]) { "line ${l.purchaseOrderLineId} is not on this order" }
            val location = l.locationId ?: poLine.deliverToLocationId ?: receivingLocationId
            val bin = l.binId ?: poLine.deliverToBinId.takeIf { l.locationId == null }
            ops += V2RowOp.upsert(
                "stock_movement_lines", lineIds[i],
                mapOf(
                    "movement_id" to id, "line_no" to (i + 1).toLong(), "product_id" to poLine.productId,
                    "location_id" to location, "bin_id" to bin, "condition_code" to "good",
                    "quantity_micros" to V2Micros.fromDecimal(l.quantity), "recount_required" to 0L,
                    "purchase_order_id" to order.id, "purchase_order_line_id" to poLine.id,
                    "discrepancy_qty_micros" to l.discrepancyQty?.let(V2Micros::fromDecimal),
                    "discrepancy_reason" to l.discrepancyReason.clean(), "unit_cost" to poLine.unitCost.toPlainString(),
                    "cost_currency" to poLine.currency, "notes" to l.notes.clean(),
                ) + created(nowMillis, userId),
            )
            ops += V2RowOp.upsert(
                "purchase_order_lines", mapOf("id" to poLine.id), updated(nowMillis),
                mapOf("received_qty_micros" to V2Micros.fromDecimal(l.quantity)),
            )
            received += l.quantity
            val productId = poLine.productId ?: return@forEachIndexed
            val delta = V2Micros.fromDecimal(l.quantity)
            val known = positionFor(productId, location, bin)
            ops += if (known != null) {
                V2RowOp.upsert("stock_positions", mapOf("id" to known.id), emptyMap(), mapOf("on_hand_qty_micros" to delta))
            } else {
                V2RowOp.upsert(
                    "stock_positions",
                    mapOf(
                        "product_id" to productId, "location_id" to location, "bin_id" to bin, "lot_id" to null,
                        "stock_unit_id" to null, "condition_code" to "good",
                    ),
                    mapOf("id" to newId(), "version" to 1L, "created_at" to nowMillis, "updated_at" to nowMillis),
                    mapOf("on_hand_qty_micros" to delta),
                )
            }
        }
        val fullyReceived = order.lines.all { line ->
            val now = draft.lines.filter { it.purchaseOrderLineId == line.id }.fold(BigDecimal.ZERO) { a, l -> a + l.quantity }
            line.receivedQty + now >= line.orderedQty
        }
        val status = if (fullyReceived) PurchaseOrderStatus.RECEIVED else PurchaseOrderStatus.PARTIALLY_RECEIVED
        ops += V2RowOp.upsert("purchase_orders", order.id, mapOf("status" to status.wire) + updated(nowMillis))
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    // ------------------------------------------------------------------ totals

    /** Line tax and total (incl. tax) in minor units. */
    data class LineAmounts(val taxMinor: Long, val subtotalMinor: Long) {
        val totalMinor: Long get() = subtotalMinor + taxMinor

        operator fun minus(other: LineAmounts) = LineAmounts(taxMinor - other.taxMinor, subtotalMinor - other.subtotalMinor)

        operator fun unaryMinus() = LineAmounts(-taxMinor, -subtotalMinor)
    }


    fun lineAmounts(qty: BigDecimal, unitCost: BigDecimal, taxPct: BigDecimal?, currency: String): LineAmounts {
        val subtotal = (qty * unitCost).setScale(V2Money.fractionDigits(currency), RoundingMode.HALF_EVEN)
        val tax = (subtotal * (taxPct ?: BigDecimal.ZERO)).divide(BigDecimal(100), V2Money.fractionDigits(currency), RoundingMode.HALF_EVEN)
        return LineAmounts(V2Money.toMinor(tax, currency), V2Money.toMinor(subtotal, currency))
    }

    private fun totalsOp(orderId: String, delta: LineAmounts, nowMillis: Long): V2RowOp =
        V2RowOp.upsert(
            "purchase_orders", mapOf("id" to orderId), updated(nowMillis),
            mapOf(
                "subtotal_minor" to delta.subtotalMinor, "tax_total_minor" to delta.taxMinor,
                "grand_total_minor" to delta.totalMinor,
            ),
        )
}