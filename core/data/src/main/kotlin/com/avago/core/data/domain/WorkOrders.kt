package com.avago.core.data.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

enum class WorkOrderStatus(val wire: String) {
    DRAFT("draft"), PENDING_APPROVAL("pending_approval"), OPEN("open"), SCHEDULED("scheduled"), ASSIGNED("assigned"),
    IN_PROGRESS("in_progress"), ON_HOLD("on_hold"), COMPLETED("completed"), CLOSED("closed"), CANCELLED("cancelled"),
    SKIPPED("skipped");

    /** Completed, closed, cancelled or skipped. */
    val isTerminal: Boolean get() = this in TERMINAL

    companion object {
        private val TERMINAL = setOf(COMPLETED, CLOSED, CANCELLED, SKIPPED)
        fun fromWire(raw: String?): WorkOrderStatus = entries.firstOrNull { it.wire == raw } ?: OPEN
    }
}

enum class WorkOrderPriority(val wire: String) {
    CRITICAL("critical"), HIGH("high"), MEDIUM("medium"), LOW("low");

    companion object {
        fun fromWire(raw: String?): WorkOrderPriority = entries.firstOrNull { it.wire == raw } ?: MEDIUM
    }
}

enum class WorkOrderComplexity(val wire: String) {
    SIMPLE("simple"), STANDARD("standard"), COMPLEX("complex");

    companion object {
        fun fromWire(raw: String?): WorkOrderComplexity? = entries.firstOrNull { it.wire == raw }
    }
}

/** Labor timer actions of `maint.work_orders.timer`. */
enum class TimerAction(val wire: String) { START("start"), PAUSE("pause"), RESUME("resume"), STOP("stop") }

/** A work order (`work_orders`) with display joins. */
data class WorkOrder(
    val id: String,
    val title: String,
    val description: String?,
    val categoryId: String?,
    val categoryName: String?,
    val priority: WorkOrderPriority,
    val status: WorkOrderStatus,
    val complexity: WorkOrderComplexity?,
    val approvalState: String?,
    val requesterUserId: String?,
    val assignedWorkerId: String?,
    val assignedWorkerName: String?,
    val assignedTeamId: String?,
    val locationId: String?,
    val locationName: String?,
    /** The job (`projects`) the work is booked to. */
    val projectId: String?,
    val projectName: String?,
    /** Covered assets, primary first. */
    val assetIds: List<String>,
    val primaryAssetName: String?,
    val dueAt: Long?,
    val plannedStart: Long?,
    val plannedEnd: Long?,
    val startedAt: Long?,
    val completedAt: Long?,
    val completionActivityId: String?,
    val failureCode: String?,
    val causeCode: String?,
    val remedyCode: String?,
    val completionNotes: String?,
    val skipReason: String?,
    val requiresPermit: Boolean,
    val requiredSkills: List<String>,
    val estimatedEffortMinutes: Int?,
    val currency: String?,
    val dispatcherNotes: String?,
    /** Account-defined and app fields (`attributes`, JSON object text), e.g. hold, vendor, close-out. */
    val attributes: String,
    val createdBy: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
) {
    val primaryAssetId: String? get() = assetIds.firstOrNull()

    fun isOverdue(now: Long): Boolean = dueAt != null && dueAt < now && !status.isTerminal
}

/** Fields the user edits on a work order. Blank text means "no value". */
data class WorkOrderDraft(
    val title: String = "",
    val description: String? = null,
    val assetIds: List<String> = emptyList(),
    val locationId: String? = null,
    /** Category name; the server creates unknown work-order categories. */
    val category: String? = null,
    val priority: WorkOrderPriority = WorkOrderPriority.MEDIUM,
    val complexity: WorkOrderComplexity? = null,
    /** The assignee's user id (`workers.user_id`). */
    val assignedUserId: String? = null,
    val requiredSkills: List<String> = emptyList(),
    val estimatedEffortMinutes: Int? = null,
    val dueAt: Long? = null,
    /** Planned work window; both or neither (edit only, `maint.work_orders.create` has no window). */
    val plannedStart: Long? = null,
    val plannedEnd: Long? = null,
    /** The job (`projects`) the work is booked to (edit only). */
    val projectId: String? = null,
    val dispatcherNotes: String? = null,
) {
    companion object {
        /** The stored values of [wo]; [assignedUserId] is the assignee's user id. */
        fun of(wo: WorkOrder, assignedUserId: String?) = WorkOrderDraft(
            title = wo.title, description = wo.description, assetIds = wo.assetIds, locationId = wo.locationId,
            category = wo.categoryName, priority = wo.priority, complexity = wo.complexity, assignedUserId = assignedUserId,
            requiredSkills = wo.requiredSkills, estimatedEffortMinutes = wo.estimatedEffortMinutes, dueAt = wo.dueAt,
            plannedStart = wo.plannedStart, plannedEnd = wo.plannedEnd, projectId = wo.projectId,
            dispatcherNotes = wo.dispatcherNotes,
        )
    }
}

