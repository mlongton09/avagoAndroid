package com.avago.feature.log.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.FormFillRouter
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.data.db.entity.PhotoEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.data.repository.UserPreferencesRepository
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
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** "total" | "itemized" */
enum class CostMode { TOTAL, ITEMIZED }

/** Typed validation failures shown as alert dialogs — mirrors iOS UIAlertController usage. */
enum class LogValidationError { TITLE_REQUIRED, NO_ASSET, NO_ACCOUNT }

/**
 * Defines a single category-specific item attribute field shown in the "Item Details" section.
 */
data class ItemAttributeDef(
    val key: String,
    val label: String,
    val fieldType: String, // "text", "number", "enum", "checkbox", "multiline"
    val options: List<String> = emptyList(),
    val unit: String? = null,
    val placeholder: String? = null,
)

data class AddEditLogFormState(
    // IDs
    val entryId: String = UUID.randomUUID().toString(),
    val assetId: String? = null,
    val assetName: String? = null,
    val assetType: String? = null,

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
    /** Mirrors iOS AVDefaultsKeyDefaultOdometerUnit — "mi" or "km". */
    val distanceUnit: String = "mi",

    // Fuel (shown when category is "fuel")
    val fuelVolume: String = "",
    /** "gallon" or "liter" — mirrors iOS AVDefaultsKeyFuelVolumeUnit. */
    val fuelVolumeUnit: String = "gallon",

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

    // Inspection subtype (e.g. "Base", "Full") and mode ("Basic"/"Full")
    val inspectionSubtype: String? = null,
    val inspectionMode: String? = null,
    val inspectionConfigId: String? = null,
    val inspectionConfigVersion: Long = 0L,
    val availableInspectionSubtypes: List<String> = emptyList(),

    // Inspection field definitions loaded from ConfigEntity (scope="Inspection", key="{assetType}_{subtype}")
    // Populated by loadInspectionFields(); empty until the asset type is known.
    val inspectionFields: List<InspectionFieldDef> = emptyList(),

    // Category-specific item attributes
    val itemAttributes: Map<String, String> = emptyMap(),
    val itemAttributeDefs: List<ItemAttributeDef> = emptyList(),

    // Save state
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedEntryId: String? = null,
    val validationError: LogValidationError? = null,

    val isLoadingExisting: Boolean = false,
) {
    val itemizedTotal: Double get() = pendingCostLines.sumOf { it.quantity * it.unitCost + (it.taxAmount ?: 0.0) }
    val costLineCount: Int get() = pendingCostLines.size
    val isEdit: Boolean get() = savedEntryId == null && isLoadingExisting // simple heuristic

    /** Human-readable meter label based on asset meterType and user distance unit preference. */
    val meterLabel: String get() = when (meterType?.lowercase()) {
        "odometer", "miles", "mi", "km" -> "Odometer ($distanceUnit)"
        "hours", "hour", "hr" -> "Hours"
        else -> "Meter"
    }
}

