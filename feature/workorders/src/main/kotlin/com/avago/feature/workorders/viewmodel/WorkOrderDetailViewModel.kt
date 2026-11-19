package com.avago.feature.workorders.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.ai.ScreenContextStore
import com.avago.core.auth.IdentityManager
import com.avago.core.data.FormFillRouter
import com.avago.core.data.Formatters
import com.avago.core.data.domain.ChecklistItem
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.PmFilter
import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmRepository
import com.avago.core.data.domain.Worker
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.data.domain.WorkOrder
import com.avago.core.data.domain.WorkOrderAssignment
import com.avago.core.data.domain.WorkOrderComment
import com.avago.core.data.domain.WorkOrderDetail
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderStatus
import com.avago.core.data.domain.WorkOrderTemplateDraft
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.core.sync.SyncEngine
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.ChecklistRowState
import com.avago.feature.workorders.model.DEFAULT_SLA_POLICY
import com.avago.feature.workorders.model.SlaWindow
import com.avago.feature.workorders.model.WO_KEY_HOLD
import com.avago.feature.workorders.model.WO_KEY_VENDOR
import com.avago.feature.workorders.model.WoCloseout
import com.avago.feature.workorders.model.WoCloseoutOptions
import com.avago.feature.workorders.model.WoCode
import com.avago.feature.workorders.model.WoCodeKind
import com.avago.feature.workorders.model.WoHold
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.model.WoVendor
import com.avago.feature.workorders.model.isChecklistRowRequired
import com.avago.feature.workorders.model.parseWoAttributes
import com.avago.feature.workorders.model.validateCloseout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.math.BigDecimal
import javax.inject.Inject

data class WorkOrderMapPreview(
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
)

