package com.avago.core.data.v2.commands

import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetDraft
import com.avago.core.data.domain.AssetStatusCode
import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.DefectState
import com.avago.core.data.domain.DowntimeKind
import com.avago.core.data.domain.InspectionDefect
import com.avago.core.data.domain.MeterReading
import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.Worker
import com.avago.core.data.domain.WorkerDraft
import com.avago.core.data.v2.V2Micros
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import com.avago.core.data.v2.V2StagedFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.Instant

/**
 * Builders for the `maint.*` catalog commands other than work orders ([WorkOrderCommands]):
 * assets, meters, attachments, activities, cost lines, defects, PM occurrences and workers. Each
 * returns the outbox entry with its optimistic overlay.
 */
object MaintCommands {

    // ------------------------------------------------------------------ assets

    fun createAsset(id: String, draft: AssetDraft, nowMillis: Long, userId: String?): V2NewCommand {
        val name = requireNotNull(draft.name.clean()) { "an asset needs a name" }
        val payload = MaintAssetsCreate(
            id = id,
            name = name,
            assetTypeCode = draft.assetTypeCode.clean(),
            makeOverride = draft.make.clean(),
            modelOverride = draft.model.clean(),
            modelYear = draft.modelYear,
            serialNumber = draft.serialNumber.clean(),
            vin = draft.vin.clean(),
            tags = draft.tags.cleanList(),
            locationId = draft.locationId,
            conditionScore = draft.conditionScore,
            avatarColor = draft.avatarColor.clean(),
            avatarInitial = draft.avatarInitial.clean(),
            attributes = draft.attributes.jsonObjectOrNull(),
            rentable = draft.rentable,
        )
        val row = mapOf(
            "name" to name,
            "asset_type_code" to payload.assetTypeCode,
            "make_override" to payload.makeOverride,
            "model_override" to payload.modelOverride,
            "model_year" to payload.modelYear,
            "serial_number" to payload.serialNumber,
            "vin" to payload.vin,
            "tags" to payload.tags?.let(::jsonList),
            "location_id" to payload.locationId,
            "condition_score" to payload.conditionScore,
            "avatar_color" to payload.avatarColor,
            "avatar_initial" to payload.avatarInitial,
            "rentable" to draft.rentable,
            "status_code" to AssetStatusCode.ONLINE.wire,
            "status_since" to nowMillis,
            "attributes" to (payload.attributes?.toString() ?: "{}"),
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch.of(V2RowOp.upsert("assets", id, row)))
    }

