package com.avago.feature.log.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.sync.SyncEngine
import com.avago.feature.log.data.InspectionDraftStore
import com.avago.feature.log.model.InspectionChecklist
import com.avago.feature.log.model.InspectionDraft
import com.avago.feature.log.model.InspectionValidation
import com.avago.feature.log.model.InspectionFieldDef
import com.avago.feature.log.model.InspectionGroup
import com.avago.feature.log.model.InspectionItem
import com.avago.feature.log.model.InspectionOption
import com.avago.feature.log.model.InspectionSection
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.LogCostLineDraft
import com.avago.feature.log.model.LogPacks
import com.avago.feature.log.model.LogPacks.readBool
import com.avago.feature.log.model.LogPacks.readDouble
import com.avago.feature.log.model.LogPacks.readNumMap
import com.avago.feature.log.model.LogPacks.readString
import com.avago.feature.log.model.LogPacks.readStringList
import com.avago.core.ocr.AvagoTextRecognizer
import android.content.Context
import com.avago.feature.log.model.humanizeIfKey
import com.avago.feature.log.model.parseInspectionChecklist
import com.avago.feature.log.model.parseInspectionFields
import com.avago.feature.log.model.parseInspectionLocaleStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

/** "total" | "itemized" */
enum class CostMode { TOTAL, ITEMIZED }

/** Typed validation failures shown as alert dialogs — mirrors iOS UIAlertController usage. */
enum class LogValidationError { TITLE_REQUIRED, NO_ASSET, NO_ACCOUNT, FUEL_VOLUME_REQUIRED }

data class LogVendorOption(val vendorId: String, val name: String)

/** Inspection items blocking save (web requiredMissing / notesMissing), plus certification name. */
data class InspectionSaveIssues(
    val required: List<InspectionValidation.FocusTarget> = emptyList(),
    val notes: List<InspectionValidation.FocusTarget> = emptyList(),
    val certificationNameMissing: Boolean = false,
) {
    val isEmpty: Boolean get() = required.isEmpty() && notes.isEmpty() && !certificationNameMissing
}

/** Sync state of the entry being edited, from the sync queue row log_<entryId>. */
enum class LogSyncStatus { SAVED, PENDING, CONFLICT }