/** Completion details saved with (or before) a status change. Null leaves a field as is. */
data class WorkOrderCompletion(
    val failureCode: String? = null,
    val causeCode: String? = null,
    val remedyCode: String? = null,
    val completionNotes: String? = null,
)

data class WorkOrderAssignment(
    val id: String,
    val workOrderId: String,
    val workerId: String,
    val workerName: String?,
    val workerUserId: String?,
    val role: String,
    val acceptanceStatus: String,
    val estimatedHours: BigDecimal?,
    val notes: String?,
    val version: Long,
)

data class WorkOrderComment(
    val id: String,
    val workOrderId: String,
    val authorUserId: String?,
    val authorName: String?,
    val body: String?,
    val commentType: String,
    val photoFileId: String?,
    val createdAt: Long,
    val version: Long,
)

/** A checklist row of the work order's procedure (`procedure_responses` + template row). */
data class ChecklistItem(
    val id: String,
    val label: String,
    val description: String?,
    val rowType: String,
    val required: Boolean,
    val sortOrder: Int,
    val completed: Boolean,
    val completedAt: Long?,
    val passFail: String?,
    /** Recorded answer (JSON text). */
    val response: String?,
    val notes: String?,
    val version: Long,
)

enum class CostLineType(val wire: String) {
    PART("part"), LABOR("labor"), MISC("misc");

    companion object {
        fun fromWire(raw: String?): CostLineType = entries.firstOrNull { it.wire == raw } ?: MISC
    }
}

/** Labor, part or misc cost of an activity or work order (`activity_cost_lines`). */
data class CostLine(
    val id: String,
    /** Null until the server attaches a line created on a work order to its draft activity. */
    val activityId: String?,
    val workOrderId: String?,
    val kind: CostLineType,
    val sortOrder: Int,
    val productId: String?,
    val performerWorkerId: String?,
    val description: String?,
    val quantity: BigDecimal,
    val uomCode: String?,
    val unitCost: BigDecimal?,
    val taxAmountMinor: Long?,
    val glAccountId: String?,
    val costStatus: String,
    val partNumber: String?,
    val notes: String?,
    val version: Long,
) {
    val total: BigDecimal get() = unitCost?.multiply(quantity) ?: BigDecimal.ZERO

    /** An estimate line (`cost_status = estimate`) rather than an actual cost. */
    val isEstimate: Boolean get() = costStatus == "estimate"
}

data class CostLineDraft(
    val kind: CostLineType,
    val description: String? = null,
    val productId: String? = null,
    /** User id of the performer (labor). */
    val performerUserId: String? = null,
    val quantity: BigDecimal = BigDecimal.ONE,
    val unitCost: BigDecimal? = null,
    val taxAmountMinor: Long? = null,
    /** General-ledger account (`gl_accounts`) the cost is booked to. */
    val glAccountId: String? = null,
    val estimate: Boolean = false,
    val notes: String? = null,
)

data class WorkPermit(
    val id: String,
    val workOrderId: String,
    val permitType: String,
    val status: String,
    val validFrom: Long?,
    val validUntil: Long?,
    val hazards: List<String>,
    val precautions: List<String>,
    val notes: String?,
    val signatures: List<PermitSignature>,
    val version: Long,
)

data class PermitSignature(val signerName: String, val role: String, val signedAt: Long)

data class WorkPermitDraft(
    val permitType: String,
    val validFrom: Long,
    val validUntil: Long,
    val hazards: List<String> = emptyList(),
    val precautions: List<String> = emptyList(),
    val notes: String? = null,
)

data class RcaReport(
    val id: String,
    val workOrderId: String,
    val assetId: String?,
    val problemDescription: String?,
    val immediateAction: String?,
    val rootCause: String?,
    val correctiveAction: String?,
    val preventiveAction: String?,
    val status: String,
    val completedAt: Long?,
    val version: Long,
)

data class RcaDraft(
    val problemDescription: String? = null,
    val immediateAction: String? = null,
    val rootCause: String? = null,
    val correctiveAction: String? = null,
    val preventiveAction: String? = null,
    val status: String? = null,
)

