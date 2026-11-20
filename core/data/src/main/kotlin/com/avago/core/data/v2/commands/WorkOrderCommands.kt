package com.avago.core.data.v2.commands

import com.avago.core.data.domain.ApprovalRequest
import com.avago.core.data.domain.ChecklistItem
import com.avago.core.data.domain.RcaDraft
import com.avago.core.data.domain.RcaReport
import com.avago.core.data.domain.RecurrenceRule
import com.avago.core.data.domain.TimeEntry
import com.avago.core.data.domain.TimeEntryDraft
import com.avago.core.data.domain.TimerAction
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderAssignment
import com.avago.core.data.domain.WorkOrderCompletion
import com.avago.core.data.domain.WorkOrderDraft
import com.avago.core.data.domain.WorkOrderRecurrence
import com.avago.core.data.domain.WorkOrderStatus
import com.avago.core.data.domain.WorkOrderTemplate
import com.avago.core.data.domain.WorkOrderTemplateDraft
import com.avago.core.data.domain.WorkPermit
import com.avago.core.data.domain.WorkPermitDraft
import com.avago.core.data.v2.V2NewCommand
import com.avago.core.data.v2.V2OverlayPatch
import com.avago.core.data.v2.V2RowOp
import com.avago.core.data.v2.V2StagedFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Local ids the overlays need for names the commands carry: category names, failure/cause/remedy
 * codes and assignee user ids resolve to `categories`, `reference_values` and `workers` rows.
 */
data class WorkOrderLookups(
    val categoryIdByName: (String) -> String? = { null },
    val codeIdByKindAndCode: (String, String) -> String? = { _, _ -> null },
    val workerIdByUserId: (String) -> String? = { null },
)

/** Builders for `maint.work_orders.*` and the work-order sub-records. */
object WorkOrderCommands {

    // ------------------------------------------------------------------ work orders

