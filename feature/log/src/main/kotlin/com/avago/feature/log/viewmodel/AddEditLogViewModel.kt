package com.avago.feature.log.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.sync.SyncEngine
import com.avago.feature.log.model.InspectionFieldDef
import com.avago.feature.log.model.LogCostLineDraft
import com.avago.feature.log.model.parseInspectionFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** "total" | "itemized" */
enum class CostMode { TOTAL, ITEMIZED }

/**
 * Defines a single category-specific item attribute field shown in the "Item Details" section.
 */
data class ItemAttributeDef(
    val key: String,
    val label: String,
    val fieldType: String, // "text", "number", "enum"
    val options: List<String> = emptyList(),
    val unit: String? = null,
)

data class AddEditLogFormState(
    // IDs
    val entryId: String = UUID.randomUUID().toString(),
    val assetId: String? = null,
    val assetName: String? = null,

    // Core fields
    val title: String = "",
    val category: String? = null,
    val logType: String = "service", // service | inspection | note | fuel
    val entryDate: Long = System.currentTimeMillis(),
    val notes: String = "",

    // Performer
    val performedByUserId: String? = null,
    val performedByName: String? = null,

    // Meter
    val meterReading: String = "",
    /** Derived from the selected asset's meterType field. */
    val meterType: String? = null,

    // Cost
    val costMode: CostMode = CostMode.TOTAL,
    val totalCost: String = "",
    val pendingCostLines: List<LogCostLineDraft> = emptyList(),

    // Photos (local URIs pending upload)
    val photoUris: List<Uri> = emptyList(),

    // Inspection form answers: key -> value string
    val inspectionAnswers: Map<String, String> = emptyMap(),

    // Available categories from config
    val availableCategories: List<String> = emptyList(),

    // Inspection field definitions loaded from ConfigEntity (scope="system", key="inspection_fields")
    // Populated by loadInspectionFields(); empty until the asset type is known.
    val inspectionFields: List<InspectionFieldDef> = emptyList(),

    // Category-specific item attributes
    val itemAttributes: Map<String, String> = emptyMap(),
    val itemAttributeDefs: List<ItemAttributeDef> = emptyList(),

    // Save state
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedEntryId: String? = null,

    val isLoadingExisting: Boolean = false,
) {
    val itemizedTotal: Double get() = pendingCostLines.sumOf { it.quantity * it.unitCost + (it.taxAmount ?: 0.0) }
    val costLineCount: Int get() = pendingCostLines.size
    val isEdit: Boolean get() = savedEntryId == null && isLoadingExisting // simple heuristic

    /** Human-readable meter label based on asset meterType. */
    val meterLabel: String get() = when (meterType?.lowercase()) {
        "odometer", "miles", "mi" -> "Odometer (mi)"
        "hours", "hour", "hr" -> "Hours"
        else -> "Meter"
    }
}