@HiltViewModel
class AddEditLogViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val syncEngine: SyncEngine,
    private val userPrefsRepository: UserPreferencesRepository,
    private val formFillRouter: FormFillRouter,
) : ViewModel() {

    init {
        formFillRouter.register("add_log_entry") { fields -> applyScoutFields(fields) }
        viewModelScope.launch {
            val distanceUnit = userPrefsRepository.distanceUnitFlow.first()
            val fuelUnit = userPrefsRepository.fuelVolumeUnitFlow.first()
            _form.update { it.copy(distanceUnit = distanceUnit, fuelVolumeUnit = fuelUnit) }
        }
    }

    override fun onCleared() {
        formFillRouter.unregister("add_log_entry")
        super.onCleared()
    }

    fun applyScoutFields(fields: Map<String, String?>): List<String> {
        val touched = mutableListOf<String>()
        _form.update { state ->
            var s = state
            fields["title"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                s = s.copy(title = it); touched.add("title")
            }
            fields["asset_id"]?.let {
                s = s.copy(assetId = it); touched.add("asset")
            }
            fields["category"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                s = s.copy(category = it); touched.add("category")
            }
            fields["cost"]?.toDoubleOrNull()?.let {
                s = s.copy(totalCost = it.toString()); touched.add("cost")
            }
            (fields["meter"] ?: fields["odometer"])?.trim()?.takeIf { it.isNotEmpty() }?.let {
                s = s.copy(meterReading = it); touched.add("meter")
            }
            fields["notes"]?.trim()?.let {
                s = s.copy(notes = it); touched.add("notes")
            }
            s
        }
        return touched
    }

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

                val inspectionAnswers = parseJsonMap(entity.data)
                val inspectionSubtype = entity.data?.let { parseJsonField(it, "inspection_subtype") }
                val inspectionMode = entity.data?.let { parseJsonField(it, "inspection_mode") }

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
                val assetType = asset?.assetType

                // Restore fuel attributes from entity.attributes JSON
                val savedFuelVolume = entity.attributes?.let { parseJsonField(it, "fuel_volume") } ?: ""
                val savedFuelUnit = entity.attributes?.let { parseJsonField(it, "fuel_volume_unit") }
                    ?: userPrefsRepository.fuelVolumeUnitFlow.first()
                val savedDistanceUnit = userPrefsRepository.distanceUnitFlow.first()
                // Restore category-specific item attributes (excludes known fuel keys)
                val savedItemAttributes = entity.attributes?.let { attrs ->
                    parseJsonMap(attrs).filterKeys { it != "fuel_volume" && it != "fuel_volume_unit" }
                } ?: emptyMap()

                _form.update { state ->
                    state.copy(
                        entryId = entity.entryId,
                        assetId = entity.assetId,
                        assetType = assetType,
                        title = entity.title,
                        category = entity.category,
                        logType = entity.data?.let { parseJsonField(it, "log_type") } ?: "service",
                        entryDate = entity.entryDate,
                        notes = entity.notes ?: "",
                        performedByUserId = entity.performedByUserId,
                        performedByName = entity.performedBy,
                        meterReading = entity.odometerValue?.toString() ?: "",
                        meterType = meterType,
                        distanceUnit = savedDistanceUnit,
                        fuelVolume = savedFuelVolume,
                        fuelVolumeUnit = savedFuelUnit,
                        costMode = if (entity.costMode == "itemized") CostMode.ITEMIZED else CostMode.TOTAL,
                        totalCost = entity.cost?.toString() ?: "",
                        pendingCostLines = drafts,
                        inspectionAnswers = inspectionAnswers,
                        inspectionSubtype = inspectionSubtype,
                        inspectionMode = inspectionMode,
                        inspectionConfigId = entity.configId,
                        inspectionConfigVersion = entity.configVersion ?: 0L,
                        itemAttributes = savedItemAttributes,
                        isLoadingExisting = false,
                    )
                }
                // Load item attribute defs for the restored category (so Add Details is shown for edits)
                if (!entity.category.isNullOrBlank()) {
                    loadItemAttributeDefs(entity.category)
                }
                // Load available categories so the category picker is populated for edits
                loadCategories(assetType)
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
                val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: "light_vehicle"

                // 1. Try server-synced ItemCategory config for this asset type
                val categories = db.configDao().getByKey("ItemCategory", type)
                    ?.let { parseCategoriesFromConfig(it.value) }
                    ?.takeIf { it.isNotEmpty() }
                // 2. Fall back to seeded system/log_categories filtered by assetType
                    ?: db.configDao().getByKey("system", "log_categories")
                        ?.let { parseCategoriesFromSeeded(it.value, type) }
                    ?: emptyList()

                _form.update { it.copy(availableCategories = categories) }

                // Auto-select default category — mirrors iOS:
                //   selectedCategory = initialCategory ?? existing?.category ?? assetCategories.first ?? "service"
                val currentCat = _form.value.category
                if ((currentCat == null || currentCat !in categories) && categories.isNotEmpty()) {
                    onCategoryChanged(categories.first())
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadCategories failed")
            }
        }
    }

    /**
     * Loads available inspection subtypes for the given asset type.
     * Queries configs with scope="Inspection" and key like "{assetType}_%".
     * Extracts the subtype portion from the key.
     */
    fun loadInspectionSubtypes(assetType: String? = null) {
        val accountId = identity.getActiveAccountId() ?: return
        val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: "light_vehicle"
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val configs = db.configDao().getByPattern("Inspection", "${type}_%")
                val subtypes = configs.mapNotNull { config ->
                    val key = config.key
                    val prefix = "${type}_"
                    if (key.startsWith(prefix)) key.removePrefix(prefix) else null
                }.distinct()
                _form.update { it.copy(availableInspectionSubtypes = subtypes) }
                // Auto-load "Base" subtype if available and none selected
                if (_form.value.inspectionSubtype == null) {
                    val defaultSubtype = when {
                        subtypes.contains("Base") -> "Base"
                        subtypes.isNotEmpty() -> subtypes.first()
                        else -> null
                    }
                    defaultSubtype?.let { setInspectionSubtype(it, "Basic", type) }
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadInspectionSubtypes failed for assetType=$assetType")
            }
        }
    }

    fun setInspectionSubtype(subtype: String, mode: String?, assetType: String? = null) {
        val accountId = identity.getActiveAccountId() ?: return
        val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: "light_vehicle"
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val config = db.configDao().getByPattern("Inspection", "${type}_$subtype")
                    .maxByOrNull { it.version }
                    ?: db.configDao().getByPattern("Inspection", "light_vehicle_$subtype")
                        .maxByOrNull { it.version }
                val fields = parseInspectionFields(config?.value)
                _form.update {
                    it.copy(
                        inspectionSubtype = subtype,
                        inspectionMode = mode,
                        inspectionConfigId = config?.configId,
                        inspectionConfigVersion = config?.version ?: 0L,
                        inspectionFields = fields,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] setInspectionSubtype failed for subtype=$subtype")
            }
        }
    }

    /**
     * Loads inspection field definitions from ConfigEntity.
     * Scope="Inspection", key="{assetType}_{subtype}". Falls back to scope="system",
     * key="inspection_fields_{assetType}" for backwards compatibility.
     */
    fun loadInspectionFields(assetType: String? = null) {
        val accountId = identity.getActiveAccountId() ?: return
        val subtype = _form.value.inspectionSubtype ?: "Base"
        val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: "light_vehicle"
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                // Try Inspection scope (iOS-style) first
                val config = db.configDao().getByPattern("Inspection", "${type}_$subtype")
                    .maxByOrNull { it.version }
                    ?: db.configDao().getByKey("system", "inspection_fields_$type")
                    ?: db.configDao().getByKey("system", "inspection_fields")
                val fields = parseInspectionFields(config?.value)
                if (config != null && config.scope == "Inspection") {
                    _form.update {
                        it.copy(
                            inspectionFields = fields,
                            inspectionConfigId = config.configId,
                            inspectionConfigVersion = config.version,
                        )
                    }
                } else {
                    _form.update { it.copy(inspectionFields = fields) }
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadInspectionFields failed")
            }
        }
    }

    /**
     * Loads category-specific item attribute definitions from ConfigEntity.
     * Mirrors iOS two-pass lookup:
     *   1. scope="ItemAttributes", key="{category}_{assetType}" (asset-specific)
     *   2. scope="ItemAttributes", key="{category}" (generic fallback)
     * Uses version-ordered getByPattern to avoid stale configs.
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
                val assetType = _form.value.assetType
                // Two-pass lookup matching iOS behaviour
                val config = if (!assetType.isNullOrBlank()) {
                    db.configDao().getByPattern("ItemAttributes", "${category}_${assetType}%")
                        .maxByOrNull { it.version }
                        ?: db.configDao().getByPattern("ItemAttributes", "$category%")
                            .filter { it.key == category }
                            .maxByOrNull { it.version }
                } else {
                    db.configDao().getByPattern("ItemAttributes", "$category%")
                        .filter { it.key == category }
                        .maxByOrNull { it.version }
                }
                val rawDefs = parseItemAttributeDefs(config?.value)
                val strings = loadItemAttrLocaleStrings(db)
                val resolvedDefs = rawDefs.map { def ->
                    def.copy(label = strings[def.label] ?: def.label.toAttrDisplayLabel())
                }
                _form.update { it.copy(itemAttributeDefs = resolvedDefs) }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadItemAttributeDefs failed for category=$category")
                _form.update { it.copy(itemAttributeDefs = emptyList()) }
            }
        }
    }

    /** Fetches the locale strings map for item attribute labels (scope="Locale", key="ItemAttributes"). */
    private suspend fun loadItemAttrLocaleStrings(db: com.avago.core.data.db.AvagoDatabase): Map<String, String> {
        return try {
            val config = db.configDao()
                .getByPattern("Locale", "ItemAttributes%")
                .maxByOrNull { it.version }
            if (config?.value.isNullOrBlank()) return emptyMap()
            val stringsStart = config!!.value.indexOf("\"strings\"")
            if (stringsStart < 0) return emptyMap()
            val mapStart = config.value.indexOf('{', stringsStart)
            if (mapStart < 0) return emptyMap()
            var depth = 0
            var mapEnd = mapStart
            for (i in mapStart until config.value.length) {
                when (config.value[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { mapEnd = i; break } }
                }
            }
            val mapJson = config.value.substring(mapStart, mapEnd + 1)
            Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
                .findAll(mapJson)
                .associate { it.groupValues[1] to it.groupValues[2] }
        } catch (e: Exception) {
            Timber.e(e, "[AddEditLogViewModel] loadItemAttrLocaleStrings failed")
            emptyMap()
        }
    }

    /** Converts a dot/underscore key like "item_attr.oil_change.oil_brand" to "Oil brand". */
    private fun String.toAttrDisplayLabel(): String =
        substringAfterLast('.').substringAfterLast('_').replace('_', ' ')
            .replaceFirstChar { it.uppercaseChar() }

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
        val accountId = identity.getActiveAccountId() ?: return
        // Load asset details first so assetType is known before reloading categories/inspection
        viewModelScope.launch {
            try {
                val asset = dbFactory.get(accountId).assetDao().getById(assetId)
                _form.update { it.copy(meterType = asset?.meterType, assetType = asset?.assetType) }
                // Reload categories and inspection fields with the correct assetType
                loadCategories(asset?.assetType)
                loadInspectionSubtypes(asset?.assetType)
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] onAssetSelected: failed to load asset details")
            }
        }
        prefillMeterFromPriorEntry(assetId)
    }

    fun onTitleChanged(value: String) = _form.update { it.copy(title = value) }

    fun onCategoryChanged(value: String?) {
        // Derive logType from category key — mirrors iOS behaviour where log type
        // is implicitly set by the category rather than a separate picker.
        val derivedLogType = when {
            value?.lowercase()?.contains("fuel") == true -> "fuel"
            value?.lowercase()?.contains("inspection") == true -> "inspection"
            value?.lowercase()?.contains("note") == true -> "note"
            else -> "service"
        }
        _form.update { it.copy(category = value, logType = derivedLogType) }
        loadItemAttributeDefs(value)
    }

    fun onLogTypeChanged(value: String) = _form.update { it.copy(logType = value) }
    fun onEntryDateChanged(value: Long) = _form.update { it.copy(entryDate = value) }
    fun onNotesChanged(value: String) = _form.update { it.copy(notes = value) }
    fun onMeterReadingChanged(value: String) = _form.update { it.copy(meterReading = value) }
    fun onFuelVolumeChanged(value: String) = _form.update { it.copy(fuelVolume = value) }
    fun onFuelVolumeUnitChanged(unit: String) = _form.update { it.copy(fuelVolumeUnit = unit) }
    fun onTotalCostChanged(value: String) = _form.update { it.copy(totalCost = value) }
    fun onCostModeChanged(mode: CostMode) = _form.update { it.copy(costMode = mode) }

    fun onPerformedBySelected(userId: String?, name: String?) {
        _form.update { it.copy(performedByUserId = userId, performedByName = name) }
    }

    /** Free-text entry: typing clears the linked user ID (mirrors iOS performed-by text field behaviour). */
    fun onPerformedByTextChanged(text: String) {
        _form.update { it.copy(performedByName = text.ifEmpty { null }, performedByUserId = null) }
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

    fun clearValidationError() {
        _form.update { it.copy(validationError = null) }
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
            _form.update { it.copy(validationError = LogValidationError.NO_ACCOUNT) }
            return
        }
        if (current.assetId == null) {
            _form.update { it.copy(validationError = LogValidationError.NO_ASSET) }
            return
        }
        if (current.title.isBlank()) {
            _form.update { it.copy(validationError = LogValidationError.TITLE_REQUIRED) }
            return
        }

        _form.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val now = System.currentTimeMillis()
                val entryId = current.entryId

                val dataJson = buildDataJson(
                    logType = current.logType,
                    inspectionAnswers = current.inspectionAnswers,
                    inspectionSubtype = current.inspectionSubtype,
                    inspectionMode = current.inspectionMode,
                    inspectionFields = current.inspectionFields,
                )

                val costMode = if (current.costMode == CostMode.ITEMIZED) "itemized" else "total"
                val cost = when (current.costMode) {
                    CostMode.TOTAL -> current.totalCost.toDoubleOrNull()
                    CostMode.ITEMIZED -> current.itemizedTotal.takeIf { it > 0 }
                }

                // Read the user's preferred currency code (mirrors iOS CurrencyManager.shared.preferredCurrencyCode)
                val preferredCurrency = userPrefsRepository.currencyFlow.firstOrNull() ?: "USD"

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
                    attributes = buildAttributesJson(current),
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
                    // Persist currency so server-side cost rollups and reporting can convert
                    // to a common base amount — mirrors iOS CurrencyManager.shared.preferredCurrencyCode.
                    // For USD the exchange rate is 1.0 and baseAmount == cost; for other currencies
                    // this will need to be updated when ExchangeRateService is wired in.
                    currency = preferredCurrency,
                    baseAmount = if (preferredCurrency == "USD") cost else null,
                    exchangeRateUsed = if (preferredCurrency == "USD") 1.0 else null,
                    configId = current.inspectionConfigId,
                    configVersion = current.inspectionConfigVersion.takeIf { it > 0 },
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
                _form.update { it.copy(isSaving = false, saveError = "save_failed") }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Sync helpers
    // ---------------------------------------------------------------------------

    private suspend fun enqueueLogSync(accountId: String, entryId: String, now: Long) {
        val db = dbFactory.get(accountId)
        db.syncQueueDao().enqueueWithDedup(
            SyncQueueEntity(
                queueId = "log_$entryId",
                entityType = "log",
                entityId = entryId,
                operation = if (originalServerVersion == 0L) "insert" else "update",
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
        val existingVersion = db.logCostLineDao().getById(lineId)?.serverVersion ?: 0L
        db.syncQueueDao().enqueueWithDedup(
            SyncQueueEntity(
                queueId = "log_cost_line_$lineId",
                entityType = "log_cost_line",
                entityId = lineId,
                operation = if (existingVersion == 0L) "insert" else "update",
                serverVersion = existingVersion,
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

    private fun buildDataJson(
        logType: String,
        inspectionAnswers: Map<String, String>,
        inspectionSubtype: String? = null,
        inspectionMode: String? = null,
        inspectionFields: List<InspectionFieldDef> = emptyList(),
    ): String? {
        val parts = mutableListOf<String>()
        parts += "\"log_type\":\"$logType\""
        if (!inspectionSubtype.isNullOrBlank()) {
            parts += "\"inspection_subtype\":\"${inspectionSubtype.replace("\"", "\\\"")}\""
        }
        if (!inspectionMode.isNullOrBlank()) {
            parts += "\"inspection_mode\":\"${inspectionMode.replace("\"", "\\\"")}\""
        }
        inspectionAnswers.forEach { (k, v) ->
            val escaped = v.replace("\\", "\\\\").replace("\"", "\\\"")
            parts += "\"$k\":\"$escaped\""
        }
        // Embed a stripped config snapshot so read mode can render without the configs table.
        // Only embed when we have actual inspection fields (mirrors iOS AVRenderableConfigSnapshot).
        if (logType == "inspection" && inspectionFields.isNotEmpty()) {
            val fieldJsons = inspectionFields.joinToString(",") { field ->
                val optStr = if (field.options.isEmpty()) "" else
                    ",\"options\":[${field.options.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
                "{\"key\":\"${field.key}\",\"label\":\"${field.label.replace("\"", "\\\"")}\",\"type\":\"${field.type}\"$optStr}"
            }
            parts += "\"configSnapshot\":{\"fields\":[$fieldJsons]}"
        }
        return if (parts.size <= 1 && inspectionAnswers.isEmpty()) null else "{${parts.joinToString(",")}}"
    }

    private fun buildAttributesJson(form: AddEditLogFormState): String? {
        val parts = mutableMapOf<String, String>()
        val isFuel = form.category?.lowercase()?.contains("fuel") == true
        if (isFuel && form.fuelVolume.isNotBlank()) {
            parts["fuel_volume"] = form.fuelVolume
            parts["fuel_volume_unit"] = form.fuelVolumeUnit
        }
        form.itemAttributes.forEach { (k, v) ->
            if (v.isNotBlank()) parts[k] = v
        }
        if (parts.isEmpty()) return null
        val json = parts.entries.joinToString(",") {
            "\"${it.key.replace("\"", "\\\"")}\":\"${it.value.replace("\"", "\\\"")}\""
        }
        return "{$json}"
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

    private fun parseCategoriesFromConfig(json: String): List<String> {
        // Server-synced ItemCategory value: {"categories":[{"id":"oil_change",...},...]}
        val catKey = json.indexOf("\"categories\"")
        val arrStart = if (catKey >= 0) json.indexOf('[', catKey) else json.indexOf('[')
        if (arrStart < 0) return emptyList()
        var depth = 0; var arrEnd = arrStart
        for (i in arrStart until json.length) when (json[i]) {
            '[' -> depth++; ']' -> { depth--; if (depth == 0) { arrEnd = i; break } }
        }
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(json.substring(arrStart, arrEnd + 1))
            .map { it.groupValues[1] }.toList()
    }

    private fun parseCategoriesFromSeeded(json: String, assetType: String): List<String> {
        // Seeded log_categories value: [{"asset_type":"light_vehicle","categories":[{"id":"oil_change",...}]}]
        val assetKey = "\"asset_type\":\"$assetType\""
        val blockStart = json.indexOf(assetKey).takeIf { it >= 0 }
            ?: return parseCategoriesFromConfig(json) // no matching assetType block; try direct parse
        val catKey = json.indexOf("\"categories\"", blockStart)
        val arrStart = if (catKey >= 0) json.indexOf('[', catKey) else return emptyList()
        var depth = 0; var arrEnd = arrStart
        for (i in arrStart until json.length) when (json[i]) {
            '[' -> depth++; ']' -> { depth--; if (depth == 0) { arrEnd = i; break } }
        }
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(json.substring(arrStart, arrEnd + 1))
            .map { it.groupValues[1] }.toList()
    }

    /**
     * Parses ItemAttributeDef objects from a config value string.
     * Handles both server format `{"attributes":[...]}` and legacy bare array `[...]`.
     * Server field names: id, label, type, values (enum options), placeholder, unit.
     */
    private fun parseItemAttributeDefs(json: String?): List<ItemAttributeDef> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val defs = mutableListOf<ItemAttributeDef>()
            // Unwrap {"attributes":[...]} → bare array
            val arrayJson = if (json.trim().startsWith("{")) {
                val s = json.indexOf('[')
                val e = json.lastIndexOf(']')
                if (s < 0 || e < 0) return emptyList()
                json.substring(s, e + 1)
            } else {
                json.trim()
            }
            // Brace-depth scanner to extract each attribute object
            val inner = arrayJson.removePrefix("[").removeSuffix("]")
            val objects = mutableListOf<String>()
            var depth = 0
            var start = -1
            for (i in inner.indices) {
                when (inner[i]) {
                    '{' -> { if (depth == 0) start = i; depth++ }
                    '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            objects += inner.substring(start, i + 1)
                            start = -1
                        }
                    }
                }
            }
            // Skip location-only attributes matching iOS locationKeys set
            val locationKeys = setOf("floor", "unit_number", "suite_number", "bay_number")
            val keyPattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
            val arrayPattern = Regex("\"(?:options|values)\"\\s*:\\s*\\[([^\\]]*)]")
            for (obj in objects) {
                val fields = keyPattern.findAll(obj)
                    .associate { it.groupValues[1] to it.groupValues[2] }
                val key = fields["id"] ?: fields["key"] ?: continue
                if (key in locationKeys) continue
                val label = fields["label"] ?: key
                val fieldType = fields["fieldType"] ?: fields["field_type"] ?: fields["type"] ?: "text"
                val unit = fields["unit"]?.ifBlank { null }
                val placeholder = fields["placeholder"]?.ifBlank { null }
                val optionsJson = arrayPattern.find(obj)?.groupValues?.get(1) ?: ""
                val options = Regex("\"([^\"]+)\"").findAll(optionsJson)
                    .map { it.groupValues[1] }.toList()
                defs += ItemAttributeDef(
                    key = key,
                    label = label,
                    fieldType = fieldType,
                    options = options,
                    unit = unit,
                    placeholder = placeholder,
                )
            }
            defs
        } catch (e: Exception) {
            Timber.e(e, "[AddEditLogViewModel] parseItemAttributeDefs failed")
            emptyList()
        }
    }
}