/** Map preview of the work order's location; coordinates come from the location row (no geocoding). */
fun Location.toMapPreview(): WorkOrderMapPreview? {
    val address = listOfNotNull(addressLine1, addressLine2, locality, region, postalCode, countryCode)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    if (address.isBlank() && (latitude == null || longitude == null)) return null
    return WorkOrderMapPreview(address = address.ifBlank { "$latitude, $longitude" }, latitude = latitude, longitude = longitude)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val pm: PmRepository,
    workers: WorkerRepository,
    locations: LocationRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
    private val serviceClient: AvagoServiceClient,
    private val permissionsManager: PermissionsManager,
    private val screenContextStore: ScreenContextStore,
    private val formFillRouter: FormFillRouter,
    private val closeoutSupport: WoCloseoutSupport,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private fun str(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    private fun closeoutMissingRes(key: String): Int = when (key) {
        "failure_code" -> R.string.wo_closeout_failure_code
        "cause_code" -> R.string.wo_closeout_cause_code
        "remedy_code" -> R.string.wo_closeout_remedy_code
        "resolution_notes" -> R.string.wo_closeout_resolution_notes
        "meter_reading" -> R.string.wo_closeout_meter_reading
        "checklist" -> R.string.wo_closeout_missing_checklist
        "downtime" -> R.string.wo_closeout_downtime
        else -> R.string.wo_closeout_signoff
    }

    private val woId: String = requireNotNull(savedStateHandle["woId"]) {
        "WorkOrderDetailViewModel requires woId in SavedStateHandle"
    }

    val detail: StateFlow<WorkOrderDetail?> = repository.observeDetail(woId)
        .catch { e -> Timber.e(e, "[WoDetailVM] detail flow error"); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val workOrder: StateFlow<WorkOrder?> = detail.map { it?.workOrder }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val assetName: StateFlow<String?> = workOrder.map { it?.primaryAssetName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val assignments: StateFlow<List<WorkOrderAssignment>> = detail.map { it?.assignments.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val checklistItems: StateFlow<List<ChecklistItem>> = detail.map { it?.checklist.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val comments: StateFlow<List<WorkOrderComment>> = detail.map { it?.comments.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val me: StateFlow<Worker?> = workers.observeMe()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val mapPreview: StateFlow<WorkOrderMapPreview?> = workOrder
        .map { it?.locationId }
        .distinctUntilChanged()
        .flatMapLatest { locationId ->
            if (locationId == null) flowOf<WorkOrderMapPreview?>(null)
            else flow { emit(locations.location(locationId)?.toMapPreview()) }
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The open PM occurrence this work order was generated for, if any. */
    val pmOccurrence: StateFlow<PmOccurrence?> = pm.observeOccurrences(PmFilter())
        .map { list -> list.firstOrNull { it.workOrderId == woId } }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canEdit: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_ASSIGN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_ASSIGN))

    /**
     * True when the WO should show the merged log screen instead of the regular detail view:
     * in progress and completed always qualify; assigned qualifies for the assigned technician.
     */
    val shouldShowLogView: StateFlow<Boolean> = combine(workOrder, me) { wo, me ->
        when (wo?.status) {
            WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED -> true
            WorkOrderStatus.ASSIGNED -> me != null && wo.assignedWorkerId == me.id
            else -> false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val canApprove: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_APPROVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_APPROVE))

    val canDelete: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_DELETE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_DELETE))

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ---------------------------------------------------------------------------
    // Close-out gate, codes, SLA, hold, vendor
    // ---------------------------------------------------------------------------

    val codes: StateFlow<Map<WoCodeKind, List<WoCode>>> = closeoutSupport.codes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WoCloseoutSupport.DEFAULT_CODES)

    /** Default SLA windows; per-work-order overrides live in `attributes.sla`. */
    val slaPolicy: StateFlow<Map<String, SlaWindow>> = MutableStateFlow(DEFAULT_SLA_POLICY).asStateFlow()

    val closeoutOptions: StateFlow<WoCloseoutOptions> = closeoutSupport.options
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WoCloseoutOptions())

    private val _showCloseout = MutableStateFlow(false)
    val showCloseout: StateFlow<Boolean> = _showCloseout.asStateFlow()

    fun checklistStates(): List<ChecklistRowState> = checklistItems.value.map {
        ChecklistRowState(required = isChecklistRowRequired(it.rowType, it.required), completed = it.completed)
    }

    fun dismissCloseout() { _showCloseout.value = false }

    fun completeWithCloseout(closeout: WoCloseout) {
        val wo = workOrder.value ?: return
        val assetId = wo.primaryAssetId
        val check = validateCloseout(closeout, assetId != null, checklistStates(), closeoutOptions.value)
        if (!check.ok) {
            _error.value = str(R.string.wo_closeout_missing, check.missing.joinToString(", ") { str(closeoutMissingRes(it)) })
            return
        }
        launchSaving("completeWithCloseout") {
            closeoutSupport.complete(wo, closeout)
            _showCloseout.value = false
        }
    }

    fun setHold(reason: String, note: String?) {
        val wo = workOrder.value ?: return
        val since = WoHold.from(parseWoAttributes(wo.attributes))?.since ?: java.time.Instant.now().toString()
        patchAttributes(mapOf(WO_KEY_HOLD to WoHold(reason, since, note).toJson()))
    }

    fun clearHold() = patchAttributes(mapOf(WO_KEY_HOLD to null))

    fun saveVendor(vendor: WoVendor) =
        patchAttributes(mapOf(WO_KEY_VENDOR to if (vendor.isEmpty && vendor.extra.isEmpty()) null else vendor.toJson()))

    private fun patchAttributes(patch: Map<String, kotlinx.serialization.json.JsonElement?>) =
        launchAction("patchAttributes") { repository.patchAttributes(woId, patch) }

    // ---------------------------------------------------------------------------
    // Pull-to-refresh
    // ---------------------------------------------------------------------------

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { syncEngine.sync() } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] sync failed")
            }
            _isRefreshing.value = false
        }
    }

    // ---------------------------------------------------------------------------
    // Approval workflow: pending_approval -> open (approve) or cancelled (reject)
    // ---------------------------------------------------------------------------

    fun approveWorkOrder() = launchAction("approve", R.string.wo_error_approve_failed) {
        repository.transition(woId, WorkOrderStatus.OPEN)
    }

    fun rejectWorkOrder() = launchAction("reject", R.string.wo_error_reject_failed) {
        repository.transition(woId, WorkOrderStatus.CANCELLED)
    }

    // ---------------------------------------------------------------------------
    // Inline title/description editing
    // ---------------------------------------------------------------------------

    private val _isEditingHeader = MutableStateFlow(false)
    val isEditingHeader: StateFlow<Boolean> = _isEditingHeader.asStateFlow()

    private val _editTitle = MutableStateFlow("")
    val editTitle: StateFlow<String> = _editTitle.asStateFlow()

    private val _editDescription = MutableStateFlow("")
    val editDescription: StateFlow<String> = _editDescription.asStateFlow()

    fun startEditingHeader() {
        _editTitle.value = workOrder.value?.title ?: ""
        _editDescription.value = workOrder.value?.description ?: ""
        _isEditingHeader.value = true
    }

    fun cancelEditingHeader() { _isEditingHeader.value = false }

    fun saveHeader() {
        val title = _editTitle.value.trim()
        if (title.isBlank()) return
        val description = _editDescription.value.trim().ifBlank { null }
        launchAction("saveHeader", R.string.wo_error_save_failed) {
            repository.edit(woId) { it.copy(title = title, description = description) }
            _isEditingHeader.value = false
        }
    }

    fun onEditTitleChanged(v: String) { _editTitle.value = v }
    fun onEditDescriptionChanged(v: String) { _editDescription.value = v }

    // ---------------------------------------------------------------------------
    // Team chat thread (chat stays on REST)
    // ---------------------------------------------------------------------------

    private val _chatThreadId = MutableStateFlow<String?>(null)
    val chatThreadId: StateFlow<String?> = _chatThreadId.asStateFlow()

    private fun resolveWorkOrderThread() {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            try {
                when (val result = serviceClient.resolveWoThread(accountId, woId)) {
                    is NetworkResult.Success -> _chatThreadId.value = result.data.thread_id
                    else -> Unit
                }
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] resolveWorkOrderThread failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Status, assignment and edits
    // ---------------------------------------------------------------------------

    fun transitionStatus(targetStatus: WoStatus) {
        // Completion always goes through the close-out sheet.
        if (targetStatus == WoStatus.COMPLETE) {
            _showCloseout.value = true
            return
        }
        launchSaving("transitionStatus") { repository.transition(woId, targetStatus.domain) }
    }

    /** Assigns the work order to the signed-in user as lead; the server rejects the claim if someone else got it first. */
    fun claimWorkOrder() {
        val userId = identityManager.getActiveUserId() ?: return
        launchSaving("claim", R.string.wo_error_claim_failed) { repository.assign(woId, userId, lead = true) }
    }

    /** Assigns the technician with user id [techUserId]. */
    fun assignTech(techUserId: String, lead: Boolean = true) =
        launchSaving("assignTech") { repository.assign(woId, techUserId, lead = lead) }

    fun acceptAssignment(assignmentId: String) =
        launchAction("acceptAssignment") { repository.respondToAssignment(assignmentId, accept = true) }

    fun declineAssignment(assignmentId: String) =
        launchAction("declineAssignment") { repository.respondToAssignment(assignmentId, accept = false) }

    fun saveDispatcherNotes(notes: String) =
        launchAction("saveDispatcherNotes") { repository.edit(woId) { it.copy(dispatcherNotes = notes.trim().ifBlank { null }) } }

    fun toggleChecklistItem(item: ChecklistItem) =
        launchAction("toggleChecklistItem") { repository.setChecklistCompleted(item.id, !item.completed) }

    fun addChecklistItem(title: String) {
        if (title.isBlank()) return
        launchAction("addChecklistItem") { repository.addChecklistItem(woId, title.trim()) }
    }

    fun addComment(body: String) {
        if (body.isBlank()) return
        launchAction("addComment") { repository.addComment(woId, body.trim()) }
    }

    /** Skips the PM occurrence this work order belongs to. */
    fun skipOccurrence(reason: String, onSkipped: () -> Unit) {
        val occurrence = pmOccurrence.value ?: return
        launchAction("skipOccurrence", R.string.wo_error_skip_failed) {
            pm.skip(occurrence.id, reason)
            onSkipped()
        }
    }

    fun deleteWorkOrder(onDeleted: () -> Unit) = launchAction("delete", R.string.wo_error_delete_failed) {
        repository.delete(woId)
        onDeleted()
    }

    fun saveAsTemplate(name: String) {
        val wo = workOrder.value ?: return
        if (name.isBlank()) return
        launchAction("saveAsTemplate", R.string.wo_error_save_failed) {
            repository.createTemplate(
                WorkOrderTemplateDraft(
                    title = name.trim(),
                    descriptionTemplate = wo.description,
                    category = wo.categoryName,
                    priority = wo.priority,
                    estimatedEffortMinutes = wo.estimatedEffortMinutes,
                    requiredSkills = wo.requiredSkills,
                ),
            )
        }
    }

    fun reschedule(newDate: java.time.LocalDate) {
        val dueMs = newDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        launchSaving("reschedule") { repository.edit(woId) { it.copy(dueAt = dueMs) } }
    }

    fun exportPdf(context: Context) {
        val current = detail.value ?: return
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { createWorkOrderPdf(context, current) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, context.getString(R.string.wo_pdf_share_chooser)).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] exportPdf failed")
                _error.value = str(R.string.wo_error_export_pdf_failed)
            }
        }
    }

    fun clearError() { _error.value = null }

    private fun launchAction(name: String, @StringRes fallback: Int? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] $name failed")
                _error.value = e.message ?: fallback?.let { str(it) }
            }
        }
    }

    private fun launchSaving(name: String, @StringRes fallback: Int? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] $name failed")
                _error.value = e.message ?: fallback?.let { str(it) }
            } finally {
                _isSaving.value = false
            }
        }
    }

    init {
        screenContextStore.setWorkOrderScope(woId)
        formFillRouter.register("wo_detail") { fields ->
            val changed = mutableListOf<String>()
            val title = fields["title"]
            val desc = fields["description"] ?: fields["notes"]
            if (title != null || desc != null) {
                _editTitle.value = title ?: (workOrder.value?.title ?: "")
                _editDescription.value = desc ?: (workOrder.value?.description ?: "")
                _isEditingHeader.value = true
                changed += listOfNotNull(title?.let { "title" }, desc?.let { "description" })
            }
            changed
        }
        resolveWorkOrderThread()
    }

    override fun onCleared() {
        formFillRouter.unregister("wo_detail")
        super.onCleared()
    }
}