data class WorkOrderTemplate(
    val id: String,
    val title: String,
    val titleTemplate: String?,
    val descriptionTemplate: String?,
    val categoryId: String?,
    val categoryName: String?,
    val priority: WorkOrderPriority?,
    val estimatedEffortMinutes: Int?,
    val requiredSkills: List<String>,
    val version: Long,
)

data class WorkOrderTemplateDraft(
    val title: String,
    val titleTemplate: String? = null,
    val descriptionTemplate: String? = null,
    val category: String? = null,
    val priority: WorkOrderPriority? = null,
    val estimatedEffortMinutes: Int? = null,
    val requiredSkills: List<String> = emptyList(),
)

/** A labor time entry (`time_entries`); open entries are running timers. */
data class TimeEntry(
    val id: String,
    val workerId: String,
    val workOrderId: String?,
    val start: Long,
    val end: Long?,
    val breakMinutes: Int,
    val status: String,
    val workerName: String? = null,
    val notes: String? = null,
    val rejectReason: String? = null,
    val approvedAt: Long? = null,
    val version: Long = 0L,
) {
    val isRunning: Boolean get() = end == null

    /** Stopped and not approved: its worker can still correct or delete it. */
    val isEditable: Boolean get() = !isRunning && status != STATUS_APPROVED

    /** Submitted or rejected: a manager can decide it. */
    val isDecidable: Boolean get() = !isRunning && (status == STATUS_SUBMITTED || status == STATUS_REJECTED)

    fun minutes(now: Long): Long = (((end ?: now) - start) / 60_000L - breakMinutes).coerceAtLeast(0)

    companion object {
        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
    }
}

/** A manual time entry (`maint.time_entries.create` / `.update`). */
data class TimeEntryDraft(
    val start: Long,
    val end: Long,
    val breakMinutes: Int = 0,
    val notes: String? = null,
)

/** A pending or decided approval (`approval_requests`) with its decisions. */
data class ApprovalRequest(
    val id: String,
    val subjectKind: String,
    val workOrderId: String?,
    val purchaseOrderId: String?,
    val requestedAmountMinor: Long?,
    val currency: String?,
    val status: String,
    val requestedBy: String?,
    val requestedAt: Long?,
    val assignedApproverUserId: String?,
    val decidedAt: Long?,
    val decisions: List<ApprovalDecision>,
) {
    val isPending: Boolean get() = status == STATUS_PENDING

    companion object {
        const val STATUS_PENDING = "pending"
    }
}

data class ApprovalDecision(
    val id: String,
    val approverUserId: String,
    val decision: String,
    val comment: String?,
    val decidedAt: Long,
)

/** A work order's calendar recurrence: the calendar trigger of its PM plan. */
data class WorkOrderRecurrence(
    val planId: String,
    /** The plan's calendar trigger; null when the plan has none yet. */
    val triggerId: String?,
    /** RFC 5545 rule, e.g. `FREQ=WEEKLY;BYDAY=MO`. */
    val rrule: String,
    val endType: String,
    val endCount: Int?,
    /** Last day (epoch day) when [endType] is `date`. */
    val endOnEpochDay: Long?,
    val timezone: String?,
    val active: Boolean,
)

/** What `maint.work_orders.set_recurrence` sets; an empty [rrule] stops the recurrence. */
data class RecurrenceRule(
    val rrule: String,
    val endType: String = "never",
    val endCount: Int? = null,
    val endOnEpochDay: Long? = null,
    val timezone: String? = null,
)

/** A job (`projects`) work and costs are booked to. */
data class Project(
    val id: String,
    val number: String?,
    val name: String,
    val customerName: String?,
    val status: String,
    val budgetMinor: Long?,
    val currency: String?,
)

/** Target response / resolution hours per priority (`work_order_sla_policies`). */
data class SlaPolicy(val priority: WorkOrderPriority, val respondHours: Int?, val resolveHours: Int?)

/** Everything the work-order detail screen shows. */
data class WorkOrderDetail(
    val workOrder: WorkOrder,
    val assets: List<Asset>,
    val assignments: List<WorkOrderAssignment>,
    val checklist: List<ChecklistItem>,
    val comments: List<WorkOrderComment>,
    val costLines: List<CostLine>,
    val activities: List<Activity>,
    val permits: List<WorkPermit>,
    val rca: RcaReport?,
    val attachments: List<Attachment>,
    val timeEntries: List<TimeEntry>,
    /** The signed-in user's running timer on this work order. */
    val myRunningTimer: TimeEntry?,
    /** The signed-in user's worker id, for "own time entry" checks. */
    val myWorkerId: String? = null,
    val approvalRequests: List<ApprovalRequest> = emptyList(),
    val recurrence: WorkOrderRecurrence? = null,
)