@HiltViewModel
class AddEditLogViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _form = MutableStateFlow(AddEditLogFormState())
    val form: StateFlow<AddEditLogFormState> = _form.asStateFlow()

    private var originalCreatedAt: Long? = null
    private var originalServerVersion: Long = 0L

    // ---------------------------------------------------------------------------
    // Load for edit
    // ---------------------------------------------------------------------------

    fun loadForEdit(entryId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        _form.update { it.copy(isLoadingExisting = true) }
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val entity = db.logDao().getById(entryId) ?: run {
                    _form.update { it.copy(isLoadingExisting = false) }
                    return@launch
                }
                originalCreatedAt = entity.createdAt
                originalServerVersion = entity.serverVersion

                // Parse inspection answers from data JSON
                val inspectionAnswers = parseJsonMap(entity.data)

                // One-shot read of cost lines for this entry
                val drafts = db.logCostLineDao().observeAll(accountId)
                    .map { lines ->
                        lines.filter { it.logId == entryId }
                            .sortedBy { it.displayOrder }
                            .mapIndexed { idx, line ->
                                LogCostLineDraft(
                                    lineId = line.lineId,
                                    kind = line.kind,
                                    inventoryId = line.inventoryId,
                                    inventoryName = null,
                                    userId = line.userId,
                                    userName = null,
                                    description = line.description ?: "",
                                    quantity = line.quantity,
                                    unitCost = line.unitCost,
                                    taxAmount = line.taxAmount,
                                    glCode = line.glCode,
                                    displayOrder = idx,
                                )
                            }
                    }
                    .first()

                // Load meterType from asset
                val asset = entity.assetId.let { db.assetDao().getById(it) }
                val meterType = asset?.meterType

                _form.update { state ->
                    state.copy(
                        entryId = entity.entryId,
                        assetId = entity.assetId,
                        title = entity.title,
                        category = entity.category,
                        logType = entity.data?.let { parseJsonField(it, "log_type") } ?: "service",
                        entryDate = entity.entryDate,
                        notes = entity.notes ?: "",
                        performedByUserId = entity.performedByUserId,
                        performedByName = entity.performedBy,
                        meterReading = entity.odometerValue?.toString() ?: "",
                        meterType = meterType,
                        costMode = if (entity.costMode == "itemized") CostMode.ITEMIZED else CostMode.TOTAL,
                        totalCost = entity.cost?.toString() ?: "",
                        pendingCostLines = drafts,
                        inspectionAnswers = inspectionAnswers,
                        isLoadingExisting = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadForEdit failed")
                _form.update { it.copy(isLoadingExisting = false) }
            }
        }
    }

    fun loadCategories(assetType: String? = null) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val config = db.configDao().getByKey("system", "log_categories")
                val allCategories = parseCategoryList(config?.value)
                _form.update { it.copy(availableCategories = allCategories) }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadCategories failed")
            }
        }
    }

    /**
     * Loads inspection field definitions from ConfigEntity.
     * The config key "inspection_fields" stores a JSON array of [InspectionFieldDef].
     * Falls back to the asset-type-specific key "inspection_fields_{assetType}" when available.
     */
    fun loadInspectionFields(assetType: String? = null) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                // Try asset-type-specific config first, then fall back to global key
                val specificKey = if (!assetType.isNullOrBlank()) "inspection_fields_$assetType" else null
                val config = specificKey?.let { db.configDao().getByKey("system", it) }
                    ?: db.configDao().getByKey("system", "inspection_fields")
                val fields = parseInspectionFields(config?.value)
                _form.update { it.copy(inspectionFields = fields) }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadInspectionFields failed")
                // Leave inspectionFields as empty list; UI handles the empty-state gracefully
            }
        }
    }

    /**
     * Loads category-specific item attribute definitions from ConfigEntity.
     * Looks for key "item_attributes_{categoryKey}" under scope "system".
     * If no config is found, clears itemAttributeDefs so the section is hidden.
     */
    private fun loadItemAttributeDefs(category: String?) {
        if (category.isNullOrBlank()) {
            _form.update { it.copy(itemAttributeDefs = emptyList()) }
            return
        }
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val categoryKey = category.lowercase().replace(" ", "_")
                val config = db.configDao().getByKey("system", "item_attributes_$categoryKey")
                val defs = parseItemAttributeDefs(config?.value)
                _form.update { it.copy(itemAttributeDefs = defs) }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadItemAttributeDefs failed for category=$category")
                _form.update { it.copy(itemAttributeDefs = emptyList()) }
            }
        }
    }

    /**
     * Pre-fills the meter reading from the most recent log entry for this asset,
     * but only when the current meter field is blank.
     */
    private fun prefillMeterFromPriorEntry(assetId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                // Only pre-fill when the field is currently blank
                if (_form.value.meterReading.isNotBlank()) return@launch

                val latestEntry = db.logDao()
                    .observeAll(accountId)
                    .map { logs ->
                        logs.filter { it.assetId == assetId && it.odometerValue != null }
                            .maxByOrNull { it.entryDate }
                    }
                    .first()

                if (latestEntry?.odometerValue != null) {
                    // Only set if still blank (avoid clobbering if user typed quickly)
                    if (_form.value.meterReading.isBlank()) {
                        _form.update { it.copy(meterReading = latestEntry.odometerValue.toString()) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] prefillMeterFromPriorEntry failed for assetId=$assetId")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Field setters
    // ---------------------------------------------------------------------------

    fun onAssetSelected(assetId: String, assetName: String?) {
        _form.update { it.copy(assetId = assetId, assetName = assetName) }
        // Reload categories and inspection fields for this asset's type
        loadCategories()
        loadInspectionFields()
        // Load asset details for meterType
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val asset = dbFactory.get(accountId).assetDao().getById(assetId)
                _form.update { it.copy(meterType = asset?.meterType) }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] onAssetSelected: failed to load asset meterType")
            }
        }
        prefillMeterFromPriorEntry(assetId)
    }

    fun onTitleChanged(value: String) = _form.update { it.copy(title = value) }

    fun onCategoryChanged(value: String?) {
        _form.update { it.copy(category = value) }
        loadItemAttributeDefs(value)
    }

    fun onLogTypeChanged(value: String) = _form.update { it.copy(logType = value) }
    fun onEntryDateChanged(value: Long) = _form.update { it.copy(entryDate = value) }
    fun onNotesChanged(value: String) = _form.update { it.copy(notes = value) }
    fun onMeterReadingChanged(value: String) = _form.update { it.copy(meterReading = value) }
    fun onTotalCostChanged(value: String) = _form.update { it.copy(totalCost = value) }
    fun onCostModeChanged(mode: CostMode) = _form.update { it.copy(costMode = mode) }

    fun onPerformedBySelected(userId: String?, name: String?) {
        _form.update { it.copy(performedByUserId = userId, performedByName = name) }
    }

    fun onItemAttributeChanged(key: String, value: String) {
        _form.update { state ->
            state.copy(itemAttributes = state.itemAttributes + (key to value))
        }
    }

    /**
     * Called when a part barcode is confirmed via [MaintenanceScannerScreen].
     * Switches to itemized cost mode and adds a part cost line pre-filled with the
     * scanned part's ID and name.
     */
    fun onScannedPartSelected(partId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val part = dbFactory.get(accountId).partDao().getById(partId)
                val draft = LogCostLineDraft(
                    kind = "part",
                    inventoryId = partId,
                    inventoryName = part?.name,
                    description = part?.name ?: partId,
                    quantity = 1.0,
                    unitCost = part?.cost ?: 0.0,
                )
                _form.update { state ->
                    state.copy(
                        costMode = CostMode.ITEMIZED,
                        pendingCostLines = state.pendingCostLines + draft.copy(
                            displayOrder = state.pendingCostLines.size
                        ),
                    )
                }
                Timber.d("[AddEditLogViewModel] Added scanned part cost line: $partId")
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] onScannedPartSelected failed for partId=$partId")
            }
        }
    }

    fun onInspectionAnswerChanged(key: String, value: String) {
        _form.update { state ->
            state.copy(inspectionAnswers = state.inspectionAnswers + (key to value))
        }
    }

    // ---------------------------------------------------------------------------
    // Cost lines
    // ---------------------------------------------------------------------------

    fun addCostLine(draft: LogCostLineDraft) {
        _form.update { state ->
            state.copy(
                pendingCostLines = state.pendingCostLines + draft.copy(
                    displayOrder = state.pendingCostLines.size
                )
            )
        }
    }

    fun updateCostLine(draft: LogCostLineDraft) {
        _form.update { state ->
            state.copy(
                pendingCostLines = state.pendingCostLines.map { existing ->
                    if (existing.lineId == draft.lineId) draft else existing
                }
            )
        }
    }

    fun removeCostLine(lineId: String) {
        _form.update { state ->
            state.copy(
                pendingCostLines = state.pendingCostLines
                    .filter { it.lineId != lineId }
                    .mapIndexed { idx, d -> d.copy(displayOrder = idx) }
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Photos
    // ---------------------------------------------------------------------------

    fun addPhotoUri(uri: Uri) {
        _form.update { state ->
            state.copy(photoUris = state.photoUris + uri)
        }
    }

    fun removePhotoUri(uri: Uri) {
        _form.update { state ->
            state.copy(photoUris = state.photoUris - uri)
        }
    }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    fun save(onSuccess: (entryId: String) -> Unit) {
        val current = _form.value
        val accountId = identity.getActiveAccountId()

        if (accountId == null) {
            _form.update { it.copy(saveError = "No active account") }
            return
        }
        if (current.assetId == null) {
            _form.update { it.copy(saveError = "Please select an asset") }
            return
        }
        if (current.title.isBlank()) {
            _form.update { it.copy(saveError = "Title is required") }
            return
        }

        _form.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val now = System.currentTimeMillis()
                val entryId = current.entryId

                // Build the data JSON for log_type and inspection answers
                val dataJson = buildDataJson(current.logType, current.inspectionAnswers)

                val costMode = if (current.costMode == CostMode.ITEMIZED) "itemized" else "total"
                val cost = when (current.costMode) {
                    CostMode.TOTAL -> current.totalCost.toDoubleOrNull()
                    CostMode.ITEMIZED -> current.itemizedTotal.takeIf { it > 0 }
                }

                val entity = LogEntity(
                    entryId = entryId,
                    assetId = current.assetId,
                    accountId = accountId,
                    title = current.title.trim(),
                    entryDate = current.entryDate,
                    odometerValue = current.meterReading.toDoubleOrNull(),
                    category = current.category,
                    cost = cost,
                    performedBy = current.performedByName,
                    performedByUserId = current.performedByUserId,
                    notes = current.notes.trim().ifBlank { null },
                    data = dataJson,
                    attributes = null,
                    costMode = costMode,
                    costItems = if (current.costMode == CostMode.ITEMIZED) {
                        current.pendingCostLines
                            .filter { it.kind == "part" }
                            .sumOf { it.quantity * it.unitCost }
                    } else null,
                    costLabor = if (current.costMode == CostMode.ITEMIZED) {
                        current.pendingCostLines
                            .filter { it.kind == "labor" }
                            .sumOf { it.quantity * it.unitCost }
                    } else null,
                    costTax = if (current.costMode == CostMode.ITEMIZED) {
                        current.pendingCostLines.sumOf { it.taxAmount ?: 0.0 }.takeIf { it > 0 }
                    } else null,
                    currency = null,
                    baseAmount = null,
                    exchangeRateUsed = null,
                    configId = null,
                    configVersion = null,
                    serviceId = null,
                    costMisc = null,
                    parentId = null,
                    createdAt = originalCreatedAt ?: now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = originalServerVersion,
                    seq = null,
                )

                db.logDao().upsert(entity)
                enqueueLogSync(accountId, entryId, now)

                // Upsert cost lines if itemized
                if (current.costMode == CostMode.ITEMIZED) {
                    current.pendingCostLines.forEachIndexed { idx, draft ->
                        val lineEntity = LogCostLineEntity(
                            lineId = draft.lineId,
                            accountId = accountId,
                            logId = entryId,
                            kind = draft.kind,
                            displayOrder = idx.toLong(),
                            inventoryId = draft.inventoryId,
                            userId = draft.userId,
                            description = draft.description.ifBlank { null },
                            quantity = draft.quantity,
                            unitCost = draft.unitCost,
                            taxAmount = draft.taxAmount,
                            glCode = draft.glCode?.ifBlank { null },
                            notes = null,
                            woId = null,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                            serverVersion = 0L,
                            seq = null,
                        )
                        db.logCostLineDao().upsert(lineEntity)
                        enqueueCostLineSync(accountId, draft.lineId, now)
                    }
                }

                // Stub: save local photo URIs to PhotoEntity
                current.photoUris.forEachIndexed { idx, uri ->
                    val photoId = UUID.randomUUID().toString()
                    val photoEntity = PhotoEntity(
                        photoId = photoId,
                        entityId = entryId,
                        entityType = "log",
                        accountId = accountId,
                        storageKey = null,
                        downloadUrl = uri.toString(),
                        sortOrder = idx.toLong(),
                        isPrimary = idx == 0,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = 0L,
                    )
                    db.photoDao().upsert(photoEntity)
                }

                // Trigger push
                syncEngine.pushIfNeeded()

                _form.update { it.copy(isSaving = false, savedEntryId = entryId) }
                Timber.d("[AddEditLogViewModel] Saved log $entryId")
                onSuccess(entryId)
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] Save failed")
                _form.update { it.copy(isSaving = false, saveError = e.message ?: "Save failed") }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Sync helpers
    // ---------------------------------------------------------------------------

    private suspend fun enqueueLogSync(accountId: String, entryId: String, now: Long) {
        val db = dbFactory.get(accountId)
        db.syncQueueDao().enqueueOrReplace(
            SyncQueueEntity(
                queueId = "log:$entryId:upsert",
                entityType = "log",
                entityId = entryId,
                operation = "upsert",
                serverVersion = originalServerVersion,
                payload = null,
                syncStatus = "pending",
                attempts = 0L,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private suspend fun enqueueCostLineSync(accountId: String, lineId: String, now: Long) {
        val db = dbFactory.get(accountId)
        db.syncQueueDao().enqueueOrReplace(
            SyncQueueEntity(
                queueId = "log_cost_line:$lineId:upsert",
                entityType = "log_cost_line",
                entityId = lineId,
                operation = "upsert",
                serverVersion = null,
                payload = null,
                syncStatus = "pending",
                attempts = 0L,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    // ---------------------------------------------------------------------------
    // JSON helpers (no external dep — plain string building)
    // ---------------------------------------------------------------------------

    private fun buildDataJson(logType: String, inspectionAnswers: Map<String, String>): String? {
        val parts = mutableListOf<String>()
        parts += "\"log_type\":\"$logType\""
        inspectionAnswers.forEach { (k, v) ->
            val escaped = v.replace("\\", "\\\\").replace("\"", "\\\"")
            parts += "\"$k\":\"$escaped\""
        }
        return if (parts.isEmpty()) null else "{${parts.joinToString(",")}}"
    }

    private fun parseJsonField(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun parseJsonMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
        pattern.findAll(json).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun parseCategoryList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        // Expects a JSON array of strings: ["Oil Change","Inspection",...]
        val pattern = Regex("\"([^\"]+)\"")
        return pattern.findAll(json).map { it.groupValues[1] }.toList()
    }

    /**
     * Parses a JSON array of ItemAttributeDef objects from a config value string.
     * Expected format:
     * [{"key":"color","label":"Color","fieldType":"text"},
     *  {"key":"size","label":"Size","fieldType":"enum","options":["S","M","L"],"unit":"in"}]
     */
    private fun parseItemAttributeDefs(json: String?): List<ItemAttributeDef> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            // Reuse the lenient JSON parsing approach via InspectionFieldDef parser infrastructure.
            // We do manual regex-free parsing via the same kotlinx.serialization path used in
            // parseInspectionFields, but adapted here inline for ItemAttributeDef.
            val defs = mutableListOf<ItemAttributeDef>()
            // Strip outer array brackets and split on object boundaries
            val inner = json.trim().removePrefix("[").removeSuffix("]")
            // Use a simple brace-depth scanner to split individual objects
            val objects = mutableListOf<String>()
            var depth = 0
            var start = -1
            for (i in inner.indices) {
                when (inner[i]) {
                    '{' -> {
                        if (depth == 0) start = i
                        depth++
                    }
                    '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            objects += inner.substring(start, i + 1)
                            start = -1
                        }
                    }
                }
            }
            val keyPattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
            val arrayPattern = Regex("\"options\"\\s*:\\s*\\[([^\\]]*)]")
            for (obj in objects) {
                val fields = keyPattern.findAll(obj).associate { it.groupValues[1] to it.groupValues[2] }
                val key = fields["key"] ?: continue
                val label = fields["label"] ?: key
                val fieldType = fields["fieldType"] ?: fields["field_type"] ?: "text"
                val unit = fields["unit"]?.ifBlank { null }
                val optionsJson = arrayPattern.find(obj)?.groupValues?.get(1) ?: ""
                val options = Regex("\"([^\"]+)\"").findAll(optionsJson).map { it.groupValues[1] }.toList()
                defs += ItemAttributeDef(key = key, label = label, fieldType = fieldType, options = options, unit = unit)
            }
            defs
        } catch (e: Exception) {
            Timber.e(e, "[AddEditLogViewModel] parseItemAttributeDefs failed")
            emptyList()
        }
    }
}
