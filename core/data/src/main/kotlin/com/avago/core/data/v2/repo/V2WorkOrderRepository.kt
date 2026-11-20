package com.avago.core.data.v2.repo

import android.net.Uri
import com.avago.core.data.domain.ActivityFilter
import com.avago.core.data.domain.ApprovalDecision
import com.avago.core.data.domain.ApprovalRequest
import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.ChecklistItem
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.PermitSignature
import com.avago.core.data.domain.Project
import com.avago.core.data.domain.RcaDraft
import com.avago.core.data.domain.RcaReport
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.RecurrenceRule
import com.avago.core.data.domain.SlaPolicy
import com.avago.core.data.domain.TimeEntry
import com.avago.core.data.domain.TimeEntryDraft
import com.avago.core.data.domain.TimerAction
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderAssignment
import com.avago.core.data.domain.WorkOrderComment
import com.avago.core.data.domain.WorkOrderCompletion
import com.avago.core.data.domain.WorkOrderComplexity
import com.avago.core.data.domain.WorkOrderDetail
import com.avago.core.data.domain.WorkOrderDraft
import com.avago.core.data.domain.WorkOrderFilter
import com.avago.core.data.domain.WorkOrderPriority
import com.avago.core.data.domain.WorkOrderRecurrence
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderStatus
import com.avago.core.data.domain.WorkOrderTemplate
import com.avago.core.data.domain.WorkOrderTemplateDraft
import com.avago.core.data.domain.WorkPermit
import com.avago.core.data.domain.WorkPermitDraft
import com.avago.core.data.domain.Worker
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2FileStager
import com.avago.core.data.v2.V2OverlayMerge
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.bool
import com.avago.core.data.v2.commands.MaintCommands
import com.avago.core.data.v2.commands.WorkOrderCommands
import com.avago.core.data.v2.commands.WorkOrderLookups
import com.avago.core.data.v2.commands.parseJsonList
import com.avago.core.data.v2.decimalOrNull
import com.avago.core.data.v2.intOrNull
import com.avago.core.data.v2.longOrNull
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2WorkOrderRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
    private val stager: V2FileStager,
) : WorkOrderRepository {

    override fun observeWorkOrders(filter: WorkOrderFilter): Flow<List<WorkOrder>> =
        store.observe(LIST_TABLES, emptyList()) { conn -> workOrders(conn, filter) }

    override fun observeWorkOrder(id: String): Flow<WorkOrder?> = store.observe(LIST_TABLES, null) { conn -> byId(conn, id) }

    override suspend fun workOrder(id: String): WorkOrder? = store.read(null) { conn -> byId(conn, id) }

    override fun observeDetail(id: String): Flow<WorkOrderDetail?> =
        store.observe(DETAIL_TABLES, null) { conn -> detail(conn, id, commands.currentUserId(), stager::localFile) }

    override fun observeTemplates(): Flow<List<WorkOrderTemplate>> =
        store.observe(setOf("work_order_templates", "categories"), emptyList()) { conn -> templates(conn) }

    override suspend fun create(draft: WorkOrderDraft, attributes: Map<String, JsonElement>): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val lookups = store.read(WorkOrderLookups()) { conn -> lookups(conn) }
        commands.submit(WorkOrderCommands.create(id, draft, lookups, now, commands.currentUserId(), attributes))
        return id
    }

    override suspend fun update(id: String, draft: WorkOrderDraft) {
        val ctx = context(id)
        WorkOrderCommands.update(ctx.stored, draft, ctx.lookups, System.currentTimeMillis(), commands.currentUserId(), ctx.assignedUserId)
            ?.let { commands.submit(it) }
    }

    override suspend fun edit(id: String, change: (WorkOrderDraft) -> WorkOrderDraft) {
        val ctx = context(id)
        val draft = change(WorkOrderDraft.of(ctx.stored, ctx.assignedUserId))
        WorkOrderCommands.update(ctx.stored, draft, ctx.lookups, System.currentTimeMillis(), commands.currentUserId(), ctx.assignedUserId)
            ?.let { commands.submit(it) }
    }

    override suspend fun updateCompletion(id: String, completion: WorkOrderCompletion) {
        val ctx = context(id)
        WorkOrderCommands.updateCompletion(ctx.stored, completion, ctx.lookups, ctx.attributes, System.currentTimeMillis())
            ?.let { commands.submit(it) }
    }

    override suspend fun transition(id: String, status: WorkOrderStatus, completion: WorkOrderCompletion) {
        val ctx = context(id)
        commands.submit(WorkOrderCommands.transition(ctx.stored, status, completion, ctx.lookups, ctx.attributes, System.currentTimeMillis()))
    }

    override suspend fun patchAttributes(id: String, patch: Map<String, JsonElement?>) {
        val ctx = context(id)
        WorkOrderCommands.patchAttributes(ctx.stored, patch, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun timer(id: String, action: TimerAction) {
        val userId = commands.currentUserId()
        val (stored, me, running) = store.read(null) { conn ->
            val wo = byId(conn, id) ?: return@read null
            val worker = V2MaintRows.workerByUser(conn, userId)
            Triple(wo, worker, worker?.let { w -> timeEntries(conn, id).firstOrNull { it.workerId == w.id && it.isRunning } })
        } ?: throw RecordNotFoundException("work order", id)
        val now = System.currentTimeMillis()
        commands.submit(WorkOrderCommands.timer(stored, action, running, me?.id, V2Uuid.v7(now), now, userId))
    }

    override suspend fun delete(id: String) {
        commands.submit(WorkOrderCommands.delete(workOrder(id) ?: throw RecordNotFoundException("work order", id)))
    }

    override suspend fun addComment(workOrderId: String, body: String, photo: LocalFileRef?): String {
        val staged = photo?.let { stager.stage(Uri.parse(it.uri), it.fallbackMediaType) }
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(WorkOrderCommands.comment(id, workOrderId, body, staged, now, commands.currentUserId()))
        return id
    }

    override suspend fun deleteComment(commentId: String) {
        val version = store.read(null) { conn -> V2MaintRows.version(conn, "comments", commentId) }
            ?: throw RecordNotFoundException("comment", commentId)
        commands.submit(WorkOrderCommands.deleteComment(commentId, version))
    }

    override suspend fun addChecklistItem(workOrderId: String, title: String, required: Boolean, description: String?): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val (instanceId, nextSort) = store.read(null to 0) { conn ->
            val instance = instances(conn, workOrderId).firstOrNull()
            instance to (checklist(conn, workOrderId).maxOfOrNull { it.sortOrder + 1 } ?: 0)
        }
        commands.submit(
            WorkOrderCommands.addChecklistItem(
                id, workOrderId, instanceId, V2Uuid.v7(now), title, required, description, nextSort, now, commands.currentUserId(),
            ),
        )
        return id
    }

    override suspend fun recordChecklist(itemId: String, passFail: String?, response: String?, notes: String?, complete: Boolean) {
        val stored = checklistItem(itemId)
        val now = System.currentTimeMillis()
        val record = commands.submit(WorkOrderCommands.recordChecklist(stored, passFail, response, notes, now))
        if (complete && !stored.completed) {
            commands.submit(
                WorkOrderCommands.setChecklistCompleted(stored, true, now, commands.currentUserId()).copy(dependsOn = listOf(record)),
            )
        }
    }

    override suspend fun setChecklistCompleted(itemId: String, completed: Boolean) {
        val stored = checklistItem(itemId)
        if (stored.completed == completed) return
        commands.submit(WorkOrderCommands.setChecklistCompleted(stored, completed, System.currentTimeMillis(), commands.currentUserId()))
    }

    override suspend fun deleteChecklistItem(itemId: String) {
        commands.submit(WorkOrderCommands.deleteChecklistItem(checklistItem(itemId)))
    }

    override suspend fun addCostLine(workOrderId: String, draft: CostLineDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val (next, worker) = store.read(0 to null as String?) { conn ->
            val lines = V2MaintRows.costLines(conn, "work_order_id = ?", listOf(workOrderId)) { it["work_order_id"] == workOrderId }
            (lines.maxOfOrNull { it.sortOrder + 1 } ?: 0) to V2MaintRows.workerByUser(conn, draft.performerUserId)?.id
        }
        commands.submit(MaintCommands.createCostLine(id, null, workOrderId, draft, next, now, commands.currentUserId(), worker))
        return id
    }

    override suspend fun updateCostLine(lineId: String, draft: CostLineDraft) {
        val (stored, performerUser, worker) = V2ActivityRepository.costLineContext(store, lineId, draft.performerUserId)
        MaintCommands.updateCostLine(stored, draft, System.currentTimeMillis(), performerUser, worker)?.let { commands.submit(it) }
    }

    override suspend fun deleteCostLine(lineId: String) {
        val stored = store.read(null) { conn -> V2ActivityRepository.costLine(conn, lineId) }
            ?: throw RecordNotFoundException("cost line", lineId)
        commands.submit(MaintCommands.deleteCostLine(stored))
    }

    override suspend fun assign(workOrderId: String, userId: String, lead: Boolean, estimatedHours: BigDecimal?): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val (worker, next) = store.read(null as String? to 0) { conn ->
            V2MaintRows.workerByUser(conn, userId)?.id to (assignments(conn, workOrderId, emptyList()).size)
        }
        commands.submit(WorkOrderCommands.assign(id, workOrderId, userId, worker, lead, estimatedHours, next, now, commands.currentUserId()))
        return id
    }

    override suspend fun unassign(assignmentId: String) {
        commands.submit(WorkOrderCommands.unassign(assignment(assignmentId)))
    }

    override suspend fun respondToAssignment(assignmentId: String, accept: Boolean) {
        commands.submit(WorkOrderCommands.respond(assignment(assignmentId), accept, System.currentTimeMillis()))
    }

    override suspend fun createPermit(workOrderId: String, draft: WorkPermitDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(WorkOrderCommands.createPermit(id, workOrderId, draft, now, commands.currentUserId()))
        return id
    }

    override suspend fun updatePermitStatus(permitId: String, status: String) {
        commands.submit(WorkOrderCommands.updatePermitStatus(permit(permitId), status, System.currentTimeMillis()))
    }

    override suspend fun signPermit(permitId: String, role: String, signedName: String) {
        val now = System.currentTimeMillis()
        commands.submit(
            WorkOrderCommands.signPermit(permit(permitId), V2Uuid.v7(now), role, signedName, now, commands.currentUserId()),
        )
    }

    override suspend fun deletePermit(permitId: String) {
        commands.submit(WorkOrderCommands.deletePermit(permit(permitId)))
    }

    override suspend fun saveRca(workOrderId: String, draft: RcaDraft): String {
        val (existing, assetId) = store.read(null as RcaReport? to null as String?) { conn ->
            rca(conn, workOrderId) to workOrderAssetIds(conn, workOrderId)[workOrderId]?.firstOrNull()
        }
        val now = System.currentTimeMillis()
        if (existing != null) {
            WorkOrderCommands.updateRca(existing, draft, now)?.let { commands.submit(it) }
            return existing.id
        }
        val id = V2Uuid.v7(now)
        commands.submit(WorkOrderCommands.createRca(id, workOrderId, assetId, draft, now, commands.currentUserId()))
        return id
    }

    override suspend fun deleteRca(rcaId: String) {
        val stored = store.read(null) { conn ->
            V2OverlayMerge.query(conn, "rca_reports", "id = ?", listOf(rcaId)) { it["id"] == rcaId }.firstOrNull()?.let(::rca)
        } ?: throw RecordNotFoundException("rca report", rcaId)
        commands.submit(WorkOrderCommands.deleteRca(stored))
    }

    override suspend fun createTemplate(draft: WorkOrderTemplateDraft): String {
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        val categoryId = store.read(null) { conn -> draft.category?.let { lookups(conn).categoryIdByName(it) } }
        commands.submit(WorkOrderCommands.createTemplate(id, draft, categoryId, now, commands.currentUserId()))
        return id
    }

    override suspend fun updateTemplate(id: String, draft: WorkOrderTemplateDraft) {
        val (stored, categoryId) = store.read(null) { conn ->
            val t = templates(conn).firstOrNull { it.id == id } ?: return@read null
            t to draft.category?.let { lookups(conn).categoryIdByName(it) }
        } ?: throw RecordNotFoundException("work order template", id)
        WorkOrderCommands.updateTemplate(stored, draft, categoryId, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun deleteTemplate(id: String) {
        val stored = store.read(null) { conn -> templates(conn).firstOrNull { it.id == id } }
            ?: throw RecordNotFoundException("work order template", id)
        commands.submit(WorkOrderCommands.deleteTemplate(stored))
    }

    override suspend fun attach(workOrderId: String, file: LocalFileRef): String {
        val staged = stager.stage(Uri.parse(file.uri), file.fallbackMediaType)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(MaintCommands.attach(id, AttachmentTarget.ToWorkOrder(workOrderId), staged, null, now, commands.currentUserId()))
        return id
    }

    override suspend fun claim(id: String) {
        val userId = commands.currentUserId()
        val (stored, me) = store.read(null) { conn ->
            val wo = byId(conn, id) ?: return@read null
            wo to V2MaintRows.workerByUser(conn, userId)?.id
        } ?: throw RecordNotFoundException("work order", id)
        commands.submit(WorkOrderCommands.claim(stored, me, System.currentTimeMillis()))
    }

    override suspend fun setRecurrence(id: String, rule: RecurrenceRule) {
        val current = store.read(null) { conn -> recurrence(conn, id) }
        commands.submit(WorkOrderCommands.setRecurrence(id, rule, current, System.currentTimeMillis()))
    }

    override suspend fun decideApproval(requestId: String, approve: Boolean, notes: String?) {
        val request = store.read(null) { conn ->
            approvalRequests(conn, "id = ?", listOf(requestId)) { it["id"] == requestId }.firstOrNull()
        } ?: throw RecordNotFoundException("approval request", requestId)
        val now = System.currentTimeMillis()
        commands.submit(WorkOrderCommands.decideApproval(request, approve, notes, V2Uuid.v7(now), now, commands.currentUserId()))
    }

    override suspend fun logTime(workOrderId: String, draft: TimeEntryDraft): String {
        val userId = commands.currentUserId()
        val me = store.read(null) { conn -> V2MaintRows.workerByUser(conn, userId)?.id }
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(WorkOrderCommands.logTime(id, workOrderId, draft, me, now, userId))
        return id
    }

    override suspend fun updateTimeEntry(id: String, draft: TimeEntryDraft) {
        WorkOrderCommands.updateTimeEntry(timeEntry(id), draft, System.currentTimeMillis())?.let { commands.submit(it) }
    }

    override suspend fun deleteTimeEntry(id: String) {
        commands.submit(WorkOrderCommands.deleteTimeEntry(timeEntry(id)))
    }

    override suspend fun approveTimeEntry(id: String) {
        commands.submit(WorkOrderCommands.approveTimeEntry(timeEntry(id), System.currentTimeMillis(), commands.currentUserId()))
    }

    override suspend fun rejectTimeEntry(id: String, reason: String) {
        commands.submit(WorkOrderCommands.rejectTimeEntry(timeEntry(id), reason, System.currentTimeMillis()))
    }

    override fun observeProjects(): Flow<List<Project>> = store.observe(setOf("projects"), emptyList()) { conn -> projects(conn) }

    override fun observeSlaPolicies(): Flow<List<SlaPolicy>> = store.observe(setOf("work_order_sla_policies"), emptyList()) { conn ->
        V2OverlayMerge.query(conn, "work_order_sla_policies").map {
            SlaPolicy(
                priority = WorkOrderPriority.fromWire(it.strOrNull("priority")),
                respondHours = it.intOrNull("respond_hours"),
                resolveHours = it.intOrNull("resolve_hours"),
            )
        }
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun timeEntry(id: String): TimeEntry = store.read(null) { conn ->
        V2OverlayMerge.query(conn, "time_entries", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()?.let { V2MaintRows.timeEntry(it) }
    } ?: throw RecordNotFoundException("time entry", id)

    private class Context(val stored: WorkOrder, val attributes: String?, val assignedUserId: String?, val lookups: WorkOrderLookups)

    private suspend fun context(id: String): Context = store.read(null) { conn ->
        val raw = V2OverlayMerge.query(conn, "work_orders", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull() ?: return@read null
        val wo = byId(conn, id) ?: return@read null
        val assignee = V2MaintRows.workers(conn).firstOrNull { it.id == wo.assignedWorkerId }?.userId
        Context(wo, raw.strOrNull("attributes"), assignee, lookups(conn))
    } ?: throw RecordNotFoundException("work order", id)

    private suspend fun checklistItem(id: String): ChecklistItem = store.read(null) { conn ->
        val r = V2OverlayMerge.query(conn, "procedure_responses", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull() ?: return@read null
        checklistItems(conn, listOf(r)).firstOrNull()
    } ?: throw RecordNotFoundException("checklist item", id)

    private suspend fun assignment(id: String): WorkOrderAssignment = store.read(null) { conn ->
        val r = V2OverlayMerge.query(conn, "work_order_assignments", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()
            ?: return@read null
        assignment(r, V2MaintRows.workers(conn).associateBy { it.id })
    } ?: throw RecordNotFoundException("assignment", id)

    private suspend fun permit(id: String): WorkPermit = store.read(null) { conn ->
        V2OverlayMerge.query(conn, "work_permits", "id = ?", listOf(id)) { it["id"] == id }.firstOrNull()?.let { permit(conn, it) }
    } ?: throw RecordNotFoundException("work permit", id)

    internal companion object {
        val LIST_TABLES = setOf(
            "work_orders", "work_order_assets", "assets", "locations", "categories", "workers", "reference_values", "activity_records", "projects",
        )
        val DETAIL_TABLES = LIST_TABLES + setOf(
            "work_order_assignments", "procedure_instances", "procedure_responses", "procedure_template_rows", "comments",
            "activity_cost_lines", "meter_readings", "work_permits", "signatures", "rca_reports", "attachments", "files", "time_entries",
            "approval_requests", "approval_decisions", "pm_occurrences", "pm_plans", "pm_triggers",
        )

        fun projects(conn: V2Connection): List<Project> = V2OverlayMerge.query(conn, "projects").map { r ->
            Project(
                id = r.str("id"),
                number = r.strOrNull("number"),
                name = r.str("name"),
                customerName = r.strOrNull("customer_name_snapshot"),
                status = r.strOrNull("status") ?: "active",
                budgetMinor = r.longOrNull("budget_minor"),
                currency = r.strOrNull("currency"),
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.number ?: it.name })

        fun approvalRequests(conn: V2Connection, where: String, args: List<Any?>, predicate: (V2Row) -> Boolean): List<ApprovalRequest> {
            val rows = V2OverlayMerge.query(conn, "approval_requests", where, args, predicate)
            if (rows.isEmpty()) return emptyList()
            val ids = rows.map { it.str("id") }.toSet()
            val decisions = V2OverlayMerge.query(conn, "approval_decisions") { it["request_id"] in ids }
                .filter { it.str("request_id") in ids }
                .groupBy { it.str("request_id") }
            return rows.map { r ->
                ApprovalRequest(
                    id = r.str("id"),
                    subjectKind = r.str("subject_kind"),
                    workOrderId = r.strOrNull("work_order_id"),
                    purchaseOrderId = r.strOrNull("purchase_order_id"),
                    requestedAmountMinor = r.longOrNull("requested_amount_minor"),
                    currency = r.strOrNull("currency"),
                    status = r.strOrNull("status") ?: ApprovalRequest.STATUS_PENDING,
                    requestedBy = r.strOrNull("requested_by"),
                    requestedAt = r.longOrNull("requested_at"),
                    assignedApproverUserId = r.strOrNull("assigned_approver_user_id"),
                    decidedAt = r.longOrNull("decided_at"),
                    decisions = decisions[r.str("id")].orEmpty().map { d ->
                        ApprovalDecision(
                            id = d.str("id"),
                            approverUserId = d.str("approver_user_id"),
                            decision = d.str("decision"),
                            comment = d.strOrNull("comment"),
                            decidedAt = d.longOrNull("decided_at") ?: 0L,
                        )
                    }.sortedBy { it.decidedAt },
                )
            }.sortedByDescending { it.requestedAt ?: 0L }
        }

        /** The calendar recurrence of the PM plan whose occurrence [workOrderId] is. */
        fun recurrence(conn: V2Connection, workOrderId: String): WorkOrderRecurrence? {
            val planId = V2OverlayMerge.query(conn, "pm_occurrences", "work_order_id = ?", listOf(workOrderId)) { it["work_order_id"] == workOrderId }
                .firstOrNull()?.strOrNull("pm_plan_id") ?: return null
            val plan = V2OverlayMerge.query(conn, "pm_plans", "id = ?", listOf(planId)) { it["id"] == planId }.firstOrNull() ?: return null
            val trigger = V2OverlayMerge.query(conn, "pm_triggers", "pm_plan_id = ? AND kind = 'calendar'", listOf(planId)) {
                it["pm_plan_id"] == planId && it["kind"] == "calendar"
            }.minByOrNull { it.longOrNull("created_at") ?: 0L }
            val rrule = trigger?.strOrNull("rrule").orEmpty()
            return WorkOrderRecurrence(
                planId = planId,
                triggerId = trigger?.str("id"),
                rrule = rrule,
                endType = plan.strOrNull("end_type") ?: "never",
                endCount = plan.intOrNull("end_count"),
                endOnEpochDay = plan.strOrNull("end_on")?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() },
                timezone = plan.strOrNull("timezone"),
                active = plan.bool("active") && rrule.isNotEmpty(),
            )
        }

        fun lookups(conn: V2Connection): WorkOrderLookups {
            val categories = V2OverlayMerge.query(conn, "categories", "kind = 'work_order'") { it["kind"] == "work_order" }
                .associate { it.str("name").lowercase() to it.str("id") }
            val codes = V2OverlayMerge.query(conn, "reference_values")
                .associate { (it.str("kind") to it.str("code")) to it.str("id") }
            val workers = V2MaintRows.workers(conn).mapNotNull { w -> w.userId?.let { it to w.id } }.toMap()
            return WorkOrderLookups(
                categoryIdByName = { categories[it.trim().lowercase()] },
                codeIdByKindAndCode = { kind, code -> codes[kind to code] },
                workerIdByUserId = { workers[it] },
            )
        }

        fun workOrders(conn: V2Connection, filter: WorkOrderFilter): List<WorkOrder> {
            val q = filter.query.trim().lowercase()
            return mapped(conn, V2OverlayMerge.query(conn, "work_orders")).filter { w ->
                (filter.statuses.isEmpty() || w.status in filter.statuses) &&
                    (filter.assetId == null || filter.assetId in w.assetIds) &&
                    (filter.assignedWorkerId == null || w.assignedWorkerId == filter.assignedWorkerId) &&
                    (!filter.unassignedOnly || (w.assignedWorkerId == null && !w.status.isTerminal)) &&
                    (filter.dueFrom == null || (w.dueAt != null && w.dueAt >= filter.dueFrom)) &&
                    (filter.dueTo == null || (w.dueAt != null && w.dueAt < filter.dueTo)) &&
                    (
                        q.isEmpty() || listOfNotNull(w.title, w.description, w.categoryName, w.primaryAssetName, w.locationName, w.assignedWorkerName)
                            .any { it.lowercase().contains(q) }
                        )
            }.sortedWith(
                compareBy<WorkOrder> { it.status.isTerminal }
                    .thenBy(nullsLast()) { it.dueAt }
                    .thenByDescending { it.createdAt },
            )
        }

        fun byId(conn: V2Connection, id: String): WorkOrder? =
            mapped(conn, V2OverlayMerge.query(conn, "work_orders", "id = ?", listOf(id)) { it["id"] == id }).firstOrNull()

        private fun mapped(conn: V2Connection, rows: List<V2Row>): List<WorkOrder> {
            if (rows.isEmpty()) return emptyList()
            val single = rows.singleOrNull()?.str("id")
            val assetsByWo = workOrderAssetIds(conn, single)
            val assetNames = V2MaintRows.assetNames(conn)
            val locations = conn.query("SELECT id, name FROM locations").associate { it.str("id") to it.str("name") }
            val categories = V2OverlayMerge.query(conn, "categories").associate { it.str("id") to it.str("name") }
            val workers = V2MaintRows.workers(conn).associate { it.id to it.displayName }
            val projects = V2OverlayMerge.query(conn, "projects").associate { it.str("id") to it.str("name") }
            val codes = V2OverlayMerge.query(conn, "reference_values").associate { it.str("id") to it.str("code") }
            val completionIds = rows.mapNotNull { it.strOrNull("completion_activity_id") }.toSet()
            val completionNotes = if (completionIds.isEmpty()) {
                emptyMap()
            } else {
                V2OverlayMerge.query(conn, "activity_records") { it["id"] in completionIds }
                    .filter { it.str("id") in completionIds }
                    .associate { it.str("id") to it.strOrNull("notes") }
            }
            return rows.map { r ->
                val id = r.str("id")
                val assetIds = assetsByWo[id].orEmpty()
                val completion = r.strOrNull("completion_activity_id")
                WorkOrder(
                    id = id,
                    title = r.str("title"),
                    description = r.strOrNull("description"),
                    categoryId = r.strOrNull("category_id"),
                    categoryName = r.strOrNull("category_id")?.let(categories::get),
                    priority = WorkOrderPriority.fromWire(r.strOrNull("priority")),
                    status = WorkOrderStatus.fromWire(r.strOrNull("status")),
                    complexity = WorkOrderComplexity.fromWire(r.strOrNull("complexity")),
                    approvalState = r.strOrNull("approval_state"),
                    requesterUserId = r.strOrNull("requester_user_id"),
                    assignedWorkerId = r.strOrNull("assigned_worker_id"),
                    assignedWorkerName = r.strOrNull("assigned_worker_id")?.let(workers::get),
                    assignedTeamId = r.strOrNull("assigned_team_id"),
                    locationId = r.strOrNull("location_id"),
                    locationName = r.strOrNull("location_id")?.let(locations::get),
                    projectId = r.strOrNull("project_id"),
                    projectName = r.strOrNull("project_id")?.let(projects::get),
                    assetIds = assetIds,
                    primaryAssetName = assetIds.firstOrNull()?.let(assetNames::get),
                    dueAt = r.longOrNull("due_at"),
                    plannedStart = r.longOrNull("planned_during_start"),
                    plannedEnd = r.longOrNull("planned_during_end"),
                    startedAt = r.longOrNull("started_at"),
                    completedAt = r.longOrNull("completed_at"),
                    completionActivityId = completion,
                    failureCode = r.strOrNull("failure_value_id")?.let(codes::get),
                    causeCode = r.strOrNull("cause_value_id")?.let(codes::get),
                    remedyCode = r.strOrNull("remedy_value_id")?.let(codes::get),
                    completionNotes = completion?.let(completionNotes::get) ?: inspectionNotes(r.strOrNull("attributes")),
                    skipReason = r.strOrNull("skip_reason"),
                    requiresPermit = r.bool("requires_permit"),
                    requiredSkills = parseJsonList(r.strOrNull("required_skills")),
                    estimatedEffortMinutes = r.intOrNull("estimated_effort_minutes"),
                    currency = r.strOrNull("currency"),
                    dispatcherNotes = r.strOrNull("dispatcher_notes"),
                    attributes = r.strOrNull("attributes") ?: "{}",
                    createdBy = r.strOrNull("created_by"),
                    createdAt = r.longOrNull("created_at") ?: 0L,
                    updatedAt = r.longOrNull("updated_at") ?: 0L,
                    version = r.longOrNull("version") ?: 0L,
                )
            }
        }

        /** `attributes.inspection.notes`, where completion notes live before a completion activity exists. */
        fun inspectionNotes(attributes: String?): String? = attributes?.let {
            runCatching {
                ((Json.parseToJsonElement(it) as? JsonObject)?.get("inspection") as? JsonObject)?.get("notes")
                    ?.let { n -> (n as? JsonPrimitive)?.contentOrNull }
            }.getOrNull()
        }

        fun detail(conn: V2Connection, id: String, userId: String?, localFile: (String) -> File?): WorkOrderDetail? {
            val wo = byId(conn, id) ?: return null
            val workers = V2MaintRows.workers(conn)
            val assets = if (wo.assetIds.isEmpty()) {
                emptyList()
            } else {
                val ids = wo.assetIds.toSet()
                V2MaintRows.assets(conn) { it["id"] in ids }.filter { it.id in ids }.sortedBy { wo.assetIds.indexOf(it.id) }
            }
            val lines = V2MaintRows.costLines(conn, null, emptyList()) { true }
                .filter { it.workOrderId == id || (wo.completionActivityId != null && it.activityId == wo.completionActivityId) }
            val workerNames = workers.associate { it.id to it.displayName }
            val entries = timeEntries(conn, id, workerNames)
            val me = workers.firstOrNull { userId != null && it.userId == userId }
            return WorkOrderDetail(
                workOrder = wo,
                assets = assets,
                assignments = assignments(conn, id, workers),
                checklist = checklist(conn, id),
                comments = comments(conn, id, workers),
                costLines = lines,
                activities = V2ActivityRepository.activities(conn, ActivityFilter(workOrderId = id)),
                permits = V2OverlayMerge.query(conn, "work_permits", "work_order_id = ?", listOf(id)) { it["work_order_id"] == id }
                    .map { permit(conn, it) }
                    .sortedByDescending { it.validFrom ?: 0L },
                rca = rca(conn, id),
                attachments = V2MaintRows.attachments(conn, "work_order_id = ?", listOf(id), { it["work_order_id"] == id }, localFile),
                timeEntries = entries.sortedByDescending { it.start },
                myRunningTimer = me?.let { m -> entries.firstOrNull { it.workerId == m.id && it.isRunning } },
                myWorkerId = me?.id,
                approvalRequests = approvalRequests(conn, "work_order_id = ?", listOf(id)) { it["work_order_id"] == id },
                recurrence = recurrence(conn, id),
            )
        }

        fun instances(conn: V2Connection, workOrderId: String): List<String> =
            V2OverlayMerge.query(conn, "procedure_instances", "work_order_id = ?", listOf(workOrderId)) { it["work_order_id"] == workOrderId }
                .sortedBy { it.longOrNull("created_at") ?: 0L }
                .map { it.str("id") }

        fun checklist(conn: V2Connection, workOrderId: String): List<ChecklistItem> {
            val instances = instances(conn, workOrderId).toSet()
            if (instances.isEmpty()) return emptyList()
            val responses = V2OverlayMerge.query(conn, "procedure_responses") { it["instance_id"] in instances }
                .filter { it.str("instance_id") in instances }
            return checklistItems(conn, responses).sortedWith(compareBy<ChecklistItem> { it.sortOrder }.thenBy { it.id })
        }

        private fun checklistItems(conn: V2Connection, responses: List<V2Row>): List<ChecklistItem> {
            if (responses.isEmpty()) return emptyList()
            val rowIds = responses.map { it.str("row_id") }.toSet()
            val rows = V2OverlayMerge.query(conn, "procedure_template_rows") { it["id"] in rowIds }
                .filter { it.str("id") in rowIds }
                .associateBy { it.str("id") }
            return responses.mapNotNull { r ->
                val row = rows[r.str("row_id")] ?: return@mapNotNull null
                ChecklistItem(
                    id = r.str("id"),
                    label = row.strOrNull("label") ?: "",
                    description = row.strOrNull("description"),
                    rowType = row.strOrNull("row_type") ?: "step",
                    required = row.bool("required"),
                    sortOrder = row.intOrNull("sort_order") ?: 0,
                    completed = r.bool("completed"),
                    completedAt = r.longOrNull("completed_at"),
                    passFail = r.strOrNull("pass_fail"),
                    response = r.strOrNull("response"),
                    notes = r.strOrNull("notes"),
                    version = r.longOrNull("version") ?: 0L,
                )
            }
        }

        fun assignments(conn: V2Connection, workOrderId: String, workers: List<Worker>): List<WorkOrderAssignment> {
            val byId = workers.ifEmpty { V2MaintRows.workers(conn) }.associateBy { it.id }
            return V2OverlayMerge.query(conn, "work_order_assignments", "work_order_id = ?", listOf(workOrderId)) {
                it["work_order_id"] == workOrderId
            }.sortedBy { it.intOrNull("sort_order") ?: 0 }.map { assignment(it, byId) }
        }

        private fun assignment(r: V2Row, workers: Map<String, Worker>): WorkOrderAssignment {
            val worker = workers[r.str("worker_id")]
            return WorkOrderAssignment(
                id = r.str("id"),
                workOrderId = r.str("work_order_id"),
                workerId = r.str("worker_id"),
                workerName = worker?.displayName,
                workerUserId = worker?.userId,
                role = r.strOrNull("role") ?: "lead",
                acceptanceStatus = r.strOrNull("acceptance_status") ?: "pending",
                estimatedHours = r.decimalOrNull("estimated_hours"),
                notes = r.strOrNull("notes"),
                version = r.longOrNull("version") ?: 0L,
            )
        }

        fun comments(conn: V2Connection, workOrderId: String, workers: List<Worker>): List<WorkOrderComment> {
            val names = workers.mapNotNull { w -> w.userId?.let { it to w.displayName } }.toMap()
            return V2OverlayMerge.query(conn, "comments", "work_order_id = ?", listOf(workOrderId)) { it["work_order_id"] == workOrderId }
                .map { r ->
                    val author = r.strOrNull("author_user_id")
                    WorkOrderComment(
                        id = r.str("id"),
                        workOrderId = workOrderId,
                        authorUserId = author,
                        authorName = author?.let(names::get),
                        body = r.strOrNull("body"),
                        commentType = r.strOrNull("comment_type") ?: "comment",
                        photoFileId = r.strOrNull("photo_file_id"),
                        createdAt = r.longOrNull("created_at") ?: 0L,
                        version = r.longOrNull("version") ?: 0L,
                    )
                }
                .sortedBy { it.createdAt }
        }

        fun permit(conn: V2Connection, r: V2Row): WorkPermit {
            val id = r.str("id")
            val signatures = V2OverlayMerge.query(conn, "signatures", "work_permit_id = ?", listOf(id)) { it["work_permit_id"] == id }
                .map { s -> PermitSignature(s.str("signer_name"), s.strOrNull("signer_role") ?: "", s.longOrNull("signed_at") ?: 0L) }
                .sortedBy { it.signedAt }
            return WorkPermit(
                id = id,
                workOrderId = r.str("work_order_id"),
                permitType = r.str("permit_type"),
                status = r.strOrNull("status") ?: "draft",
                validFrom = r.longOrNull("valid_during_start"),
                validUntil = r.longOrNull("valid_during_end"),
                hazards = parseJsonList(r.strOrNull("hazards")),
                precautions = parseJsonList(r.strOrNull("precautions")),
                notes = r.strOrNull("notes"),
                signatures = signatures,
                version = r.longOrNull("version") ?: 0L,
            )
        }

        fun rca(conn: V2Connection, workOrderId: String): RcaReport? =
            V2OverlayMerge.query(conn, "rca_reports", "work_order_id = ?", listOf(workOrderId)) { it["work_order_id"] == workOrderId }
                .maxByOrNull { it.longOrNull("created_at") ?: 0L }
                ?.let(::rca)

        fun rca(r: V2Row) = RcaReport(
            id = r.str("id"),
            workOrderId = r.str("work_order_id"),
            assetId = r.strOrNull("asset_id"),
            problemDescription = r.strOrNull("problem_description"),
            immediateAction = r.strOrNull("immediate_action"),
            rootCause = r.strOrNull("root_cause"),
            correctiveAction = r.strOrNull("corrective_action"),
            preventiveAction = r.strOrNull("preventive_action"),
            status = r.strOrNull("status") ?: "draft",
            completedAt = r.longOrNull("completed_at"),
            version = r.longOrNull("version") ?: 0L,
        )

        fun timeEntries(conn: V2Connection, workOrderId: String, workerNames: Map<String, String> = emptyMap()) =
            V2OverlayMerge.query(conn, "time_entries", "work_order_id = ?", listOf(workOrderId)) { it["work_order_id"] == workOrderId }
                .map { V2MaintRows.timeEntry(it, workerNames[it.strOrNull("worker_id")]) }

        fun templates(conn: V2Connection): List<WorkOrderTemplate> {
            val categories = V2OverlayMerge.query(conn, "categories").associate { it.str("id") to it.str("name") }
            return V2OverlayMerge.query(conn, "work_order_templates").map { r ->
                WorkOrderTemplate(
                    id = r.str("id"),
                    title = r.str("title"),
                    titleTemplate = r.strOrNull("title_template"),
                    descriptionTemplate = r.strOrNull("description_template"),
                    categoryId = r.strOrNull("category_id"),
                    categoryName = r.strOrNull("category_id")?.let(categories::get),
                    priority = r.strOrNull("priority")?.let(WorkOrderPriority::fromWire),
                    estimatedEffortMinutes = r.intOrNull("estimated_effort_minutes"),
                    requiredSkills = parseJsonList(r.strOrNull("required_skills")),
                    version = r.longOrNull("version") ?: 0L,
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, WorkOrderTemplate::title))
        }
    }
}
