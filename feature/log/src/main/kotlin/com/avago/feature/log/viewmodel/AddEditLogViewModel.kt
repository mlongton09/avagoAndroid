package com.avago.feature.log.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.FormFillRouter
import com.avago.core.data.domain.Activity
import com.avago.core.data.domain.ActivityDraft
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.ActivityType
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.AttachmentTarget
import com.avago.core.data.domain.ChangeQueueRepository
import com.avago.core.data.domain.ConfigDocument
import com.avago.core.data.domain.ConfigDocuments
import com.avago.core.data.domain.ConfigRepository
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.CostLineType
import com.avago.core.data.domain.GlAccount
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.SupplierRepository
import com.avago.core.data.domain.WorkOrderFilter
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.ocr.AvagoTextRecognizer
import com.avago.feature.log.data.InspectionDraftStore
import com.avago.feature.log.model.InspectionChecklist
import com.avago.feature.log.model.InspectionDraft
import com.avago.feature.log.model.InspectionFieldDef
import com.avago.feature.log.model.InspectionGroup
import com.avago.feature.log.model.InspectionItem
import com.avago.feature.log.model.InspectionOption
import com.avago.feature.log.model.InspectionSection
import com.avago.feature.log.model.InspectionV2
import com.avago.feature.log.model.InspectionValidation
import com.avago.feature.log.model.LogCostLineDraft
import com.avago.feature.log.model.LogEditConflict
import com.avago.feature.log.model.LogRebase
import com.avago.feature.log.model.LogPacks
import com.avago.feature.log.model.LogPacks.readBool
import com.avago.feature.log.model.LogPacks.readDouble
import com.avago.feature.log.model.LogPacks.readNumMap
import com.avago.feature.log.model.LogPacks.readString
import com.avago.feature.log.model.LogPacks.readStringList
import com.avago.feature.log.model.LogPersonOption
import com.avago.feature.log.model.humanizeIfKey
import com.avago.feature.log.model.parseInspectionChecklist
import com.avago.feature.log.model.parseInspectionFields
import com.avago.feature.log.model.parseInspectionLocaleStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
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