    /** Null when nothing changed. */
    fun updateAsset(stored: Asset, draft: AssetDraft, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(MaintAssetsUpdate.Clear.entries)
        val name = d.text("name", stored.name, draft.name)
        val type = d.text("asset_type_code", stored.assetTypeCode, draft.assetTypeCode)
        val make = d.text("make_override", stored.make, draft.make)
        val model = d.text("model_override", stored.model, draft.model)
        val year = d.field("model_year", stored.modelYear, draft.modelYear)
        val serial = d.text("serial_number", stored.serialNumber, draft.serialNumber)
        val vin = d.text("vin", stored.vin, draft.vin)
        val tags = d.field("tags", stored.tags.cleanList(), draft.tags.cleanList(), toColumn = ::jsonList)
        val location = d.text("location_id", stored.locationId, draft.locationId)
        val condition = d.field("condition_score", stored.conditionScore, draft.conditionScore)
        val color = d.text("avatar_color", stored.avatarColor, draft.avatarColor)
        val initial = d.text("avatar_initial", stored.avatarInitial, draft.avatarInitial)
        val rentable = d.field("rentable", stored.rentable, draft.rentable)
        d.check()
        // Attributes are NOT NULL on the server: null in the draft means "unchanged", and the client
        // sends the whole merged object (merge semantics are a catalog request).
        val attributes = draft.attributes?.let { (it.jsonObjectOrNull() ?: JsonObject(emptyMap())) }
            ?.takeIf { it != (stored.attributes.jsonObjectOrNull() ?: JsonObject(emptyMap())) }
        if (d.isEmpty && attributes == null) return null
        val payload = MaintAssetsUpdate(
            id = stored.id, name = name, assetTypeCode = type, makeOverride = make, modelOverride = model,
            modelYear = year, serialNumber = serial, vin = vin, tags = tags, locationId = location,
            conditionScore = condition, avatarColor = color, avatarInitial = initial, attributes = attributes,
            rentable = rentable, clear = d.clear,
        )
        val overlay = d.overlay + listOfNotNull(attributes?.let { "attributes" to it.toString() }) + updated(nowMillis)
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("assets", stored.id, overlay)),
        )
    }

    fun deleteAsset(stored: Asset): V2NewCommand =
        MaintAssetsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("assets", stored.id)),
        )

    fun setAssetStatus(
        stored: Asset,
        status: AssetStatusCode,
        downtime: DowntimeKind?,
        reason: String?,
        intervalId: String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val payload = MaintAssetsSetStatus(
            id = stored.id,
            statusCode = AssetStatus.entries.first { it.wire == status.wire },
            downtimeType = downtime?.let { k -> DowntimeType.entries.first { it.wire == k.wire } },
            reason = reason.clean(),
        )
        val ops = listOf(
            V2RowOp.upsert("assets", stored.id, mapOf("status_code" to status.wire, "status_since" to nowMillis) + updated(nowMillis)),
            V2RowOp.upsert(
                "asset_status_intervals", intervalId,
                mapOf(
                    "asset_id" to stored.id, "status_code" to status.wire, "downtime_type" to downtime?.wire,
                    "valid_during_start" to nowMillis, "valid_during_end" to null, "reason" to payload.reason,
                    "actor_user_id" to userId,
                ) + created(nowMillis, userId),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    fun setAssetParent(stored: Asset, parentAssetId: String?, nowMillis: Long): V2NewCommand =
        MaintAssetsSetParent(id = stored.id, parentAssetId = parentAssetId).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert("assets", stored.id, mapOf("parent_asset_id" to parentAssetId) + updated(nowMillis)),
            ),
        )

    // ------------------------------------------------------------------ meters

    /** A reading on one of the asset's meters; the server evaluates its meter-based PM triggers. */
    fun recordReading(
        id: String,
        assetId: String,
        meterId: String,
        value: BigDecimal,
        readAtMillis: Long,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        require(value.signum() >= 0) { "a meter reading cannot be negative" }
        require(readAtMillis <= nowMillis) { "a meter reading cannot be in the future" }
        val payload = MaintMeterReadingsRecord(
            id = id, assetId = assetId, meterId = meterId, value = value, readAt = Instant.ofEpochMilli(readAtMillis),
        )
        return payload.toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    "meter_readings", id,
                    mapOf(
                        "asset_id" to assetId, "meter_id" to meterId, "value_micros" to V2Micros.fromDecimal(value),
                        "read_at" to readAtMillis, "source" to "manual", "recorded_by" to userId,
                    ) + created(nowMillis, userId),
                ),
            ),
        )
    }

    fun correctReading(stored: MeterReading, value: BigDecimal, reason: String, nowMillis: Long): V2NewCommand =
        MaintMeterReadingsUpdate(id = stored.id, value = value, correctionReason = reason.clean()).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    "meter_readings", stored.id,
                    mapOf("value_micros" to V2Micros.fromDecimal(value), "correction_reason" to reason.clean()) + updated(nowMillis),
                ),
            ),
        )

    // ------------------------------------------------------------------ files

    /** The optimistic `files` row of a staged upload (pending until the server verifies it). */
    fun pendingFileRow(file: V2StagedFile, nowMillis: Long, userId: String?): V2RowOp =
        V2RowOp.upsert(
            "files", file.fileId,
            mapOf(
                "storage_key" to "local/${file.fileId}", "status" to "pending", "sha256" to file.sha256,
                "mime_type" to file.mediaType, "size_bytes" to file.byteSize, "original_name" to file.originalName,
            ) + created(nowMillis, userId),
        )

    /** Links an uploaded file; the command waits for the upload (E12) before it is sent. */
    fun attach(
        id: String,
        target: AttachmentTarget,
        file: V2StagedFile,
        sortOrder: Int?,
        nowMillis: Long,
        userId: String?,
        dependsOn: List<String> = emptyList(),
    ): V2NewCommand {
        val (assetId, activityId, workOrderId) = when (target) {
            is AttachmentTarget.ToAsset -> Triple(target.assetId, null, null)
            is AttachmentTarget.ToActivity -> Triple(null, target.activityId, null)
            is AttachmentTarget.ToWorkOrder -> Triple(null, null, target.workOrderId)
        }
        val payload = MaintAttachmentsCreate(
            id = id, fileObjectId = file.fileId, assetId = assetId, activityId = activityId, workOrderId = workOrderId,
            sortOrder = sortOrder,
        )
        val ops = listOf(
            pendingFileRow(file, nowMillis, userId),
            V2RowOp.upsert(
                "attachments", id,
                mapOf(
                    "file_object_id" to file.fileId, "asset_id" to assetId, "activity_id" to activityId,
                    "work_order_id" to workOrderId, "sort_order" to sortOrder,
                ) + created(nowMillis, userId),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops), dependsOn = dependsOn, uploads = listOf(file.toUpload()))
    }

    fun detach(id: String, version: Long): V2NewCommand =
        MaintAttachmentsDelete(id = id).toNewCommand(
            baseVersion = version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("attachments", id)),
        )

    // ------------------------------------------------------------------ activities

    fun createActivity(id: String, draft: ActivityDraft, nowMillis: Long, userId: String?): V2NewCommand {
        require(draft.assetId != null || draft.workOrderId != null) { "an activity belongs to an asset or a work order" }
        val payload = MaintActivityRecordsCreate(
            id = id,
            assetId = draft.assetId,
            workOrderId = draft.workOrderId,
            kind = ActivityKind.entries.first { it.wire == draft.type.wire },
            categoryLabel = draft.categoryLabel.clean(),
            title = draft.title.clean(),
            occurredAt = Instant.ofEpochMilli(draft.occurredAt),
            meterValue = draft.meterValue,
            performedByUserId = draft.performedByUserId,
            performedByName = draft.performedByName.clean(),
            providerName = draft.providerName.clean(),
            currency = draft.currency.clean(),
            overtimeMultiplier = draft.overtimeMultiplier,
            notes = draft.notes.clean(),
            payload = draft.payload.jsonObjectOrNull(),
            attributes = draft.attributes.jsonObjectOrNull(),
        )
        val row = mapOf(
            "asset_id" to draft.assetId,
            "work_order_id" to draft.workOrderId,
            "kind" to draft.type.wire,
            "category_label" to payload.categoryLabel,
            "title" to payload.title,
            "occurred_at" to draft.occurredAt,
            "performed_by_user_id" to draft.performedByUserId,
            "performed_by_name" to payload.performedByName,
            "provider_name" to payload.providerName,
            "cost_mode" to "detailed",
            "currency" to payload.currency,
            "overtime_multiplier" to draft.overtimeMultiplier?.toPlainString(),
            "notes" to payload.notes,
            "payload" to (payload.payload?.toString() ?: "{}"),
            "attributes" to (payload.attributes?.toString() ?: "{}"),
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch.of(V2RowOp.upsert("activity_records", id, row)))
    }

    /** Null when nothing changed. Kind, asset and work order are fixed after creation. */
    fun updateActivity(stored: Activity, draft: ActivityDraft, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(MaintActivityRecordsUpdate.Clear.entries)
        val category = d.text("category_label", stored.categoryLabel, draft.categoryLabel)
        val title = d.text("title", stored.title, draft.title)
        val occurred = d.field("occurred_at", stored.occurredAt, draft.occurredAt)
        val meter = d.decimal("meter_value", stored.meterValue, draft.meterValue, column = null)
        val performerId = d.text("performed_by_user_id", stored.performedByUserId, draft.performedByUserId)
        val performer = d.text("performed_by_name", stored.performedByName, draft.performedByName)
        val provider = d.text("provider_name", stored.providerName, draft.providerName)
        val currency = d.text("currency", stored.currency, draft.currency)
        val overtime = d.decimal("overtime_multiplier", stored.overtimeMultiplier, draft.overtimeMultiplier)
        val notes = d.text("notes", stored.notes, draft.notes)
        val payloadJson = d.field("payload", stored.payload.jsonObjectOrNull(), draft.payload.jsonObjectOrNull(), toColumn = { it.toString() })
        val attributes = d.field("attributes", stored.attributes.jsonObjectOrNull(), draft.attributes.jsonObjectOrNull(), toColumn = { it.toString() })
        d.check()
        if (d.isEmpty) return null
        val payload = MaintActivityRecordsUpdate(
            id = stored.id, categoryLabel = category, title = title, occurredAt = occurred?.let(Instant::ofEpochMilli),
            meterValue = meter, performedByUserId = performerId, performedByName = performer, providerName = provider,
            currency = currency, overtimeMultiplier = overtime, notes = notes, payload = payloadJson,
            attributes = attributes, clear = d.clear,
        )
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("activity_records", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun deleteActivity(stored: Activity): V2NewCommand =
        MaintActivityRecordsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("activity_records", stored.id)),
        )

    // ------------------------------------------------------------------ cost lines

    /** Exactly one of [activityId] / [workOrderId] (a work order's line lands on its draft activity). */
    fun createCostLine(
        id: String,
        activityId: String?,
        workOrderId: String?,
        draft: CostLineDraft,
        sortOrder: Int,
        nowMillis: Long,
        userId: String?,
        performerWorkerId: String? = null,
        dependsOn: List<String> = emptyList(),
    ): V2NewCommand {
        require((activityId == null) != (workOrderId == null)) { "a cost line belongs to an activity or a work order" }
        val payload = MaintActivityCostLinesCreate(
            id = id,
            activityId = activityId,
            workOrderId = workOrderId,
            kind = CostLineKind.entries.first { it.wire == draft.kind.wire },
            sortOrder = sortOrder,
            productId = draft.productId,
            performerUserId = draft.performerUserId,
            description = draft.description.clean(),
            quantity = draft.quantity,
            unitCost = draft.unitCost,
            taxAmountMinor = draft.taxAmountMinor,
            glAccountId = draft.glAccountId,
            costStatus = if (draft.estimate) CostStatus.ESTIMATE else CostStatus.ACTUAL,
            notes = draft.notes.clean(),
        )
        val row = mapOf(
            "activity_id" to activityId,
            "work_order_id" to workOrderId,
            "kind" to draft.kind.wire,
            "sort_order" to sortOrder,
            "product_id" to draft.productId,
            "performer_worker_id" to performerWorkerId,
            "description" to payload.description,
            "quantity_micros" to V2Micros.fromDecimal(draft.quantity),
            "unit_cost" to draft.unitCost?.toPlainString(),
            "tax_amount_minor" to draft.taxAmountMinor,
            "gl_account_id" to draft.glAccountId,
            "overtime" to 0L,
            "cost_status" to payload.costStatus?.wire,
            "notes" to payload.notes,
        ) + created(nowMillis, userId)
        return payload.toNewCommand(
            overlay = V2OverlayPatch.of(V2RowOp.upsert("activity_cost_lines", id, row)),
            dependsOn = dependsOn,
        )
    }

    /** Null when nothing changed. The kind is fixed after creation. */
    /**
     * Null when nothing changed. The kind is fixed after creation. [storedPerformerUserId] is the
     * user behind `performer_worker_id`; [performerWorkerId] the worker of the new performer.
     */
    fun updateCostLine(
        stored: CostLine,
        draft: CostLineDraft,
        nowMillis: Long,
        storedPerformerUserId: String?,
        performerWorkerId: String?,
    ): V2NewCommand? {
        val d = V2UpdateDiff(MaintActivityCostLinesUpdate.Clear.entries)
        val product = d.text("product_id", stored.productId, draft.productId)
        val performer = d.text("performer_user_id", storedPerformerUserId, draft.performerUserId, column = null)
        if (performer != null || (draft.performerUserId == null && storedPerformerUserId != null)) {
            d.overlay["performer_worker_id"] = performerWorkerId
        }
        val description = d.text("description", stored.description, draft.description)
        val quantity = d.decimal("quantity", stored.quantity, draft.quantity, column = "quantity_micros", toColumn = V2Micros::fromDecimal)
        val unitCost = d.decimal("unit_cost", stored.unitCost, draft.unitCost)
        val tax = d.field("tax_amount_minor", stored.taxAmountMinor, draft.taxAmountMinor)
        val glAccount = d.text("gl_account_id", stored.glAccountId, draft.glAccountId)
        val status = d.text("cost_status", stored.costStatus, if (draft.estimate) CostStatus.ESTIMATE.wire else CostStatus.ACTUAL.wire)
        val notes = d.text("notes", stored.notes, draft.notes)
        d.check()
        if (d.isEmpty) return null
        val payload = MaintActivityCostLinesUpdate(
            id = stored.id, productId = product, performerUserId = performer, description = description, quantity = quantity,
            unitCost = unitCost, taxAmountMinor = tax, glAccountId = glAccount, costStatus = status?.let { s -> CostStatus.entries.first { it.wire == s } },
            notes = notes, clear = d.clear,
        )
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("activity_cost_lines", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun deleteCostLine(stored: CostLine): V2NewCommand =
        MaintActivityCostLinesDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("activity_cost_lines", stored.id)),
        )

    // ------------------------------------------------------------------ defects, PM, workers

    fun updateDefect(stored: InspectionDefect, status: DefectState, resolutionNote: String?, nowMillis: Long, userId: String?): V2NewCommand? {
        val d = V2UpdateDiff(MaintInspectionDefectsUpdate.Clear.entries)
        val newStatus = d.text("status", stored.status.wire, status.wire)
        val note = d.text("resolution_note", stored.resolutionNote, resolutionNote)
        d.check()
        if (d.isEmpty) return null
        if (newStatus != null) {
            val resolved = status == DefectState.REPAIRED
            d.overlay["resolved_at"] = if (resolved) nowMillis else null
            d.overlay["resolved_by"] = if (resolved) userId else null
        }
        val payload = MaintInspectionDefectsUpdate(
            // monitoring is the open state of a Monitor-bucket defect; the server stores it when sent open.
            id = stored.id,
            status = newStatus?.let { if (status == DefectState.MONITORING) DefectStatus.OPEN else DefectStatus.entries.first { e -> e.wire == it } },
            resolutionNote = note,
            clear = d.clear,
        )
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("inspection_defects", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun completeOccurrence(stored: PmOccurrence, completedAtMillis: Long?, meterValue: BigDecimal?, nowMillis: Long): V2NewCommand =
        MaintPmOccurrencesComplete(
            id = stored.id, completedAt = completedAtMillis?.let(Instant::ofEpochMilli), meterValue = meterValue,
        ).toNewCommand(overlay = occurrencePatch(stored.id, mapOf("state" to "completed", "closed_at" to (completedAtMillis ?: nowMillis)), nowMillis))

    fun skipOccurrence(stored: PmOccurrence, reason: String, nowMillis: Long): V2NewCommand {
        val why = requireNotNull(reason.clean()) { "a skip needs a reason" }
        return MaintPmOccurrencesSkip(id = stored.id, reason = why)
            .toNewCommand(overlay = occurrencePatch(stored.id, mapOf("state" to "skipped", "closed_at" to nowMillis), nowMillis))
    }

    fun deferOccurrence(stored: PmOccurrence, dueAtMillis: Long, nowMillis: Long): V2NewCommand =
        MaintPmOccurrencesDefer(id = stored.id, dueAt = Instant.ofEpochMilli(dueAtMillis))
            .toNewCommand(overlay = occurrencePatch(stored.id, mapOf("due_at" to dueAtMillis), nowMillis))

    /** Null when nothing changed. */
    fun updateWorker(stored: Worker, draft: WorkerDraft, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(MaintWorkersUpdate.Clear.entries)
        val name = d.text("display_name", stored.displayName, draft.displayName)
        val skills = d.field("skills", stored.skills.cleanList(), draft.skills.cleanList(), toColumn = ::jsonList)
        val max = d.field("max_active_work_orders", stored.maxActiveWorkOrders, draft.maxActiveWorkOrders)
        val available = d.field("available", stored.available, draft.available)
        val home = d.text("home_location_id", stored.homeLocationId, draft.homeLocationId)
        d.check()
        if (d.isEmpty) return null
        val payload = MaintWorkersUpdate(
            id = stored.id, displayName = name, skills = skills, maxActiveWorkOrders = max, available = available,
            homeLocationId = home, clear = d.clear,
        )
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("workers", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun occurrencePatch(id: String, set: Map<String, Any?>, nowMillis: Long) =
        V2OverlayPatch.of(V2RowOp.upsert("pm_occurrences", id, set + updated(nowMillis)))
}

/** Trimmed non-blank entries, null when none. */
internal fun List<String>.cleanList(): List<String>? = mapNotNull { it.clean() }.ifEmpty { null }

/** JSON array text of a `[text]` column. */
internal fun jsonList(values: List<String>): String = JsonArray(values.map(::JsonPrimitive)).toString()

/** Parses JSON array text of a `[text]` column; empty when absent or malformed. */
fun parseJsonList(raw: String?): List<String> = raw?.let {
    runCatching { (Json.parseToJsonElement(it) as? JsonArray)?.mapNotNull { e -> (e as? JsonPrimitive)?.content } }.getOrNull()
}.orEmpty()

/** A JSON object payload, null when blank, `{}` or malformed. */
internal fun String?.jsonObjectOrNull(): JsonElement? = this?.clean()?.let {
    runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull()?.takeIf { o -> o.isNotEmpty() }
}
