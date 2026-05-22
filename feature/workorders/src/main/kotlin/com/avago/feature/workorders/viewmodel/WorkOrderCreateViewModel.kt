package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WoChecklistItemEntity
import com.avago.core.data.db.entity.WoTemplateEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.feature.workorders.model.WoPriority
import com.avago.feature.workorders.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class ChecklistDraft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
)

@HiltViewModel
class WorkOrderCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
) : ViewModel() {

    /** null = create mode; non-null = edit mode */
    private val editingWoId: String? = savedStateHandle["woId"]

    // ---------------------------------------------------------------------------
    // Form state
    // ---------------------------------------------------------------------------

    val title = MutableStateFlow("")
    val description = MutableStateFlow("")
    val assetId = MutableStateFlow<String?>(null)
    val assetName = MutableStateFlow<String?>(null)
    val dueDateMs = MutableStateFlow<Long?>(null)
    val priority = MutableStateFlow(WoPriority.MEDIUM)
    val estimatedHours = MutableStateFlow("")
    val assignedTechIds = MutableStateFlow<List<String>>(emptyList())
    val checklistDrafts = MutableStateFlow<List<ChecklistDraft>>(emptyList())
    val selectedTemplateId = MutableStateFlow<String?>(null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _titleError = MutableStateFlow<String?>(null)
    val titleError: StateFlow<String?> = _titleError.asStateFlow()

    private val _savedSuccessfully = MutableStateFlow(false)
    val savedSuccessfully: StateFlow<Boolean> = _savedSuccessfully.asStateFlow()

    // ---------------------------------------------------------------------------
    // Templates
    // ---------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<WoTemplateEntity>> =
        identityManager.activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList())
                else repository.observeTemplates(accountId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------------------------------------------------------------------
    // Load existing WO for edit mode
    // ---------------------------------------------------------------------------

    init {
        if (editingWoId != null) {
            viewModelScope.launch {
                val accountId = identityManager.getActiveAccountId() ?: return@launch
                val wo = repository.getById(accountId, editingWoId) ?: return@launch
                title.value = wo.title
                description.value = wo.description ?: ""
                assetId.value = wo.assetId
                dueDateMs.value = wo.dueDate
                priority.value = WoPriority.fromKey(wo.priority)
                estimatedHours.value = wo.estimatedEffortMinutes
                    ?.let { (it / 60.0).toString() } ?: ""
                assignedTechIds.value = if (!wo.assignedTo.isNullOrBlank())
                    listOf(wo.assignedTo ?: error("unreachable")) else emptyList()

                // Load checklist items (one-shot snapshot)
                repository.observeChecklistForWo(accountId, editingWoId)
                    .firstOrNull()
                    ?.also { items ->
                        if (checklistDrafts.value.isEmpty()) {
                            checklistDrafts.value = items.map {
                                ChecklistDraft(id = it.itemId, title = it.title)
                            }
                        }
                    }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Template application
    // ---------------------------------------------------------------------------

    fun applyTemplate(template: WoTemplateEntity) {
        selectedTemplateId.value = template.templateId
        if (title.value.isBlank()) title.value = template.title
        if (description.value.isBlank()) description.value = template.description ?: ""
        if (estimatedHours.value.isBlank()) {
            estimatedHours.value = template.estimatedEffortMinutes
                ?.let { (it / 60.0).toString() } ?: ""
        }
        // Parse checklist JSON from template if present
        val jsonItems = template.checklistItems
        if (!jsonItems.isNullOrBlank() && checklistDrafts.value.isEmpty()) {
            // Simple parse: try to extract "title" fields from JSON array
            val titlePattern = Regex(""""title"\s*:\s*"([^"]+)"""")
            checklistDrafts.value = titlePattern.findAll(jsonItems)
                .map { ChecklistDraft(title = it.groupValues[1]) }
                .toList()
        }
    }

    // ---------------------------------------------------------------------------
    // Checklist mutations
    // ---------------------------------------------------------------------------

    fun addChecklistItem() {
        checklistDrafts.value = checklistDrafts.value + ChecklistDraft()
    }

    fun updateChecklistItem(id: String, newTitle: String) {
        checklistDrafts.value = checklistDrafts.value.map {
            if (it.id == id) it.copy(title = newTitle) else it
        }
    }

    fun removeChecklistItem(id: String) {
        checklistDrafts.value = checklistDrafts.value.filter { it.id != id }
    }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    fun save() {
        val titleVal = title.value.trim()
        if (titleVal.isBlank()) {
            _titleError.value = "Title is required"
            return
        }
        _titleError.value = null

        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val now = System.currentTimeMillis()
                val woId = editingWoId ?: UUID.randomUUID().toString()
                val existing = if (editingWoId != null)
                    repository.getById(accountId, editingWoId) else null

                val estimatedMinutes = estimatedHours.value.toDoubleOrNull()
                    ?.let { (it * 60).toLong() }

                val entity = WorkOrderEntity(
                    woId = woId,
                    accountId = accountId,
                    assetId = assetId.value,
                    locationId = existing?.locationId,
                    title = titleVal,
                    description = description.value.ifBlank { null },
                    category = existing?.category,
                    priority = priority.value.key,
                    status = existing?.status ?: "open",
                    requesterId = existing?.requesterId,
                    assignedTo = assignedTechIds.value.firstOrNull() ?: existing?.assignedTo,
                    dispatcherNotes = existing?.dispatcherNotes,
                    requiredSkills = existing?.requiredSkills,
                    estimatedEffortMinutes = estimatedMinutes ?: existing?.estimatedEffortMinutes,
                    actualEffortMinutes = existing?.actualEffortMinutes,
                    failureCode = existing?.failureCode,
                    completionNotes = existing?.completionNotes,
                    partsNeeded = existing?.partsNeeded,
                    logId = existing?.logId,
                    dueDate = dueDateMs.value,
                    startedAt = existing?.startedAt,
                    completedAt = existing?.completedAt,
                    timerStartedAt = existing?.timerStartedAt,
                    laborCost = existing?.laborCost,
                    partsCost = existing?.partsCost,
                    totalCost = existing?.totalCost,
                    currency = existing?.currency,
                    baseAmount = existing?.baseAmount,
                    exchangeRateUsed = existing?.exchangeRateUsed,
                    attributes = existing?.attributes,
                    createdBy = existing?.createdBy ?: accountId,
                    approvalState = existing?.approvalState,
                    jobId = existing?.jobId,
                    woKind = existing?.woKind,
                    rrule = existing?.rrule,
                    endType = existing?.endType,
                    endCount = existing?.endCount,
                    endDate = existing?.endDate,
                    meterType = existing?.meterType,
                    meterDue = existing?.meterDue,
                    meterInterval = existing?.meterInterval,
                    parentWoId = existing?.parentWoId,
                    occurrenceDate = existing?.occurrenceDate,
                    scheduleId = existing?.scheduleId,
                    lastCompletedAt = existing?.lastCompletedAt,
                    timezone = existing?.timezone,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = existing?.serverVersion ?: 0L,
                    seq = existing?.seq,
                )
                repository.upsert(accountId, entity)

                // Upsert checklist items
                checklistDrafts.value.forEachIndexed { idx, draft ->
                    if (draft.title.isNotBlank()) {
                        repository.upsertChecklistItem(
                            accountId,
                            WoChecklistItemEntity(
                                itemId = draft.id,
                                woId = woId,
                                title = draft.title,
                                isCompleted = false,
                                completedAt = null,
                                displayOrder = idx.toLong(),
                                serverVersion = 0L,
                                seq = null,
                            )
                        )
                    }
                }

                _savedSuccessfully.value = true
            } catch (e: Exception) {
                Timber.e(e, "[WoCreateVM] save failed")
            } finally {
                _isSaving.value = false
            }
        }
    }
}