private fun createWorkOrderPdf(context: Context, detail: WorkOrderDetail): File {
    val workOrder = detail.workOrder
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    val width = pageInfo.pageWidth.toFloat()
    val margin = 40f
    val contentWidth = width - (margin * 2)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    var y = 60f
    y = drawWrappedPdfText(canvas, workOrder.title, margin, y, contentWidth, titlePaint) + 16f
    workOrder.description?.takeIf { it.isNotBlank() }?.let { description ->
        y = drawWrappedPdfText(canvas, description, margin, y, contentWidth, bodyPaint) + 18f
    }

    val totalCost = detail.costLines.fold(BigDecimal.ZERO) { acc, l -> acc + l.total }
    val detailRows = listOf(
        context.getString(R.string.wo_pdf_status) to workOrder.status.wire.replace('_', ' ').replaceFirstChar { it.uppercase() },
        context.getString(R.string.wo_pdf_priority) to workOrder.priority.wire.replaceFirstChar { it.uppercase() },
        context.getString(R.string.wo_pdf_due_date) to (
            workOrder.dueAt?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }
                ?: "\u2014"
            ),
        context.getString(R.string.wo_pdf_total_cost) to (
            if (detail.costLines.isEmpty()) "\u2014" else Formatters.formatCurrency(totalCost.toDouble(), workOrder.currency)
            ),
    )
    detailRows.forEach { (label, value) ->
        canvas.drawText("$label:", margin, y, labelPaint)
        y = drawWrappedPdfText(canvas, value, margin + 110f, y, contentWidth - 110f, bodyPaint)
        y += 16f
    }

    canvas.drawText(context.getString(R.string.wo_pdf_assignments), margin, y, sectionPaint)
    y += 18f
    if (detail.assignments.isEmpty()) {
        y = drawWrappedPdfText(canvas, context.getString(R.string.wo_pdf_unassigned), margin, y, contentWidth, bodyPaint) + 8f
    } else {
        detail.assignments.forEach { assignment ->
            y = drawWrappedPdfText(
                canvas,
                "\u2022 ${assignment.workerName ?: assignment.workerId}",
                margin,
                y,
                contentWidth,
                bodyPaint,
            ) + 6f
        }
    }

    document.finishPage(page)
    val file = File(context.cacheDir, "wo_${workOrder.id}.pdf")
    file.outputStream().use(document::writeTo)
    document.close()
    return file
}

private fun drawWrappedPdfText(
    canvas: android.graphics.Canvas,
    text: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    paint: Paint,
): Float {
    var currentY = y
    text.split('\n').forEach { paragraph ->
        var remaining = paragraph.trim()
        if (remaining.isEmpty()) {
            currentY += paint.textSize + 4f
        } else {
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null)
                var line = remaining.take(count).trimEnd()
                val nextIndex = (if (count < remaining.length) {
                    val lastSpace = line.lastIndexOf(' ')
                    if (lastSpace > 0) {
                        line = line.take(lastSpace)
                        lastSpace
                    } else {
                        count
                    }
                } else {
                    count
                }).coerceAtLeast(1)
                canvas.drawText(line, x, currentY, paint)
                currentY += paint.textSize + 4f
                remaining = remaining.drop(nextIndex).trimStart()
            }
        }
    }
    return currentY
}
