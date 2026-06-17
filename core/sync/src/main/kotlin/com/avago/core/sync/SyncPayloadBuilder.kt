package com.avago.core.sync

import com.avago.core.data.DatabaseFactory
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPayloadBuilder @Inject constructor(
    private val dbFactory: DatabaseFactory,
) {

    /**
     * Reads the entity row from Room and converts it to a [JsonObject] ready for the push payload.
     * Returns null if the entity is not found or the entity type is unsupported.
     */
    suspend fun buildPayload(
        accountId: String,
        entityType: String,
        entityId: String,
    ): JsonObject? {
        val db = dbFactory.get(accountId)
        return try {
            when (entityType) {
                "asset" -> {
                    val e = db.assetDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("asset_id", e.assetId)
                        put("account_id", e.accountId)
                        put("name", e.name)
                        e.make?.let { put("make", it) }
                        e.model?.let { put("model", it) }
                        e.year?.let { put("year", it) }
                        e.assetType?.let { put("asset_type", it) }
                        e.meterType?.let { put("meter_type", it) }
                        e.avatarColor?.let { put("avatar_color", it) }
                        e.avatarInitial?.let { put("avatar_initial", it) }
                        e.addressLine1?.let { put("address_line1", it) }
                        e.addressLine2?.let { put("address_line2", it) }
                        e.city?.let { put("city", it) }
                        e.state?.let { put("state", it) }
                        e.postalCode?.let { put("postal_code", it) }
                        e.country?.let { put("country", it) }
                        e.locationId?.let { put("location_id", it) }
                        e.attributes?.let { put("attributes", it) }
                        put("is_fre_sample", e.isFreSample)
                        e.parentAssetId?.let { put("parent_asset_id", it) }
                        put("is_rental", e.isRental)
                        e.rentalRate?.let { put("rental_rate", it) }
                        e.rentalRateUnit?.let { put("rental_rate_unit", it) }
                        e.purchasePrice?.let { put("purchase_price", it) }
                        e.salvageValue?.let { put("salvage_value", it) }
                        e.usefulLifeMonths?.let { put("useful_life_months", it) }
                        e.depreciationMethod?.let { put("depreciation_method", it) }
                        e.placedInServiceDate?.let { put("placed_in_service_date", msToDateOnly(it)) }
                        // Change 10/31/61
                        e.currentStatus?.let { put("current_status", it) }
                        e.downtimeType?.let { put("downtime_type", it) }
                        e.customStatusId?.let { put("custom_status_id", it) }
                        e.criticalityId?.let { put("criticality_id", it) }
                        e.globalUuid?.let { put("global_uuid", it) }
                        // Change 22
                        e.customFields?.let { put("custom_fields", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "log" -> {
                    val e = db.logDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("log_id", e.entryId)
                        put("asset_id", e.assetId)
                        put("account_id", e.accountId)
                        put("title", e.title)
                        put("log_date", msToDateOnly(e.entryDate))
                        e.odometerValue?.let { put("meter", it) }
                        e.category?.let { put("category", it) }
                        e.cost?.let { put("cost", it) }
                        e.performedBy?.let { put("performed_by", it) }
                        e.performedByUserId?.let { put("performed_by_user_id", it) }
                        e.notes?.let { put("notes", it) }
                        e.data?.let { put("data", it) }
                        e.attributes?.let { put("attributes", it) }
                        e.costMode?.let { put("cost_mode", it) }
                        e.costItems?.let { put("cost_items", it) }
                        e.costLabor?.let { put("cost_labor", it) }
                        e.costTax?.let { put("cost_tax", it) }
                        e.costMisc?.let { put("cost_misc", it) }
                        e.currency?.let { put("currency", it) }
                        e.baseAmount?.let { put("base_amount", it) }
                        e.exchangeRateUsed?.let { put("exchange_rate_used", it) }
                        e.configId?.let { put("config_id", it) }
                        e.configVersion?.let { put("config_version", it) }
                        e.serviceId?.let { put("service_id", it) }
                        e.parentId?.let { put("parent_id", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "log_cost_line" -> {
                    val e = db.logCostLineDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("line_id", e.lineId)
                        put("account_id", e.accountId)
                        put("log_id", e.logId)
                        put("kind", e.kind)
                        put("display_order", e.displayOrder)
                        e.inventoryId?.let { put("inventory_id", it) }
                        e.userId?.let { put("user_id", it) }
                        e.description?.let { put("description", it) }
                        put("quantity", e.quantity)
                        put("unit_cost", e.unitCost)
                        e.taxAmount?.let { put("tax_amount", it) }
                        e.glCode?.let { put("gl_code", it) }
                        e.notes?.let { put("notes", it) }
                        e.woId?.let { put("wo_id", it) }
                        put("cost_status", e.costStatus)
                        e.approvedBy?.let { put("approved_by", it) }
                        e.approvedAt?.let { put("approved_at", msToIso(it)) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "work_order" -> {
                    // Full-entity payload sent via POST /sync/push. The server is expected to
                    // treat absent optional fields as no-change (PATCH semantics). If concurrent
                    // server edits are a concern, switch to the typed patchWorkOrder PATCH overload
                    // on AvagoServiceClient and store only the changed fields in the sync queue.
                    val e = db.workOrderDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("wo_id", e.woId)
                        put("account_id", e.accountId)
                        e.assetId?.let { put("asset_id", it) }
                        e.locationId?.let { put("location_id", it) }
                        put("title", e.title)
                        e.description?.let { put("description", it) }
                        e.category?.let { put("category", it) }
                        e.priority?.let { put("priority", it) }
                        put("status", e.status)
                        e.requesterId?.let { put("requester_id", it) }
                        e.assignedTo?.let { put("assigned_to", it) }
                        e.dispatcherNotes?.let { put("dispatcher_notes", it) }
                        e.requiredSkills?.let { put("required_skills", it) }
                        e.estimatedEffortMinutes?.let { put("estimated_effort_minutes", it) }
                        e.actualEffortMinutes?.let { put("actual_effort_minutes", it) }
                        e.failureCode?.let { put("failure_code", it) }
                        e.completionNotes?.let { put("completion_notes", it) }
                        e.partsNeeded?.let { put("parts_needed", it) }
                        e.logId?.let { put("log_id", it) }
                        e.dueDate?.let { put("due_date", msToIso(it)) }
                        e.startedAt?.let { put("started_at", msToIso(it)) }
                        e.completedAt?.let { put("completed_at", msToIso(it)) }
                        e.laborCost?.let { put("labor_cost", it) }
                        e.partsCost?.let { put("parts_cost", it) }
                        e.totalCost?.let { put("total_cost", it) }
                        e.currency?.let { put("currency", it) }
                        e.baseAmount?.let { put("base_amount", it) }
                        e.exchangeRateUsed?.let { put("exchange_rate_used", it) }
                        e.attributes?.let { put("attributes", it) }
                        e.createdBy?.let { put("created_by", it) }
                        e.approvalState?.let { put("approval_state", it) }
                        e.jobId?.let { put("job_id", it) }
                        e.woKind?.let { put("wo_kind", it) }
                        e.rrule?.let { put("rrule", it) }
                        e.endType?.let { put("end_type", it) }
                        e.endCount?.let { put("end_count", it) }
                        e.endDate?.let { put("end_date", msToIso(it)) }
                        e.meterType?.let { put("meter_type", it) }
                        e.meterDue?.let { put("meter_due", it) }
                        e.meterInterval?.let { put("meter_interval", it) }
                        e.parentWoId?.let { put("parent_wo_id", it) }
                        e.occurrenceDate?.let { put("occurrence_date", it) }
                        e.scheduleId?.let { put("schedule_id", it) }
                        e.lastCompletedAt?.let { put("last_completed_at", msToIso(it)) }
                        e.timezone?.let { put("timezone", it) }
                        // Change 15/19/21/22/83
                        e.childWoCount?.let { put("child_wo_count", it) }
                        e.childWoIds?.let { put("child_wo_ids", it) }
                        e.procedureTemplateId?.let { put("procedure_template_id", it) }
                        e.permitStatusSummary?.let { put("permit_status_summary", it) }
                        e.customFields?.let { put("custom_fields", it) }
                        e.assignedTeamId?.let { put("assigned_team_id", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "wo_assignment" -> {
                    val e = db.woAssignmentDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("assignment_id", e.assignmentId)
                        put("wo_id", e.woId)
                        put("account_id", e.accountId)
                        put("tech_id", e.technicianId) // server reads "tech_id" — was "technician_id" (mismatch)
                        e.assignedBy?.let { put("assigned_by", it) }
                        put("assigned_at", msToIso(e.assignedAt))
                        e.unassignedAt?.let { put("unassigned_at", msToIso(it)) }
                        e.scheduledStart?.let { put("scheduled_start", msToIso(it)) }
                        e.scheduledEnd?.let { put("scheduled_end", msToIso(it)) }
                        put("status", e.status)
                        e.notes?.let { put("notes", it) }
                        // Change 8/29/64
                        e.role?.let { put("role", it) }
                        e.estimatedHours?.let { put("estimated_hours", it) }
                        e.actualHours?.let { put("actual_hours", it) }
                        e.createdById?.let { put("created_by_id", it) }
                        e.updatedById?.let { put("updated_by_id", it) }
                    }
                }

                "wo_checklist_item" -> {
                    val e = db.woChecklistItemDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("item_id", e.itemId)
                        put("wo_id", e.woId)
                        put("title", e.title)
                        put("is_completed", e.isCompleted)
                        e.completedAt?.let { put("completed_at", msToIso(it)) }
                        put("display_order", e.displayOrder)
                        // Change 19/42/64
                        e.rowType?.let { put("row_type", it) }
                        e.rowDescription?.let { put("row_description", it) }
                        e.urlsJson?.let { put("urls_json", it) }
                        e.response?.let { put("response", it) }
                        e.notes?.let { put("notes", it) }
                        e.passFail?.let { put("pass_fail", it) }
                        e.signatureUrl?.let { put("signature_url", it) }
                        e.createdById?.let { put("created_by_id", it) }
                        e.updatedById?.let { put("updated_by_id", it) }
                    }
                }

                "wo_comment" -> {
                    val e = db.woCommentDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("comment_id", e.commentId)
                        put("wo_id", e.woId)
                        put("author_id", e.authorId)
                        put("body", e.body)
                        // Change 1/5/14/64
                        e.mentionedUserIds?.let { put("mentioned_user_ids", it) }
                        put("is_internal", e.isInternal)
                        e.commentType?.let { put("comment_type", it) }
                        e.createdById?.let { put("created_by_id", it) }
                        e.updatedById?.let { put("updated_by_id", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "schedule" -> {
                    val e = db.scheduleDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("schedule_id", e.scheduleId)
                        put("asset_id", e.assetId)
                        put("account_id", e.accountId)
                        put("title", e.title)
                        e.category?.let { put("category", it) }
                        put("schedule_type", e.scheduleType)
                        e.rrule?.let { put("rrule", it) }
                        e.endType?.let { put("end_type", it) }
                        e.endCount?.let { put("end_count", it) }
                        e.endDate?.let { put("end_date", msToIso(it)) }
                        e.meterType?.let { put("meter_type", it) }
                        e.meterDue?.let { put("meter_due", it) }
                        e.meterInterval?.let { put("meter_interval", it) }
                        e.lastCompletedAt?.let { put("last_completed_at", msToIso(it)) }
                        e.nextDueAt?.let { put("next_due_at", msToIso(it)) }
                        put("is_active", e.isActive)
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "inventory" -> {
                    val e = db.inventoryDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("inventory_id", e.inventoryId)
                        put("account_id", e.accountId)
                        put("part_id", e.partId)
                        e.locationId?.let { put("location_id", it) }
                        e.binId?.let { put("bin_id", it) }
                        put("quantity_on_hand", e.quantityOnHand)
                        put("status", e.status)
                        e.lastTransactionId?.let { put("last_transaction_id", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "part" -> {
                    val e = db.partDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("part_id", e.partId)
                        put("account_id", e.accountId)
                        e.sku?.let { put("part_number", it) }
                        put("part_name", e.name)
                        e.description?.let { put("description", it) }
                        e.category?.let { put("category", it) }
                        e.unitOfMeasure?.let { put("unit_of_measure", it) }
                        e.defaultVendorId?.let { put("default_vendor_id", it) }
                        e.cost?.let { put("unit_cost", it) }
                        e.currency?.let { put("currency", it) }
                        e.manufacturer?.let { put("manufacturer", it) }
                        e.reorderQuantity?.let { put("reorder_quantity", it) }
                        e.status?.let { put("status", it) }
                        e.entityType?.let { put("entity_type", it) }
                        e.entityId?.let { put("entity_id", it) }
                        e.quantity?.let { put("quantity", it) }
                        e.gtin?.let { put("gtin", it) }
                        e.serialNumber?.let { put("serial_number", it) }
                        e.notes?.let { put("notes", it) }
                        e.baseAmount?.let { put("base_amount", it) }
                        e.exchangeRateUsed?.let { put("exchange_rate_used", it) }
                        e.attributes?.let { put("attributes", it) }
                        // Change 22/108/150
                        e.customFields?.let { put("custom_fields", it) }
                        e.abcClass?.let { put("abc_class", it) }
                        e.committedReservedQuantity?.let { put("committed_reserved_quantity", it) }
                        e.committedInProgressQuantity?.let { put("committed_in_progress_quantity", it) }
                        e.committedCompletedQuantity?.let { put("committed_completed_quantity", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "stocking_level" -> {
                    val e = db.stockingLevelDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("stocking_level_id", e.stockingLevelId)
                        put("part_id", e.partId)
                        put("location_id", e.locationId)
                        e.minQty?.let { put("min_qty", it) }
                        e.maxQty?.let { put("max_qty", it) }
                        e.reorderQty?.let { put("reorder_qty", it) }
                        e.safetyStock?.let { put("safety_stock", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                    }
                }

                "vendor" -> {
                    val e = db.vendorDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("vendor_id", e.vendorId)
                        put("account_id", e.accountId)
                        put("name", e.name)
                        e.vendorCode?.let { put("vendor_code", it) }
                        e.contactName?.let { put("contact_name", it) }
                        e.email?.let { put("email", it) }
                        e.phone?.let { put("phone", it) }
                        e.fax?.let { put("fax", it) }
                        e.website?.let { put("website", it) }
                        e.address?.let { put("address", it) }
                        e.accountNumber?.let { put("account_number", it) }
                        e.paymentTerms?.let { put("payment_terms", it) }
                        e.defaultCurrency?.let { put("default_currency", it) }
                        e.taxId?.let { put("tax_id", it) }
                        e.rating?.let { put("rating", it) }
                        put("preferred", e.preferred)
                        put("active", e.active)
                        e.qboVendorId?.let { put("qbo_vendor_id", it) }
                        e.notes?.let { put("notes", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "purchase_order" -> {
                    val e = db.purchaseOrderDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("po_id", e.poId)
                        put("account_id", e.accountId)
                        e.poNumber?.let { put("po_number", it) }
                        e.vendorId?.let { put("vendor_id", it) }
                        put("status", e.status)
                        e.currency?.let { put("currency", it) }
                        e.subtotal?.let { put("subtotal", it) }
                        e.taxTotal?.let { put("tax_total", it) }
                        e.shippingCost?.let { put("shipping_cost", it) }
                        e.discount?.let { put("discount", it) }
                        e.grandTotal?.let { put("grand_total", it) }
                        e.baseGrandTotal?.let { put("base_grand_total", it) }
                        e.exchangeRateUsed?.let { put("exchange_rate_used", it) }
                        e.expectedDelivery?.let { put("expected_delivery", it) }
                        e.shipToLocationId?.let { put("ship_to_location_id", it) }
                        e.workOrderId?.let { put("work_order_id", it) }
                        e.assetId?.let { put("asset_id", it) }
                        e.requestedBy?.let { put("requested_by", it) }
                        e.approvedBy?.let { put("approved_by", it) }
                        e.approvedAt?.let { put("approved_at", msToIso(it)) }
                        e.orderedAt?.let { put("ordered_at", msToIso(it)) }
                        e.closedAt?.let { put("closed_at", msToIso(it)) }
                        e.notes?.let { put("notes", it) }
                        e.vendorInvoiceNo?.let { put("vendor_invoice_no", it) }
                        e.createdBy?.let { put("created_by", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "po_line" -> {
                    val e = db.poLineDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("po_line_id", e.poLineId)
                        put("po_id", e.poId)
                        e.partId?.let { put("part_id", it) }
                        e.description?.let { put("description", it) }
                        put("quantity", e.quantity)
                        e.unitCost?.let { put("unit_cost", it) }
                        e.currency?.let { put("currency", it) }
                        e.glCode?.let { put("gl_code", it) }
                        e.receivedQty?.let { put("received_qty", it) }
                        put("display_order", e.displayOrder)
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                    }
                }

                "grn" -> {
                    val e = db.grnDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("grn_id", e.grnId)
                        put("account_id", e.accountId)
                        e.poId?.let { put("po_id", it) }
                        e.grnNumber?.let { put("grn_number", it) }
                        e.receivedAt?.let { put("received_at", msToIso(it)) }
                        e.receivedBy?.let { put("received_by", it) }
                        e.receivedAtLocationId?.let { put("received_at_location_id", it) }
                        e.carrier?.let { put("carrier", it) }
                        e.trackingNumber?.let { put("tracking_number", it) }
                        e.packingSlipNo?.let { put("packing_slip_no", it) }
                        e.notes?.let { put("notes", it) }
                        put("has_discrepancy", e.hasDiscrepancy)
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "grn_line" -> {
                    val e = db.grnLineDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("grn_line_id", e.grnLineId)
                        put("grn_id", e.grnId)
                        e.poLineId?.let { put("po_line_id", it) }
                        e.partId?.let { put("part_id", it) }
                        put("quantity_received", e.quantityReceived)
                        e.quantityExpected?.let { put("quantity_expected", it) }
                        e.varianceReason?.let { put("variance_reason", it) }
                        e.notes?.let { put("notes", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                    }
                }

                "cycle_count_line" -> {
                    val e = db.cycleCountLineDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("line_id", e.lineId)
                        put("cycle_count_id", e.cycleCountId)
                        put("inventory_id", e.inventoryId)
                        e.partId?.let { put("part_id", it) }
                        e.expectedQty?.let { put("expected_qty", it) }
                        e.countedQty?.let { put("counted_qty", it) }
                        e.variance?.let { put("variance", it) }
                        put("is_counted", e.isCounted)
                        e.countedAt?.let { put("counted_at", msToIso(it)) }
                        e.countedBy?.let { put("counted_by", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                    }
                }

                "part_issue" -> {
                    val e = db.partIssueDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("issue_id", e.issueId)
                        put("account_id", e.accountId)
                        e.locationId?.let { put("location_id", it) }
                        e.fromLocationId?.let { put("from_location_id", it) }
                        e.toLocationId?.let { put("to_location_id", it) }
                        put("issue_type", e.issueType)
                        put("issued_at", msToIso(e.issuedAt))
                        e.issuedBy?.let { put("issued_by", it) }
                        e.referenceId?.let { put("reference_id", it) }
                        e.referenceType?.let { put("reference_type", it) }
                        e.notes?.let { put("notes", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "part_issue_line" -> {
                    val e = db.partIssueLineDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("line_id", e.lineId)
                        put("issue_id", e.issueId)
                        put("part_id", e.partId)
                        e.inventoryId?.let { put("inventory_id", it) }
                        put("quantity", e.quantity)
                        e.unitCost?.let { put("unit_cost", it) }
                        e.notes?.let { put("notes", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                    }
                }

                "doc" -> {
                    val e = db.docDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("doc_id", e.docId)
                        e.assetId?.let { put("asset_id", it) }
                        e.entityId?.let { put("entity_id", it) }
                        e.entityType?.let { put("entity_type", it) }
                        put("account_id", e.accountId)
                        put("title", e.name)
                        e.docType?.let { put("document_type", it) }
                        e.mimeType?.let { put("mime_type", it) }
                        e.storageKey?.let { put("storage_key", it) }
                        e.downloadUrl?.let { put("download_url", it) }
                        e.fileHash?.let { put("file_hash", it) }
                        e.fileSize?.let { put("file_size", it) }
                        e.vendor?.let { put("vendor", it) }
                        e.total?.let { put("total_amount", it) }
                        e.currency?.let { put("currency", it) }
                        e.purchaseDate?.let { put("purchase_date", msToDateOnly(it)) }
                        e.warrantyEndDate?.let { put("warranty_end_date", msToDateOnly(it)) }
                        e.uploadedBy?.let { put("uploaded_by", it) }
                        e.uploadedAt?.let { put("uploaded_at", msToIso(it)) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "photo" -> {
                    val e = db.photoDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("photo_id", e.photoId)
                        put("entity_id", e.entityId)
                        put("entity_type", e.entityType)
                        put("account_id", e.accountId)
                        e.storageKey?.let { put("storage_key", it) }
                        e.downloadUrl?.let { put("download_url", it) }
                        put("sort_order", e.sortOrder)
                        put("is_primary", e.isPrimary)
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "tech_profile" -> {
                    val e = db.techProfileDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("tech_id", e.techId)
                        put("account_id", e.accountId)
                        put("user_id", e.userId)
                        e.skills?.let { put("skills", it) }
                        put("is_available", e.isAvailable)
                        e.hourlyRate?.let { put("hourly_rate", it) }
                        e.currency?.let { put("rate_currency", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "user" -> {
                    val e = db.userDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("user_id", e.userId)
                        e.accountId?.let { put("account_id", it) }
                        e.displayName?.let { put("display_name", it) }
                        e.email?.let { put("email", it) }
                        e.photoUrl?.let { put("photo_url", it) }
                        e.role?.let { put("role", it) }
                        put("is_active", e.isActive)
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                    }
                }

                "location" -> {
                    val e = db.locationDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("location_id", e.locationId)
                        put("account_id", e.accountId)
                        put("name", e.name)
                        e.shortCode?.let { put("short_code", it) }
                        e.address?.let { put("address", it) }
                        e.city?.let { put("city", it) }
                        e.state?.let { put("state", it) }
                        e.postalCode?.let { put("postal_code", it) }
                        e.country?.let { put("country", it) }
                        e.latitude?.let { put("latitude", it) }
                        e.longitude?.let { put("longitude", it) }
                        e.timezone?.let { put("timezone", it) }
                        put("is_primary", e.isPrimary)
                        put("archived", e.archived)
                        // Change 58/62
                        e.addressStreet?.let { put("address_street", it) }
                        e.parentLocationId?.let { put("parent_location_id", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "cycle_count" -> {
                    val e = db.cycleCountDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("cycle_count_id", e.cycleCountId)
                        put("account_id", e.accountId)
                        put("location_id", e.locationId)
                        put("status", e.status)
                        e.scopeType?.let { put("scope_type", it) }
                        e.scopeValue?.let { put("scope_value", it) }
                        e.startedAt?.let { put("started_at", msToIso(it)) }
                        e.lockedAt?.let { put("locked_at", msToIso(it)) }
                        e.completedAt?.let { put("completed_at", msToIso(it)) }
                        e.startedBy?.let { put("started_by", it) }
                        e.lockedBy?.let { put("locked_by", it) }
                        // Change 90
                        e.scheduledDate?.let { put("scheduled_date", it) }
                        e.completedDate?.let { put("completed_date", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "bin" -> {
                    val e = db.binDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("bin_id", e.binId)
                        put("account_id", e.accountId)
                        put("name", e.name)
                        e.locationId?.let { put("location_id", it) }
                        e.aisle?.let { put("aisle", it) }
                        e.row?.let { put("row", it) }
                        e.shelf?.let { put("shelf", it) }
                        e.barcode?.let { put("barcode", it) }
                        put("is_active", e.isActive)
                        // Change 145/147/64
                        e.capacity?.let { put("capacity", it) }
                        e.currentCount?.let { put("current_count", it) }
                        e.binType?.let { put("bin_type", it) }
                        e.createdById?.let { put("created_by_id", it) }
                        e.updatedById?.let { put("updated_by_id", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "job" -> {
                    val e = db.jobDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("job_id", e.jobId)
                        put("account_id", e.accountId)
                        e.assetId?.let { put("asset_id", it) }
                        put("title", e.title)
                        e.description?.let { put("description", it) }
                        put("status", e.status)
                        e.jobType?.let { put("job_type", it) }
                        e.priority?.let { put("priority", it) }
                        e.assignedTo?.let { put("assigned_to", it) }
                        e.dueDate?.let { put("due_date", msToIso(it)) }
                        e.startedAt?.let { put("started_at", msToIso(it)) }
                        e.completedAt?.let { put("completed_at", msToIso(it)) }
                        e.notes?.let { put("notes", it) }
                        e.attributes?.let { put("attributes", it) }
                        e.createdBy?.let { put("created_by", it) }
                        // Change 127/149
                        e.budgetAmount?.let { put("budget_amount", it) }
                        e.budgetAlertThreshold?.let { put("budget_alert_threshold", it) }
                        e.spentAmount?.let { put("spent_amount", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                // Change 86/87: item (part issue line in log) — always include part_id even if null
                "item" -> {
                    val e = db.itemDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("item_id", e.itemId)
                        put("log_id", e.logId)
                        put("account_id", e.accountId)
                        // Change 87: always send part_id (nullable) so server processes it explicitly
                        if (e.partId != null) put("part_id", e.partId) else put("part_id", "")
                        e.description?.let { put("name", it) }
                        put("quantity", e.quantity)
                        e.unitCost?.let { put("unit_price", it) }
                        e.currency?.let { put("currency", it) }
                        e.notes?.let { put("notes", it) }
                        e.partNumber?.let { put("part_number", it) }
                        e.gtin?.let { put("gtin", it) }
                        e.serialNumber?.let { put("serial_number", it) }
                        e.modelNumber?.let { put("model_number", it) }
                        e.lotNumber?.let { put("lot_number", it) }
                        // Change 86
                        put("deduct_inventory", e.deductInventory)
                        e.inventoryTransactionId?.let { put("inventory_transaction_id", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "wo_template" -> {
                    val e = db.woTemplateDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("template_id", e.templateId)
                        put("account_id", e.accountId)
                        put("title", e.title)
                        e.description?.let { put("description", it) }
                        e.category?.let { put("category", it) }
                        e.checklistItems?.let { put("checklist_items", it) }
                        e.estimatedEffortMinutes?.let { put("estimated_effort_minutes", it) }
                        // Change 71/109/64
                        e.rowsJson?.let { put("rows_json", it) }
                        e.seq?.let { put("seq", it) }
                        e.createdById?.let { put("created_by_id", it) }
                        e.updatedById?.let { put("updated_by_id", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "meter_reading" -> {
                    val e = db.meterReadingDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("meter_reading_id", e.meterReadingId)
                        put("asset_id", e.assetId)
                        put("account_id", e.accountId)
                        put("meter_type", e.meterType)
                        put("reading_value", e.readingValue)
                        put("read_at", msToIso(e.readAt))
                        e.recordedBy?.let { put("recorded_by", it) }
                        e.triggeredWoIds?.let { put("triggered_wo_ids", it) }
                        e.notes?.let { put("notes", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "work_permit" -> {
                    val e = db.workPermitDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("permit_id", e.permitId)
                        put("wo_id", e.woId)
                        put("account_id", e.accountId)
                        put("status", e.status)
                        e.permitType?.let { put("permit_type", it) }
                        e.requiredApprovers?.let { put("required_approvers", it) }
                        e.approvedBy?.let { put("approved_by", it) }
                        e.approvedAt?.let { put("approved_at", msToIso(it)) }
                        e.rejectedBy?.let { put("rejected_by", it) }
                        e.rejectedAt?.let { put("rejected_at", msToIso(it)) }
                        e.notes?.let { put("notes", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "work_permit_signature" -> {
                    val e = db.workPermitSignatureDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("signature_id", e.signatureId)
                        put("permit_id", e.permitId)
                        put("account_id", e.accountId)
                        put("signer_id", e.signerId)
                        e.signerName?.let { put("signer_name", it) }
                        e.signatureUrl?.let { put("signature_url", it) }
                        e.signedAt?.let { put("signed_at", msToIso(it)) }
                        put("all_signed", e.allSigned)
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                    }
                }

                "part_transfer_request" -> {
                    val e = db.partTransferRequestDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("request_id", e.requestId)
                        put("account_id", e.accountId)
                        put("part_id", e.partId)
                        put("quantity", e.quantity)
                        e.fromLocationId?.let { put("from_location_id", it) }
                        e.toLocationId?.let { put("to_location_id", it) }
                        put("status", e.status)
                        e.requestedBy?.let { put("requested_by", it) }
                        e.approvedBy?.let { put("approved_by", it) }
                        e.approvedAt?.let { put("approved_at", msToIso(it)) }
                        e.notes?.let { put("notes", it) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "owner_assignment" -> {
                    val e = db.ownerAssignmentDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("assignment_id", e.assignmentId)
                        put("account_id", e.accountId)
                        put("resource_type", e.resourceType)
                        put("resource_id", e.resourceId)
                        put("owner_user_id", e.ownerUserId)
                        e.secondaryOwnerId?.let { put("secondary_owner_id", it) }
                        put("fallback_enabled", e.fallbackEnabled)
                        e.role?.let { put("role", it) }
                        e.assignedAt?.let { put("assigned_at", msToIso(it)) }
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                "po_comment" -> {
                    val e = db.poCommentDao().getById(entityId) ?: return null
                    buildJsonObject {
                        put("comment_id", e.commentId)
                        put("po_id", e.poId)
                        put("account_id", e.accountId)
                        put("author_id", e.authorId)
                        put("body", e.body)
                        put("created_at", msToIso(e.createdAt))
                        put("updated_at", msToIso(e.updatedAt))
                        e.deletedAt?.let { put("deleted_at", msToIso(it)) }
                    }
                }

                else -> {
                    Timber.w("SyncPayloadBuilder: unsupported entity type '$entityType'")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncPayloadBuilder: failed to build payload for $entityType/$entityId")
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Convert epoch-milliseconds to ISO-8601 string for the wire format. */
    private fun msToIso(ms: Long): String =
        Instant.fromEpochMilliseconds(ms).toString()

    /**
     * Convert epoch-milliseconds to a calendar-date string sent as noon UTC.
     *
     * Date-only fields (log_date, purchase_date, placed_in_service_date, etc.) are stored
     * as local-midnight epoch millis. Sending the raw UTC instant can cause off-by-one-day
     * errors in UTC+ timezones (local midnight is the prior UTC day). Noon UTC is safely
     * within the same calendar day across all timezones from UTC-11 to UTC+11.
     */
    private fun msToDateOnly(ms: Long): String {
        val local = Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val noon = LocalDateTime(local.year, local.month, local.dayOfMonth, 12, 0, 0)
        return noon.toInstant(TimeZone.UTC).toString()
    }
}