    fun create(
        id: String,
        draft: WorkOrderDraft,
        lookups: WorkOrderLookups,
        nowMillis: Long,
        userId: String?,
        attributes: Map<String, JsonElement> = emptyMap(),
    ): V2NewCommand {
        val attrs = attributes.takeIf { it.isNotEmpty() }?.let(::JsonObject)
        val title = requireNotNull(draft.title.clean()) { "a work order needs a title" }
        val category = draft.category.clean()
        val payload = MaintWorkOrdersCreate(
            id = id,
            title = title,
            description = draft.description.clean(),
            assetIds = draft.assetIds.ifEmpty { null },
            locationId = draft.locationId,
            category = category,
            priority = WoPriority.entries.first { it.wire == draft.priority.wire },
            complexity = draft.complexity?.let { c -> WoComplexity.entries.first { it.wire == c.wire } },
            assignedUserId = draft.assignedUserId,
            requiredSkills = draft.requiredSkills.cleanList(),
            estimatedEffortMinutes = draft.estimatedEffortMinutes,
            dueAt = draft.dueAt?.let(Instant::ofEpochMilli),
            attributes = attrs,
        )
        val row = mapOf(
            "title" to title,
            "description" to payload.description,
            "category_id" to category?.let(lookups.categoryIdByName),
            "priority" to draft.priority.wire,
            "status" to WorkOrderStatus.OPEN.wire,
            "complexity" to draft.complexity?.wire,
            "requester_user_id" to userId,
            "assigned_worker_id" to draft.assignedUserId?.let(lookups.workerIdByUserId),
            "location_id" to draft.locationId,
            "due_at" to draft.dueAt,
            "requires_permit" to 0L,
            "required_skills" to payload.requiredSkills?.let(::jsonList),
            "estimated_effort_minutes" to draft.estimatedEffortMinutes,
            "exclude_from_estimates" to 0L,
            "attributes" to (attrs?.toString() ?: "{}"),
        ) + created(nowMillis, userId)
        val ops = listOf(V2RowOp.upsert("work_orders", id, row)) + assetLinks(id, draft.assetIds, nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /** Null when nothing changed. */
    fun update(stored: WorkOrder, draft: WorkOrderDraft, lookups: WorkOrderLookups, nowMillis: Long, userId: String?, storedAssignedUserId: String?): V2NewCommand? {
        val d = V2UpdateDiff(MaintWorkOrdersUpdate.Clear.entries)
        val title = d.text("title", stored.title, draft.title)
        val description = d.text("description", stored.description, draft.description)
        val assets = d.field("asset_ids", stored.assetIds.ifEmpty { null }, draft.assetIds.ifEmpty { null }, column = null)
        val location = d.text("location_id", stored.locationId, draft.locationId)
        val category = d.text("category", stored.categoryName, draft.category, column = null)
        if (category != null || (draft.category.clean() == null && stored.categoryName != null)) {
            d.overlay["category_id"] = category?.let(lookups.categoryIdByName)
        }
        val priority = d.text("priority", stored.priority.wire, draft.priority.wire)
        val complexity = d.text("complexity", stored.complexity?.wire, draft.complexity?.wire)
        val assignee = d.text("assigned_user_id", storedAssignedUserId, draft.assignedUserId, column = null)
        if (assignee != null) d.overlay["assigned_worker_id"] = lookups.workerIdByUserId(assignee)
        val skills = d.field("required_skills", stored.requiredSkills.cleanList(), draft.requiredSkills.cleanList(), toColumn = ::jsonList)
        val effort = d.field("estimated_effort_minutes", stored.estimatedEffortMinutes, draft.estimatedEffortMinutes)
        val due = d.field("due_at", stored.dueAt, draft.dueAt)
        val project = d.text("project_id", stored.projectId, draft.projectId)
        val window = plannedWindow(d, stored, draft)
        val notes = d.text("dispatcher_notes", stored.dispatcherNotes, draft.dispatcherNotes)
        d.check()
        if (d.isEmpty) return null
        val payload = MaintWorkOrdersUpdate(
            id = stored.id, title = title, description = description, assetIds = assets, locationId = location,
            category = category, priority = priority?.let { p -> WoPriority.entries.first { it.wire == p } },
            complexity = complexity?.let { c -> WoComplexity.entries.first { it.wire == c } }, assignedUserId = assignee,
            projectId = project, requiredSkills = skills, estimatedEffortMinutes = effort, dueAt = due?.let(Instant::ofEpochMilli),
            plannedStart = window?.first?.let(Instant::ofEpochMilli), plannedEnd = window?.second?.let(Instant::ofEpochMilli),
            dispatcherNotes = notes, clear = d.clear,
        )
        val ops = mutableListOf(V2RowOp.upsert("work_orders", stored.id, d.overlay + updated(nowMillis)))
        if (assets != null) {
            stored.assetIds.filterNot { it in assets }.forEach {
                ops += V2RowOp.delete("work_order_assets", mapOf("work_order_id" to stored.id, "asset_id" to it))
            }
            ops += assetLinks(stored.id, assets, nowMillis, userId)
        }
        return payload.toNewCommand(baseVersion = stored.version, overlay = V2OverlayPatch(ops))
    }

    /**
     * The planned window is set or cleared as a pair: a changed bound sends both. Returns the new
     * window when it is set, null when unchanged or cleared (the clear is recorded in [d]).
     */
    private fun plannedWindow(d: V2UpdateDiff<MaintWorkOrdersUpdate.Clear>, stored: WorkOrder, draft: WorkOrderDraft): Pair<Long, Long>? {
        val start = draft.plannedStart
        val end = draft.plannedEnd
        require((start == null) == (end == null)) { "the planned window needs a start and an end" }
        require(start == null || end!! > start) { "the planned window must end after it starts" }
        if (start == stored.plannedStart && end == stored.plannedEnd) return null
        if (start == null || end == null) {
            d.clear += MaintWorkOrdersUpdate.Clear.PLANNED_START
            d.clear += MaintWorkOrdersUpdate.Clear.PLANNED_END
            d.overlay["planned_during_start"] = null
            d.overlay["planned_during_end"] = null
            return null
        }
        d.overlay["planned_during_start"] = start
        d.overlay["planned_during_end"] = end
        return start to end
    }

    /** Self-assignment by the caller's worker [myWorkerId]: the server makes it owner and lead. */
    fun claim(stored: WorkOrder, myWorkerId: String?, nowMillis: Long): V2NewCommand {
        val set = mutableMapOf<String, Any?>("status" to WorkOrderStatus.ASSIGNED.wire)
        if (myWorkerId != null) set["assigned_worker_id"] = myWorkerId
        return MaintWorkOrdersClaim(id = stored.id).toNewCommand(
            overlay = V2OverlayPatch.of(V2RowOp.upsert("work_orders", stored.id, set + updated(nowMillis))),
        )
    }

    /**
     * Sets (or, with an empty rule, stops) the calendar recurrence. With an existing plan
     * ([current]) the overlay mirrors the server: plan activation and end fields, and the calendar
     * trigger's rule; a new plan is created server-side and arrives by sync.
     */
    fun setRecurrence(workOrderId: String, rule: RecurrenceRule, current: WorkOrderRecurrence?, nowMillis: Long): V2NewCommand {
        val rrule = rule.rrule.trim()
        val stop = rrule.isEmpty()
        val endType = rule.endType.takeUnless { stop }
        require(endType == null || endType in END_TYPES) { "end type must be never, count or date" }
        require(endType != "count" || (rule.endCount ?: 0) > 0) { "a count end needs a positive count" }
        require(endType != "date" || rule.endOnEpochDay != null) { "a date end needs a date" }
        val payload = MaintWorkOrdersSetRecurrence(
            id = workOrderId,
            rrule = rrule,
            endType = endType,
            endCount = rule.endCount.takeIf { endType == "count" },
            endOn = rule.endOnEpochDay.takeIf { endType == "date" }?.let(LocalDate::ofEpochDay),
            timezone = rule.timezone.clean().takeUnless { stop },
        )
        val ops = mutableListOf<V2RowOp>()
        if (current != null) {
            val plan = mutableMapOf<String, Any?>("active" to !stop)
            if (endType != null) {
                plan["end_type"] = endType
                plan["end_count"] = payload.endCount?.toLong()
                plan["end_on"] = payload.endOn?.toString()
            }
            payload.timezone?.let { plan["timezone"] = it }
            ops += V2RowOp.upsert("pm_plans", current.planId, plan + updated(nowMillis))
            if (!stop && current.triggerId != null) {
                ops += V2RowOp.upsert("pm_triggers", current.triggerId, mapOf("rrule" to rrule) + updated(nowMillis))
            }
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    // ------------------------------------------------------------------ approvals

    /**
     * Records the caller's decision ([decisionId] is the overlay's row id). A rejection decides
     * the request and the work order's approval; an approval may need further approvers, so the
     * request status is left to the server.
     */
    fun decideApproval(
        request: ApprovalRequest,
        approve: Boolean,
        notes: String?,
        decisionId: String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val comment = notes.clean()
        val decision = if (approve) "approved" else "rejected"
        val ops = mutableListOf<V2RowOp>()
        if (userId != null) {
            ops += V2RowOp.upsert(
                "approval_decisions", decisionId,
                mapOf(
                    "request_id" to request.id, "approver_user_id" to userId, "decision" to decision, "comment" to comment,
                    "decided_at" to nowMillis, "created_at" to nowMillis,
                ),
            )
        }
        if (!approve) {
            ops += V2RowOp.upsert("approval_requests", request.id, mapOf("status" to decision, "decided_at" to nowMillis) + updated(nowMillis))
            request.workOrderId?.let { wo ->
                ops += V2RowOp.upsert("work_orders", wo, mapOf("approval_state" to decision) + updated(nowMillis))
            }
        }
        val payload: V2CommandPayload = if (approve) {
            MaintApprovalRequestsApprove(id = request.id, notes = comment)
        } else {
            MaintApprovalRequestsReject(id = request.id, notes = comment)
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    // ------------------------------------------------------------------ time entries

    /** A finished stretch of the caller's work (status submitted). */
    fun logTime(id: String, workOrderId: String, draft: TimeEntryDraft, myWorkerId: String?, nowMillis: Long, userId: String?): V2NewCommand {
        checkTimeEntry(draft, nowMillis)
        val payload = MaintTimeEntriesCreate(
            id = id, workOrderId = workOrderId, start = Instant.ofEpochMilli(draft.start), end = Instant.ofEpochMilli(draft.end),
            breakMinutes = draft.breakMinutes.takeIf { it > 0 }, notes = draft.notes.clean(),
        )
        val ops = listOfNotNull(
            myWorkerId?.let { worker ->
                V2RowOp.upsert(
                    "time_entries", id,
                    mapOf(
                        "worker_id" to worker, "calendar_id" to "", "work_order_id" to workOrderId, "during_start" to draft.start,
                        "during_end" to draft.end, "break_minutes" to draft.breakMinutes.toLong(), "status" to TimeEntry.STATUS_SUBMITTED,
                        "notes" to payload.notes,
                    ) + created(nowMillis, userId),
                )
            },
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /** Null when nothing changed; a rejected entry goes back to submitted. */
    fun updateTimeEntry(stored: TimeEntry, draft: TimeEntryDraft, nowMillis: Long): V2NewCommand? {
        require(stored.isEditable) { "only a stopped, unapproved time entry can be corrected" }
        checkTimeEntry(draft, nowMillis)
        val d = V2UpdateDiff(MaintTimeEntriesUpdate.Clear.entries)
        val start = d.field("start", stored.start, draft.start, column = "during_start")
        val end = d.field("end", stored.end, draft.end, column = "during_end")
        val breakMinutes = d.field("break_minutes", stored.breakMinutes, draft.breakMinutes) { it.toLong() }
        val notes = d.text("notes", stored.notes, draft.notes)
        d.check()
        if (d.isEmpty) return null
        if (stored.status == TimeEntry.STATUS_REJECTED) {
            d.overlay["status"] = TimeEntry.STATUS_SUBMITTED
            d.overlay["reject_reason"] = null
        }
        val payload = MaintTimeEntriesUpdate(
            id = stored.id, start = start?.let(Instant::ofEpochMilli), end = end?.let(Instant::ofEpochMilli),
            breakMinutes = breakMinutes, notes = notes, clear = d.clear,
        )
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("time_entries", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun deleteTimeEntry(stored: TimeEntry): V2NewCommand {
        require(stored.isEditable) { "only a stopped, unapproved time entry can be deleted" }
        return MaintTimeEntriesDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("time_entries", stored.id)),
        )
    }

    fun approveTimeEntry(stored: TimeEntry, nowMillis: Long, userId: String?): V2NewCommand {
        require(stored.isDecidable) { "only a submitted or rejected time entry can be approved" }
        return MaintTimeEntriesApprove(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    "time_entries", stored.id,
                    mapOf(
                        "status" to TimeEntry.STATUS_APPROVED, "approved_by" to userId, "approved_at" to nowMillis, "reject_reason" to null,
                    ) + updated(nowMillis),
                ),
            ),
        )
    }

    fun rejectTimeEntry(stored: TimeEntry, reason: String, nowMillis: Long): V2NewCommand {
        require(stored.isDecidable) { "only a submitted or rejected time entry can be rejected" }
        val why = requireNotNull(reason.clean()) { "a rejection needs a reason" }
        return MaintTimeEntriesReject(id = stored.id, reason = why).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert("time_entries", stored.id, mapOf("status" to TimeEntry.STATUS_REJECTED, "reject_reason" to why) + updated(nowMillis)),
            ),
        )
    }

    private fun checkTimeEntry(draft: TimeEntryDraft, nowMillis: Long) {
        require(draft.end > draft.start) { "a time entry must end after it starts" }
        require(draft.end <= nowMillis) { "a time entry cannot end in the future" }
        require(draft.breakMinutes in 0..MAX_BREAK_MINUTES) { "the break must be 0 to $MAX_BREAK_MINUTES minutes" }
        require(draft.breakMinutes * 60_000L < draft.end - draft.start) { "the break must fit the time span" }
    }

    /**
     * Saves completion codes and notes (null fields are cleared). Null when nothing changed.
     * [attributesJson] is the stored `attributes`, where notes live until completion.
     */
    fun updateCompletion(
        stored: WorkOrder,
        completion: WorkOrderCompletion,
        lookups: WorkOrderLookups,
        attributesJson: String?,
        nowMillis: Long,
    ): V2NewCommand? {
        val d = V2UpdateDiff(MaintWorkOrdersUpdate.Clear.entries)
        val failure = d.text("failure_code", stored.failureCode, completion.failureCode, column = null)
        val cause = d.text("cause_code", stored.causeCode, completion.causeCode, column = null)
        val remedy = d.text("remedy_code", stored.remedyCode, completion.remedyCode, column = null)
        val notes = d.text("completion_notes", stored.completionNotes, completion.completionNotes, column = null)
        d.check()
        if (d.isEmpty) return null
        val payload = MaintWorkOrdersUpdate(
            id = stored.id, failureCode = failure, causeCode = cause, remedyCode = remedy, completionNotes = notes,
            clear = d.clear,
        )
        val ops = completionOps(stored, completion, lookups, attributesJson, nowMillis, changedOnly = d)
        return payload.toNewCommand(baseVersion = stored.version, overlay = V2OverlayPatch(ops))
    }

    /** Status change; non-null [completion] fields are saved first (the server's order too). */
    fun transition(
        stored: WorkOrder,
        status: WorkOrderStatus,
        completion: WorkOrderCompletion,
        lookups: WorkOrderLookups,
        attributesJson: String?,
        nowMillis: Long,
    ): V2NewCommand {
        val payload = MaintWorkOrdersTransition(
            id = stored.id,
            status = WoStatus.entries.first { it.wire == status.wire },
            failureCode = completion.failureCode.clean(),
            causeCode = completion.causeCode.clean(),
            remedyCode = completion.remedyCode.clean(),
            completionNotes = completion.completionNotes.clean(),
        )
        val set = mutableMapOf<String, Any?>("status" to status.wire)
        if (status == WorkOrderStatus.IN_PROGRESS && stored.startedAt == null) set["started_at"] = nowMillis
        if (status == WorkOrderStatus.COMPLETED) set["completed_at"] = nowMillis
        val present = WorkOrderCompletion(
            failureCode = payload.failureCode, causeCode = payload.causeCode, remedyCode = payload.remedyCode,
            completionNotes = payload.completionNotes,
        )
        val ops = listOf(V2RowOp.upsert("work_orders", stored.id, set + updated(nowMillis))) +
            completionOps(stored, present, lookups, attributesJson, nowMillis, changedOnly = null)
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /**
     * The caller's labor timer. Start/resume opens a time entry ([newEntryId]); pause/stop closes
     * [running]. Starting a work order that has not started moves it to in progress.
     */
    fun timer(
        stored: WorkOrder,
        action: TimerAction,
        running: TimeEntry?,
        workerId: String?,
        newEntryId: String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val ops = mutableListOf<V2RowOp>()
        when (action) {
            TimerAction.START, TimerAction.RESUME -> if (running == null && workerId != null) {
                ops += V2RowOp.upsert(
                    "time_entries", newEntryId,
                    mapOf(
                        "worker_id" to workerId, "calendar_id" to "", "work_order_id" to stored.id, "during_start" to nowMillis,
                        "during_end" to null, "break_minutes" to 0L, "status" to "open",
                    ) + created(nowMillis, userId),
                )
            }
            TimerAction.PAUSE, TimerAction.STOP -> if (running != null) {
                ops += V2RowOp.upsert("time_entries", running.id, mapOf("during_end" to nowMillis) + updated(nowMillis))
            }
        }
        if (action == TimerAction.START && stored.startedAt == null && !stored.status.isTerminal) {
            ops += V2RowOp.upsert(
                "work_orders", stored.id,
                mapOf("status" to WorkOrderStatus.IN_PROGRESS.wire, "started_at" to nowMillis) + updated(nowMillis),
            )
        }
        // The server keeps one time entry per run: pause is a stop and resume a new start.
        val wire = if (action == TimerAction.START || action == TimerAction.RESUME) "start" else "stop"
        return MaintWorkOrdersTimer(id = stored.id, action = wire, at = Instant.ofEpochMilli(nowMillis))
            .toNewCommand(overlay = V2OverlayPatch(ops))
    }

    /** Merges [patch] into the stored attributes (null removes a key); null when nothing changed. */
    fun patchAttributes(stored: WorkOrder, patch: Map<String, JsonElement?>, nowMillis: Long): V2NewCommand? {
        val current = runCatching { Json.parseToJsonElement(stored.attributes) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        val merged = LinkedHashMap<String, JsonElement>(current)
        for ((k, v) in patch) if (v == null || v is JsonNull) merged.remove(k) else merged[k] = v
        val next = JsonObject(merged)
        if (next == current) return null
        return MaintWorkOrdersUpdate(id = stored.id, attributes = next).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("work_orders", stored.id, mapOf("attributes" to next.toString()) + updated(nowMillis))),
        )
    }

    fun delete(stored: WorkOrder): V2NewCommand =
        MaintWorkOrdersDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("work_orders", stored.id)),
        )

    // ------------------------------------------------------------------ comments

    fun comment(id: String, workOrderId: String, body: String?, photo: V2StagedFile?, nowMillis: Long, userId: String?): V2NewCommand {
        val text = body.clean()
        require(text != null || photo != null) { "a comment needs text or a picture" }
        val payload = MaintCommentsCreate(id = id, workOrderId = workOrderId, body = text, photoFileId = photo?.fileId)
        val ops = listOfNotNull(
            photo?.let { MaintCommands.pendingFileRow(it, nowMillis, userId) },
            V2RowOp.upsert(
                "comments", id,
                mapOf(
                    "work_order_id" to workOrderId, "author_user_id" to userId, "body" to text, "comment_type" to "comment",
                    "audience" to "internal", "photo_file_id" to photo?.fileId,
                ) + created(nowMillis, userId),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops), uploads = listOfNotNull(photo?.toUpload()))
    }

    fun deleteComment(id: String, version: Long): V2NewCommand =
        MaintCommentsDelete(id = id).toNewCommand(baseVersion = version, overlay = V2OverlayPatch.of(V2RowOp.delete("comments", id)))

    // ------------------------------------------------------------------ checklist

    /**
     * Adds an ad-hoc row. The server creates the template row (and the instance when the work
     * order has none, [instanceId] null); the overlay shows the row at once through provisional
     * rows: a template row keyed by the response id and, if needed, an instance [newInstanceId].
     */
    fun addChecklistItem(
        id: String,
        workOrderId: String,
        instanceId: String?,
        newInstanceId: String,
        title: String,
        required: Boolean,
        description: String?,
        sortOrder: Int,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val label = requireNotNull(title.clean()) { "a checklist row needs a title" }
        val payload = MaintProcedureResponsesCreate(
            id = id, workOrderId = workOrderId, title = label, rowType = STEP_ROW, required = required,
            description = description.clean(),
        )
        val instance = instanceId ?: newInstanceId
        val ops = mutableListOf<V2RowOp>()
        if (instanceId == null) {
            ops += V2RowOp.upsert(
                "procedure_instances", newInstanceId,
                mapOf("version_id" to "", "work_order_id" to workOrderId) + created(nowMillis, userId),
            )
        }
        ops += listOf(
            V2RowOp.upsert(
                "procedure_template_rows", id,
                mapOf(
                    "version_id" to "", "sort_order" to sortOrder, "row_type" to STEP_ROW, "label" to label,
                    "description" to payload.description, "required" to required, "attributes" to "{}",
                ) + created(nowMillis, userId),
            ),
            V2RowOp.upsert(
                "procedure_responses", id,
                mapOf(
                    "instance_id" to instance, "version_id" to "", "row_id" to id, "completed" to 0L,
                ) + created(nowMillis, userId),
            ),
        )
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    fun recordChecklist(
        stored: ChecklistItem,
        passFail: String?,
        response: String?,
        notes: String?,
        nowMillis: Long,
    ): V2NewCommand {
        val payload = MaintProcedureResponsesRecord(
            id = stored.id,
            response = response.clean()?.let { runCatching { Json.parseToJsonElement(it) }.getOrElse { _ -> JsonPrimitive(it) } },
            passFail = passFail?.let { p -> PassFail.entries.firstOrNull { it.wire == p } },
            notes = notes.clean(),
        )
        val set = buildMap<String, Any?> {
            if (payload.response != null) put("response", payload.response.toString())
            if (payload.passFail != null) put("pass_fail", payload.passFail.wire)
            if (payload.notes != null) put("notes", payload.notes)
        }
        return payload.toNewCommand(overlay = V2OverlayPatch.of(V2RowOp.upsert("procedure_responses", stored.id, set + updated(nowMillis))))
    }

    fun setChecklistCompleted(stored: ChecklistItem, completed: Boolean, nowMillis: Long, userId: String?): V2NewCommand {
        val set = mapOf(
            "completed" to completed, "completed_at" to if (completed) nowMillis else null,
            "completed_by" to if (completed) userId else null,
        ) + updated(nowMillis)
        val overlay = V2OverlayPatch.of(V2RowOp.upsert("procedure_responses", stored.id, set))
        return if (completed) {
            MaintProcedureResponsesComplete(id = stored.id).toNewCommand(overlay = overlay)
        } else {
            MaintProcedureResponsesUncomplete(id = stored.id).toNewCommand(overlay = overlay)
        }
    }

    fun deleteChecklistItem(stored: ChecklistItem): V2NewCommand =
        MaintProcedureResponsesDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("procedure_responses", stored.id)),
        )

    // ------------------------------------------------------------------ assignments

    fun assign(
        id: String,
        workOrderId: String,
        userId: String,
        workerId: String?,
        lead: Boolean,
        estimatedHours: BigDecimal?,
        sortOrder: Int,
        nowMillis: Long,
        actorUserId: String?,
    ): V2NewCommand {
        val role = if (lead) WoAssignmentRole.LEAD else WoAssignmentRole.SECONDARY
        val payload = MaintWorkOrderAssignmentsCreate(
            id = id, workOrderId = workOrderId, userId = userId, role = role, estimatedHours = estimatedHours,
        )
        val ops = mutableListOf<V2RowOp>()
        if (workerId != null) {
            ops += V2RowOp.upsert(
                "work_order_assignments", id,
                mapOf(
                    "work_order_id" to workOrderId, "worker_id" to workerId, "role" to role.wire,
                    "acceptance_status" to "pending", "assigned_by" to actorUserId,
                    "estimated_hours" to estimatedHours?.toPlainString(), "sort_order" to sortOrder,
                ) + created(nowMillis, actorUserId),
            )
            if (lead) ops += V2RowOp.upsert("work_orders", workOrderId, mapOf("assigned_worker_id" to workerId) + updated(nowMillis))
        }
        return payload.toNewCommand(overlay = V2OverlayPatch(ops))
    }

    fun unassign(stored: WorkOrderAssignment): V2NewCommand =
        MaintWorkOrderAssignmentsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("work_order_assignments", stored.id)),
        )

    fun respond(stored: WorkOrderAssignment, accept: Boolean, nowMillis: Long): V2NewCommand {
        val overlay = V2OverlayPatch.of(
            V2RowOp.upsert(
                "work_order_assignments", stored.id,
                mapOf("acceptance_status" to if (accept) "accepted" else "declined") + updated(nowMillis),
            ),
        )
        return if (accept) {
            MaintWorkOrderAssignmentsAccept(id = stored.id).toNewCommand(overlay = overlay)
        } else {
            MaintWorkOrderAssignmentsDecline(id = stored.id).toNewCommand(overlay = overlay)
        }
    }

    // ------------------------------------------------------------------ permits

    fun createPermit(id: String, workOrderId: String, draft: WorkPermitDraft, nowMillis: Long, userId: String?): V2NewCommand {
        val type = requireNotNull(draft.permitType.clean()) { "a permit needs a type" }
        require(draft.validUntil > draft.validFrom) { "a permit must end after it starts" }
        val hazards = draft.hazards.cleanList().orEmpty()
        val precautions = draft.precautions.cleanList().orEmpty()
        val payload = MaintWorkPermitsCreate(
            id = id, workOrderId = workOrderId, permitType = type,
            validFrom = Instant.ofEpochMilli(draft.validFrom), validUntil = Instant.ofEpochMilli(draft.validUntil),
            hazards = JsonArray(hazards.map(::JsonPrimitive)), precautions = JsonArray(precautions.map(::JsonPrimitive)),
            notes = draft.notes.clean(),
        )
        val row = mapOf(
            "work_order_id" to workOrderId, "permit_type" to type, "hazards" to jsonList(hazards),
            "precautions" to jsonList(precautions), "valid_during_start" to draft.validFrom,
            "valid_during_end" to draft.validUntil, "status" to "draft", "notes" to payload.notes,
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch.of(V2RowOp.upsert("work_permits", id, row)))
    }

    fun updatePermitStatus(stored: WorkPermit, status: String, nowMillis: Long): V2NewCommand =
        MaintWorkPermitsUpdate(id = stored.id, status = status).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("work_permits", stored.id, mapOf("status" to status) + updated(nowMillis))),
        )

    fun signPermit(
        stored: WorkPermit,
        signatureId: String,
        role: String,
        signedName: String,
        nowMillis: Long,
        userId: String?,
    ): V2NewCommand {
        val name = requireNotNull(signedName.clean()) { "a signature needs a name" }
        val payload = MaintWorkPermitsSign(id = stored.id, role = role, signedName = name)
        val signerRole = if (role in SIGNER_ROLES) role else "permit_holder"
        return payload.toNewCommand(
            overlay = V2OverlayPatch.of(
                V2RowOp.upsert(
                    "signatures", signatureId,
                    mapOf(
                        "work_permit_id" to stored.id, "signer_name" to name, "signer_role" to signerRole,
                        "signed_at" to nowMillis, "received_at" to nowMillis, "method" to "in_person", "recorded_by" to userId,
                    ) + created(nowMillis, userId),
                ),
            ),
        )
    }

    fun deletePermit(stored: WorkPermit): V2NewCommand =
        MaintWorkPermitsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("work_permits", stored.id)),
        )

    // ------------------------------------------------------------------ RCA

    fun createRca(id: String, workOrderId: String, assetId: String?, draft: RcaDraft, nowMillis: Long, userId: String?): V2NewCommand {
        val payload = MaintRcaReportsCreate(
            id = id, workOrderId = workOrderId, assetId = assetId,
            problemDescription = draft.problemDescription.clean(), immediateAction = draft.immediateAction.clean(),
            rootCause = draft.rootCause.clean(), correctiveAction = draft.correctiveAction.clean(),
            preventiveAction = draft.preventiveAction.clean(), status = draft.status.clean(),
        )
        val row = mapOf(
            "work_order_id" to workOrderId, "asset_id" to assetId, "problem_description" to payload.problemDescription,
            "immediate_action" to payload.immediateAction, "root_cause" to payload.rootCause,
            "corrective_action" to payload.correctiveAction, "preventive_action" to payload.preventiveAction,
            "status" to (payload.status ?: "draft"),
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch.of(V2RowOp.upsert("rca_reports", id, row)))
    }

    /** Null when nothing changed. */
    fun updateRca(stored: RcaReport, draft: RcaDraft, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(MaintRcaReportsUpdate.Clear.entries)
        val problem = d.text("problem_description", stored.problemDescription, draft.problemDescription)
        val immediate = d.text("immediate_action", stored.immediateAction, draft.immediateAction)
        val root = d.text("root_cause", stored.rootCause, draft.rootCause)
        val corrective = d.text("corrective_action", stored.correctiveAction, draft.correctiveAction)
        val preventive = d.text("preventive_action", stored.preventiveAction, draft.preventiveAction)
        val status = draft.status.clean()?.let { d.text("status", stored.status, it) }
        d.check()
        if (d.isEmpty) return null
        val payload = MaintRcaReportsUpdate(
            id = stored.id, problemDescription = problem, immediateAction = immediate, rootCause = root,
            correctiveAction = corrective, preventiveAction = preventive, status = status, clear = d.clear,
        )
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("rca_reports", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun deleteRca(stored: RcaReport): V2NewCommand =
        MaintRcaReportsDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("rca_reports", stored.id)),
        )

    // ------------------------------------------------------------------ templates

    fun createTemplate(id: String, draft: WorkOrderTemplateDraft, categoryId: String?, nowMillis: Long, userId: String?): V2NewCommand {
        val title = requireNotNull(draft.title.clean()) { "a template needs a title" }
        val payload = MaintWorkOrderTemplatesCreate(
            id = id, title = title, titleTemplate = draft.titleTemplate.clean(),
            descriptionTemplate = draft.descriptionTemplate.clean(), category = draft.category.clean(),
            priority = draft.priority?.let { p -> WoPriority.entries.first { it.wire == p.wire } },
            estimatedEffortMinutes = draft.estimatedEffortMinutes, requiredSkills = draft.requiredSkills.cleanList(),
        )
        val row = mapOf(
            "title" to title, "title_template" to payload.titleTemplate, "description_template" to payload.descriptionTemplate,
            "category_id" to categoryId, "priority" to draft.priority?.wire,
            "estimated_effort_minutes" to draft.estimatedEffortMinutes,
            "required_skills" to payload.requiredSkills?.let(::jsonList), "attributes" to "{}",
        ) + created(nowMillis, userId)
        return payload.toNewCommand(overlay = V2OverlayPatch.of(V2RowOp.upsert("work_order_templates", id, row)))
    }

    /** Null when nothing changed. */
    fun updateTemplate(stored: WorkOrderTemplate, draft: WorkOrderTemplateDraft, categoryId: String?, nowMillis: Long): V2NewCommand? {
        val d = V2UpdateDiff(MaintWorkOrderTemplatesUpdate.Clear.entries)
        val title = d.text("title", stored.title, draft.title)
        val titleTemplate = d.text("title_template", stored.titleTemplate, draft.titleTemplate)
        val description = d.text("description_template", stored.descriptionTemplate, draft.descriptionTemplate)
        val category = d.text("category", stored.categoryName, draft.category, column = null)
        if (category != null || (draft.category.clean() == null && stored.categoryName != null)) d.overlay["category_id"] = categoryId
        val priority = draft.priority?.let { d.text("priority", stored.priority?.wire, it.wire) }
        val effort = d.field("estimated_effort_minutes", stored.estimatedEffortMinutes, draft.estimatedEffortMinutes)
        val skills = d.field("required_skills", stored.requiredSkills.cleanList(), draft.requiredSkills.cleanList(), toColumn = ::jsonList)
        d.check()
        if (d.isEmpty) return null
        val payload = MaintWorkOrderTemplatesUpdate(
            id = stored.id, title = title, titleTemplate = titleTemplate, descriptionTemplate = description,
            category = category, priority = priority?.let { p -> WoPriority.entries.first { it.wire == p } },
            estimatedEffortMinutes = effort, requiredSkills = skills, clear = d.clear,
        )
        return payload.toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.upsert("work_order_templates", stored.id, d.overlay + updated(nowMillis))),
        )
    }

    fun deleteTemplate(stored: WorkOrderTemplate): V2NewCommand =
        MaintWorkOrderTemplatesDelete(id = stored.id).toNewCommand(
            baseVersion = stored.version,
            overlay = V2OverlayPatch.of(V2RowOp.delete("work_order_templates", stored.id)),
        )

    // ------------------------------------------------------------------ helpers

    private const val STEP_ROW = "step"
    private const val MAX_BREAK_MINUTES = 1440
    private val END_TYPES = setOf("never", "count", "date")
    private val SIGNER_ROLES = setOf("customer", "staff", "driver", "witness", "permit_holder", "approver")

    private fun assetLinks(workOrderId: String, assetIds: List<String>, nowMillis: Long, userId: String?): List<V2RowOp> =
        assetIds.mapIndexed { index, assetId ->
            V2RowOp.upsert(
                "work_order_assets",
                mapOf("work_order_id" to workOrderId, "asset_id" to assetId),
                mapOf("is_primary" to (index == 0), "sort_order" to index) + created(nowMillis, userId),
            )
        }

    /**
     * Overlay ops for completion fields: codes go to the `*_value_id` columns; notes go to the
     * completion activity when there is one, else to `attributes.inspection.notes`. With
     * [changedOnly] only the fields the diff kept are applied (clears included).
     */
    private fun completionOps(
        stored: WorkOrder,
        completion: WorkOrderCompletion,
        lookups: WorkOrderLookups,
        attributesJson: String?,
        nowMillis: Long,
        changedOnly: V2UpdateDiff<*>?,
    ): List<V2RowOp> {
        fun touched(wire: String, value: String?): Boolean =
            if (changedOnly == null) value != null else value.clean() != current(stored, wire)
        val set = mutableMapOf<String, Any?>()
        listOf(
            Triple("failure_code", "failure_value_id", completion.failureCode to "wo_failure"),
            Triple("cause_code", "cause_value_id", completion.causeCode to "wo_cause"),
            Triple("remedy_code", "remedy_value_id", completion.remedyCode to "wo_remedy"),
        ).forEach { (wire, column, valueAndKind) ->
            val (value, kind) = valueAndKind
            if (touched(wire, value)) set[column] = value.clean()?.let { lookups.codeIdByKindAndCode(kind, it) }
        }
        val ops = mutableListOf<V2RowOp>()
        val notes = completion.completionNotes
        if (touched("completion_notes", notes)) {
            val activity = stored.completionActivityId
            if (activity != null) {
                ops += V2RowOp.upsert("activity_records", activity, mapOf("notes" to notes.clean()) + updated(nowMillis))
            } else {
                set["attributes"] = withInspectionNotes(attributesJson, notes.clean())
            }
        }
        if (set.isNotEmpty()) ops += V2RowOp.upsert("work_orders", stored.id, set + updated(nowMillis))
        return ops
    }

    private fun current(stored: WorkOrder, wire: String): String? = when (wire) {
        "failure_code" -> stored.failureCode
        "cause_code" -> stored.causeCode
        "remedy_code" -> stored.remedyCode
        else -> stored.completionNotes
    }.clean()

    internal fun withInspectionNotes(attributesJson: String?, notes: String?): String {
        val root = attributesJson?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: JsonObject(emptyMap())
        val inspection = (root["inspection"] as? JsonObject) ?: JsonObject(emptyMap())
        val nextInspection = JsonObject(
            if (notes == null) inspection - "notes" else inspection + ("notes" to JsonPrimitive(notes)),
        )
        return JsonObject(root + ("inspection" to nextInspection)).toString()
    }
}
