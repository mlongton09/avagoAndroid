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
    val dispatcherNotes: String? = null,
)

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
    val costStatus: String,
    val partNumber: String?,
    val notes: String?,
    val version: Long,
) {
    val total: BigDecimal get() = unitCost?.multiply(quantity) ?: BigDecimal.ZERO
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
) {
    val isRunning: Boolean get() = end == null

    fun minutes(now: Long): Long = (((end ?: now) - start) / 60_000L - breakMinutes).coerceAtLeast(0)
}

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

    suspend fun create(draft: WorkOrderDraft): String

    suspend fun update(id: String, draft: WorkOrderDraft)

    /** Saves completion codes and notes without a status change (clearing allowed). */
    suspend fun updateCompletion(id: String, completion: WorkOrderCompletion)

    suspend fun transition(id: String, status: WorkOrderStatus, completion: WorkOrderCompletion = WorkOrderCompletion())

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

    suspend fun signPermit(permitId: String, role: String, signedName: String, comment: String?)

    suspend fun deletePermit(permitId: String)

    suspend fun saveRca(workOrderId: String, draft: RcaDraft): String

    suspend fun deleteRca(rcaId: String)

    suspend fun createTemplate(draft: WorkOrderTemplateDraft): String

    suspend fun updateTemplate(id: String, draft: WorkOrderTemplateDraft)

    suspend fun deleteTemplate(id: String)

    suspend fun attach(workOrderId: String, file: LocalFileRef): String
}