data class WorkOrderFilter(
    val query: String = "",
    val statuses: Set<WorkOrderStatus> = emptySet(),
    val assetId: String? = null,
    val assignedWorkerId: String? = null,
    /** Unassigned and not terminal ("available jobs"). */
    val unassignedOnly: Boolean = false,
    val dueFrom: Long? = null,
    val dueTo: Long? = null,
)

interface WorkOrderRepository {
    fun observeWorkOrders(filter: WorkOrderFilter = WorkOrderFilter()): Flow<List<WorkOrder>>

    fun observeWorkOrder(id: String): Flow<WorkOrder?>

    suspend fun workOrder(id: String): WorkOrder?

    fun observeDetail(id: String): Flow<WorkOrderDetail?>

    fun observeTemplates(): Flow<List<WorkOrderTemplate>>

    /** Creates a work order; [attributes] seeds `attributes` (e.g. `source` provenance). */
    suspend fun create(draft: WorkOrderDraft, attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()): String

    suspend fun update(id: String, draft: WorkOrderDraft)

    /** Applies [change] to the stored values and saves only what changed. */
    suspend fun edit(id: String, change: (WorkOrderDraft) -> WorkOrderDraft)

    /** Saves completion codes and notes without a status change (clearing allowed). */
    suspend fun updateCompletion(id: String, completion: WorkOrderCompletion)

    suspend fun transition(id: String, status: WorkOrderStatus, completion: WorkOrderCompletion = WorkOrderCompletion())

    /**
     * Shallow-merges [patch] into `attributes` (a null value removes the key) and saves the whole
     * object, so keys other clients wrote are kept.
     */
    suspend fun patchAttributes(id: String, patch: Map<String, kotlinx.serialization.json.JsonElement?>)

    suspend fun timer(id: String, action: TimerAction)

    suspend fun delete(id: String)

    suspend fun addComment(workOrderId: String, body: String, photo: LocalFileRef? = null): String

    suspend fun deleteComment(commentId: String)

    suspend fun addChecklistItem(workOrderId: String, title: String, required: Boolean = false, description: String? = null): String

    /** Records an answer and, when [complete], ticks the row. */
    suspend fun recordChecklist(itemId: String, passFail: String?, response: String?, notes: String?, complete: Boolean)

    suspend fun setChecklistCompleted(itemId: String, completed: Boolean)

    suspend fun deleteChecklistItem(itemId: String)

    suspend fun addCostLine(workOrderId: String, draft: CostLineDraft): String

    suspend fun updateCostLine(lineId: String, draft: CostLineDraft)

    suspend fun deleteCostLine(lineId: String)

    suspend fun assign(workOrderId: String, userId: String, lead: Boolean = true, estimatedHours: BigDecimal? = null): String

    suspend fun unassign(assignmentId: String)

    suspend fun respondToAssignment(assignmentId: String, accept: Boolean)

    suspend fun createPermit(workOrderId: String, draft: WorkPermitDraft): String

    suspend fun updatePermitStatus(permitId: String, status: String)

    suspend fun signPermit(permitId: String, role: String, signedName: String)

    suspend fun deletePermit(permitId: String)

    suspend fun saveRca(workOrderId: String, draft: RcaDraft): String

    suspend fun deleteRca(rcaId: String)

    suspend fun createTemplate(draft: WorkOrderTemplateDraft): String

    suspend fun updateTemplate(id: String, draft: WorkOrderTemplateDraft)

    suspend fun deleteTemplate(id: String)

    suspend fun attach(workOrderId: String, file: LocalFileRef): String

    /** Self-assigns an open work order; of two concurrent claims one is rejected (`wo_already_claimed`). */
    suspend fun claim(id: String)

    /** Makes the work order recurring, changes its rule or (empty rule) stops it. */
    suspend fun setRecurrence(id: String, rule: RecurrenceRule)

    suspend fun decideApproval(requestId: String, approve: Boolean, notes: String?)

    /** Logs a finished stretch of work for the signed-in user. */
    suspend fun logTime(workOrderId: String, draft: TimeEntryDraft): String

    suspend fun updateTimeEntry(id: String, draft: TimeEntryDraft)

    suspend fun deleteTimeEntry(id: String)

    suspend fun approveTimeEntry(id: String)

    suspend fun rejectTimeEntry(id: String, reason: String)

    fun observeProjects(): Flow<List<Project>>

    fun observeSlaPolicies(): Flow<List<SlaPolicy>>
}
