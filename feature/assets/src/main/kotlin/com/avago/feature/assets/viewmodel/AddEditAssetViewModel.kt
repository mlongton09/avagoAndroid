package com.avago.feature.assets.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.FormFillRouter
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetDraft
import com.avago.core.data.domain.AssetRepository
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.GeocodeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.longOrNull
import com.avago.feature.assets.model.AssetScope
import javax.inject.Inject
import com.avago.feature.assets.data.AssetTaxonomyRepository
import com.avago.feature.assets.data.AssetScopeRepository
import com.avago.feature.assets.model.AssetTaxonomy
import com.avago.feature.assets.model.text
import com.avago.feature.assets.model.validAssetMetadata
import com.avago.feature.assets.model.mergeAssetAttributes
import com.avago.feature.assets.model.AssetTypeInfo
import com.avago.feature.assets.model.AssetTypeCatalog
import com.avago.feature.assets.model.AssetSuggestionTarget
import com.avago.feature.assets.model.decodeAssetAttributes
import com.avago.core.network.json.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Result of a geocode lookup triggered from the address section of the add/edit form.
 * Mirrors the iOS geocodeLookupTapped(_:) action in AddAssetViewController.
 */
sealed interface GeocodeResult {
    data class Success(val lat: Double, val lon: Double, val formattedAddress: String?) : GeocodeResult
    data class Error(val message: String) : GeocodeResult
}

/**
 * All mutable form fields for the AddEditAsset screen collected in one data class
 * so that the UI observes a single StateFlow<FormState>.
 */
data class AssetFormState(
    val name: String = "",
    val assetType: String? = null,
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val avatarColor: String = "#4FC3F7",
    val licensePlate: String = "",
    val vinSerial: String = "",
    /** Current odometer reading for road vehicles, recorded on the asset's meter. */
    val mileage: String = "",
    /** Readings need an existing asset meter (the catalog cannot create one). */
    val hasMeter: Boolean = false,
    val purchaseDate: Long? = null,
    val purchasePrice: String = "",
    val location: String = "",
    val notes: String = "",
    // Address fields
    val streetAddress: String = "",
    val city: String = "",
    val stateProvince: String = "",
    val postalCode: String = "",
    val country: String = "",
    // Fleet number
    val fleetNumber: String = "",
    // Custom attributes (keys not in the known set)
    val customAttributes: Map<String, String> = emptyMap(),
    // Change 106: tags
    val tags: List<String> = emptyList(),
    val isRental: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedAssetId: String? = null,
    val classification: JsonObject? = null,
) {
    val isNameValid: Boolean get() = name.isNotBlank()
}

/** Keys that are handled explicitly and should not appear in the custom attributes section. */
private val KNOWN_ATTRIBUTE_KEYS = setOf(
    "name", "make", "model", "year", "color", "license_plate", "vin", "serial_number", "odometer",
    "purchase_date", "purchase_price", "notes",
    "street_address", "city", "state", "zip_code", "country", "fleet_number",
    "latitude", "longitude",
    "floor_unit_ranges", "floor_suite_ranges", "bay_ranges",
    "wheel_config",
    "classification",
)
private const val SUGGESTION_MIN_NAME = 3
private const val SUGGESTION_DEBOUNCE_MS = 700L
private val EDITED_ATTRIBUTE_KEYS = setOf(
    "license_plate", "vin", "serial_number", "odometer", "purchase_date", "purchase_price", "notes", "fleet_number",
    "street_address", "city", "state", "zip_code", "country",
)