/** Sync state of the entry being edited (its queued commands). */
enum class LogSyncStatus { SAVED, PENDING, FAILED }

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

    // Link to a work order (activity work_order_id); fixed once the activity is recorded.
    val linkedWoId: String? = null,
    val woOptions: List<LogWoOption> = emptyList(),
    val woLinkLocked: Boolean = false,
    /** Fields changed both here and elsewhere since the form was opened (review before saving). */
    val editConflicts: List<LogEditConflict> = emptyList(),

    // People selectable as labor technician
    val technicians: List<LogPersonOption> = emptyList(),

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
    val availableInspectionSubtypes: List<String> = emptyList(),

    // Inspection field definitions from the `inspection` config document for {subtype, assetType}.
    // Populated by loadInspectionFields(); empty until the asset type is known.
    // Legacy flat format — kept for old configs that don't use the nested groups/sections schema below.
    val inspectionFields: List<InspectionFieldDef> = emptyList(),

    // Full nested checklist (groups -> sections -> items), localized via the locale/Inspection
    // config document. Preferred over [inspectionFields] when the config uses the newer schema (matches iOS).
    val inspectionChecklist: InspectionChecklist? = null,

    // `insp.*` key -> localized text for the active locale (labels of carried-forward defects).
    val inspectionStrings: Map<String, String> = emptyMap(),

    // Open defects of this asset from the defect register (new inspections only, contract §3.2).
    val inspectionOpenDefects: List<InspectionV2.OpenDefect> = emptyList(),
    val openDefectsLoading: Boolean = false,
    // OpenDefect.key -> "repaired" | "still_present"; written to data.resolvedDefects on save.
    val openDefectResolutions: Map<String, String> = emptyMap(),


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
    /** Result of the last defect status change (snackbar), null when shown. */
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
    private val activities: ActivityRepository,
    private val assets: AssetRepository,
    private val config: ConfigRepository,
    private val workers: WorkerRepository,
    private val workOrders: WorkOrderRepository,
    private val inventory: InventoryRepository,
    private val suppliers: SupplierRepository,
    private val changes: ChangeQueueRepository,
    private val identity: IdentityManager,
    private val userPrefsRepository: UserPreferencesRepository,
    private val formFillRouter: FormFillRouter,
    private val serviceClient: AvagoServiceClient,
    private val textRecognizer: AvagoTextRecognizer,
    private val draftStore: InspectionDraftStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Attributes as last stored (edit mode). Every key not edited by this form is preserved on save. */
    private var storedAttributes: JsonObject = JsonObject(emptyMap())
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

    /** Active GL accounts for coding cost lines. */
    val glAccounts: StateFlow<List<GlAccount>> = activities.observeGlAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        loadVendors()
        loadTechnicians()
    }

    /** The activity being edited; null for a new entry. */
    private var stored: Activity? = null
    private var storedLines: List<CostLine> = emptyList()

    /** The stored `data` object of the entry being edited — the base every save merges into. */
    private var storedData: JsonObject? = null
    /** Raw JSON of the inspection config in use (snapshotted into data.configSnapshot on save). */
    private var inspectionConfigRaw: String? = null
    /** True once the checklist came from the entry's own configSnapshot (don't replace it). */
    private var snapshotChecklistApplied = false
    private var openDefectsLoadedFor: String? = null
    /** `data.values` as loaded (text form). Keys missing from the form's answers were cleared by the user. */
    private var originalValues: Map<String, String> = emptyMap()

    // ---------------------------------------------------------------------------
    // Load for edit
    // ---------------------------------------------------------------------------

    fun loadForEdit(entryId: String) {
        _form.update { it.copy(isLoadingExisting = true) }
        viewModelScope.launch {
            try {
                val entity = activities.activity(entryId) ?: run {
                    _form.update { it.copy(isLoadingExisting = false) }
                    return@launch
                }
                stored = entity
                originalMeterText = entity.meterValue?.toPlainString() ?: ""

                val dataObj = InspectionV2.parseDataObject(entity.payload)
                storedData = dataObj
                val isInspection = entity.type == ActivityType.INSPECTION
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
                    inspectionStrings = loadInspectionLocaleStrings()
                    val snapshotCfg = envelope.configSnapshot?.get("inspectionChecklist") as? JsonObject
                    if (snapshotCfg != null) {
                        snapshotChecklist = parseInspectionChecklist(snapshotCfg.toString(), inspectionStrings)
                        if (snapshotChecklist != null) {
                            inspectionConfigRaw = snapshotCfg.toString()
                            snapshotChecklistApplied = true
                        }
                    }
                }

                val lines = activities.observeCostLines(entryId).first().sortedBy { it.sortOrder }
                storedLines = lines
                val currency = entity.currency ?: userPrefsRepository.currencyFlow.firstOrNull() ?: DEFAULT_CURRENCY
                val totalLine = totalLineOf(lines)
                val drafts = if (totalLine != null) emptyList() else toLineDrafts(lines, currency)

                val asset = entity.assetId?.let { assets.asset(it) }
                val meter = entity.assetId?.let { defaultMeter(it) }
                val assetType = asset?.assetTypeCode

                // Restore §1 attributes (typed; unknown keys kept in storedAttributes)
                val attrs = LogPacks.parse(entity.attributes)
                storedAttributes = attrs
                // A work order's completion record cannot move (server conflict completion_activity).
                val isCompletionRecord = entity.workOrderId
                    ?.let { workOrders.workOrder(it) }?.completionActivityId == entity.id
                val savedFuelVolume = attrs.readString("fuel_volume") ?: ""
                val savedFuelUnit = attrs.readString("fuel_volume_unit")?.let(::fromContractVolumeUnit)
                    ?: userPrefsRepository.fuelVolumeUnitFlow.first()
                val savedDistanceUnit = distanceUnitOf(meter?.unit) ?: userPrefsRepository.distanceUnitFlow.first()
                val savedPackFields = PACK_SCALAR_KEYS.mapNotNull { k -> attrs.readString(k)?.let { k to it } }.toMap()
                val savedPackMaps = PACK_MAP_KEYS.associateWith { attrs.readNumMap(it) }.filterValues { it.isNotEmpty() }
                // Restore server-defined item attribute values (all scalar keys; only keys with a
                // matching def are edited/written back).
                val savedItemAttributes = attrs.keys.mapNotNull { k -> attrs.readString(k)?.let { k to it } }.toMap()

                _form.update { state ->
                    state.copy(
                        entryId = entity.id,
                        assetId = entity.assetId,
                        assetName = entity.assetName,
                        assetType = assetType,
                        title = entity.title.orEmpty(),
                        category = entity.categoryLabel,
                        logType = logTypeOf(entity.type),
                        entryDate = entity.occurredAt,
                        notes = entity.notes ?: "",
                        performedByUserId = entity.performedByUserId,
                        performedByName = entity.performedByName,
                        meterReading = entity.meterValue?.toPlainString() ?: "",
                        meterType = meter?.kind,
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
                        vendorName = entity.providerName ?: attrs.readString("vendor_name") ?: "",
                        linkedWoId = entity.workOrderId,
                        woLinkLocked = isCompletionRecord,
                        costMode = if (drafts.isNotEmpty()) CostMode.ITEMIZED else CostMode.TOTAL,
                        totalCost = totalLine?.unitCost?.stripTrailingZeros()?.toPlainString() ?: "",
                        pendingCostLines = drafts,
                        inspectionAnswers = inspectionAnswers,
                        inspectionSubtype = inspectionSubtype,
                        inspectionMode = inspectionMode,
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
                if (!entity.categoryLabel.isNullOrBlank()) {
                    loadItemAttributeDefs(entity.categoryLabel)
                }
                // Load available categories so the category picker is populated for edits
                loadCategories(assetType)
                entity.assetId?.let {
                    loadAssetContext(it)
                    loadMeterHistory(it)
                }
                observeSyncStatus(entity.id)
                if (envelope != null) identity.getActiveAccountId()?.let { restoreDraft(it, entity.assetId, entity.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadForEdit failed")
                _form.update { it.copy(isLoadingExisting = false) }
            }
        }
    }

    /** Log categories of the asset type from the `item_category` config document. */
    fun loadCategories(assetType: String? = null) {
        viewModelScope.launch {
            try {
                val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_TYPE
                val categories = parseCategories(config.document(ConfigRepository.ITEM_CATEGORY, null, type)?.payload)

                _form.update { it.copy(availableCategories = categories) }

                // Auto-select default category — mirrors iOS:
                //   selectedCategory = initialCategory ?? existing?.category ?? assetCategories.first ?? "service"
                val currentCat = _form.value.category
                if ((currentCat == null || currentCat !in categories) && categories.isNotEmpty()) {
                    onCategoryChanged(categories.first())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadCategories failed")
            }
        }
    }

    /** Inspection subtypes (Base, DVIR...) published for the asset type. */
    fun loadInspectionSubtypes(assetType: String? = null) {
        val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_TYPE
        viewModelScope.launch {
            try {
                val subtypes = inspectionSubtypes(config.documents(ConfigRepository.INSPECTION), type)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadInspectionSubtypes failed for assetType=$assetType")
            }
        }
    }

    fun setInspectionSubtype(subtype: String, mode: String?, assetType: String? = null) {
        val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_TYPE
        viewModelScope.launch {
            try {
                val doc = inspectionDocument(subtype, type)
                val localeStrings = loadInspectionLocaleStrings()
                val checklist = parseInspectionChecklist(doc?.payload, localeStrings)
                val fields = if (checklist == null) parseInspectionFields(doc?.payload) else emptyList()
                inspectionConfigRaw = doc?.payload
                snapshotChecklistApplied = false
                _form.update {
                    it.copy(
                        inspectionSubtype = subtype,
                        inspectionMode = mode,
                        inspectionConfigId = doc?.id,
                        inspectionFields = fields,
                        inspectionChecklist = checklist,
                        inspectionStrings = localeStrings,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] setInspectionSubtype failed for subtype=$subtype")
            }
        }
    }

    /** Loads the checklist of the selected subtype (default Base) for the asset type. */
    fun loadInspectionFields(assetType: String? = null) {
        val subtype = _form.value.inspectionSubtype ?: "Base"
        val type = (assetType ?: _form.value.assetType)?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET_TYPE
        viewModelScope.launch {
            try {
                val doc = inspectionDocument(subtype, type)
                val localeStrings = loadInspectionLocaleStrings()
                val checklist = parseInspectionChecklist(doc?.payload, localeStrings)
                val fields = if (checklist == null) parseInspectionFields(doc?.payload) else emptyList()
                // An edited inspection already renders the checklist it was filled against.
                if (snapshotChecklistApplied) return@launch
                inspectionConfigRaw = doc?.payload
                _form.update {
                    it.copy(
                        inspectionFields = fields,
                        inspectionChecklist = checklist,
                        inspectionConfigId = doc?.id,
                        inspectionStrings = localeStrings,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadInspectionFields failed")
            }
        }
    }

    /** The subtype's checklist for the asset type, else the light-vehicle one. */
    private suspend fun inspectionDocument(subtype: String, assetType: String): ConfigDocument? {
        val docs = config.documents(ConfigRepository.INSPECTION)
        return ConfigDocuments.pick(docs, subtype, assetType, Locale.getDefault())
            ?: ConfigDocuments.pick(docs, subtype, DEFAULT_ASSET_TYPE, Locale.getDefault())
    }

    /**
     * Category-specific item attribute definitions (`item_attributes` document with subtype =
     * category): the asset-type specific document first, then the generic one (as on iOS).
     */
    private fun loadItemAttributeDefs(category: String?) {
        if (category.isNullOrBlank()) {
            _form.update { it.copy(itemAttributeDefs = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val assetType = _form.value.assetType
                val docs = config.documents(ConfigRepository.ITEM_ATTRIBUTES)
                val doc = assetType?.takeIf { it.isNotBlank() }
                    ?.let { ConfigDocuments.pick(docs, category, it, Locale.getDefault()) }
                    ?: ConfigDocuments.pick(docs, category, null, Locale.getDefault())
                val rawDefs = parseItemAttributeDefs(doc?.payload)
                val strings = parseItemAttributeStrings(
                    config.document(ConfigRepository.LOCALE, ConfigRepository.LOCALE_ITEM_ATTRIBUTES, null)?.payload,
                )
                val resolvedDefs = rawDefs.map { def ->
                    def.copy(label = strings[def.label] ?: def.label.toAttrDisplayLabel())
                }
                _form.update { it.copy(itemAttributeDefs = resolvedDefs) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] loadItemAttributeDefs failed for category=$category")
                _form.update { it.copy(itemAttributeDefs = emptyList()) }
            }
        }
    }

    /** Converts a dot/underscore key like "item_attr.oil_change.oil_brand" to "Oil Brand". */
    private fun String.toAttrDisplayLabel(): String =
        substringAfterLast('.').replace('_', ' ')
            .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }

    /** The flat `insp.*` -> translated-string map of the device locale (locale/Inspection document). */
    private suspend fun loadInspectionLocaleStrings(): Map<String, String> = try {
        parseInspectionLocaleStrings(config.document(ConfigRepository.LOCALE, ConfigRepository.LOCALE_INSPECTION, null)?.payload)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "[AddEditLogViewModel] loadInspectionLocaleStrings failed")
        emptyMap()
    }

    private suspend fun defaultMeter(assetId: String) =
        assets.observeMeters(assetId).first().let { meters -> meters.firstOrNull { it.isDefault } ?: meters.firstOrNull() }

    /** Pre-fills the meter reading from the asset's default meter when the field is blank. */
    private fun prefillMeterFromPriorEntry(assetId: String) {
        viewModelScope.launch {
            try {
                if (_form.value.meterReading.isNotBlank()) return@launch
                val last = defaultMeter(assetId)?.lastValue ?: return@launch
                // Only set if still blank (avoid clobbering if user typed quickly)
                if (_form.value.meterReading.isBlank()) {
                    _form.update { it.copy(meterReading = last.stripTrailingZeros().toPlainString()) }
                }
            } catch (e: CancellationException) {
                throw e
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
        // Load asset details first so assetType is known before reloading categories/inspection
        viewModelScope.launch {
            try {
                val asset = assets.asset(assetId)
                val meter = defaultMeter(assetId)
                _form.update { s ->
                    s.copy(
                        meterType = meter?.kind,
                        assetType = asset?.assetTypeCode,
                        distanceUnit = distanceUnitOf(meter?.unit) ?: s.distanceUnit,
                    )
                }
                // Reload categories and inspection fields with the correct assetType
                loadCategories(asset?.assetTypeCode)
                loadInspectionSubtypes(asset?.assetTypeCode)
            } catch (e: CancellationException) {
                throw e
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
        viewModelScope.launch {
            try {
                val vendors = suppliers.observeSuppliers().first()
                    .sortedWith(compareByDescending<com.avago.core.data.domain.Supplier> { it.isPreferred }.thenBy { it.name.lowercase() })
                    .map { LogVendorOption(it.partyId, it.name) }
                _form.update { it.copy(vendorOptions = vendors) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] loadVendors failed")
            }
        }
    }

    private fun loadTechnicians() {
        viewModelScope.launch {
            try {
                val people = workers.observeWorkers().first()
                    .mapNotNull { w -> w.userId?.let { LogPersonOption(it, w.displayName) } }
                    .sortedBy { it.name.lowercase() }
                _form.update { it.copy(technicians = people) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] loadTechnicians failed")
            }
        }
    }

    /** Loads open work orders (WO link picker) and fuel history (economy) for the asset. */
    private fun loadAssetContext(assetId: String) {
        viewModelScope.launch {
            try {
                val wos = workOrders.observeWorkOrders(WorkOrderFilter(assetId = assetId)).first()
                    .filter { !it.status.isTerminal || it.id == _form.value.linkedWoId }
                    .map { LogWoOption(it.id, it.title, it.status.wire) }
                val fuelLogs = activities.observeActivities(
                    com.avago.core.data.domain.ActivityFilter(assetId = assetId, types = setOf(ActivityType.FUEL)),
                ).first()
                fuelHistory = fuelLogs.map { l ->
                    val a = LogPacks.parse(l.attributes)
                    LogPacks.FuelFill(
                        entryId = l.id,
                        dateMs = l.occurredAt,
                        meter = l.meterValue?.toDouble(),
                        volume = a.readDouble("fuel_volume"),
                        fullFill = a.readBool("fuel_full_fill") ?: true,
                    )
                }
                _form.update { it.copy(woOptions = wos) }
                refreshFuelEconomy()
            } catch (e: CancellationException) {
                throw e
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
        viewModelScope.launch {
            try {
                val product = inventory.product(partId)
                val draft = LogCostLineDraft(
                    kind = "part",
                    productId = partId,
                    productName = product?.name,
                    description = product?.name ?: partId,
                    quantity = 1.0,
                    unitCost = product?.standardUnitCost?.toDouble() ?: 0.0,
                )
                _form.update { state ->
                    state.copy(
                        costMode = CostMode.ITEMIZED,
                        pendingCostLines = state.pendingCostLines + draft.copy(
                            displayOrder = state.pendingCostLines.size
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
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

    /** Loads the asset's open defects from the register when starting a NEW inspection. */
    fun loadOpenDefects(assetId: String?, isNewEntry: Boolean) {
        if (!isNewEntry || assetId.isNullOrBlank()) {
            openDefectsLoadedFor = null
            _form.update { it.copy(inspectionOpenDefects = emptyList(), openDefectResolutions = emptyMap(), openDefectsLoading = false) }
            return
        }
        if (openDefectsLoadedFor == assetId) return
        openDefectsLoadedFor = assetId
        _form.update { it.copy(openDefectsLoading = true, inspectionOpenDefects = emptyList(), openDefectResolutions = emptyMap()) }
        viewModelScope.launch {
            val defects = try {
                InspectionV2.sortOpen(
                    activities.observeDefects(assetId, openOnly = false).first()
                        .filter { it.status != com.avago.core.data.domain.DefectState.REPAIRED }
                        .map(OpenDefectsViewModel::toOpenDefect),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] open defects failed")
                emptyList()
            }
            if (openDefectsLoadedFor != assetId) return@launch
            _form.update { it.copy(inspectionOpenDefects = defects, openDefectsLoading = false) }
        }
    }

    /** Web OpenDefectsPanel lifecycle: repaired (with an optional resolution note), deferred or open. */
    fun updateDefectStatus(defect: InspectionV2.OpenDefect, status: String, note: String? = null) {
        viewModelScope.launch {
            val ok = try {
                activities.updateDefect(
                    defect.id,
                    OpenDefectsViewModel.stateFor(status, defect.bucket),
                    note?.trim()?.takeIf { it.isNotEmpty() },
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] defect update failed")
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

    /** Localized display label for an `insp.*` key (defect labels, corner labels, section titles). */
    fun inspectionLabel(key: String?): String? =
        key?.let { _form.value.inspectionStrings[it] ?: humanizeIfKey(it) }

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
        viewModelScope.launch {
            try {
                meterPoints = assets.observeReadings(assetId, limit = METER_HISTORY_LIMIT).first()
                    .map { InspectionValidation.MeterPoint(it.value.toDouble(), it.readAt, it.activityId ?: it.id) }
                val entryId = _form.value.entryId
                val last = meterPoints.filter { it.logId != entryId && it.value > 0 }.maxByOrNull { it.atMs }
                _form.update { it.copy(lastMeter = last) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] meter history failed")
            }
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
    // Drafts (DataStore + SavedStateHandle, on this device)
    // ---------------------------------------------------------------------------

    private var draftJob: Job? = null
    private var serverDraftJob: Job? = null
    /** (account, asset, log) scopes whose server draft this form may have written or restored. */
    private val serverDraftScopes = mutableSetOf<Triple<String, String, String?>>()
    private var draftRestoreAttempted = false
    private val draftKeysToClear = mutableSetOf<String>()

    private val isExistingEntry: Boolean get() = stored != null

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
            baseServerVersion = stored?.version,
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
            delay(SERVER_DRAFT_DEBOUNCE_MS)
            val d = currentDraft()?.takeIf { isMeaningful(it) } ?: return@launch
            val assetId = d.assetId ?: return@launch
            serverDraftScopes += Triple(d.acct, assetId, d.logId)
            draftStore.pushServer(d)
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
        if (assetId == null) return
        serverDraftScopes += Triple(acct, assetId, logId)
        val server = draftStore.fetchServer(acct, assetId, logId) ?: return
        if (server.savedAt > (local?.savedAt ?: 0L) + SERVER_DRAFT_NEWER_MS && isMeaningful(server)) {
            _form.update { it.copy(serverDraftOffer = server) }
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

    fun dismissRestoredDraftBanner() = _form.update { it.copy(restoredDraftAt = null) }

    /** Takes the newer draft saved on another device (or the web) over this form. */
    fun acceptServerDraft() {
        _form.value.serverDraftOffer?.let { applyDraft(it); scheduleDraftSave() }
    }

    fun dismissServerDraft() = _form.update { it.copy(serverDraftOffer = null) }

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
        val scopes = serverDraftScopes.toList()
        serverDraftScopes.clear()
        viewModelScope.launch {
            keys.forEach { draftStore.remove(it) }
            scopes.forEach { (acct, assetId, logId) -> draftStore.removeServer(acct, assetId, logId) }
        }
    }

    // ---------------------------------------------------------------------------
    // Sync status
    // ---------------------------------------------------------------------------

    private var syncStatusJob: Job? = null

    private fun observeSyncStatus(entryId: String) {
        syncStatusJob?.cancel()
        syncStatusJob = viewModelScope.launch {
            try {
                changes.observeRecordState(ACTIVITY_ENTITY, entryId).collect { state ->
                    val status = when (state) {
                        RecordSyncState.SAVED -> LogSyncStatus.SAVED
                        RecordSyncState.PENDING -> LogSyncStatus.PENDING
                        RecordSyncState.FAILED -> LogSyncStatus.FAILED
                    }
                    _form.update { it.copy(syncStatus = status) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AddEditLogViewModel] sync status observe failed")
            }
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

    /**
     * Saves the form. When the entry changed through sync while the form was open, the user's edits
     * are rebased onto the latest version; fields both sides changed differently are listed in
     * [AddEditLogFormState.editConflicts] first unless [keepMine] confirms overwriting them.
     */
    fun save(onSuccess: (entryId: String) -> Unit, skipMeterCheck: Boolean = false, keepMine: Boolean = false) {
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
                var existing = stored
                var current = current
                val currency = existing?.currency ?: userPrefsRepository.currencyFlow.firstOrNull() ?: DEFAULT_CURRENCY
                var draft = ActivityDraft(
                    assetId = current.assetId,
                    // An existing entry's picker starts at its stored work order; "none" detaches it.
                    workOrderId = if (existing != null) current.linkedWoId else current.linkedWoId ?: current.workOrderId,
                    type = activityTypeOf(current.logType, current.category),
                    categoryLabel = current.category,
                    title = current.title.trim(),
                    occurredAt = current.entryDate,
                    meterValue = parseDecimal(current.meterReading),
                    performedByUserId = current.performedByUserId,
                    performedByName = current.performedByName?.trim()?.ifBlank { null },
                    providerName = current.vendorName.trim().ifBlank { null },
                    currency = currency,
                    overtimeMultiplier = existing?.overtimeMultiplier,
                    notes = current.notes.trim().ifBlank { null },
                    payload = buildDataJson(current),
                    attributes = buildAttributesJson(current),
                )
                val latest = existing?.let { activities.activity(it.id) }
                if (existing != null && latest != null && latest.version != existing.version) {
                    val rebase = LogRebase.rebase(existing, latest, draft, ::formatConflictDate)
                    val isInspection = current.logType == "inspection"
                    val theirsValues = if (isInspection) {
                        InspectionV2.parseEnvelope(InspectionV2.parseDataObject(latest.payload)).values
                    } else {
                        emptyMap()
                    }
                    val mine = InspectionV2.diffValues(originalValues, current.inspectionAnswers)
                    val conflicts = rebase.conflicts + if (isInspection) {
                        InspectionV2.conflictingKeys(originalValues, mine, theirsValues).map { LogEditConflict(it.key, it.mine, it.theirs) }
                    } else {
                        emptyList()
                    }
                    if (conflicts.isNotEmpty() && !keepMine) {
                        conflictPendingOnSuccess = onSuccess
                        _form.update { it.copy(isSaving = false, editConflicts = conflicts) }
                        return@launch
                    }
                    // Adopt the latest version as the base this save diffs against.
                    stored = latest
                    existing = latest
                    storedData = InspectionV2.parseDataObject(latest.payload)
                    draft = rebase.draft
                    if (isInspection) {
                        originalValues = theirsValues
                        val rebased = InspectionV2.rebaseValues(theirsValues, mine)
                        _form.update { it.copy(inspectionAnswers = rebased) }
                        current = current.copy(inspectionAnswers = rebased)
                        draft = draft.copy(payload = buildDataJson(current))
                    }
                }
                val lines = linesFor(current)
                val files = current.photoUris.map { LocalFileRef(it.toString(), "image/jpeg") }
                val entryId = if (existing == null) {
                    activities.create(draft, lines.map { toCostLineDraft(it, currency) }, files)
                } else {
                    activities.update(existing.id, draft)
                    saveLines(existing.id, lines, currency)
                    files.forEach { assets.attach(AttachmentTarget.ToActivity(existing.id), it) }
                    existing.id
                }

                draftJob?.cancel()
                serverDraftJob?.cancel()
                clearStoredDrafts()
                _form.update { it.copy(isSaving = false, savedEntryId = entryId) }
                Timber.d("[AddEditLogViewModel] Saved log $entryId")
                onSuccess(entryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[AddEditLogViewModel] Save failed")
                _form.update { it.copy(isSaving = false, saveError = "save_failed") }
            }
        }
    }

    private var conflictPendingOnSuccess: ((String) -> Unit)? = null

    /** Edit conflict — "Keep mine": applies this device's edits on top of the latest version. */
    fun resolveEditConflictKeepMine() {
        val onSuccess = conflictPendingOnSuccess ?: return
        conflictPendingOnSuccess = null
        _form.update { it.copy(editConflicts = emptyList()) }
        save(onSuccess, skipMeterCheck = true, keepMine = true)
    }

    /** Edit conflict — "Reload theirs": discards this device's edits and reloads the latest version. */
    fun resolveEditConflictReload() {
        val entryId = _form.value.entryId ?: return
        conflictPendingOnSuccess = null
        _form.update { it.copy(editConflicts = emptyList()) }
        snapshotChecklistApplied = false
        loadForEdit(entryId)
    }

    fun dismissEditConflict() {
        conflictPendingOnSuccess = null
        _form.update { it.copy(editConflicts = emptyList()) }
    }

    /** Label for a conflicting inspection value or attribute key. */
    fun conflictKeyLabel(key: String): String = when {
        key.startsWith(LogEditConflict.ATTRIBUTE_PREFIX) -> humanizeIfKey(key.removePrefix(LogEditConflict.ATTRIBUTE_PREFIX))
        else -> InspectionV2.keyLabel(_form.value.inspectionChecklist, key)
    }

    private fun formatConflictDate(millis: Long): String =
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))

    /** The form's cost as lines: the itemized lines, or one misc line holding the total. */
    private fun linesFor(form: AddEditLogFormState): List<LogCostLineDraft> = when (form.costMode) {
        CostMode.ITEMIZED -> form.pendingCostLines
        CostMode.TOTAL -> {
            val total = parseDecimal(form.totalCost)?.takeIf { it.signum() > 0 }
            if (total == null) {
                emptyList()
            } else {
                listOf(
                    LogCostLineDraft(
                        lineId = totalLineOf(storedLines)?.id ?: UUID.randomUUID().toString(),
                        kind = CostLineType.MISC.wire,
                        quantity = 1.0,
                        unitCost = total.toDouble(),
                        glAccountId = totalLineOf(storedLines)?.glAccountId,
                    ),
                )
            }
        }
    }

    /** Adds, changes and removes the stored lines of [activityId] to match [lines]. */
    private suspend fun saveLines(activityId: String, lines: List<LogCostLineDraft>, currency: String) {
        val storedIds = storedLines.map { it.id }.toSet()
        storedLines.filter { line -> lines.none { it.lineId == line.id } }.forEach { activities.deleteCostLine(it.id) }
        lines.forEach { line ->
            val draft = toCostLineDraft(line, currency)
            if (line.lineId in storedIds) activities.updateCostLine(line.lineId, draft) else activities.addCostLine(activityId, draft)
        }
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
        // Other kinds keep their payload as stored (the kind itself is the activity's `kind`).
        if (current.logType != "inspection") return stored?.toString()

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

    /**
     * Builds the attributes JSON by merging this form's §1 keys over the attributes as stored,
     * so unknown keys (other platforms / future packs) survive the write. Packs that don't apply
     * to the current category are left untouched.
     */
    private fun buildAttributesJson(form: AddEditLogFormState): String {
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
        // The vendor name is the activity's provider_name; the attribute links the vendor record.
        patch["vendor_id"] = LogPacks.str(form.vendorId)
        patch["vendor_name"] = null
        listOf("warranty_until", "warranty_provider").forEach(::scalar)

        return LogPacks.merge(storedAttributes, patch).toString()
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

    /** Form lines of the stored [lines] ([currency] converts tax minor units). */
    private suspend fun toLineDrafts(lines: List<CostLine>, currency: String): List<LogCostLineDraft> {
        val people = workers.observeWorkers().first().associate { it.id to it.userId }
        return lines.mapIndexed { idx, line ->
            LogCostLineDraft(
                lineId = line.id,
                kind = line.kind.wire,
                productId = line.productId,
                productName = line.productId?.let { inventory.product(it)?.name },
                performerUserId = line.performerWorkerId?.let { people[it] },
                description = line.description.orEmpty(),
                quantity = line.quantity.toDouble(),
                unitCost = line.unitCost?.toDouble() ?: 0.0,
                taxAmount = line.taxAmountMinor?.let { minorToMajor(it, currency).toDouble() },
                glAccountId = line.glAccountId,
                displayOrder = idx,
            )
        }
    }

    companion object {
        const val ACTIVITY_ENTITY = "activity_records"
        const val DEFAULT_CURRENCY = "USD"
        const val DEFAULT_ASSET_TYPE = "light_vehicle"
        private const val METER_HISTORY_LIMIT = 500
        private const val STOP_TIMEOUT_MS = 5_000L

        /** A lone, unlabeled misc line is how the "total" cost mode is stored. */
        fun totalLineOf(lines: List<CostLine>): CostLine? = lines.singleOrNull()?.takeIf {
            it.kind == CostLineType.MISC && it.productId == null && it.performerWorkerId == null &&
                it.description.isNullOrBlank() && it.quantity.compareTo(BigDecimal.ONE) == 0 && (it.taxAmountMinor ?: 0L) == 0L
        }

        fun toCostLineDraft(line: LogCostLineDraft, currency: String): CostLineDraft {
            val kind = CostLineType.fromWire(line.kind)
            return CostLineDraft(
                kind = kind,
                description = line.description.trim().ifBlank { null },
                productId = line.productId.takeIf { kind == CostLineType.PART },
                performerUserId = line.performerUserId.takeIf { kind == CostLineType.LABOR },
                quantity = BigDecimal.valueOf(line.quantity),
                unitCost = BigDecimal.valueOf(line.unitCost),
                taxAmountMinor = line.taxAmount?.takeIf { it != 0.0 }?.let { majorToMinor(BigDecimal.valueOf(it), currency) },
                glAccountId = line.glAccountId,
            )
        }

        private fun fractionDigits(currency: String): Int =
            runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrNull()?.takeIf { it >= 0 } ?: 2

        fun majorToMinor(amount: BigDecimal, currency: String): Long =
            amount.movePointRight(fractionDigits(currency)).setScale(0, RoundingMode.HALF_UP).longValueExact()

        fun minorToMajor(minor: Long, currency: String): BigDecimal =
            BigDecimal.valueOf(minor).movePointLeft(fractionDigits(currency))

        /** Form log type of an activity kind. */
        fun logTypeOf(type: ActivityType): String = when (type) {
            ActivityType.INSPECTION -> "inspection"
            ActivityType.FUEL -> "fuel"
            ActivityType.NOTE -> "note"
            else -> "service"
        }

        /** Activity kind for the form's log type (service categories may still map to repair...). */
        fun activityTypeOf(logType: String, category: String?): ActivityType = when (logType) {
            "inspection" -> ActivityType.INSPECTION
            "fuel" -> ActivityType.FUEL
            "note" -> ActivityType.NOTE
            else -> ActivityType.forCategory(category).takeUnless {
                it == ActivityType.INSPECTION || it == ActivityType.FUEL || it == ActivityType.NOTE
            } ?: ActivityType.SERVICE
        }

        /** Form distance unit for a meter unit (`mi` / `km`); null for non-distance meters. */
        fun distanceUnitOf(meterUnit: String?): String? = when (meterUnit?.lowercase()) {
            "mi" -> "mi"
            "km" -> "km"
            else -> null
        }

        fun parseDecimal(text: String): BigDecimal? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

        /** Category ids of an `item_category` document: `{"categories":[{"id":...}]}`. */
        fun parseCategories(payload: String?): List<String> {
            val root = payload?.let { InspectionV2.parseDataObject(it) } ?: return emptyList()
            val arr = root["categories"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            return arr.mapNotNull { (it as? JsonObject)?.get("id")?.let { id -> (id as? JsonPrimitive)?.content } }
        }

        /** Subtypes with a checklist for [assetType], falling back to the light-vehicle set. */
        fun inspectionSubtypes(docs: List<ConfigDocument>, assetType: String): List<String> {
            fun of(type: String) = docs.filter { it.assetType == type }.mapNotNull { it.subtype }.distinct()
            return of(assetType).ifEmpty { of(DEFAULT_ASSET_TYPE) }
        }

        /** Item attribute label strings: the locale/ItemAttributes payload wraps them in `strings`. */
        fun parseItemAttributeStrings(payload: String?): Map<String, String> {
            val root = payload?.let { InspectionV2.parseDataObject(it) } ?: return emptyMap()
            val strings = root["strings"] as? JsonObject ?: return emptyMap()
            return strings.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content } }.toMap()
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

/** Server drafts are pushed after this much quiet time (the device copy is written at once). */
private const val SERVER_DRAFT_DEBOUNCE_MS = 10_000L

/** A server draft is offered only when saved this much later than the device copy. */
private const val SERVER_DRAFT_NEWER_MS = 5_000L
private const val KEY_NEW_ENTRY_ID = "inspection_new_entry_id"
private val draftJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