data class LogWoOption(val woId: String, val title: String, val status: String?)

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
    /** "gallon" or "liter" — mirrors iOS AVDefaultsKeyFuelVolumeUnit. "kWh" for EVs. */
    val fuelVolumeUnit: String = "gallon",
    /** Contract §1 fuel_price_per_unit (string form value). */
    val fuelPrice: String = "",
    /** Which of "price" | "volume" | "total" was auto-computed from the other two. */
    val fuelDerived: String? = null,
    val fuelFullFill: Boolean = true,
    /** Preview of fuel_economy computed from the previous full fill (null = not computable). */
    val fuelEconomyPreview: Double? = null,
    val fuelEconomyUnit: String? = null,

    // Contract §1 field packs (oil/tire/battery/brake + fuel grade/station/card + warranty):
    // scalar values keyed by the attribute key.
    val packFields: Map<String, String> = emptyMap(),
    val tirePositions: Set<String> = emptySet(),
    /** Per-position numeric maps: tire_tread, tire_psi, brake_pad_mm, brake_rotor_mm. */
    val packMaps: Map<String, Map<String, String>> = emptyMap(),

    // Vendor (any category) — vendor_id / vendor_name attributes
    val vendorId: String? = null,
    val vendorName: String = "",
    val vendorOptions: List<LogVendorOption> = emptyList(),

    // Link to a work order (log.wo_id)
    val linkedWoId: String? = null,
    val woOptions: List<LogWoOption> = emptyList(),

    // Receipt OCR
    val receiptOcrRunning: Boolean = false,
    /** "applied" | "attached" | null — outcome of the last receipt scan. */
    val receiptOcrOutcome: String? = null,

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

    // Inspection field definitions loaded from ConfigEntity (scope="Inspection", key="{subtype}_{assetType}")
    // Populated by loadInspectionFields(); empty until the asset type is known.
    // Legacy flat format — kept for old configs that don't use the nested groups/sections schema below.
    val inspectionFields: List<InspectionFieldDef> = emptyList(),

    // Full nested checklist (groups -> sections -> items), localized via the "Locale"/"Inspection"
    // config. Preferred over [inspectionFields] when the config uses the newer schema (matches iOS).
    val inspectionChecklist: InspectionChecklist? = null,

    // `insp.*` key -> localized text for the active locale (labels of carried-forward defects).
    val inspectionStrings: Map<String, String> = emptyMap(),

    // Open defects from previous inspections of this asset (new inspections only, contract §3.2).
    val inspectionOpenDefects: List<InspectionV2.OpenDefect> = emptyList(),
    val openDefectsLoading: Boolean = false,
    // OpenDefect.key -> "repaired" | "still_present"; written to data.resolvedDefects on save.
    val openDefectResolutions: Map<String, String> = emptyMap(),

    // Set when an inspection update was rejected with 409 — holds the server's current row.
    val inspectionConflict: JsonObject? = null,
    // Keys both this device and the newer server version changed (shown for review before "keep mine").
    val conflictKeys: List<InspectionV2.ConflictKey> = emptyList(),
    // Number of value keys this device changed (applied on top of the server version on "keep mine").
    val conflictMineCount: Int = 0,

    // Inspector sign-off and DVIR certification (web signOff / certification envelope keys).
    val signOff: InspectionV2.SignOff? = null,
    val certification: InspectionV2.Certification? = null,
    // Blocking validation shown before save (missing required answers / defect notes).
    val inspectionIssues: InspectionSaveIssues? = null,
    // Non-blocking meter sanity warning; "Save anyway" re-runs save without the check.
    val meterIssue: InspectionValidation.MeterIssue? = null,
    // Latest earlier meter reading of this asset (hint + sanity check).
    val lastMeter: InspectionValidation.MeterPoint? = null,
    // Draft autosave: epoch millis of the restored draft (banner) and a newer server draft offer.
    val restoredDraftAt: Long? = null,
    val serverDraftOffer: InspectionDraft? = null,
    /** Open defects came from the server register (lifecycle actions available). */
    val openDefectsServerBacked: Boolean = false,
    /** Result of the last defect lifecycle PATCH (snackbar), null when shown. */
    val defectUpdateOk: Boolean? = null,
    val syncStatus: LogSyncStatus? = null,
    // High-contrast "sunlight mode" (persisted in DataStore via UserPreferencesRepository).
    val sunlightMode: Boolean = false,

    // Category-specific item attributes
    val itemAttributes: Map<String, String> = emptyMap(),
    val itemAttributeDefs: List<ItemAttributeDef> = emptyList(),

    // Title hint — true when title was auto-set from category and not yet edited by the user.
    // Mirrors iOS isTitleHint: text shows with reduced opacity, clears on first tap.
    val isTitleHint: Boolean = false,

    // Save state
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedEntryId: String? = null,
    val validationError: LogValidationError? = null,

    val isLoadingExisting: Boolean = false,
    // WO context — timer is only shown when non-null
    val workOrderId: String? = null,
    // Timer: epoch millis when the timer was started; null = not running
    val timerStartedAt: Long? = null,
) {
    val itemizedTotal: Double get() = pendingCostLines.sumOf { it.quantity * it.unitCost + (it.taxAmount ?: 0.0) }
    val packs: Set<LogPacks.Pack> get() = LogPacks.packsFor(category)
    val isFuel: Boolean get() = LogPacks.Pack.FUEL in packs
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
    private val serviceClient: AvagoServiceClient,
    private val textRecognizer: AvagoTextRecognizer,
    private val draftStore: InspectionDraftStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Attributes as last stored (edit mode). Every key not edited by this form is preserved on save. */
    private var storedAttributes: JsonObject = JsonObject(emptyMap())
    private var storedWoId: String? = null
    private var fuelHistory: List<LogPacks.FuelFill> = emptyList()

    init {
        formFillRouter.register("add_log_entry") { fields -> applyScoutFields(fields) }
        viewModelScope.launch {
            val distanceUnit = userPrefsRepository.distanceUnitFlow.first()
            val fuelUnit = userPrefsRepository.fuelVolumeUnitFlow.first()
            _form.update { it.copy(distanceUnit = distanceUnit, fuelVolumeUnit = fuelUnit) }
        }
        viewModelScope.launch {
            userPrefsRepository.sunlightModeFlow.collect { on -> _form.update { it.copy(sunlightMode = on) } }
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
                s = s.copy(title = it, isTitleHint = false); touched.add("title")
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

    private val _form = MutableStateFlow(
        savedStateHandle.get<String>(KEY_NEW_ENTRY_ID)?.let { AddEditLogFormState(entryId = it) } ?: AddEditLogFormState(),
    )
    val form: StateFlow<AddEditLogFormState> = _form.asStateFlow()

    init {
        loadVendors()
    }

    private var originalCreatedAt: Long? = null
    private var originalServerVersion: Long = 0L

    /** The stored `data` object of the entry being edited — the base every save merges into. */
    private var storedData: JsonObject? = null
    /** Raw JSON of the inspection config in use (snapshotted into data.configSnapshot on save). */
    private var inspectionConfigRaw: String? = null
    /** True once the checklist came from the entry's own configSnapshot (don't replace it). */
    private var snapshotChecklistApplied = false
    private var pendingOnSuccess: ((String) -> Unit)? = null
    private var openDefectsLoadedFor: String? = null
    /** `data.values` as loaded (text form). Keys missing from the form's answers were cleared by the user. */
    private var originalValues: Map<String, String> = emptyMap()

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
                originalMeterText = entity.odometerValue?.toString() ?: ""
                originalServerVersion = entity.serverVersion

                val dataObj = InspectionV2.parseDataObject(entity.data)
                storedData = dataObj
                val storedLogType = (dataObj?.get("log_type") as? JsonPrimitive)?.takeIf { it.isString }?.content
                val isInspection = storedLogType == "inspection" ||
                    (storedLogType == null && entity.category?.lowercase()?.contains("inspect") == true)
                val envelope = if (isInspection) InspectionV2.parseEnvelope(dataObj) else null
                val inspectionAnswers = envelope?.values ?: emptyMap()
                originalValues = inspectionAnswers
                val inspectionSubtype = envelope?.subtype
                val inspectionMode = envelope?.mode
                storedSignOff = envelope?.signOff
                storedCertification = envelope?.certification
                signOffDirty = false
                certificationDirty = false

                // Prefer the checklist the inspection was filled against (configSnapshot) so edits
                // render the same questions even if the live config changed since.
                var snapshotChecklist: InspectionChecklist? = null
                var inspectionStrings: Map<String, String> = emptyMap()
                if (envelope != null) {
                    inspectionStrings = loadInspectionLocaleStrings(db)
                    val snapshotCfg = envelope.configSnapshot?.get("inspectionChecklist") as? JsonObject
                    if (snapshotCfg != null) {
                        snapshotChecklist = parseInspectionChecklist(snapshotCfg.toString(), inspectionStrings)
                        if (snapshotChecklist != null) {
                            inspectionConfigRaw = snapshotCfg.toString()
                            snapshotChecklistApplied = true
                        }
                    }
                }

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

                // Restore §1 attributes from entity.attributes JSON (typed; unknown keys kept in storedAttributes)
                val attrs = LogPacks.parse(entity.attributes)
                storedAttributes = attrs
                storedWoId = entity.woId
                val savedFuelVolume = attrs.readString("fuel_volume") ?: ""
                val savedFuelUnit = attrs.readString("fuel_volume_unit")?.let(::fromContractVolumeUnit)
                    ?: userPrefsRepository.fuelVolumeUnitFlow.first()
                val savedDistanceUnit = userPrefsRepository.distanceUnitFlow.first()
                val packScalarKeys = PACK_SCALAR_KEYS
                val savedPackFields = packScalarKeys.mapNotNull { k -> attrs.readString(k)?.let { k to it } }.toMap()
                val savedPackMaps = PACK_MAP_KEYS.associateWith { attrs.readNumMap(it) }.filterValues { it.isNotEmpty() }
                // Restore server-defined item attribute values (all scalar keys; only keys with a
                // matching def are edited/written back).
                val savedItemAttributes = attrs.keys.mapNotNull { k -> attrs.readString(k)?.let { k to it } }.toMap()

                _form.update { state ->
                    state.copy(
                        entryId = entity.entryId,
                        assetId = entity.assetId,
                        assetType = assetType,
                        title = entity.title,
                        category = entity.category,
                        logType = storedLogType ?: if (isInspection) "inspection" else "service",
                        entryDate = entity.entryDate,
                        notes = entity.notes ?: "",
                        performedByUserId = entity.performedByUserId,
                        performedByName = entity.performedBy,
                        meterReading = entity.odometerValue?.toString() ?: "",
                        meterType = meterType,
                        distanceUnit = savedDistanceUnit,
                        fuelVolume = savedFuelVolume,
                        fuelVolumeUnit = savedFuelUnit,
                        fuelPrice = attrs.readString("fuel_price_per_unit") ?: "",
                        fuelFullFill = attrs.readBool("fuel_full_fill") ?: true,
                        fuelEconomyPreview = attrs.readDouble("fuel_economy"),
                        fuelEconomyUnit = attrs.readString("fuel_economy_unit"),
                        packFields = savedPackFields,
                        tirePositions = attrs.readStringList("tire_positions").toSet(),
                        packMaps = savedPackMaps,
                        vendorId = attrs.readString("vendor_id"),
                        vendorName = attrs.readString("vendor_name") ?: "",
                        linkedWoId = entity.woId,
                        costMode = if (entity.costMode == "itemized") CostMode.ITEMIZED else CostMode.TOTAL,
                        totalCost = entity.cost?.toString() ?: "",
                        pendingCostLines = drafts,
                        inspectionAnswers = inspectionAnswers,
                        inspectionSubtype = inspectionSubtype,
                        inspectionMode = inspectionMode,
                        inspectionConfigId = entity.configId,
                        inspectionConfigVersion = entity.configVersion ?: 0L,
                        inspectionChecklist = snapshotChecklist ?: state.inspectionChecklist,
                        inspectionFields = if (snapshotChecklist != null) emptyList() else state.inspectionFields,
                        inspectionStrings = inspectionStrings.ifEmpty { state.inspectionStrings },
                        itemAttributes = savedItemAttributes,
                        signOff = envelope?.signOff,
                        certification = envelope?.certification,
                        isLoadingExisting = false,
                    )
                }
                if (envelope != null && snapshotChecklist == null && !inspectionSubtype.isNullOrBlank()) {
                    setInspectionSubtype(inspectionSubtype, inspectionMode, assetType)
                }
                // Load item attribute defs for the restored category (so Add Details is shown for edits)
                if (!entity.category.isNullOrBlank()) {
                    loadItemAttributeDefs(entity.category)
                }
                // Load available categories so the category picker is populated for edits
                loadCategories(assetType)
                loadAssetContext(entity.assetId)
                loadMeterHistory(entity.assetId)
                observeSyncStatus(accountId, entity.entryId)
                if (envelope != null) restoreDraft(accountId, entity.assetId, entity.entryId)
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
     * Queries configs with scope="Inspection" and key like "%_{assetType}".
     * Extracts the subtype portion from the key.
     *
     * NOTE: the sync engine derives the local key as "{subtype}_{assetType}" (see
     * SyncEngine.kt's config upsert — "Android schema: scope = type, key = subtype[_asset_type]"),
     * matching the convention already used for ItemAttributes. This previously searched with the
     * order reversed ("{assetType}_%"), which never matched real synced configs.
     */
    fun loadInspectionSubtypes(assetType: String? = null) {
        val accountId = identity.getActiveAccountId() ?: return
        val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: "light_vehicle"
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val configs = db.configDao().getByPattern("Inspection", "%_$type")
                val suffix = "_$type"
                val subtypes = configs.mapNotNull { config ->
                    val key = config.key
                    if (key.endsWith(suffix)) key.removeSuffix(suffix) else null
                }.distinct()
                _form.update { it.copy(availableInspectionSubtypes = subtypes) }
                // Auto-load "Base" subtype if available and none selected
                if (_form.value.inspectionSubtype == null && !snapshotChecklistApplied) {
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
                val config = db.configDao().getByPattern("Inspection", "${subtype}_$type")
                    .maxByOrNull { it.version }
                    ?: db.configDao().getByPattern("Inspection", "${subtype}_light_vehicle")
                        .maxByOrNull { it.version }
                val localeStrings = loadInspectionLocaleStrings(db)
                val checklist = parseInspectionChecklist(config?.value, localeStrings)
                val fields = if (checklist == null) parseInspectionFields(config?.value) else emptyList()
                inspectionConfigRaw = config?.value
                snapshotChecklistApplied = false
                _form.update {
                    it.copy(
                        inspectionSubtype = subtype,
                        inspectionMode = mode,
                        inspectionConfigId = config?.configId,
                        inspectionConfigVersion = config?.version ?: 0L,
                        inspectionFields = fields,
                        inspectionChecklist = checklist,
                        inspectionStrings = localeStrings,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] setInspectionSubtype failed for subtype=$subtype")
            }
        }
    }

    /**
     * Loads inspection field definitions from ConfigEntity.
     * Scope="Inspection", key="{subtype}_{assetType}". Falls back to scope="system",
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
                val config = db.configDao().getByPattern("Inspection", "${subtype}_$type")
                    .maxByOrNull { it.version }
                    ?: db.configDao().getByKey("system", "inspection_fields_$type")
                    ?: db.configDao().getByKey("system", "inspection_fields")
                val localeStrings = if (config?.scope == "Inspection") loadInspectionLocaleStrings(db) else emptyMap()
                val checklist = parseInspectionChecklist(config?.value, localeStrings)
                val fields = if (checklist == null) parseInspectionFields(config?.value) else emptyList()
                // An edited inspection already renders the checklist it was filled against.
                if (snapshotChecklistApplied) return@launch
                inspectionConfigRaw = config?.value
                if (config != null && config.scope == "Inspection") {
                    _form.update {
                        it.copy(
                            inspectionFields = fields,
                            inspectionChecklist = checklist,
                            inspectionConfigId = config.configId,
                            inspectionConfigVersion = config.version,
                            inspectionStrings = localeStrings,
                        )
                    }
                } else {
                    _form.update { it.copy(inspectionFields = fields, inspectionChecklist = checklist) }
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

    /** Fetches the locale strings map for item attribute labels (scope="Locale", key="ItemAttributes_{locale}"). */
    private suspend fun loadItemAttrLocaleStrings(db: com.avago.core.data.db.AvagoDatabase): Map<String, String> {
        return try {
            val config = pickLocaleRow(db, "ItemAttributes")
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

    /** Converts a dot/underscore key like "item_attr.oil_change.oil_brand" to "Oil Brand". */
    private fun String.toAttrDisplayLabel(): String =
        substringAfterLast('.').replace('_', ' ')
            .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }

    /**
     * Fetches the flat `insp.*` -> translated-string map (scope="Locale", key="Inspection_{locale}").
     * Unlike [loadItemAttrLocaleStrings], this config's value is a flat top-level JSON object
     * (no `"strings"` wrapper) — see parseInspectionLocaleStrings for details.
     */
    private suspend fun loadInspectionLocaleStrings(db: com.avago.core.data.db.AvagoDatabase): Map<String, String> {
        return try {
            parseInspectionLocaleStrings(pickLocaleRow(db, "Inspection")?.value)
        } catch (e: Exception) {
            Timber.e(e, "[AddEditLogViewModel] loadInspectionLocaleStrings failed")
            emptyMap()
        }
    }

    /**
     * Picks the `Locale`-scope config row for [subtype] matching the device's active
     * locale, falling back to en-us, then to whatever's cached if neither is present.
     *
     * `SyncEngine` stores one row PER LOCALE under keys like "Inspection_en-us" /
     * "Inspection_de-de" (locale folded into the key since Room's `configs` table has
     * no separate `locale` column). Without this locale-aware selection, every caller
     * here was previously just grabbing whichever locale's row happened to have the
     * highest `version` — which could silently serve the wrong language, or an older
     * cached copy missing recently-added keys, instead of the user's selected locale.
     */
    private suspend fun pickLocaleRow(
        db: com.avago.core.data.db.AvagoDatabase,
        subtype: String,
    ): com.avago.core.data.db.entity.ConfigEntity? {
        val locale = java.util.Locale.getDefault().toLanguageTag().lowercase()
        val rows = db.configDao().getByPattern("Locale", "$subtype%")
        return rows.firstOrNull { it.key == "${subtype}_$locale" }
            ?: rows.firstOrNull { it.key == "${subtype}_en-us" }
            ?: rows.maxByOrNull { it.version }
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
        loadMeterHistory(assetId)
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
        loadAssetContext(assetId)
    }

    fun onTitleChanged(value: String) = _form.update { it.copy(title = value, isTitleHint = false) }

    /** Called when the title field gains focus — clears the category-defaulted hint text. */
    fun onTitleFocused() {
        _form.update { state ->
            if (state.isTitleHint) state.copy(title = "", isTitleHint = false) else state
        }
    }

    fun onCategoryChanged(value: String?, displayName: String? = null) {
        // Derive logType from category key — mirrors iOS behaviour where log type
        // is implicitly set by the category rather than a separate picker.
        val derivedLogType = when {
            LogPacks.isFuel(value) -> "fuel"
            value?.lowercase()?.contains("inspection") == true -> "inspection"
            value?.lowercase()?.contains("note") == true -> "note"
            else -> "service"
        }
        _form.update { state ->
            val effectiveDisplayName = displayName ?: value?.replace("_", " ")
                ?.split(" ")?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
            val shouldApplyHint = value != null && effectiveDisplayName != null
                && (state.title.isEmpty() || state.isTitleHint)
            state.copy(
                category = value,
                logType = derivedLogType,
                title = if (shouldApplyHint) effectiveDisplayName!! else if (value == null && state.isTitleHint) "" else state.title,
                isTitleHint = if (shouldApplyHint) true else if (value == null) false else state.isTitleHint,
            )
        }
        loadItemAttributeDefs(value)
    }

    fun onLogTypeChanged(value: String) = _form.update { it.copy(logType = value) }
    fun onEntryDateChanged(value: Long) = _form.update { it.copy(entryDate = value) }
    fun onNotesChanged(value: String) = _form.update { it.copy(notes = value) }
    fun onMeterReadingChanged(value: String) {
        _form.update { it.copy(meterReading = value) }
        refreshFuelEconomy()
    }
    fun onFuelVolumeChanged(value: String) {
        applyFuelEdit("volume", value)
        refreshFuelEconomy()
    }
    fun onFuelVolumeUnitChanged(unit: String) {
        _form.update { it.copy(fuelVolumeUnit = unit) }
        refreshFuelEconomy()
    }
    fun onFuelPriceChanged(value: String) = applyFuelEdit("price", value)
    fun onFuelFullFillChanged(value: Boolean) {
        _form.update { it.copy(fuelFullFill = value) }
        refreshFuelEconomy()
    }
    fun onTotalCostChanged(value: String) {
        if (_form.value.isFuel && _form.value.costMode == CostMode.TOTAL) applyFuelEdit("total", value)
        else _form.update { it.copy(totalCost = value) }
    }
    fun onCostModeChanged(mode: CostMode) = _form.update { it.copy(costMode = mode) }

    /** Any two of price / volume / total compute the third (contract §1). */
    private fun applyFuelEdit(field: String, value: String) {
        _form.update { s ->
            if (!s.isFuel || s.costMode != CostMode.TOTAL) {
                return@update when (field) {
                    "price" -> s.copy(fuelPrice = value)
                    "volume" -> s.copy(fuelVolume = value)
                    else -> s.copy(totalCost = value)
                }
            }
            val r = LogPacks.applyFuelEdit(
                LogPacks.FuelInputs(s.fuelPrice, s.fuelVolume, s.totalCost, s.fuelDerived), field, value,
            )
            s.copy(fuelPrice = r.price, fuelVolume = r.volume, totalCost = r.total, fuelDerived = r.derived)
        }
    }

    fun onPackFieldChanged(key: String, value: String) {
        _form.update { it.copy(packFields = it.packFields + (key to value)) }
    }

    fun onTirePositionToggled(position: String) {
        _form.update {
            val next = if (position in it.tirePositions) it.tirePositions - position else it.tirePositions + position
            it.copy(tirePositions = next)
        }
    }

    fun onPackMapValueChanged(key: String, position: String, value: String) {
        _form.update {
            val inner = (it.packMaps[key] ?: emptyMap()) + (position to value)
            it.copy(packMaps = it.packMaps + (key to inner))
        }
    }

    fun onVendorSelected(option: LogVendorOption?) {
        _form.update { it.copy(vendorId = option?.vendorId, vendorName = option?.name ?: "") }
    }

    /** Free-text vendor name clears the linked vendor_id. */
    fun onVendorNameChanged(name: String) {
        _form.update { it.copy(vendorName = name, vendorId = null) }
    }

    fun onLinkedWorkOrderSelected(woId: String?) {
        _form.update { it.copy(linkedWoId = woId) }
    }

    fun clearReceiptOcrOutcome() = _form.update { it.copy(receiptOcrOutcome = null) }

    /**
     * Receipt OCR (contract §5): attaches the photo, runs on-device text recognition and the
     * service extractor, then pre-fills vendor / total / date when available. Falls back to
     * attach-only when OCR or extraction is unavailable.
     */
    fun scanReceipt(uri: Uri, context: Context) {
        addPhotoUri(uri)
        val accountId = identity.getActiveAccountId() ?: return
        _form.update { it.copy(receiptOcrRunning = true, receiptOcrOutcome = null) }
        viewModelScope.launch {
            var applied = false
            try {
                val raw = textRecognizer.recognize(uri, context).getOrNull()
                if (!raw.isNullOrBlank()) {
                    val res = serviceClient.extractDocOcr(
                        accountId = accountId,
                        ocrRawText = raw,
                        documentType = "receipt",
                        assetId = _form.value.assetId,
                    )
                    if (res is NetworkResult.Success) {
                        val ocr = res.data
                        _form.update { s ->
                            var n = s
                            ocr.vendor?.takeIf { it.isNotBlank() && s.vendorName.isBlank() }?.let { v ->
                                val match = s.vendorOptions.firstOrNull { it.name.equals(v.trim(), ignoreCase = true) }
                                n = n.copy(vendorName = match?.name ?: v.trim(), vendorId = match?.vendorId)
                                applied = true
                            }
                            ocr.total?.takeIf { it > 0 && s.costMode == CostMode.TOTAL && s.totalCost.isBlank() }?.let {
                                n = n.copy(totalCost = LogPacks.formatNumber(it))
                                applied = true
                            }
                            ocr.date?.let { parseOcrDate(it) }?.let {
                                n = n.copy(entryDate = it)
                                applied = true
                            }
                            n
                        }
                        if (_form.value.isFuel) applyFuelEdit("total", _form.value.totalCost)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] receipt OCR failed — attach only")
            }
            _form.update { it.copy(receiptOcrRunning = false, receiptOcrOutcome = if (applied) "applied" else "attached") }
        }
    }

    private fun parseOcrDate(s: String): Long? = runCatching {
        java.time.LocalDate.parse(s.take(10)).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    private fun loadVendors() {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val vendors = dbFactory.get(accountId).vendorDao().observeAll(accountId).first()
                    .filter { it.deletedAt == null && it.active }
                    .sortedWith(compareByDescending<com.avago.core.data.db.entity.VendorEntity> { it.preferred }.thenBy { it.name.lowercase() })
                    .map { LogVendorOption(it.vendorId, it.name) }
                _form.update { it.copy(vendorOptions = vendors) }
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] loadVendors failed")
            }
        }
    }

    /** Loads open work orders (WO link picker) and fuel history (economy) for the asset. */
    private fun loadAssetContext(assetId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val closed = setOf("completed", "complete", "cancelled")
                val wos = db.workOrderDao().observeByAsset(assetId).first()
                    .filter { it.deletedAt == null && (it.status?.lowercase() !in closed || it.woId == _form.value.linkedWoId) }
                    .sortedByDescending { it.updatedAt }
                    .map { LogWoOption(it.woId, it.title, it.status) }
                val logs = db.logDao().observeAll(accountId).first()
                    .filter { it.assetId == assetId && it.deletedAt == null && LogPacks.isFuel(it.category) }
                fuelHistory = logs.map { l ->
                    val a = LogPacks.parse(l.attributes)
                    LogPacks.FuelFill(
                        entryId = l.entryId,
                        dateMs = l.entryDate,
                        meter = l.odometerValue,
                        volume = a.readDouble("fuel_volume"),
                        fullFill = a.readBool("fuel_full_fill") ?: true,
                    )
                }
                _form.update { it.copy(woOptions = wos) }
                refreshFuelEconomy()
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] loadAssetContext failed")
            }
        }
    }

    private fun computeEconomy(s: AddEditLogFormState): Pair<Double, String>? {
        if (!s.isFuel) return null
        val unit = LogPacks.economyUnitFor(toContractVolumeUnit(s.fuelVolumeUnit), s.distanceUnit)
        val econ = LogPacks.computeEconomy(
            LogPacks.FuelFill(
                entryId = s.entryId,
                dateMs = s.entryDate,
                meter = s.meterReading.toDoubleOrNull(),
                volume = s.fuelVolume.trim().replace(',', '.').toDoubleOrNull(),
                fullFill = s.fuelFullFill,
            ),
            fuelHistory,
            unit,
        ) ?: return null
        return econ to unit
    }

    private fun refreshFuelEconomy() {
        _form.update { s ->
            val e = computeEconomy(s)
            s.copy(fuelEconomyPreview = e?.first, fuelEconomyUnit = e?.second)
        }
    }

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
            // An empty value removes the key so cleared answers / notes don't linger in data.values.
            val answers = if (value.isEmpty()) state.inspectionAnswers - key else state.inspectionAnswers + (key to value)
            state.copy(inspectionAnswers = answers)
        }
        scheduleDraftSave()
    }

    /** Applies several value writes at once (measurements + their severity, mark all, voice fill). */
    fun onInspectionAnswersChanged(writes: List<Pair<String, String>>) {
        if (writes.isEmpty()) return
        _form.update { state ->
            var answers = state.inspectionAnswers
            writes.forEach { (k, v) -> answers = if (v.isEmpty()) answers - k else answers + (k to v) }
            state.copy(inspectionAnswers = answers)
        }
        scheduleDraftSave()
    }

    /** Marks a carried-forward defect "repaired" / "still_present"; tapping the active choice clears it. */
    fun setOpenDefectResolution(key: String, resolution: String?) {
        _form.update { state ->
            val current = state.openDefectResolutions[key]
            val next = if (resolution == null || current == resolution) {
                state.openDefectResolutions - key
            } else {
                state.openDefectResolutions + (key to resolution)
            }
            state.copy(openDefectResolutions = next)
        }
        scheduleDraftSave()
    }

    /**
     * Loads open defects for [assetId] when starting a NEW inspection: the service's defect register
     * first (GET /defects), falling back to replaying cached inspection history (contract §2) when
     * the endpoint is unavailable (404/405), offline or failing.
     */
    fun loadOpenDefects(assetId: String?, isNewEntry: Boolean) {
        if (!isNewEntry || assetId.isNullOrBlank()) {
            openDefectsLoadedFor = null
            _form.update { it.copy(inspectionOpenDefects = emptyList(), openDefectResolutions = emptyMap(), openDefectsLoading = false) }
            return
        }
        if (openDefectsLoadedFor == assetId) return
        openDefectsLoadedFor = assetId
        val accountId = identity.getActiveAccountId() ?: return
        _form.update { it.copy(openDefectsLoading = true, inspectionOpenDefects = emptyList(), openDefectResolutions = emptyMap()) }
        viewModelScope.launch {
            var serverBacked = false
            val defects = try {
                when (val res = serviceClient.getDefects(accountId, assetId)) {
                    is NetworkResult.Success -> InspectionV2.sortOpen(
                        res.data.mapNotNull { it as? JsonObject }
                            .map { InspectionV2.fromServerDefect(it) }
                            .filter { it.status == "open" || it.status == "monitoring" || it.status == "deferred" },
                    ).also { serverBacked = true }
                    else -> deriveOpenDefectsLocally(accountId, assetId)
                }
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] getDefects failed — deriving from local history")
                runCatching { deriveOpenDefectsLocally(accountId, assetId) }.getOrDefault(emptyList())
            }
            if (openDefectsLoadedFor != assetId) return@launch
            _form.update { it.copy(inspectionOpenDefects = defects, openDefectsLoading = false, openDefectsServerBacked = serverBacked) }
        }
    }

    /**
     * Web OpenDefectsPanel lifecycle: PATCH the server register to repaired (with an optional
     * resolution note), deferred or open. Only offered when the list came from the server.
     */
    fun updateDefectStatus(defect: InspectionV2.OpenDefect, status: String, note: String? = null) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            val ok = try {
                serviceClient.patchDefect(accountId, defect.id, status, note?.trim()?.takeIf { it.isNotEmpty() }) is NetworkResult.Success
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] patchDefect failed")
                false
            }
            _form.update { state ->
                if (!ok) return@update state.copy(defectUpdateOk = false)
                val list = if (status == InspectionV2.RESOLUTION_REPAIRED) {
                    state.inspectionOpenDefects.filterNot { it.id == defect.id }
                } else {
                    state.inspectionOpenDefects.map { if (it.id == defect.id) it.copy(status = status) else it }
                }
                state.copy(
                    inspectionOpenDefects = list,
                    openDefectResolutions = if (status == InspectionV2.RESOLUTION_REPAIRED) state.openDefectResolutions - defect.key else state.openDefectResolutions,
                    defectUpdateOk = true,
                )
            }
        }
    }

    fun clearDefectUpdateResult() = _form.update { it.copy(defectUpdateOk = null) }

    private suspend fun deriveOpenDefectsLocally(accountId: String, assetId: String): List<InspectionV2.OpenDefect> {
        val logs = dbFactory.get(accountId).logDao().observeAll(accountId).first()
        val history = logs.filter { it.assetId == assetId && it.deletedAt == null }.map { log ->
            InspectionV2.HistoryEntry(
                logId = log.entryId,
                assetId = log.assetId,
                logDate = Instant.ofEpochMilli(log.entryDate).toString(),
                category = log.category,
                data = InspectionV2.parseDataObject(log.data),
            )
        }
        return InspectionV2.deriveOpenDefects(history, excludeLogId = _form.value.entryId)
    }

    /** Localized display label for an `insp.*` key (defect labels, corner labels, section titles). */
    fun inspectionLabel(key: String?): String? =
        key?.let { _form.value.inspectionStrings[it] ?: humanizeIfKey(it) }

    /** 409 → "Reload theirs": adopt the server row locally and reload the form from it. */
    fun resolveConflictReloadTheirs() {
        val current = _form.value.inspectionConflict ?: return
        val accountId = identity.getActiveAccountId() ?: return
        val entryId = _form.value.entryId
        _form.update { it.copy(inspectionConflict = null, conflictKeys = emptyList(), conflictMineCount = 0) }
        pendingOnSuccess = null
        viewModelScope.launch {
            try {
                syncEngine.logEntityFromJson(current, accountId)?.let { dbFactory.get(accountId).logDao().upsert(it) }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] reload theirs: local upsert failed")
            }
            snapshotChecklistApplied = false
            loadForEdit(entryId)
        }
    }

    /**
     * 409 → "Keep mine" after the user reviewed [AddEditLogFormState.conflictKeys]: rebases this
     * device's edits onto the server's current row (their other changes survive) and re-sends with
     * the server's current version as `expected_server_version`, so a further concurrent write
     * raises a new conflict instead of being overwritten.
     */
    fun resolveConflictOverwrite() {
        val theirs = _form.value.inspectionConflict ?: return
        val accountId = identity.getActiveAccountId() ?: return
        val onSuccess: (String) -> Unit = pendingOnSuccess ?: { _ -> }
        val theirsEntity = syncEngine.logEntityFromJson(theirs, accountId)
        val theirsData = InspectionV2.parseDataObject(theirsEntity?.data)
        val theirsValues = InspectionV2.parseEnvelope(theirsData).values
        val mine = InspectionV2.diffValues(originalValues, _form.value.inspectionAnswers)
        storedData = theirsData
        originalValues = theirsValues
        theirsEntity?.let { originalServerVersion = it.serverVersion }
        _form.update {
            it.copy(
                inspectionConflict = null,
                conflictKeys = emptyList(),
                conflictMineCount = 0,
                inspectionAnswers = InspectionV2.rebaseValues(theirsValues, mine),
            )
        }
        save(onSuccess, skipMeterCheck = true)
    }

    fun dismissConflict() {
        _form.update { it.copy(inspectionConflict = null, conflictKeys = emptyList(), conflictMineCount = 0) }
    }

    /** Label for a conflicting values key, for the review list. */
    fun conflictKeyLabel(key: String): String = InspectionV2.keyLabel(_form.value.inspectionChecklist, key)

    fun clearValidationError() {
        _form.update { it.copy(validationError = null) }
    }

    // ---------------------------------------------------------------------------
    // Sign-off / certification
    // ---------------------------------------------------------------------------

    private var storedSignOff: InspectionV2.SignOff? = null
    private var storedCertification: InspectionV2.Certification? = null
    /** Only a touched sign-off / certification is written, so a web-set value is never clobbered. */
    private var signOffDirty = false
    private var certificationDirty = false

    fun setSignOff(value: InspectionV2.SignOff?) {
        signOffDirty = true
        _form.update { it.copy(signOff = value) }
        scheduleDraftSave()
    }

    fun setCertification(value: InspectionV2.Certification?) {
        certificationDirty = true
        _form.update { it.copy(certification = value) }
        scheduleDraftSave()
    }

    // ---------------------------------------------------------------------------
    // Validation / meter sanity
    // ---------------------------------------------------------------------------

    private var meterPoints: List<InspectionValidation.MeterPoint> = emptyList()
    private var originalMeterText: String? = null
    private var meterPendingOnSuccess: ((String) -> Unit)? = null

    fun clearInspectionIssues() = _form.update { it.copy(inspectionIssues = null) }

    fun dismissMeterIssue() {
        meterPendingOnSuccess = null
        _form.update { it.copy(meterIssue = null) }
    }

    fun saveIgnoringMeter() {
        val cb = meterPendingOnSuccess ?: return
        meterPendingOnSuccess = null
        _form.update { it.copy(meterIssue = null) }
        save(cb, skipMeterCheck = true)
    }

    fun setSunlightMode(enabled: Boolean) {
        viewModelScope.launch { userPrefsRepository.setSunlightMode(enabled) }
    }

    private fun loadMeterHistory(assetId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            runCatching {
                val logs = dbFactory.get(accountId).logDao().observeAll(accountId).first()
                meterPoints = logs.filter { it.assetId == assetId && it.deletedAt == null }
                    .mapNotNull { l -> l.odometerValue?.let { InspectionValidation.MeterPoint(it, l.entryDate, l.entryId) } }
                val entryId = _form.value.entryId
                val last = meterPoints.filter { it.logId != entryId && it.value > 0 }.maxByOrNull { it.atMs }
                _form.update { it.copy(lastMeter = last) }
            }.onFailure { Timber.w(it, "[AddEditLogViewModel] meter history failed") }
        }
    }

    private fun meterIssueFor(s: AddEditLogFormState): InspectionValidation.MeterIssue? {
        if (s.meterReading == originalMeterText) return null
        val kind = if (s.meterType?.lowercase() in setOf("hours", "hour", "hr", "engine_hours")) {
            InspectionValidation.METER_ENGINE_HOURS
        } else {
            InspectionValidation.METER_ODOMETER
        }
        val zone = ZoneId.systemDefault()
        val cutoff = Instant.ofEpochMilli(s.entryDate).atZone(zone).toLocalDate().plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return InspectionValidation.checkMeterSanity(
            entered = InspectionValidation.parseMeterValue(s.meterReading),
            enteredAtMs = s.entryDate,
            cutoffMs = cutoff,
            points = meterPoints,
            kind = kind,
            excludeLogId = s.entryId,
        )
    }

    // ---------------------------------------------------------------------------
    // Drafts (DataStore + SavedStateHandle, mirrored to the server drafts API)
    // ---------------------------------------------------------------------------

    private var draftJob: Job? = null
    private var serverDraftJob: Job? = null
    private var draftRestoreAttempted = false
    private val draftKeysToClear = mutableSetOf<String>()

    private val isExistingEntry: Boolean get() = originalCreatedAt != null

    private fun currentDraft(): InspectionDraft? {
        val s = _form.value
        val acct = identity.getActiveAccountId() ?: return null
        if (s.logType != "inspection") return null
        val logId = if (isExistingEntry) s.entryId else null
        return InspectionDraft(
            acct = acct,
            assetId = s.assetId,
            logId = logId,
            savedAt = System.currentTimeMillis(),
            baseServerVersion = originalServerVersion.takeIf { it > 0 },
            category = s.category,
            configId = s.inspectionConfigId,
            mode = s.inspectionMode,
            subtype = s.inspectionSubtype,
            values = s.inspectionAnswers,
            resolutions = s.openDefectResolutions,
            signOff = s.signOff.takeIf { signOffDirty },
            certification = s.certification.takeIf { certificationDirty },
            form = buildMap {
                if (s.title.isNotBlank() && !s.isTitleHint) put("title", s.title)
                put("log_date", Instant.ofEpochMilli(s.entryDate).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                if (s.meterReading.isNotBlank()) put("meter", s.meterReading)
                if (s.notes.isNotBlank()) put("notes", s.notes)
                s.performedByName?.let { put("performed_by", it) }
                s.performedByUserId?.let { put("performed_by_user_id", it) }
            },
            entryId = if (isExistingEntry) null else s.entryId,
            edits = if (isExistingEntry) InspectionV2.diffValues(originalValues, s.inspectionAnswers) else null,
        )
    }

    private fun isMeaningful(d: InspectionDraft): Boolean =
        (d.edits?.let { !it.isEmpty() } ?: (d.values != originalValues)) ||
            d.resolutions.isNotEmpty() || d.signOff != null || d.certification != null

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(1_500)
            val d = currentDraft()?.takeIf { isMeaningful(it) } ?: return@launch
            if (!isExistingEntry) savedStateHandle[KEY_NEW_ENTRY_ID] = _form.value.entryId
            savedStateHandle[KEY_DRAFT] = d.toJson().toString()
            draftKeysToClear += d.key
            draftStore.save(d)
        }
        serverDraftJob?.cancel()
        serverDraftJob = viewModelScope.launch {
            delay(10_000)
            currentDraft()?.takeIf { isMeaningful(it) }?.let { draftStore.pushServer(it) }
        }
    }

    /** New entries: restores a local draft for this account / asset (called once from the screen). */
    fun restoreNewDraft(assetId: String?) {
        val acct = identity.getActiveAccountId() ?: return
        viewModelScope.launch { restoreDraft(acct, assetId, null) }
    }

    private suspend fun restoreDraft(acct: String, assetId: String?, logId: String?) {
        if (draftRestoreAttempted) return
        draftRestoreAttempted = true
        val key = InspectionDraft.draftKey(acct, assetId, logId)
        draftKeysToClear += key
        val fromState = savedStateHandle.get<String>(KEY_DRAFT)?.let { raw ->
            runCatching { InspectionDraft.fromJson(draftJson.parseToJsonElement(raw) as? JsonObject) }.getOrNull()
        }?.takeIf { it.key == key }
        val local = listOfNotNull(fromState, draftStore.load(key)).maxByOrNull { it.savedAt }
        if (local != null && isMeaningful(local)) applyDraft(local)
        if (assetId != null) {
            val server = draftStore.fetchServer(acct, assetId, logId)
            if (server != null && server.savedAt > (local?.savedAt ?: 0L) + 5_000 &&
                server.values.isNotEmpty() && isMeaningful(server)
            ) {
                _form.update { it.copy(serverDraftOffer = server) }
            }
        }
    }

    private fun applyDraft(d: InspectionDraft) {
        val before = _form.value
        // Android drafts carry the edit diff, so a draft restored onto a newer server version only
        // re-applies this device's changes; web drafts carry full values.
        val values = d.edits?.let { InspectionV2.rebaseValues(originalValues, it) } ?: d.values
        if (d.signOff != null) signOffDirty = true
        if (d.certification != null) certificationDirty = true
        _form.update { s ->
            s.copy(
                entryId = if (!isExistingEntry) d.entryId ?: s.entryId else s.entryId,
                logType = "inspection",
                category = d.category ?: s.category,
                inspectionSubtype = if (isExistingEntry) s.inspectionSubtype else d.subtype ?: s.inspectionSubtype,
                inspectionMode = if (isExistingEntry) s.inspectionMode else d.mode ?: s.inspectionMode,
                inspectionAnswers = values,
                openDefectResolutions = d.resolutions,
                signOff = d.signOff ?: s.signOff,
                certification = d.certification ?: s.certification,
                title = d.form["title"]?.takeIf { it.isNotBlank() } ?: s.title,
                isTitleHint = if (d.form["title"].isNullOrBlank()) s.isTitleHint else false,
                notes = d.form["notes"] ?: s.notes,
                meterReading = d.form["meter"] ?: s.meterReading,
                restoredDraftAt = d.savedAt,
                serverDraftOffer = null,
            )
        }
        val subtype = d.subtype
        if (!isExistingEntry && !subtype.isNullOrBlank() && (subtype != before.inspectionSubtype || before.inspectionChecklist == null)) {
            setInspectionSubtype(subtype, d.mode, before.assetType)
        }
    }

    fun acceptServerDraft() {
        _form.value.serverDraftOffer?.let { applyDraft(it); scheduleDraftSave() }
    }

    fun dismissServerDraft() = _form.update { it.copy(serverDraftOffer = null) }

    fun dismissRestoredDraftBanner() = _form.update { it.copy(restoredDraftAt = null) }

    /** Drops the restored draft and returns the form to the stored entry (or a blank inspection). */
    fun discardDraft() {
        draftJob?.cancel()
        serverDraftJob?.cancel()
        clearStoredDrafts()
        signOffDirty = false
        certificationDirty = false
        _form.update {
            it.copy(
                inspectionAnswers = originalValues,
                openDefectResolutions = emptyMap(),
                signOff = storedSignOff,
                certification = storedCertification,
                restoredDraftAt = null,
                serverDraftOffer = null,
            )
        }
    }

    private fun clearStoredDrafts() {
        savedStateHandle.remove<String>(KEY_DRAFT)
        savedStateHandle.remove<String>(KEY_NEW_ENTRY_ID)
        currentDraft()?.key?.let { draftKeysToClear += it }
        val keys = draftKeysToClear.toList()
        draftKeysToClear.clear()
        viewModelScope.launch { keys.forEach { draftStore.remove(it) } }
    }

    // ---------------------------------------------------------------------------
    // Sync status
    // ---------------------------------------------------------------------------

    private var syncStatusJob: Job? = null

    private fun observeSyncStatus(accountId: String, entryId: String) {
        syncStatusJob?.cancel()
        syncStatusJob = viewModelScope.launch {
            runCatching {
                dbFactory.get(accountId).syncQueueDao().observeById("log_").collect { row ->
                    val status = when (row?.syncStatus) {
                        null -> LogSyncStatus.SAVED
                        "conflict" -> LogSyncStatus.CONFLICT
                        else -> LogSyncStatus.PENDING
                    }
                    _form.update { it.copy(syncStatus = status) }
                }
            }.onFailure { Timber.w(it, "[AddEditLogViewModel] sync status observe failed") }
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

    fun save(onSuccess: (entryId: String) -> Unit, skipMeterCheck: Boolean = false) {
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
        // Contract §1: fuel_volume is required for fuel logs.
        if (current.isFuel && current.fuelVolume.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } == null) {
            _form.update { it.copy(validationError = LogValidationError.FUEL_VOLUME_REQUIRED) }
            return
        }
        if (current.logType == "inspection") {
            val checklist = current.inspectionChecklist ?: legacyChecklist(current.inspectionFields)
            val issues = InspectionSaveIssues(
                required = InspectionValidation.missingRequired(checklist, current.inspectionAnswers),
                notes = InspectionValidation.missingDefectNotes(checklist, current.inspectionAnswers),
                certificationNameMissing = current.certification != null && current.certification.name.isBlank(),
            )
            if (!issues.isEmpty) {
                _form.update { it.copy(inspectionIssues = issues) }
                return
            }
        }
        if (!skipMeterCheck) {
            meterIssueFor(current)?.let { issue ->
                meterPendingOnSuccess = onSuccess
                _form.update { it.copy(meterIssue = issue) }
                return
            }
        }

        _form.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                val db = dbFactory.get(accountId)
                val now = System.currentTimeMillis()
                val entryId = current.entryId

                val dataJson = buildDataJson(current)

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
                    woId = current.linkedWoId ?: current.workOrderId ?: storedWoId,
                )

                // Inspection updates go straight to the service with expected_server_version so a
                // concurrent edit from another device/web surfaces as a 409 instead of being
                // silently overwritten. Offline / other failures fall back to the queued sync path.
                var remoteRow: JsonObject? = null
                if (current.logType == "inspection" && originalServerVersion > 0L) {
                    val body = buildUpdateBody(entity, expectedServerVersion = originalServerVersion)
                    when (val res = serviceClient.updateLog(accountId, entryId, body)) {
                        is NetworkResult.Success -> remoteRow = res.data
                        is NetworkResult.Error -> {
                            val theirs = if (res.code == 409) conflictCurrentRow(res.message) else null
                            if (theirs != null) {
                                pendingOnSuccess = onSuccess
                                val theirsValues = InspectionV2.parseEnvelope(
                                    InspectionV2.parseDataObject(syncEngine.logEntityFromJson(theirs, accountId)?.data),
                                ).values
                                val mine = InspectionV2.diffValues(originalValues, current.inspectionAnswers)
                                _form.update {
                                    it.copy(
                                        isSaving = false,
                                        inspectionConflict = theirs,
                                        conflictKeys = InspectionV2.conflictingKeys(originalValues, mine, theirsValues),
                                        conflictMineCount = mine.keys.size,
                                    )
                                }
                                return@launch
                            }
                            Timber.w("[AddEditLogViewModel] inspection PUT failed (${res.code}) — queuing sync")
                        }
                        else -> Timber.w("[AddEditLogViewModel] inspection PUT unauthorized — queuing sync")
                    }
                }

                val stored = remoteRow?.let { syncEngine.logEntityFromJson(it, accountId) } ?: entity
                db.logDao().upsert(stored)
                if (remoteRow == null) {
                    enqueueLogSync(accountId, entryId, now)
                } else {
                    originalServerVersion = stored.serverVersion
                    storedData = InspectionV2.parseDataObject(stored.data)
                    originalValues = InspectionV2.parseEnvelope(storedData).values
                }
                pendingOnSuccess = null

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

                draftJob?.cancel()
                serverDraftJob?.cancel()
                clearStoredDrafts()
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

    /**
     * Builds the `data` JSON for the entry. Always starts from the stored object (the entry being
     * edited) so keys written by web/iOS/newer clients survive (contract rule 1).
     *
     * Inspections are written as the v2 envelope: answers under `values`, `envelopeVersion: 2`,
     * recomputed `defects` / `result` / `outOfService`, `configSnapshot.inspectionChecklist` and any
     * carried-forward defect resolutions in `resolvedDefects`.
     */
    private fun buildDataJson(current: AddEditLogFormState): String? {
        val stored = storedData
        if (current.logType != "inspection") {
            if (stored == null) return null
            val merged = LinkedHashMap<String, JsonElement>(stored)
            merged["log_type"] = JsonPrimitive(current.logType)
            return JsonObject(merged).toString()
        }

        val checklist = current.inspectionChecklist ?: legacyChecklist(current.inspectionFields)
        // Only keys the user removed are dropped; every other stored key survives (contract rule 1).
        val cleared = originalValues.keys.filter { it !in current.inspectionAnswers }.toSet()
        val storedValues = InspectionV2.parseEnvelope(stored).values
        val values = InspectionV2.mergeValues(storedValues, current.inspectionAnswers, cleared)
        val defects = InspectionV2.collectDefects(checklist, values)
        val summary = InspectionV2.computeResult(checklist, values)

        // Older Android builds rendered read-only detail from configSnapshot.fields — keep feeding it.
        val legacySnapshotFields = (checklist?.allItems() ?: emptyList()).map { item ->
            JsonObject(
                mapOf(
                    "key" to JsonPrimitive(item.id),
                    "label" to JsonPrimitive(item.text),
                    "type" to JsonPrimitive(item.type),
                ),
            )
        }
        val previousSnapshot = stored?.get("configSnapshot") as? JsonObject
        val snapshot = InspectionV2.buildConfigSnapshot(
            rawConfig = inspectionConfigRaw,
            previous = previousSnapshot,
            extra = if (legacySnapshotFields.isEmpty()) emptyMap()
            else mapOf("fields" to kotlinx.serialization.json.JsonArray(legacySnapshotFields)),
        )

        val at = Instant.now().toString()
        val resolved = current.inspectionOpenDefects.mapNotNull { d ->
            current.openDefectResolutions[d.key]?.let { InspectionV2.resolvedEntry(d, it, at) }
        }

        return InspectionV2.buildEnvelope(
            stored = stored,
            values = values,
            subtype = current.inspectionSubtype,
            mode = current.inspectionMode,
            configSnapshot = snapshot,
            defects = defects,
            result = summary.result,
            outOfService = summary.outOfService,
            newResolved = resolved,
            clearedKeys = cleared,
            signOff = if (signOffDirty) with(InspectionV2) { current.signOff?.toJson() } ?: JsonNull else null,
            certification = if (certificationDirty) {
                with(InspectionV2) { current.certification?.toJson(stored?.get("certification") as? JsonObject) } ?: JsonNull
            } else {
                null
            },
        ).toString()
    }

    /** Wraps a legacy flat field list as a one-section checklist so defects/result can be computed. */
    private fun legacyChecklist(fields: List<InspectionFieldDef>): InspectionChecklist? {
        if (fields.isEmpty()) return null
        val items = fields.map { f ->
            InspectionItem(
                id = f.key,
                text = f.label,
                type = if (f.type == "select") "select" else f.type,
                options = if (f.type == "select") f.options.map { InspectionOption(value = it) } else emptyList(),
            )
        }
        return InspectionChecklist(
            title = "",
            groups = listOf(InspectionGroup(id = "legacy", title = "", sections = listOf(InspectionSection("legacy", "", items)))),
        )
    }

    /** PUT /log/:id body (service UpdateLogRequest) — `data` / `attributes` sent as JSON objects. */
    private fun buildUpdateBody(entity: LogEntity, expectedServerVersion: Long?): JsonObject {
        val body = LinkedHashMap<String, JsonElement>()
        body["title"] = JsonPrimitive(entity.title)
        val logDate = Instant.ofEpochMilli(entity.entryDate).atZone(ZoneId.systemDefault())
            .toLocalDate().atTime(12, 0).toInstant(ZoneOffset.UTC)
        body["log_date"] = JsonPrimitive(logDate.toString())
        entity.odometerValue?.let { m ->
            body["meter"] = JsonPrimitive(if (m % 1.0 == 0.0) m.toLong().toString() else m.toString())
        }
        entity.category?.let { body["category"] = JsonPrimitive(it) }
        entity.cost?.let { body["cost"] = JsonPrimitive(it) }
        entity.costMode?.let { body["cost_mode"] = JsonPrimitive(it) }
        entity.performedBy?.let { body["performed_by"] = JsonPrimitive(it) }
        body["performed_by_user_id"] = entity.performedByUserId?.let { JsonPrimitive(it) } ?: JsonNull
        body["notes"] = JsonPrimitive(entity.notes ?: "")
        InspectionV2.parseDataObject(entity.data)?.let { body["data"] = it }
        InspectionV2.parseDataObject(entity.attributes)?.let { body["attributes"] = it }
        entity.woId?.let { body["wo_id"] = JsonPrimitive(it) }
        entity.currency?.let { body["currency"] = JsonPrimitive(it) }
        entity.baseAmount?.let { body["base_amount"] = JsonPrimitive(it) }
        entity.exchangeRateUsed?.let { body["exchange_rate_used"] = JsonPrimitive(it) }
        if (expectedServerVersion != null) body["expected_server_version"] = JsonPrimitive(expectedServerVersion)
        return JsonObject(body)
    }

    /** The `current` row from a 409 VersionConflict body; null for other 409s (no `current`). */
    private fun conflictCurrentRow(body: String): JsonObject? {
        val obj = InspectionV2.parseDataObject(body) ?: return null
        val error = (obj["error"] as? JsonPrimitive)?.content
        return (obj["current"] as? JsonObject)?.takeIf { error == null || error == "conflict" }
    }

    /**
     * Builds the attributes JSON by merging this form's §1 keys over the attributes as stored,
     * so unknown keys (other platforms / future packs) survive the write. Packs that don't apply
     * to the current category are left untouched.
     */
    private fun buildAttributesJson(form: AddEditLogFormState): String? {
        val patch = LinkedHashMap<String, JsonElement?>()
        // Server-defined item attribute defs take precedence over the built-in packs for their keys.
        val defKeys = form.itemAttributeDefs.map { it.key }.toSet() - FUEL_CORE_KEYS
        form.itemAttributeDefs.filter { it.key in defKeys }.forEach { def ->
            val v = form.itemAttributes[def.key]
            patch[def.key] = when (def.fieldType) {
                "number" -> LogPacks.num(v) ?: LogPacks.str(v)
                "checkbox" -> v?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it.equals("true", true)) }
                else -> LogPacks.str(v)
            }
        }
        val packs = form.packs
        val f = form.packFields
        fun scalar(key: String) {
            if (key in defKeys) return
            patch[key] = if (key in NUMERIC_PACK_KEYS) LogPacks.num(f[key]) else LogPacks.str(f[key])
        }
        if (LogPacks.Pack.FUEL in packs) {
            patch["fuel_volume"] = LogPacks.num(form.fuelVolume)
            patch["fuel_volume_unit"] = JsonPrimitive(toContractVolumeUnit(form.fuelVolumeUnit))
            patch["fuel_price_per_unit"] = LogPacks.num(form.fuelPrice)
            patch["fuel_full_fill"] = JsonPrimitive(form.fuelFullFill)
            listOf("fuel_grade", "fuel_station", "fuel_card_last4").forEach(::scalar)
            val econ = computeEconomy(form)
            when {
                econ != null -> {
                    patch["fuel_economy"] = JsonPrimitive(econ.first)
                    patch["fuel_economy_unit"] = JsonPrimitive(econ.second)
                }
                !form.fuelFullFill -> {
                    patch["fuel_economy"] = null
                    patch["fuel_economy_unit"] = null
                }
            }
        }
        if (LogPacks.Pack.OIL in packs) {
            listOf("oil_type", "oil_viscosity", "oil_quantity", "oil_quantity_unit", "oil_filter_part",
                "oil_next_due_meter", "oil_next_due_date").forEach(::scalar)
        }
        if (LogPacks.Pack.TIRE in packs) {
            listOf("tire_action", "tire_brand", "tire_size", "tire_dot_date", "tire_tread_unit",
                "tire_pressure_unit").forEach(::scalar)
            patch["tire_positions"] = LogPacks.strList(LogPacks.TIRE_POSITIONS.filter { it in form.tirePositions })
            patch["tire_tread"] = LogPacks.numMap(form.packMaps["tire_tread"] ?: emptyMap())
            patch["tire_psi"] = LogPacks.numMap(form.packMaps["tire_psi"] ?: emptyMap())
        }
        if (LogPacks.Pack.BATTERY in packs) {
            listOf("battery_action", "battery_rated_cca", "battery_measured_cca", "battery_voltage",
                "battery_test_result", "battery_date_code").forEach(::scalar)
        }
        if (LogPacks.Pack.BRAKE in packs) {
            listOf("brake_axle", "brake_fluid").forEach(::scalar)
            patch["brake_pad_mm"] = LogPacks.numMap(form.packMaps["brake_pad_mm"] ?: emptyMap())
            patch["brake_rotor_mm"] = LogPacks.numMap(form.packMaps["brake_rotor_mm"] ?: emptyMap())
        }
        // Any category
        patch["vendor_id"] = LogPacks.str(form.vendorId)
        patch["vendor_name"] = LogPacks.str(form.vendorName)
        listOf("warranty_until", "warranty_provider").forEach(::scalar)

        val merged = LogPacks.merge(storedAttributes, patch)
        return if (merged.isEmpty()) null else merged.toString()
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

    fun setWorkOrderId(woId: String?) {
        _form.update { it.copy(workOrderId = woId) }
    }

    fun startTimer() {
        _form.update { it.copy(timerStartedAt = System.currentTimeMillis()) }
    }

    fun stopTimer(): Long {
        val started = _form.value.timerStartedAt ?: return 0L
        val elapsed = (System.currentTimeMillis() - started) / 1000L
        _form.update { it.copy(timerStartedAt = null) }
        return elapsed
    }

    fun lapTimer(): Long {
        val started = _form.value.timerStartedAt ?: return 0L
        return (System.currentTimeMillis() - started) / 1000L
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

/** Fuel keys with dedicated editors; never routed through server item-attribute defs. */
private val FUEL_CORE_KEYS = setOf("fuel_volume", "fuel_volume_unit", "fuel_price_per_unit", "fuel_full_fill")

/** Scalar §1 keys edited through [AddEditLogFormState.packFields]. */
internal val PACK_SCALAR_KEYS = listOf(
    "fuel_grade", "fuel_station", "fuel_card_last4",
    "oil_type", "oil_viscosity", "oil_quantity", "oil_quantity_unit", "oil_filter_part",
    "oil_next_due_meter", "oil_next_due_date",
    "tire_action", "tire_brand", "tire_size", "tire_dot_date", "tire_tread_unit", "tire_pressure_unit",
    "battery_action", "battery_rated_cca", "battery_measured_cca", "battery_voltage",
    "battery_test_result", "battery_date_code",
    "brake_axle", "brake_fluid",
    "warranty_until", "warranty_provider",
)

internal val PACK_MAP_KEYS = listOf("tire_tread", "tire_psi", "brake_pad_mm", "brake_rotor_mm")

private val NUMERIC_PACK_KEYS = setOf(
    "oil_quantity", "oil_next_due_meter", "battery_rated_cca", "battery_measured_cca", "battery_voltage",
)

/** Form/pref volume unit → contract §1 fuel_volume_unit (gallon | liter | kWh). */
internal fun toContractVolumeUnit(u: String): String = LogPacks.normalizeVolumeUnit(u) ?: u

/** Stored fuel_volume_unit (incl. legacy gal / L) → form unit. */
internal fun fromContractVolumeUnit(u: String): String = LogPacks.normalizeVolumeUnit(u) ?: u

private const val KEY_DRAFT = "inspection_draft"
private const val KEY_NEW_ENTRY_ID = "inspection_new_entry_id"
private val draftJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