@HiltViewModel
class AddEditAssetViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val identityManager: IdentityManager,
    private val serviceClient: AvagoServiceClient,
    private val formFillRouter: FormFillRouter,
    private val taxonomyRepository: AssetTaxonomyRepository,
    private val scopeRepository: AssetScopeRepository,
) : ViewModel() {

    init {
        formFillRouter.register("add_asset") { fields -> applyScoutFields(fields) }
    }

    override fun onCleared() {
        formFillRouter.unregister("add_asset")
        super.onCleared()
    }

    fun applyScoutFields(fields: Map<String, String?>): List<String> {
        val touched = mutableListOf<String>()
        val current = _form.value
        var updated = current
        fields["name"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            updated = updated.copy(name = it); touched.add("name")
        }
        fields["make"]?.trim()?.let {
            updated = updated.copy(make = it); touched.add("make")
        }
        fields["model"]?.trim()?.let {
            updated = updated.copy(model = it); touched.add("model")
        }
        fields["year"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            updated = updated.copy(year = it); touched.add("year")
        }
        fields["asset_type"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            updated = updated.copy(assetType = it); touched.add("type")
        }
        if (updated !== current) { _form.value = updated; suggestClassification() }
        return touched
    }

    private val _form = MutableStateFlow(AssetFormState())
    val form: StateFlow<AssetFormState> = _form.asStateFlow()
    val taxonomy = MutableStateFlow<AssetTaxonomy?>(null)
    val classificationError = MutableStateFlow<String?>(null)
    private var suggestionJob: Job? = null
    private var originalAttributes = JsonObject(emptyMap())
    private var originalAttributesReadable = true
    private var originalMileage = ""
    private var formAccount: String? = identityManager.getActiveAccountId()

    init {
        viewModelScope.launch {
            identityManager.activeAccountId.collect { id ->
                if (id != formAccount) {
                    suggestionJob?.cancel()
                    formAccount = id
                    editingAssetId = null
                    originalAttributes = JsonObject(emptyMap())
                    originalAttributesReadable = true
                    _form.value = AssetFormState()
                    taxonomy.value = null
                    loadTaxonomy()
                }
            }
        }
    }

    fun loadTaxonomy() {
        viewModelScope.launch {
            val account = identityManager.getActiveAccountId()
            when (val r = taxonomyRepository.load()) {
                is ApiResult.Ok -> if (account == identityManager.getActiveAccountId()) taxonomy.value = r.value
                is ApiResult.Err -> if (account == identityManager.getActiveAccountId()) {
                    classificationError.value = "Category service: ${r.error.status} ${r.error.message}"
                }
            }
        }
    }

    fun selectClassification(value: JsonObject) {
        suggestionJob?.cancel()
        _form.value = _form.value.copy(classification = value)
    }

    private fun suggestClassification() {
        suggestionJob?.cancel()
        val snapshot = _form.value
        val automatic = editingAssetId == null && snapshot.classification?.text("source") != "manual"
        if (!automatic || snapshot.name.trim().length < SUGGESTION_MIN_NAME ||
            snapshot.assetType.isNullOrBlank()) return
        val account = identityManager.getActiveAccountId() ?: return
        suggestionJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MS)
            val result = taxonomyRepository.suggest(
                account, snapshot.name.trim(), snapshot.assetType, snapshot.make, snapshot.model,
            )
            val target = AssetSuggestionTarget(account, "new", snapshot.name, snapshot.assetType)
            val live = _form.value
            val liveTarget = AssetSuggestionTarget(
                identityManager.getActiveAccountId().orEmpty(), "new", live.name, live.assetType.orEmpty(),
            )
            when (result) {
                is ApiResult.Ok -> if (target.matches(liveTarget, live.classification) &&
                    live.make == snapshot.make && live.model == snapshot.model) {
                    _form.value = live.copy(
                        classification = taxonomy.value?.acceptSuggestion(live.classification, result.value),
                    )
                }
                is ApiResult.Err -> if (account == identityManager.getActiveAccountId()) {
                    classificationError.value = "Qwen: ${result.error.status} ${result.error.message}"
                }
            }
        }
    }

    private val _isVinDecoding = MutableStateFlow(false)
    val isVinDecoding: StateFlow<Boolean> = _isVinDecoding.asStateFlow()

    private val _vinDecodeError = MutableStateFlow<String?>(null)
    val vinDecodeError: StateFlow<String?> = _vinDecodeError.asStateFlow()

    /** Non-null when editing an existing asset. */
    private var editingAssetId: String? = null

    // ---------------------------------------------------------------------------
    // Load for edit
    // ---------------------------------------------------------------------------

    fun loadForEdit(assetId: String) {
        if (editingAssetId == assetId) return // already loaded
        editingAssetId = assetId
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val asset = repository.asset(assetId) ?: return@launch
            val meter = repository.observeMeters(assetId).first().let { m -> m.firstOrNull { it.isDefault } ?: m.firstOrNull() }
            if (accountId != identityManager.getActiveAccountId()) return@launch
            try {
                originalAttributes = decodeAssetAttributes(asset.attributes)
                originalAttributesReadable = true
            } catch (_: IllegalArgumentException) {
                originalAttributesReadable = false
                _form.value = AssetFormState(name = asset.name, saveError = "asset_attributes_invalid")
                return@launch
            }
            originalMileage = meter?.lastValue?.toBigInteger()?.toString().orEmpty()
            _form.value = formFor(asset, originalMileage).copy(hasMeter = meter != null)
        }
    }

    private fun formFor(asset: Asset, mileage: String) = AssetFormState(
        name = asset.name,
        assetType = asset.assetTypeCode,
        make = asset.make.orEmpty(),
        model = asset.model.orEmpty(),
        year = asset.modelYear?.toString().orEmpty(),
        avatarColor = asset.avatarColor ?: "#4FC3F7",
        licensePlate = attribute("license_plate"),
        vinSerial = asset.vin.orEmpty().ifBlank { asset.serialNumber.orEmpty() },
        mileage = mileage,
        purchaseDate = (originalAttributes["purchase_date"] as? JsonPrimitive)?.longOrNull,
        purchasePrice = attribute("purchase_price"),
        notes = attribute("notes"),
        streetAddress = attribute("street_address"),
        city = attribute("city"),
        stateProvince = attribute("state"),
        postalCode = attribute("zip_code"),
        country = attribute("country"),
        fleetNumber = attribute("fleet_number"),
        customAttributes = originalAttributes.filterKeys { it !in KNOWN_ATTRIBUTE_KEYS }.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content }
        }.toMap(),
        tags = asset.tags,
        isRental = asset.rentable,
        classification = originalAttributes["classification"] as? JsonObject,
    )

    private fun attribute(key: String): String = (originalAttributes[key] as? JsonPrimitive)?.content.orEmpty()

    // ---------------------------------------------------------------------------
    // Field updates
    // ---------------------------------------------------------------------------

    fun onNameChanged(value: String) { _form.value = _form.value.copy(name = value); suggestClassification() }
    fun onAssetTypeChanged(value: String?) {
        _form.value = _form.value.copy(assetType = value); suggestClassification()
    }
    fun onMakeChanged(value: String) { _form.value = _form.value.copy(make = value); suggestClassification() }
    fun onModelChanged(value: String) { _form.value = _form.value.copy(model = value); suggestClassification() }
    fun onYearChanged(value: String) { _form.value = _form.value.copy(year = value) }
    fun onAvatarColorChanged(value: String) { _form.value = _form.value.copy(avatarColor = value) }
    fun onLicensePlateChanged(value: String) { _form.value = _form.value.copy(licensePlate = value) }
    fun onVinSerialChanged(value: String) { _form.value = _form.value.copy(vinSerial = value) }
    fun onMileageChanged(value: String) { _form.value = _form.value.copy(mileage = normalizeMileage(value)) }
    fun onPurchaseDateChanged(value: Long?) { _form.value = _form.value.copy(purchaseDate = value) }
    fun onPurchasePriceChanged(value: String) { _form.value = _form.value.copy(purchasePrice = value) }
    fun onLocationChanged(value: String) { _form.value = _form.value.copy(location = value) }
    fun onNotesChanged(value: String) { _form.value = _form.value.copy(notes = value) }
    fun onStreetAddressChanged(value: String) { _form.value = _form.value.copy(streetAddress = value) }
    fun onCityChanged(value: String) { _form.value = _form.value.copy(city = value) }
    fun onStateProvinceChanged(value: String) { _form.value = _form.value.copy(stateProvince = value) }
    fun onPostalCodeChanged(value: String) { _form.value = _form.value.copy(postalCode = value) }
    fun onCountryChanged(value: String) { _form.value = _form.value.copy(country = value) }
    fun onFleetNumberChanged(value: String) { _form.value = _form.value.copy(fleetNumber = value) }
    fun onIsRentalChanged(value: Boolean) { _form.value = _form.value.copy(isRental = value) }

    fun onWheelConfigChanged(json: String) {
        val updated = _form.value.customAttributes.toMutableMap().also { it["wheel_config"] = json }
        _form.value = _form.value.copy(customAttributes = updated)
    }

    fun onFloorMapChanged(attributeKey: String, json: String) {
        val updated = _form.value.customAttributes.toMutableMap().also { it[attributeKey] = json }
        _form.value = _form.value.copy(customAttributes = updated)
    }

    fun onCustomAttributeChanged(key: String, value: String) {
        val updated = _form.value.customAttributes.toMutableMap().also { it[key] = value }
        _form.value = _form.value.copy(customAttributes = updated)
    }

    // Change 106: tag management
    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val current = _form.value.tags
        if (current.none { it.equals(trimmed, ignoreCase = true) }) {
            _form.value = _form.value.copy(tags = current + trimmed)
        }
    }

    fun removeTag(tag: String) {
        _form.value = _form.value.copy(tags = _form.value.tags.filter { it != tag })
    }

    /** Called when a VIN barcode is scanned externally. */
    fun onVinScanned(scanned: String) {
        _form.value = _form.value.copy(vinSerial = scanned)
    }

    fun decodeVin() {
        val vin = _form.value.vinSerial.trim()
        if (vin.isBlank()) return
        _isVinDecoding.value = true
        _vinDecodeError.value = null
        viewModelScope.launch {
            try {
                val accountId = identityManager.getActiveAccountId()
                if (accountId == null) {
                    _vinDecodeError.value = "No active account"
                    return@launch
                }
                when (val result = serviceClient.decodeVin(accountId, vin)) {
                    is NetworkResult.Success -> {
                        val decoded = result.data
                        var updated = _form.value
                        decoded.make?.takeIf { it.isNotBlank() }?.let { updated = updated.copy(make = it) }
                        decoded.model?.takeIf { it.isNotBlank() }?.let { updated = updated.copy(model = it) }
                        if (decoded.year != null) updated = updated.copy(year = decoded.year.toString())
                        _form.value = updated
                    }
                    is NetworkResult.Error -> _vinDecodeError.value = result.message
                    is NetworkResult.Unauthorized -> _vinDecodeError.value = "Unauthorized"
                }
            } catch (e: Exception) {
                _vinDecodeError.value = e.message ?: "VIN decode failed"
            } finally {
                _isVinDecoding.value = false
            }
        }
    }

    fun clearVinDecodeError() {
        _vinDecodeError.value = null
    }

    // ---------------------------------------------------------------------------
    // Geocode (real-estate address section â€” mirrors iOS geocodeLookupTapped)
    // ---------------------------------------------------------------------------

    private val _geocodeResult = MutableStateFlow<GeocodeResult?>(null)
    /** One-shot geocode result; the UI should consume it and reset to null. */
    val geocodeResult: StateFlow<GeocodeResult?> = _geocodeResult.asStateFlow()

    private val _isGeocodeLookupInProgress = MutableStateFlow(false)
    val isGeocodeLookupInProgress: StateFlow<Boolean> = _isGeocodeLookupInProgress.asStateFlow()

    /**
     * Geocodes the current address fields and updates [geocodeResult].
     * Mirrors iOS AddAssetViewController.geocodeLookupTapped().
     * The caller should set [geocodeResult] to null after consuming the result.
     */
    fun geocodeAddress() {
        val current = _form.value
        val hasAnyAddress = listOf(
            current.streetAddress,
            current.city,
            current.postalCode,
        ).any { it.isNotBlank() }

        if (!hasAnyAddress) {
            _geocodeResult.value = GeocodeResult.Error("Unable to determine location.")
            return
        }

        _isGeocodeLookupInProgress.value = true
        viewModelScope.launch {
            try {
                val accountId = identityManager.getActiveAccountId()
                if (accountId == null) {
                    _geocodeResult.value = GeocodeResult.Error("No active account.")
                    return@launch
                }
                val result = serviceClient.geocodeAddress(
                    accountId = accountId,
                    request = GeocodeRequest(
                        address_line1 = current.streetAddress.ifBlank { null },
                        city = current.city.ifBlank { null },
                        state = current.stateProvince.ifBlank { null },
                        postal_code = current.postalCode.ifBlank { null },
                        country = current.country.ifBlank { null },
                    ),
                )
                when (result) {
                    is NetworkResult.Success -> {
                        val response = result.data
                        val lat = response.lat
                        val lon = response.lon
                        if (lat != null && lon != null) {
                            _geocodeResult.value = GeocodeResult.Success(
                                lat = lat,
                                lon = lon,
                                formattedAddress = response.formatted_address,
                            )
                            // Store coordinates in customAttributes so they round-trip through
                            // the attributes JSON (mirrors iOS attributeValues["latitude"] = â€¦).
                            val updated = _form.value.customAttributes.toMutableMap()
                            updated["latitude"] = lat.toString()
                            updated["longitude"] = lon.toString()
                            _form.value = _form.value.copy(customAttributes = updated)
                        } else {
                            _geocodeResult.value = GeocodeResult.Error("Unable to determine location.")
                        }
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[AddEditAssetViewModel] Geocode failed: ${result.code} ${result.message}")
                        _geocodeResult.value = GeocodeResult.Error("Address not found.")
                    }
                    is NetworkResult.Unauthorized -> {
                        _geocodeResult.value = GeocodeResult.Error("Unauthorized. Please sign in again.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[AddEditAssetViewModel] Geocode error")
                _geocodeResult.value = GeocodeResult.Error("Address not found.")
            } finally {
                _isGeocodeLookupInProgress.value = false
            }
        }
    }

    /** Clears the geocode result after the UI has consumed it. */
    fun clearGeocodeResult() {
        _geocodeResult.value = null
    }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    private fun validateType(current: AssetFormState, catalog: AssetTypeCatalog): AssetTypeInfo? {
        val info = catalog.info(current.assetType)
        if (editingAssetId == null) {
            require(info != null && catalog.isVisible(info.id)) { "asset_types_unavailable" }
        }
        val serialRequired = info?.serialRequired ?: AssetScope.isTool(current.assetType)
        require(!serialRequired || current.vinSerial.isNotBlank()) { "asset_serial_required" }
        return info
    }

    private fun formError(current: AssetFormState): String? = when {
        !originalAttributesReadable -> "asset_attributes_invalid"
        !validAssetMetadata(current.name, current.year) -> "asset_metadata_invalid"
        editingAssetId == null && taxonomy.value?.valid(current.classification) != true ->
            "asset_classification_required"
        else -> null
    }

    fun save(onSuccess: (assetId: String) -> Unit) {
        val current = _form.value
        val error = formError(current)
        if (error != null) {
            _form.value = current.copy(saveError = error)
            return
        }

        _form.value = current.copy(isSaving = true, saveError = null)
        val accountAtSave = identityManager.getActiveAccountId()

        viewModelScope.launch {
            try {
                val accountId = accountAtSave
                if (accountId == null) {
                    _form.value = _form.value.copy(isSaving = false, saveError = "No active account")
                    return@launch
                }
                val catalog = scopeRepository.catalog()
                if (accountId != identityManager.getActiveAccountId()) return@launch
                validateType(current, catalog)

                val serial = current.vinSerial.trim().ifBlank { null }
                val usesVin = AssetScope.showsVin(current.assetType, catalog)
                val draft = AssetDraft(
                    name = current.name.trim(),
                    assetTypeCode = current.assetType,
                    make = current.make.trim().ifBlank { null },
                    model = current.model.trim().ifBlank { null },
                    modelYear = current.year.trim().toIntOrNull(),
                    serialNumber = if (usesVin) null else serial,
                    vin = if (usesVin) serial else null,
                    tags = current.tags,
                    rentable = current.isRental,
                    avatarColor = current.avatarColor,
                    avatarInitial = current.name.trim().firstOrNull()?.uppercaseChar()?.toString(),
                    attributes = buildAttributesJson(current),
                )
                val editing = editingAssetId
                val assetId = if (editing == null) {
                    repository.create(draft)
                } else {
                    val stored = repository.asset(editing) ?: error("asset_not_found")
                    repository.update(editing, draft.copy(locationId = stored.locationId, conditionScore = stored.conditionScore))
                    editing
                }
                if (current.hasMeter && AssetScope.tracksMileage(current.assetType, catalog) && current.mileage != originalMileage) {
                    current.mileage.toBigDecimalOrNull()?.let { repository.recordReading(assetId, it) }
                }
                if (accountId != identityManager.getActiveAccountId()) return@launch

                _form.value = _form.value.copy(isSaving = false, savedAssetId = assetId)
                Timber.d("[AddEditAssetViewModel] Saved asset $assetId")
                onSuccess(assetId)
            } catch (e: Exception) {
                Timber.e(e, "[AddEditAssetViewModel] Save failed")
                if (accountAtSave == identityManager.getActiveAccountId()) {
                    _form.value = _form.value.copy(isSaving = false, saveError = e.message ?: "Save failed")
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Attribute JSON helpers (minimal JSON, avoids pulling in serialization lib)
    // ---------------------------------------------------------------------------

    private fun buildAttributesJson(current: AssetFormState): String {
        val retained = JsonObject(originalAttributes - EDITED_ATTRIBUTE_KEYS - current.customAttributes.keys)
        return mergeAssetAttributes(retained, buildJsonObject {
            if (current.licensePlate.isNotBlank()) put("license_plate", current.licensePlate.trim())
            current.purchaseDate?.let { put("purchase_date", it) }
            current.purchasePrice.toDoubleOrNull()?.let { put("purchase_price", it) }
            if (current.notes.isNotBlank()) put("notes", current.notes.trim())
            if (current.fleetNumber.isNotBlank()) put("fleet_number", current.fleetNumber.trim())
            val street = current.streetAddress.trim().ifBlank { current.location.trim() }
            if (street.isNotBlank()) put("street_address", street)
            if (current.city.isNotBlank()) put("city", current.city.trim())
            if (current.stateProvince.isNotBlank()) put("state", current.stateProvince.trim())
            if (current.postalCode.isNotBlank()) put("zip_code", current.postalCode.trim())
            if (current.country.isNotBlank()) put("country", current.country.trim())
            current.customAttributes.forEach { (key, value) ->
                if (value.isNotBlank() && key != "classification") put(key, JsonPrimitive(value.trim()))
            }
            current.classification?.let { put("classification", it) }
        }).toString()
    }
}

/** Whole-unit odometer input: digits only, no leading zeros, at most 9 digits ("12,345 mi" -> "12345"). */
internal fun normalizeMileage(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return digits.trimStart('0').ifEmpty { "0" }.take(9)
}