package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.FormFillRouter
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.ReferenceRepository
import com.avago.core.data.domain.Worker
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.data.domain.WorkOrderDraft
import com.avago.core.data.domain.WorkOrderFilter
import com.avago.core.data.domain.WorkOrderPriority
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderTemplate
import com.avago.feature.workorders.model.WO_KEY_SOURCE
import com.avago.feature.workorders.model.WoPriority
import com.avago.feature.workorders.model.woSourceJson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** A checklist row typed on the form; [existingId] is set for rows already on the work order. */
data class ChecklistDraft(
    val key: Long,
    val title: String = "",
    val existingId: String? = null,
)

/** Create / edit form for a work order, saved as `maint.work_orders.*` commands. */
@HiltViewModel
class WorkOrderCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val assets: AssetRepository,
    private val locations: LocationRepository,
    private val workers: WorkerRepository,
    references: ReferenceRepository,
    private val formFillRouter: FormFillRouter,
) : ViewModel() {

    /** null = create mode; non-null = edit mode. */
    private val editingWoId: String? = savedStateHandle.get<String>("woId")?.takeIf { it.isNotBlank() }

    /** Provenance written to `attributes.source` on create (create-from-defect or template). */
    private var pendingSource: JsonObject? = null
    private val defectIds: List<String> = savedStateHandle.get<String>("defectIds")
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    private val inspectionLogId: String? = savedStateHandle.get<String>("inspectionLogId")?.takeIf { it.isNotBlank() }

    /** Stored values in edit mode, used to save only what changed. */
    private var storedChecklistIds: Set<String> = emptySet()
    private var storedAssigneeIds: List<String> = emptyList()
    private var assignmentIdByUserId: Map<String, String> = emptyMap()
    private var nextChecklistKey = 0L

    // ---------------------------------------------------------------- form state

    val title = MutableStateFlow("")
    val description = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)
    val assetId = MutableStateFlow<String?>(null)
    val assetName = MutableStateFlow<String?>(null)
    val assetType = MutableStateFlow<String?>(null)
    val assetSubtitle = MutableStateFlow<String?>(null)
    /** Further covered assets (e.g. from an asset group), after the primary [assetId]. */
    val extraAssetIds = MutableStateFlow<List<String>>(emptyList())
    val locationId = MutableStateFlow<String?>(null)
    val locationName = MutableStateFlow<String?>(null)
    val dueDateMs = MutableStateFlow<Long?>(null)
    val priority = MutableStateFlow(WoPriority.MEDIUM)
    val estimatedHours = MutableStateFlow("")
    val requiredSkills = MutableStateFlow<List<String>>(emptyList())
    /** Assignee user ids; the first is the lead. */
    val assignedTechIds = MutableStateFlow<List<String>>(emptyList())
    val checklistDrafts = MutableStateFlow<List<ChecklistDraft>>(emptyList())
    val selectedTemplateId = MutableStateFlow<String?>(null)

    val availableCategories: StateFlow<List<String>> = references.observeCategories("work_order")
        .map { list -> list.map { it.name }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Up to 4 most recently used categories on the selected asset. */
    private val _recentCategoryKeys = MutableStateFlow<List<String>>(emptyList())
    val recentCategoryKeys: StateFlow<List<String>> = _recentCategoryKeys.asStateFlow()

    val templates: StateFlow<List<WorkOrderTemplate>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workerNames: StateFlow<Map<String, String>> = workers.observeWorkers()
        .map { list -> list.mapNotNull { w -> w.userId?.let { it to w.displayName } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _titleError = MutableStateFlow<String?>(null)
    val titleError: StateFlow<String?> = _titleError.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _savedSuccessfully = MutableStateFlow(false)
    val savedSuccessfully: StateFlow<Boolean> = _savedSuccessfully.asStateFlow()

    private val screenId = if (editingWoId == null) "add_wo" else "edit_wo"

    init {
        if (editingWoId == null) {
            if (defectIds.isNotEmpty() || inspectionLogId != null) {
                pendingSource = woSourceJson("defect", defectIds = defectIds, inspectionLogId = inspectionLogId)
            }
            savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }?.let { title.value = it }
            savedStateHandle.get<String>("description")?.takeIf { it.isNotBlank() }?.let { description.value = it }
            savedStateHandle.get<String>("priority")?.takeIf { it.isNotBlank() }?.let { priority.value = WoPriority.fromKey(it) }
            savedStateHandle.get<String>("assetId")?.takeIf { it.isNotBlank() }?.let { onAssetSelected(it) }
        } else {
            viewModelScope.launch { loadForEdit(editingWoId) }
        }
        // Scout can pre-fill this form when the user's HITL setting is on.
        formFillRouter.register(screenId) { fields -> applyScoutFields(fields) }
    }

    override fun onCleared() {
        formFillRouter.unregister(screenId)
        super.onCleared()
    }

    private suspend fun loadForEdit(id: String) {
        val detail = repository.observeDetail(id).first() ?: return
        val wo = detail.workOrder
        title.value = wo.title
        description.value = wo.description.orEmpty()
        category.value = wo.categoryName
        wo.primaryAssetId?.let { showAsset(assets.asset(it)) }
        assetId.value = wo.primaryAssetId
        extraAssetIds.value = wo.assetIds.drop(1)
        locationId.value = wo.locationId
        locationName.value = wo.locationName
        dueDateMs.value = wo.dueAt
        priority.value = WoPriority.fromKey(wo.priority.wire)
        estimatedHours.value = wo.estimatedEffortMinutes?.let(::minutesToHoursText).orEmpty()
        requiredSkills.value = wo.requiredSkills
        val active = detail.assignments.filter { it.acceptanceStatus != "declined" }
        assignmentIdByUserId = active.mapNotNull { a -> a.workerUserId?.let { it to a.id } }.toMap()
        val leadUserId = active.firstOrNull { it.workerId == wo.assignedWorkerId }?.workerUserId
        storedAssigneeIds = listOfNotNull(leadUserId) + active.mapNotNull { it.workerUserId }.filter { it != leadUserId }
        assignedTechIds.value = storedAssigneeIds
        storedChecklistIds = detail.checklist.map { it.id }.toSet()
        checklistDrafts.value = detail.checklist.sortedBy { it.sortOrder }
            .map { ChecklistDraft(key = nextChecklistKey++, title = it.label, existingId = it.id) }
    }

    // ---------------------------------------------------------------- Scout form-fill

    fun applyScoutFields(fields: Map<String, String?>): List<String> {
        val touched = mutableListOf<String>()
        fields["title"]?.trim()?.takeIf { it.isNotEmpty() }?.let { title.value = it; touched.add("title") }
        fields["description"]?.trim()?.let { description.value = it; touched.add("description") }
        fields["asset_id"]?.takeIf { it.isNotBlank() }?.let { onAssetSelected(it); touched.add("asset") }
        fields["due_date"]?.let { ds -> parseDateMs(ds)?.let { dueDateMs.value = it; touched.add("due date") } }
        fields["priority"]?.lowercase()?.let { priority.value = WoPriority.fromKey(it); touched.add("priority") }
        fields["estimated_effort_minutes"]?.toDoubleOrNull()?.let {
            estimatedHours.value = minutesToHoursText(it.toInt()); touched.add("estimated effort")
        }
        fields["assigned_to"]?.takeIf { it.isNotBlank() }?.let { who ->
            viewModelScope.launch {
                val match = matchWorker(workers.observeWorkers().first(), who)
                if (match != null) assignedTechIds.value = listOf(match)
            }
            touched.add("assigned to")
        }
        return touched
    }

    // ---------------------------------------------------------------- templates

    fun applyTemplate(template: WorkOrderTemplate) {
        selectedTemplateId.value = template.id
        pendingSource = if (defectIds.isNotEmpty() || inspectionLogId != null) {
            woSourceJson("defect", defectIds = defectIds, inspectionLogId = inspectionLogId, templateId = template.id)
        } else {
            woSourceJson("template", templateId = template.id)
        }
        if (title.value.isBlank()) title.value = template.titleTemplate?.takeIf { it.isNotBlank() } ?: template.title
        if (description.value.isBlank()) description.value = template.descriptionTemplate.orEmpty()
        if (category.value == null) category.value = template.categoryName
        template.priority?.let { priority.value = WoPriority.fromKey(it.wire) }
        if (estimatedHours.value.isBlank()) {
            estimatedHours.value = template.estimatedEffortMinutes?.let(::minutesToHoursText).orEmpty()
        }
        if (requiredSkills.value.isEmpty()) requiredSkills.value = template.requiredSkills
    }

    // ---------------------------------------------------------------- checklist

    fun addChecklistItem() {
        checklistDrafts.value = checklistDrafts.value + ChecklistDraft(key = nextChecklistKey++)
    }

    fun updateChecklistItem(key: Long, newTitle: String) {
        checklistDrafts.value = checklistDrafts.value.map { if (it.key == key) it.copy(title = newTitle) else it }
    }

    fun removeChecklistItem(key: Long) {
        checklistDrafts.value = checklistDrafts.value.filter { it.key != key }
    }

    // ---------------------------------------------------------------- pickers

    fun onAssetSelected(id: String) {
        assetId.value = id
        extraAssetIds.value = extraAssetIds.value - id
        _recentCategoryKeys.value = emptyList()
        viewModelScope.launch {
            val asset = assets.asset(id)
            showAsset(asset)
            if (locationId.value == null && asset?.locationId != null) {
                locationId.value = asset.locationId
                locationName.value = asset.locationName
            }
            _recentCategoryKeys.value = recentCategories(
                repository.observeWorkOrders(WorkOrderFilter(assetId = id)).first().map { it.createdAt to it.categoryName },
            )
        }
    }

    /** Covers every asset of [typeCode] besides the primary asset. */
    fun onAssetGroupSelected(typeCode: String) {
        viewModelScope.launch {
            val ids = assets.observeAssets().first().filter { it.assetTypeCode == typeCode }.map { it.id }
            val primary = assetId.value ?: ids.firstOrNull()?.also { onAssetSelected(it) }
            extraAssetIds.value = (extraAssetIds.value + ids).distinct().filter { it != primary }
        }
    }

    fun removeExtraAsset(id: String) {
        extraAssetIds.value = extraAssetIds.value - id
    }

    private fun showAsset(asset: Asset?) {
        assetName.value = asset?.name
        assetType.value = asset?.assetTypeCode
        assetSubtitle.value = asset?.makeModel ?: asset?.locationName
    }

    fun onLocationSelected(id: String?, name: String?) {
        locationId.value = id
        locationName.value = name
        if (id != null && name == null) {
            viewModelScope.launch { locationName.value = locations.location(id)?.name }
        }
    }

    // ---------------------------------------------------------------- save

    fun clearSaveError() {
        _saveError.value = null
    }

    fun save() {
        val titleVal = title.value.trim()
        if (titleVal.isBlank()) {
            _titleError.value = "Title is required"
            return
        }
        _titleError.value = null
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val assignees = assignedTechIds.value.distinct()
                val draft = WorkOrderDraft(
                    title = titleVal,
                    description = description.value.trim().ifBlank { null },
                    assetIds = listOfNotNull(assetId.value) + extraAssetIds.value.filter { it != assetId.value },
                    locationId = locationId.value,
                    category = category.value,
                    priority = WorkOrderPriority.fromWire(priority.value.key),
                    assignedUserId = assignees.firstOrNull(),
                    requiredSkills = requiredSkills.value,
                    estimatedEffortMinutes = hoursTextToMinutes(estimatedHours.value),
                    dueAt = dueDateMs.value,
                )
                val woId = editingWoId?.also { id ->
                    repository.edit(id) { stored -> draft.copy(complexity = stored.complexity, dispatcherNotes = stored.dispatcherNotes) }
                } ?: repository.create(draft, pendingSource?.let { mapOf(WO_KEY_SOURCE to it) }.orEmpty())

                // Further assignees (the lead goes with the work order itself).
                val previous = storedAssigneeIds.toSet()
                assignees.drop(1).filter { it !in previous }.forEach { repository.assign(woId, it, lead = false) }
                (previous - assignees.toSet()).forEach { userId ->
                    assignmentIdByUserId[userId]?.let { repository.unassign(it) }
                }

                val kept = checklistDrafts.value.filter { it.title.isNotBlank() }
                (storedChecklistIds - kept.mapNotNull { it.existingId }.toSet()).forEach { repository.deleteChecklistItem(it) }
                kept.filter { it.existingId == null }.forEach { repository.addChecklistItem(woId, it.title.trim()) }

                _savedSuccessfully.value = true
            } catch (e: Exception) {
                Timber.e(e, "[WoCreateVM] save failed")
                _saveError.value = e.message ?: e.javaClass.simpleName
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun parseDateMs(v: String): Long? {
        val utc = TimeZone.getTimeZone("UTC")
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = utc }
        val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return runCatching { iso.parse(v)?.time }.getOrNull() ?: runCatching { ymd.parse(v)?.time }.getOrNull()
    }
}

/** "1.5" hours ? 90 minutes; blank or invalid ? null. */
internal fun hoursTextToMinutes(text: String): Int? =
    text.trim().replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 }
        ?.multiply(BigDecimal(60))?.setScale(0, RoundingMode.HALF_UP)?.toInt()

/** 90 minutes ? "1.5". */
internal fun minutesToHoursText(minutes: Int): String =
    BigDecimal(minutes).divide(BigDecimal(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** The 4 most recently used distinct categories from (createdAt, category) pairs. */
internal fun recentCategories(history: List<Pair<Long, String?>>): List<String> =
    history.sortedByDescending { it.first }.mapNotNull { it.second?.takeIf(String::isNotBlank) }.distinct().take(4)

/** The user id of the worker named (or with user id) [who], if exactly identifiable. */
internal fun matchWorker(all: List<Worker>, who: String): String? =
    all.firstOrNull { it.userId == who }?.userId
        ?: all.filter { it.displayName.equals(who.trim(), ignoreCase = true) }.singleOrNull()?.userId
