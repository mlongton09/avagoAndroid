package com.avago.feature.assets.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.repository.AssetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

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
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedAssetId: String? = null,
) {
    val isNameValid: Boolean get() = name.isNotBlank()
}

/** Keys that are handled explicitly and should not appear in the custom attributes section. */
private val KNOWN_ATTRIBUTE_KEYS = setOf(
    "name", "make", "model", "year", "color", "license_plate", "vin",
    "purchase_date", "purchase_price", "notes",
    "street_address", "city", "state", "zip_code", "country", "fleet_number",
)

@HiltViewModel
class AddEditAssetViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _form = MutableStateFlow(AssetFormState())
    val form: StateFlow<AssetFormState> = _form.asStateFlow()

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
            val entity = repository.getAssetById(accountId, assetId) ?: return@launch
            val customAttrs = extractCustomAttributes(entity.attributes)
            _form.value = AssetFormState(
                name = entity.name,
                assetType = entity.assetType,
                make = entity.make ?: "",
                model = entity.model ?: "",
                year = entity.year?.toString() ?: "",
                avatarColor = entity.avatarColor ?: "#4FC3F7",
                licensePlate = extractAttribute(entity.attributes, "license_plate"),
                vinSerial = extractAttribute(entity.attributes, "vin"),
                purchaseDate = extractLongAttribute(entity.attributes, "purchase_date"),
                purchasePrice = extractAttribute(entity.attributes, "purchase_price"),
                location = entity.addressLine1 ?: "",
                notes = extractAttribute(entity.attributes, "notes"),
                streetAddress = entity.addressLine1 ?: extractAttribute(entity.attributes, "street_address"),
                city = entity.city ?: extractAttribute(entity.attributes, "city"),
                stateProvince = entity.state ?: extractAttribute(entity.attributes, "state"),
                postalCode = entity.postalCode ?: extractAttribute(entity.attributes, "zip_code"),
                country = entity.country ?: extractAttribute(entity.attributes, "country"),
                fleetNumber = extractAttribute(entity.attributes, "fleet_number"),
                customAttributes = customAttrs,
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Field updates
    // ---------------------------------------------------------------------------

    fun onNameChanged(value: String) { _form.value = _form.value.copy(name = value) }
    fun onAssetTypeChanged(value: String?) { _form.value = _form.value.copy(assetType = value) }
    fun onMakeChanged(value: String) { _form.value = _form.value.copy(make = value) }
    fun onModelChanged(value: String) { _form.value = _form.value.copy(model = value) }
    fun onYearChanged(value: String) { _form.value = _form.value.copy(year = value) }
    fun onAvatarColorChanged(value: String) { _form.value = _form.value.copy(avatarColor = value) }
    fun onLicensePlateChanged(value: String) { _form.value = _form.value.copy(licensePlate = value) }
    fun onVinSerialChanged(value: String) { _form.value = _form.value.copy(vinSerial = value) }
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

    fun onCustomAttributeChanged(key: String, value: String) {
        val updated = _form.value.customAttributes.toMutableMap().also { it[key] = value }
        _form.value = _form.value.copy(customAttributes = updated)
    }

    /** Called when a VIN barcode is scanned externally. */
    fun onVinScanned(scanned: String) {
        _form.value = _form.value.copy(vinSerial = scanned)
    }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    fun save(onSuccess: (assetId: String) -> Unit) {
        val current = _form.value
        if (!current.isNameValid) {
            _form.value = current.copy(saveError = "Name is required")
            return
        }

        _form.value = current.copy(isSaving = true, saveError = null)

        viewModelScope.launch {
            try {
                val accountId = identityManager.getActiveAccountId()
                if (accountId == null) {
                    _form.value = _form.value.copy(isSaving = false, saveError = "No active account")
                    return@launch
                }

                val assetId = editingAssetId ?: UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                // Build the attributes JSON for optional fields not covered by columns
                val attributesJson = buildAttributesJson(
                    licensePlate = current.licensePlate,
                    vin = current.vinSerial,
                    purchaseDate = current.purchaseDate,
                    purchasePrice = current.purchasePrice.toDoubleOrNull(),
                    notes = current.notes,
                    fleetNumber = current.fleetNumber,
                    customAttributes = current.customAttributes,
                )

                val entity = AssetEntity(
                    assetId = assetId,
                    accountId = accountId,
                    name = current.name.trim(),
                    make = current.make.trim().ifBlank { null },
                    model = current.model.trim().ifBlank { null },
                    year = current.year.trim().toLongOrNull(),
                    assetType = current.assetType,
                    meterType = null,
                    avatarColor = current.avatarColor,
                    avatarInitial = current.name.firstOrNull()?.uppercaseChar()?.toString(),
                    addressLine1 = current.streetAddress.trim().ifBlank { current.location.trim().ifBlank { null } },
                    addressLine2 = null,
                    city = current.city.trim().ifBlank { null },
                    state = current.stateProvince.trim().ifBlank { null },
                    postalCode = current.postalCode.trim().ifBlank { null },
                    country = current.country.trim().ifBlank { null },
                    locationId = null,
                    attributes = attributesJson,
                    isFreSample = false,
                    parentAssetId = null,
                    path = null,
                    depth = 0L,
                    childCount = 0L,
                    isRental = false,
                    rentalRate = null,
                    rentalRateUnit = null,
                    createdAt = if (editingAssetId != null) {
                        // Preserve original createdAt for edits
                        repository.getAssetById(accountId, assetId)?.createdAt ?: now
                    } else now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = if (editingAssetId != null) {
                        repository.getAssetById(accountId, assetId)?.serverVersion ?: 0L
                    } else 0L,
                    seq = null,
                )

                repository.upsertAsset(accountId, entity)

                _form.value = _form.value.copy(isSaving = false, savedAssetId = assetId)
                Timber.d("[AddEditAssetViewModel] Saved asset $assetId")
                onSuccess(assetId)
            } catch (e: Exception) {
                Timber.e(e, "[AddEditAssetViewModel] Save failed")
                _form.value = _form.value.copy(isSaving = false, saveError = e.message ?: "Save failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Attribute JSON helpers (minimal JSON, avoids pulling in serialization lib)
    // ---------------------------------------------------------------------------

    private fun buildAttributesJson(
        licensePlate: String,
        vin: String,
        purchaseDate: Long?,
        purchasePrice: Double?,
        notes: String,
        fleetNumber: String,
        customAttributes: Map<String, String>,
    ): String? {
        val parts = mutableListOf<String>()
        if (licensePlate.isNotBlank()) parts += "\"license_plate\":\"${licensePlate.trim()}\""
        if (vin.isNotBlank()) parts += "\"vin\":\"${vin.trim()}\""
        if (purchaseDate != null) parts += "\"purchase_date\":$purchaseDate"
        if (purchasePrice != null) parts += "\"purchase_price\":$purchasePrice"
        if (notes.isNotBlank()) parts += "\"notes\":\"${notes.trim().replace("\"", "\\\"")}\""
        if (fleetNumber.isNotBlank()) parts += "\"fleet_number\":\"${fleetNumber.trim()}\""
        for ((key, value) in customAttributes) {
            if (value.isNotBlank()) {
                parts += "\"${key.replace("\"", "\\\"")}\":\"${value.trim().replace("\"", "\\\"")}\""
            }
        }
        return if (parts.isEmpty()) null else "{${parts.joinToString(",")}}"
    }

    private fun extractAttribute(attributes: String?, key: String): String {
        if (attributes.isNullOrBlank()) return ""
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(attributes)?.groupValues?.get(1) ?: ""
    }

    private fun extractLongAttribute(attributes: String?, key: String): Long? {
        if (attributes.isNullOrBlank()) return null
        val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return pattern.find(attributes)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * Parse all keys from the attributes JSON and return only those that are
     * not in [KNOWN_ATTRIBUTE_KEYS].
     */
    private fun extractCustomAttributes(attributes: String?): Map<String, String> {
        if (attributes.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        // Match both string values: "key":"value"
        val stringPattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
        for (match in stringPattern.findAll(attributes)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            if (key !in KNOWN_ATTRIBUTE_KEYS) {
                result[key] = value
            }
        }
        return result
    }
}
