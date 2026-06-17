package com.avago.feature.workorders.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.avago.core.data.Formatters
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WoAssignmentEntity
import com.avago.core.data.db.entity.WoChecklistItemEntity
import com.avago.core.data.db.entity.WoCommentEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.BudgetPillResponse
import com.avago.core.network.model.GeocodeRequest
import com.avago.core.network.model.WorkOrderPatch
import com.avago.core.ai.ScreenContextStore
import com.avago.core.data.FormFillRouter
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.core.sync.SyncEngine
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import org.json.JSONObject
 
data class AuditEntry(val description: String, val createdAt: String)

data class WorkOrderMapPreview(
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
)

@HiltViewModel
class WorkOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val syncEngine: SyncEngine,
    private val serviceClient: AvagoServiceClient,
    private val permissionsManager: PermissionsManager,
    private val screenContextStore: ScreenContextStore,
    private val formFillRouter: FormFillRouter,
) : ViewModel() {

    private val woId: String = requireNotNull(savedStateHandle["woId"]) {
        "WorkOrderDetailViewModel requires woId in SavedStateHandle"
    }

    private val _accountId: StateFlow<String?> = identityManager.activeAccountId
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.getActiveAccountId())

    @OptIn(ExperimentalCoroutinesApi::class)
    val workOrder: StateFlow<WorkOrderEntity?> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(null)
            else repository.observeAll(accountId)
                .map { list -> list.firstOrNull { it.woId == woId } }
                .catch { e -> Timber.e(e, "[WoDetailVM] wo flow error"); emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val assetName: StateFlow<String?> = workOrder
        .flatMapLatest { wo ->
            val accountId = _accountId.value
            val assetId = wo?.assetId
            if (accountId == null || assetId.isNullOrBlank()) flowOf<String?>(null)
            else flow<String?> { emit(repository.getAssetById(accountId, assetId)?.name) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val mapPreview: StateFlow<WorkOrderMapPreview?> = workOrder
        .flatMapLatest { wo ->
            if (wo == null) flowOf<WorkOrderMapPreview?>(null)
            else flow<WorkOrderMapPreview?> { emit(buildMapPreview(wo)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canEdit: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_ASSIGN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_ASSIGN))

    /**
     * True when the WO should show the merged log screen instead of the
     * regular detail view — mirrors iOS routeToLogItemViewIfNeeded():
     * in_progress and completed always qualify; assigned qualifies when
     * the current user is the assigned technician.
     */
    val shouldShowLogView: StateFlow<Boolean> = workOrder.map { wo ->
        if (wo == null) false
        else when (wo.status) {
            "in_progress", "completed" -> true
            "assigned" -> {
                val userId = identityManager.getActiveUserId()
                userId != null && wo.assignedTo == userId
            }
            else -> false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val canApprove: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_APPROVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_APPROVE))

    val canDelete: StateFlow<Boolean> = permissionsManager.observeCan(Permissions.WO_DELETE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissionsManager.can(Permissions.WO_DELETE))

    @OptIn(ExperimentalCoroutinesApi::class)
    val assignments: StateFlow<List<WoAssignmentEntity>> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else repository.observeAssignmentsForWo(accountId, woId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val checklistItems: StateFlow<List<WoChecklistItemEntity>> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else repository.observeChecklistForWo(accountId, woId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val comments: StateFlow<List<WoCommentEntity>> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else repository.observeCommentsForWo(accountId, woId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
    // Approval workflow
    // ---------------------------------------------------------------------------

    fun approveWorkOrder() {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            try {
                repository.upsert(accountId, wo.copy(
                    approvalState = "approved",
                    updatedAt = System.currentTimeMillis(),
                ))
            } catch (e: Exception) {
                Timber.e(e, "approveWorkOrder failed")
                _error.value = "Failed to approve"
            }
        }
    }

    fun rejectWorkOrder() {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            try {
                repository.upsert(accountId, wo.copy(
                    approvalState = "rejected",
                    updatedAt = System.currentTimeMillis(),
                ))
            } catch (e: Exception) {
                Timber.e(e, "rejectWorkOrder failed")
                _error.value = "Failed to reject"
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Inline title/description editing
    // ---------------------------------------------------------------------------

    private val _isEditingHeader = MutableStateFlow(false)
    val isEditingHeader: StateFlow<Boolean> = _isEditingHeader.asStateFlow()

    /** Non-null while Scout has provided an RRULE for HITL review — drives the RepeatsSheet. */
    private val _scoutRrule = MutableStateFlow<String?>(null)
    val scoutRrule: StateFlow<String?> = _scoutRrule.asStateFlow()
    fun clearScoutRrule() { _scoutRrule.value = null }

    /** Called from the ScoutPaletteSheet onNavigate callback when target = "set_recurrence". */
    fun onScoutRecurrence(fields: Map<String, String?>) {
        formFillRouter.dispatch("set_recurrence", fields)
    }

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
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            val title = _editTitle.value.trim()
            if (title.isBlank()) return@launch
            try {
                val newDescription = _editDescription.value.trim().ifBlank { null }
                // Change 3: send only the changed fields via PATCH instead of a full entity push.
                serviceClient.patchWorkOrder(
                    accountId,
                    woId,
                    WorkOrderPatch(
                        title = title,
                        description = newDescription,
                    ),
                )
                repository.upsert(accountId, wo.copy(
                    title = title,
                    description = newDescription,
                    updatedAt = System.currentTimeMillis(),
                ))
                _isEditingHeader.value = false
                runCatching { syncEngine.sync() }
                    .onFailure { Timber.w(it, "[WoDetailVM] sync after saveHeader failed") }
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] saveHeader failed")
                _error.value = "Failed to save"
            }
        }
    }

    fun onEditTitleChanged(v: String) { _editTitle.value = v }
    fun onEditDescriptionChanged(v: String) { _editDescription.value = v }

    // ---------------------------------------------------------------------------
    // Audit history
    // ---------------------------------------------------------------------------

    private val _auditHistory = MutableStateFlow<List<AuditEntry>>(emptyList())
    val auditHistory: StateFlow<List<AuditEntry>> = _auditHistory.asStateFlow()

    private fun loadAuditHistory() {
        viewModelScope.launch {
            val accountId = _accountId.value ?: return@launch
            try {
                when (val result = serviceClient.getWorkOrderAudit(accountId, woId)) {
                    is NetworkResult.Success -> {
                        _auditHistory.value = result.data.map { event ->
                            AuditEntry(
                                description = event.event_type,
                                createdAt = java.time.Instant.ofEpochMilli(event.occurred_at)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")),
                            )
                        }
                    }
                    else -> { /* leave empty */ }
                }
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] loadAuditHistory failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Team chat thread
    // ---------------------------------------------------------------------------

    private val _chatThreadId = MutableStateFlow<String?>(null)
    val chatThreadId: StateFlow<String?> = _chatThreadId.asStateFlow()

    private fun resolveWorkOrderThread() {
        viewModelScope.launch {
            val accountId = _accountId.value ?: return@launch
            try {
                when (val result = serviceClient.resolveWoThread(accountId, woId)) {
                    is NetworkResult.Success -> _chatThreadId.value = result.data.thread_id
                    else -> { /* leave null */ }
                }

            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] resolveWorkOrderThread failed")
            }
        }
    }

    private suspend fun buildMapPreview(wo: WorkOrderEntity): WorkOrderMapPreview? {
        val accountId = _accountId.value ?: return null
        val attrs = parseAttributes(wo.attributes)
        val location = wo.locationId?.let { repository.getLocationById(accountId, it) }

        val addressLine = firstNonBlank(
            location?.address,
            attrs["address_line1"],
            attrs["street_address"],
            attrs["address"],
            attrs["formatted_address"],
        )
        val city = firstNonBlank(location?.city, attrs["city"])
        val state = firstNonBlank(location?.state, attrs["state"])
        val postalCode = firstNonBlank(location?.postalCode, attrs["postal_code"], attrs["zip_code"], attrs["zip"])
        val country = firstNonBlank(location?.country, attrs["country"])
        val address = listOfNotNull(addressLine, city, state, postalCode, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")

        var lat = location?.latitude ?: firstNonBlank(attrs["lat"], attrs["latitude"])?.toDoubleOrNull()
        var lon = location?.longitude ?: firstNonBlank(attrs["lon"], attrs["lng"], attrs["longitude"])?.toDoubleOrNull()

        if ((lat == null || lon == null) && address.isNotBlank()) {
            when (val result = serviceClient.geocodeAddress(
                accountId = accountId,
                request = GeocodeRequest(
                    address_line1 = addressLine ?: address,
                    city = city,
                    state = state,
                    postal_code = postalCode,
                    country = country,
                ),
            )) {
                is NetworkResult.Success -> {
                    lat = result.data.lat
                    lon = result.data.lon
                }
                else -> Unit
            }
        }

        return if (address.isBlank() && (lat == null || lon == null)) null
        else WorkOrderMapPreview(address = address.ifBlank { "$lat, $lon" }, latitude = lat, longitude = lon)
    }

    private fun parseAttributes(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        }.getOrDefault(emptyMap())
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    // ---------------------------------------------------------------------------
    // Status transition
    // ---------------------------------------------------------------------------

    fun transitionStatus(targetStatus: WoStatus) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val now = System.currentTimeMillis()
                val updated = wo.copy(
                    status = targetStatus.key,
                    updatedAt = now,
                    startedAt = if (targetStatus == WoStatus.IN_PROGRESS && wo.startedAt == null) now else wo.startedAt,
                    completedAt = if (targetStatus == WoStatus.COMPLETE) now else wo.completedAt,
                )
                repository.upsert(accountId, updated)
                // Mirror iOS: also push the status change to the server so it doesn't
                // wait for the next full sync cycle (especially important for CANCELLED,
                // which iOS posts immediately via transitionWorkOrderStatus).
                try {
                    serviceClient.transitionWorkOrderStatus(accountId, woId, targetStatus.key)
                } catch (e: Exception) {
                    Timber.w(e, "[WoDetailVM] status server call failed — will retry via sync")
                }
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] transitionStatus failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Self-claim (tech claims unassigned WO from the detail screen)
    // ---------------------------------------------------------------------------

    /**
     * Mirrors iOS WorkOrderDetailViewController.claimTapped().
     * Optimistically marks the WO as assigned to the current user, then fires the
     * server claim endpoint.  On 409 (already claimed) the optimistic local update is
     * reverted and [error] is surfaced so the UI can show an appropriate message.
     */
    fun claimWorkOrder(onConflict: () -> Unit = {}) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val userId = identityManager.getActiveUserId() ?: identityManager.getActiveAccountId() ?: return@launch
                val now = System.currentTimeMillis()
                // Optimistic local update
                repository.upsert(accountId, wo.copy(
                    assignedTo = userId,
                    status = WoStatus.ASSIGNED.key,
                    updatedAt = now,
                ))
                when (val result = serviceClient.claimWorkOrder(accountId, woId)) {
                    is NetworkResult.Success -> syncEngine.sync()
                    is NetworkResult.Error -> {
                        // Revert optimistic update and surface the conflict
                        repository.upsert(accountId, wo)
                        if (result.code == 409) {
                            onConflict()
                            _error.value = "This work order was just claimed by someone else."
                        } else {
                            _error.value = "Could not claim the work order. Please try again."
                        }
                    }
                    else -> {
                        repository.upsert(accountId, wo)
                        _error.value = "Could not claim the work order. Please try again."
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] claimWorkOrder failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Assign tech (dispatcher assigns a specific technician)
    // ---------------------------------------------------------------------------

    /**
     * Mirrors iOS WorkOrderDetailViewController.presentTechPicker / TechPickerViewController.
     * Writes the assignment locally then pushes to the server via patchWorkOrder.
     * [techId] is the userId of the technician to assign.
     */
    fun assignTech(techId: String) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val now = System.currentTimeMillis()
                repository.upsert(accountId, wo.copy(
                    assignedTo = techId,
                    status = WoStatus.ASSIGNED.key,
                    updatedAt = now,
                ))
                // Persist assignment record
                val assignment = WoAssignmentEntity(
                    assignmentId = UUID.randomUUID().toString(),
                    woId = woId,
                    accountId = accountId,
                    technicianId = techId,
                    assignedBy = identityManager.getActiveUserId(),
                    assignedAt = now,
                    unassignedAt = null,
                    scheduledStart = null,
                    scheduledEnd = null,
                    status = "pending",
                    notes = null,
                    ekEventIdentifier = null,
                    serverVersion = 0L,
                    seq = null,
                )
                repository.upsertAssignment(accountId, assignment)
                // Change 3: push only the assignment field via PATCH instead of a full entity sync.
                serviceClient.patchWorkOrder(
                    accountId,
                    woId,
                    WorkOrderPatch(assigned_to = techId),
                )
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] assignTech failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Accept / Decline assignment (pending-assignment response by assignee)
    // ---------------------------------------------------------------------------

    /**
     * Mirrors iOS WorkOrderDetailViewController.handleAssignmentResponse(accepted: true).
     * Calls the server accept endpoint; local assignment status is updated via the
     * next sync pull (server is the source of truth for assignment status).
     */
    fun acceptAssignment(assignmentId: String) {
        val accountId = _accountId.value ?: return
        viewModelScope.launch {
            try {
                serviceClient.acceptAssignment(accountId, woId, assignmentId)
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] acceptAssignment failed")
                _error.value = e.message
            }
        }
    }

    /**
     * Mirrors iOS WorkOrderDetailViewController.handleAssignmentResponse(accepted: false).
     */
    fun declineAssignment(assignmentId: String) {
        val accountId = _accountId.value ?: return
        viewModelScope.launch {
            try {
                serviceClient.declineAssignment(accountId, woId, assignmentId)
                syncEngine.sync()
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] declineAssignment failed")
                _error.value = e.message
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Budget pill
    // ---------------------------------------------------------------------------

    /**
     * Mirrors iOS WorkOrderDetailViewController.loadRelatedData() → fetchBudgetPill.
     * Shows remaining / total budget for the WO's linked budget if any.
     */
    private val _budgetPill = MutableStateFlow<BudgetPillResponse?>(null)
    val budgetPill: StateFlow<BudgetPillResponse?> = _budgetPill.asStateFlow()

    private fun loadBudgetPill() {
        viewModelScope.launch {
            val accountId = _accountId.value ?: return@launch
            try {
                when (val result = serviceClient.getWorkOrderBudgetPill(accountId, woId)) {
                    is NetworkResult.Success -> _budgetPill.value = result.data
                    else -> { /* no budget configured for this WO — leave null */ }
                }
            } catch (e: Exception) {
                Timber.d(e, "[WoDetailVM] loadBudgetPill failed (non-critical)")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Dispatcher notes auto-save
    // ---------------------------------------------------------------------------

    fun saveDispatcherNotes(notes: String) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            try {
                repository.upsert(accountId, wo.copy(
                    dispatcherNotes = notes.ifBlank { null },
                    updatedAt = System.currentTimeMillis(),
                ))
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] saveDispatcherNotes failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Checklist toggle
    // ---------------------------------------------------------------------------

    fun toggleChecklistItem(item: WoChecklistItemEntity) {
        val accountId = _accountId.value ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val updated = item.copy(
                isCompleted = !item.isCompleted,
                completedAt = if (!item.isCompleted) now else null,
            )
            repository.upsertChecklistItem(accountId, updated)
        }
    }

    // ---------------------------------------------------------------------------
    // Comments
    // ---------------------------------------------------------------------------

    fun addComment(body: String) {
        val accountId = _accountId.value ?: return
        val authorId = identityManager.getActiveAccountId() ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val comment = WoCommentEntity(
                commentId = UUID.randomUUID().toString(),
                woId = woId,
                authorId = authorId,
                body = body.trim(),
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                serverVersion = 0L,
                seq = null,
            )
            repository.upsertComment(accountId, comment)
        }
    }

    // ---------------------------------------------------------------------------
    // Delete WO
    // ---------------------------------------------------------------------------

    fun deleteWorkOrder(onDeleted: () -> Unit) {
        val accountId = _accountId.value ?: return
        viewModelScope.launch {
            try {
                repository.softDelete(accountId, woId)
                onDeleted()
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] deleteWorkOrder failed")
                _error.value = e.message
            }
        }
    }

    fun exportPdf(context: Context) {
        val currentWo = workOrder.value ?: return
        val currentAssignments = assignments.value
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    createWorkOrderPdf(context, currentWo, currentAssignments)
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(
                    shareIntent,
                    context.getString(com.avago.feature.workorders.R.string.wo_pdf_share_chooser),
                ).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] exportPdf failed")
                _error.value = "Failed to export PDF"
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Recurrence
    // ---------------------------------------------------------------------------

    fun reschedule(newDate: java.time.LocalDate) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val newDueMs = newDate.atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                repository.upsert(accountId, wo.copy(
                    dueDate = newDueMs,
                    updatedAt = System.currentTimeMillis(),
                ))
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] reschedule failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Save recurrence rule + end-type metadata.
     *
     * Mirrors iOS persistRepeats() which writes rrule, endType, endCount, endDate,
     * and woKind together so the detail screen can reconstruct the full RepeatsConfig.
     * Passing a blank/null rrule clears the rule (one-off).
     *
     * @param rrule     RRULE string, e.g. "FREQ=WEEKLY;INTERVAL=1". Pass "" to clear.
     * @param endType   "never" | "count" | "date" | null (null → "never")
     * @param endCount  Occurrence limit when endType == "count", else null.
     * @param endDate   Epoch-millis cutoff when endType == "date", else null.
     */
    fun saveRecurrence(
        rrule: String,
        endType: String? = null,
        endCount: Long? = null,
        endDate: Long? = null,
    ) {
        val accountId = _accountId.value ?: return
        val wo = workOrder.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val isClearing = rrule.isBlank()
                val updated = wo.copy(
                    rrule = if (isClearing) null else rrule,
                    woKind = if (isClearing) "one_off" else "recurring_parent",
                    endType = if (isClearing) null else (endType ?: "never"),
                    endCount = if (endType == "count") endCount else null,
                    endDate = if (endType == "date") endDate else null,
                    updatedAt = System.currentTimeMillis(),
                )
                repository.upsert(accountId, updated)
                // POST to server asynchronously
                try {
                    serviceClient.updateWorkOrderRecurrence(accountId, woId, rrule)
                } catch (e: Exception) {
                    Timber.w(e, "[WoDetailVM] recurrence server POST failed — will retry via sync")
                }
            } catch (e: Exception) {
                Timber.e(e, "[WoDetailVM] saveRecurrence failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    // ---------------------------------------------------------------------------
    // Init
    // ---------------------------------------------------------------------------

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
        formFillRouter.register("set_recurrence") { fields ->
            val rrule = fields["rrule"]?.ifBlank { null }
            if (rrule != null) {
                _scoutRrule.value = rrule
                listOf("rrule")
            } else emptyList()
        }
        loadAuditHistory()
        resolveWorkOrderThread()
        loadBudgetPill()
    }

    override fun onCleared() {
        formFillRouter.unregister("wo_detail")
        formFillRouter.unregister("set_recurrence")
        super.onCleared()
    }
}

private fun createWorkOrderPdf(
    context: Context,
    workOrder: WorkOrderEntity,
    assignments: List<WoAssignmentEntity>,
): File {
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
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    var y = 60f
    y = drawWrappedPdfText(canvas, workOrder.title, margin, y, contentWidth, titlePaint) + 16f
    workOrder.description?.takeIf { it.isNotBlank() }?.let { description ->
        y = drawWrappedPdfText(canvas, description, margin, y, contentWidth, bodyPaint) + 18f
    }

    val detailRows = listOf(
        "Status" to (workOrder.status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "—"),
        "Priority" to (workOrder.priority?.replaceFirstChar { it.uppercase() } ?: "—"),
        "Due Date" to (workOrder.dueDate?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() } ?: "—"),
        "Total Cost" to (workOrder.totalCost?.let { Formatters.formatCurrency(it, workOrder.currency) } ?: "—"),
    )
    detailRows.forEach { (label, value) ->
        canvas.drawText("$label:", margin, y, labelPaint)
        y = drawWrappedPdfText(canvas, value, margin + 110f, y, contentWidth - 110f, bodyPaint)
        y += 16f
    }

    canvas.drawText("Assignments", margin, y, sectionPaint)
    y += 18f
    if (assignments.isEmpty()) {
        y = drawWrappedPdfText(canvas, "Unassigned", margin, y, contentWidth, bodyPaint) + 8f
    } else {
        assignments.forEach { assignment ->
            y = drawWrappedPdfText(
                canvas,
                "• ${assignment.technicianId}",
                margin,
                y,
                contentWidth,
                bodyPaint,
            ) + 6f
        }
    }

    document.finishPage(page)
    val file = File(context.cacheDir, "wo_${workOrder.woId}.pdf")
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
