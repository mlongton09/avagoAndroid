package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.FormFillRouter
import com.avago.core.data.db.entity.WoChecklistItemEntity
import com.avago.core.data.db.entity.WoTemplateEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.VinDecodeResponse
import com.avago.feature.workorders.model.WoPriority
import com.avago.feature.workorders.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

data class ChecklistDraft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
)

data class EffortHint(
    val typicalMinutes: Int,
    val fastMinutes: Int,
    val slowMinutes: Int,
    val sampleCount: Int,
)

@HiltViewModel
class WorkOrderCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
    private val serviceClient: AvagoServiceClient,
    private val formFillRouter: FormFillRouter,
    private val dbFactory: DatabaseFactory,
) : ViewModel() {

    /** null = create mode; non-null = edit mode */
    private val editingWoId: String? = savedStateHandle["woId"]

    // ---------------------------------------------------------------------------
    // Form state
    // ---------------------------------------------------------------------------

    val title = MutableStateFlow("")
    val vin = MutableStateFlow("")
    val description = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)
    val assetId = MutableStateFlow<String?>(null)
    val assetName = MutableStateFlow<String?>(null)
    val locationId = MutableStateFlow<String?>(null)
    val locationName = MutableStateFlow<String?>(null)
    val dueDateMs = MutableStateFlow<Long?>(null)
    val priority = MutableStateFlow(WoPriority.MEDIUM)
    val estimatedHours = MutableStateFlow("")
    val repeatsRrule = MutableStateFlow<String?>(null)
    val assignedTechIds = MutableStateFlow<List<String>>(emptyList())
    val checklistDrafts = MutableStateFlow<List<ChecklistDraft>>(emptyList())
    val selectedTemplateId = MutableStateFlow<String?>(null)
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()

    /** Up to 4 most-recently-used category keys for the selected asset (mirrors iOS LIMIT 4). */
    private val _recentCategoryKeys = MutableStateFlow<List<String>>(emptyList())
    val recentCategoryKeys: StateFlow<List<String>> = _recentCategoryKeys.asStateFlow()

    // Job picker
    val jobId = MutableStateFlow<String?>(null)
    val jobTitle = MutableStateFlow<String?>(null)

    // Timezone (defaults to device timezone)
    val timezone = MutableStateFlow<String>(TimeZone.getDefault().id)

    // Effort hint (populated from server when category is known)
    private val _effortHint = MutableStateFlow<EffortHint?>(null)
    val effortHint: StateFlow<EffortHint?> = _effortHint.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _titleError = MutableStateFlow<String?>(null)
    val titleError: StateFlow<String?> = _titleError.asStateFlow()

    private val _savedSuccessfully = MutableStateFlow(false)
    val savedSuccessfully: StateFlow<Boolean> = _savedSuccessfully.asStateFlow()

    private val _vinDecodeResult = MutableStateFlow<VinDecodeResponse?>(null)
    val vinDecodeResult: StateFlow<VinDecodeResponse?> = _vinDecodeResult.asStateFlow()

    private val _isDecodingVin = MutableStateFlow(false)
    val isDecodingVin: StateFlow<Boolean> = _isDecodingVin.asStateFlow()

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
        loadAvailableCategories()
        if (editingWoId != null) {
            viewModelScope.launch {
                val accountId = identityManager.getActiveAccountId() ?: return@launch
                val wo = repository.getById(accountId, editingWoId) ?: return@launch
                title.value = wo.title
                description.value = wo.description ?: ""
                category.value = wo.category
                assetId.value = wo.assetId
                assetName.value = wo.assetId?.let { repository.getAssetById(accountId, it)?.name }
                locationId.value = wo.locationId
                locationName.value = wo.locationId?.let { repository.getLocationById(accountId, it)?.name }
                dueDateMs.value = wo.dueDate
                priority.value = WoPriority.fromKey(wo.priority)
                estimatedHours.value = wo.estimatedEffortMinutes
                    ?.toString() ?: ""
                repeatsRrule.value = wo.rrule
                assignedTechIds.value = if (!wo.assignedTo.isNullOrBlank())
                    listOf(wo.assignedTo ?: error("unreachable")) else emptyList()
                jobId.value = wo.jobId
                // jobTitle is not stored on entity — leave null; UI will show jobId as fallback
                wo.timezone?.let { timezone.value = it }

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
    // Scout form-fill (HITL-on path)
    // ---------------------------------------------------------------------------

    init {
        // Register with FormFillRouter so Scout can pre-fill this form when the
        // user's HITL setting is ON.  Create mode uses "add_wo"; edit mode "edit_wo".
        val screenId = if (editingWoId == null) "add_wo" else "edit_wo"
        formFillRouter.register(screenId) { fields -> applyScoutFields(fields) }
    }

    override fun onCleared() {
        val screenId = if (editingWoId == null) "add_wo" else "edit_wo"
        formFillRouter.unregister(screenId)
        super.onCleared()
    }

    fun applyScoutFields(fields: Map<String, String?>): List<String> {
        val touched = mutableListOf<String>()
        fields["title"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            title.value = it; touched.add("title")
        }
        fields["description"]?.trim()?.let {
            description.value = it; touched.add("description")
        }
        fields["asset_id"]?.let {
            assetId.value = it; touched.add("asset")
        }
        fields["due_date"]?.let { ds ->
            parseDateMs(ds)?.let { ms -> dueDateMs.value = ms; touched.add("due date") }
        }
        fields["priority"]?.lowercase()?.let { p ->
            priority.value = WoPriority.fromKey(p); touched.add("priority")
        }
        fields["estimated_effort_minutes"]?.toDoubleOrNull()?.let { mins ->
            estimatedHours.value = mins.toLong().toString(); touched.add("estimated effort")
        }
        fields["assigned_to"]?.takeIf { it.isNotBlank() }?.let { name ->
            assignedTechIds.value = listOf(name); touched.add("assigned to")
        }
        return touched
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    private val ymdFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun parseDateMs(v: String): Long? = try {
        isoFormat.parse(v)?.time
    } catch (_: Exception) {
        try { ymdFormat.parse(v)?.time } catch (_: Exception) { null }
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
                ?.toString() ?: ""
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
    // Asset picker
    // ---------------------------------------------------------------------------

    fun onAssetSelected(assetId: String) {
        this.assetId.value = assetId
        _recentCategoryKeys.value = emptyList()
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val asset = repository.getAssetById(accountId, assetId)
            assetName.value = asset?.name
            val assetType = asset?.assetType?.takeIf { it.isNotBlank() } ?: "light_vehicle"
            loadCategoriesForAssetType(assetType, accountId)
            loadRecentCategories(assetId, accountId)
        }
    }

    /** Loads categories for the given asset type — same two-pass lookup as AddEditLogViewModel. */
    private suspend fun loadCategoriesForAssetType(assetType: String, accountId: String) {
        try {
            val db = dbFactory.get(accountId)
            val categories =
                db.configDao().getByKey("ItemCategory", assetType)
                    ?.let { parseCategoriesFromItemCategoryConfig(it.value) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: db.configDao().getByKey("system", "log_categories")
                        ?.let { parseCategoriesFromSeededConfig(it.value, assetType) }
            if (!categories.isNullOrEmpty()) {
                _availableCategories.value = categories
            }
        } catch (e: Exception) {
            Timber.e(e, "[WoCreateVM] loadCategoriesForAssetType failed for $assetType")
        }
    }

    /** Queries the log table for the 4 most-recently-used distinct categories on this asset. */
    private suspend fun loadRecentCategories(assetId: String, accountId: String) {
        try {
            val db = dbFactory.get(accountId)
            val recents = db.logDao()
                .observeAll(accountId)
                .map { logs ->
                    logs.filter { it.assetId == assetId && !it.category.isNullOrBlank() }
                        .sortedByDescending { it.entryDate }
                        .map { it.category!! }
                        .distinct()
                        .take(4)
                }
                .first()
            _recentCategoryKeys.value = recents
        } catch (e: Exception) {
            Timber.e(e, "[WoCreateVM] loadRecentCategories failed for assetId=$assetId")
        }
    }

    /** {"categories":[{"id":"oil_change",...},...]} — mirrors AddEditLogViewModel.parseCategoriesFromConfig */
    private fun parseCategoriesFromItemCategoryConfig(json: String): List<String> {
        val catStart = json.indexOf("\"categories\"")
        val arrStart = if (catStart >= 0) json.indexOf('[', catStart) else json.indexOf('[')
        if (arrStart < 0) return emptyList()
        var depth = 0; var arrEnd = arrStart
        for (i in arrStart until json.length) when (json[i]) {
            '[' -> depth++; ']' -> { depth--; if (depth == 0) { arrEnd = i; break } }
        }
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(json.substring(arrStart, arrEnd + 1))
            .map { it.groupValues[1] }.toList()
    }

    /** [{"asset_type":"light_vehicle","categories":[...]}] — mirrors AddEditLogViewModel.parseCategoriesFromSeeded */
    private fun parseCategoriesFromSeededConfig(json: String, assetType: String): List<String> {
        val assetKey = "\"asset_type\":\"$assetType\""
        val blockStart = json.indexOf(assetKey).takeIf { it >= 0 }
            ?: return parseCategoriesFromItemCategoryConfig(json)
        val catStart = json.indexOf("\"categories\"", blockStart)
        val arrStart = if (catStart >= 0) json.indexOf('[', catStart) else return emptyList()
        var depth = 0; var arrEnd = arrStart
        for (i in arrStart until json.length) when (json[i]) {
            '[' -> depth++; ']' -> { depth--; if (depth == 0) { arrEnd = i; break } }
        }
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(json.substring(arrStart, arrEnd + 1))
            .map { it.groupValues[1] }.toList()
    }

    // ---------------------------------------------------------------------------
    // Job picker
    // ---------------------------------------------------------------------------

    fun onJobSelected(jobId: String, jobTitle: String) {
        this.jobId.value = jobId
        this.jobTitle.value = jobTitle
    }

    fun clearJob() {
        jobId.value = null
        jobTitle.value = null
    }

    fun onLocationSelected(id: String?, name: String?) {
        locationId.value = id
        locationName.value = name
    }

    // ---------------------------------------------------------------------------
    // Timezone
    // ---------------------------------------------------------------------------

    fun onTimezoneChanged(tz: String) {
        timezone.value = tz
    }

    // ---------------------------------------------------------------------------
    // Effort hint
    // ---------------------------------------------------------------------------

    fun fetchEffortHint(categoryKey: String) {
        viewModelScope.launch {
            try {
                val accountId = identityManager.getActiveAccountId() ?: return@launch
                val result = serviceClient.getEffortStats(accountId, categoryKey)
                if (result is NetworkResult.Success) {
                    val stats = result.data
                    _effortHint.value = EffortHint(
                        typicalMinutes = (stats.p50_hours * 60).toInt(),
                        fastMinutes = (stats.p10_hours * 60).toInt(),
                        slowMinutes = (stats.p90_hours * 60).toInt(),
                        sampleCount = stats.sample_size,
                    )
                }
            } catch (e: Exception) {
                // Effort hint is not critical — silently ignore failures
                Timber.d(e, "[WoCreateVM] fetchEffortHint failed, ignoring")
            }
        }
    }

    private fun loadAvailableCategories() {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val categories = runCatching {
                val db = dbFactory.get(accountId)
                val raw = db.configDao().getByKey("system", "wo_categories")?.value
                    ?: db.configDao().getByKey("system", "work_order_categories")?.value
                    ?: db.configDao().getByKey("system", "log_categories")?.value
                parseCategoryKeys(raw)
            }.getOrDefault(emptyList())
            _availableCategories.value = categories
        }
    }

    // Parses category KEY strings (e.g. "oil_change") from the config JSON.
    // Mirrors AddEditLogViewModel.parseCategoriesFromConfig — must extract "id"/"key",
    // NOT "label", so that categoryIconName / categoryBadgeColor / categoryGroup
    // can resolve icons, colors, and groups correctly.
    private fun parseCategoryKeys(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = Json.parseToJsonElement(raw)
            val buckets = when (root) {
                is JsonObject -> listOf(root)
                else -> root.jsonArray.mapNotNull { it as? JsonObject }
            }
            val grouped = buckets.flatMap { group ->
                val items = group["items"]?.jsonArray
                    ?: group["categories"]?.jsonArray
                    ?: return@flatMap emptyList()
                items.mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull item.jsonPrimitive.contentOrNull
                    // Priority: "id" (server format) → "key" → "label" (last resort)
                    obj["id"]?.jsonPrimitive?.contentOrNull
                        ?: obj["key"]?.jsonPrimitive?.contentOrNull
                        ?: obj["label"]?.jsonPrimitive?.contentOrNull
                }
            }.distinct()
            grouped.ifEmpty {
                when (root) {
                    is JsonArray -> root.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    else -> emptyList()
                }
            }
        }.onFailure { Timber.w(it, "parseCategoryKeys: failed to parse config value") }
            .getOrDefault(emptyList())
    }

    fun decodeVin() {
        val vinValue = vin.value.trim()
        if (vinValue.isBlank()) return
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isDecodingVin.value = true
            try {
                when (val result = serviceClient.decodeVin(accountId, vinValue)) {
                    is NetworkResult.Success -> {
                        _vinDecodeResult.value = result.data
                        if (title.value.isBlank()) {
                            val generatedTitle = listOfNotNull(
                                result.data.year?.toString(),
                                result.data.make?.takeIf { it.isNotBlank() },
                                result.data.model?.takeIf { it.isNotBlank() },
                            ).joinToString(" ")
                            if (generatedTitle.isNotBlank()) {
                                title.value = generatedTitle
                            }
                        }
                    }
                    else -> {
                        _vinDecodeResult.value = null
                    }
                }
            } catch (e: Exception) {
                _vinDecodeResult.value = null
                Timber.d(e, "[WoCreateVM] decodeVin failed")
            } finally {
                _isDecodingVin.value = false
            }
        }
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
                    ?.toLong()

                val entity = WorkOrderEntity(
                    woId = woId,
                    accountId = accountId,
                    assetId = assetId.value,
                    locationId = locationId.value ?: existing?.locationId,
                    title = titleVal,
                    description = description.value.ifBlank { null },
                    category = category.value ?: existing?.category,
                    priority = priority.value.key,
                    // New WOs are submitted as "pending_review" — mirrors iOS:
                    //   wo.status = "pending_review" (draft is no longer a creatable state).
                    // Edit mode preserves the existing status.
                    status = existing?.status ?: "pending_review",
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
                    jobId = jobId.value ?: existing?.jobId,
                    woKind = if (repeatsRrule.value.isNullOrBlank()) "one_off" else "recurring_parent",
                    rrule = repeatsRrule.value?.ifBlank { null },
                    endType = if (repeatsRrule.value.isNullOrBlank()) null else existing?.endType,
                    endCount = if (repeatsRrule.value.isNullOrBlank()) null else existing?.endCount,
                    endDate = if (repeatsRrule.value.isNullOrBlank()) null else existing?.endDate,
                    meterType = existing?.meterType,
                    meterDue = existing?.meterDue,
                    meterInterval = existing?.meterInterval,
                    parentWoId = existing?.parentWoId,
                    occurrenceDate = existing?.occurrenceDate,
                    scheduleId = existing?.scheduleId,
                    lastCompletedAt = existing?.lastCompletedAt,
                    timezone = timezone.value.takeIf { dueDateMs.value != null } ?: existing?.timezone,
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
