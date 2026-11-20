package com.avago.feature.inventory.parts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.FormFillRouter
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.ProductDraft
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

val UOM_OPTIONS = listOf("each", "box", "case", "pair", "liter", "gallon", "quart", "oz", "lb", "ft", "m", "roll", "set")
val ABC_CLASSES = listOf("A", "B", "C")

data class AddEditPartUiState(
    val name: String = "",
    val sku: String = "",
    val category: String = "",
    val manufacturer: String = "",
    /** Editing only (the create command does not take them). */
    val modelNumber: String = "",
    val revision: String = "",
    val originCountryCode: String = "",
    val isEdit: Boolean = false,
    val unitCost: String = "",
    val currency: String = "USD",
    val uom: String = "each",
    val reorderQty: String = "",
    val abcClass: String = "",
    val barcode: String = "",
    val notes: String = "",
    val archived: Boolean = false,
    /** New parts only: opening stock received into [locationId]. */
    val initialQty: String = "",
    val locationId: String? = null,
    val locationName: String? = null,
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class AddEditPartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inventory: InventoryRepository,
    private val formFillRouter: FormFillRouter,
) : ViewModel() {

    private val partId: String? = savedStateHandle["partId"]
    private var stored: Product? = null

    private val _state = MutableStateFlow(AddEditPartUiState())
    val state: StateFlow<AddEditPartUiState> = _state.asStateFlow()

    init {
        formFillRouter.register("add_part") { fields -> applyScoutFields(fields) }
        partId?.let { loadPart(it) }
    }

    override fun onCleared() {
        formFillRouter.unregister("add_part")
        super.onCleared()
    }

    fun applyScoutFields(fields: Map<String, String?>): List<String> {
        val touched = mutableListOf<String>()
        _state.update { s ->
            var updated = s
            fields["name"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                updated = updated.copy(name = it); touched.add("name")
            }
            fields["sku"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                updated = updated.copy(sku = it); touched.add("SKU")
            }
            (fields["unit_cost"] ?: fields["cost"])?.toBigDecimalOrNull()?.let {
                updated = updated.copy(unitCost = it.toPlainString()); touched.add("unit cost")
            }
            fields["manufacturer"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                updated = updated.copy(manufacturer = it); touched.add("manufacturer")
            }
            (fields["reorder_quantity"] ?: fields["reorder_point"])?.toBigDecimalOrNull()?.let {
                updated = updated.copy(reorderQty = it.toPlainString()); touched.add("reorder qty")
            }
            (fields["notes"] ?: fields["description"])?.trim()?.let {
                updated = updated.copy(notes = it); touched.add("notes")
            }
            updated
        }
        return touched
    }

    private fun loadPart(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val product = inventory.product(id)
            stored = product
            if (product == null) {
                _state.update { it.copy(isLoading = false, error = "Part not found") }
                return@launch
            }
            _state.update {
                it.copy(
                    name = product.name,
                    sku = product.sku,
                    category = product.categoryName.orEmpty(),
                    manufacturer = product.manufacturer.orEmpty(),
                    modelNumber = product.modelNumber.orEmpty(),
                    revision = product.revision.orEmpty(),
                    originCountryCode = product.originCountryCode.orEmpty(),
                    isEdit = true,
                    unitCost = product.standardUnitCost?.toPlainString().orEmpty(),
                    currency = product.costCurrency ?: it.currency,
                    uom = product.uomCode,
                    reorderQty = product.defaultReorderQty?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    abcClass = product.abcClass.orEmpty(),
                    barcode = product.gtin.orEmpty(),
                    notes = product.notes.orEmpty(),
                    archived = product.archived,
                    isLoading = false,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun setSku(v: String) = _state.update { it.copy(sku = v) }
    fun setCategory(v: String) = _state.update { it.copy(category = v) }
    fun setManufacturer(v: String) = _state.update { it.copy(manufacturer = v) }
    fun setModelNumber(v: String) = _state.update { it.copy(modelNumber = v) }
    fun setRevision(v: String) = _state.update { it.copy(revision = v) }
    fun setOriginCountryCode(v: String) =
        _state.update { it.copy(originCountryCode = v.filter(Char::isLetter).uppercase().take(2)) }
    fun setUnitCost(v: String) = _state.update { it.copy(unitCost = v) }
    fun setCurrency(v: String) = _state.update { it.copy(currency = v.uppercase().take(3)) }
    fun setUom(v: String) = _state.update { it.copy(uom = v) }
    fun setReorderQty(v: String) = _state.update { it.copy(reorderQty = v) }
    fun setAbcClass(v: String) = _state.update { it.copy(abcClass = v) }
    fun setBarcode(v: String) = _state.update { it.copy(barcode = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setArchived(v: Boolean) = _state.update { it.copy(archived = v) }
    fun setInitialQty(v: String) = _state.update { it.copy(initialQty = v) }
    fun setLocation(location: Location?) = _state.update { it.copy(locationId = location?.id, locationName = location?.name) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            return
        }
        val unitCost = s.unitCost.trim().takeIf { it.isNotEmpty() }?.let {
            it.toBigDecimalOrNull()?.takeIf { c -> c.signum() >= 0 } ?: return fail("Enter a valid unit cost")
        }
        val reorderQty = s.reorderQty.trim().takeIf { it.isNotEmpty() }?.let {
            it.toBigDecimalOrNull()?.takeIf { q -> q.signum() >= 0 } ?: return fail("Enter a valid reorder quantity")
        }
        val initialQty = s.initialQty.trim().takeIf { it.isNotEmpty() && partId == null }?.let {
            it.toBigDecimalOrNull()?.takeIf { q -> q.signum() >= 0 } ?: return fail("Enter a valid initial quantity")
        }?.takeIf { it.signum() > 0 }
        if (initialQty != null && s.locationId == null) return fail("Choose the location that holds the initial stock")
        if (s.originCountryCode.isNotEmpty() && s.originCountryCode.length != 2) {
            return fail("Enter the two-letter country of origin")
        }

        val draft = ProductDraft(
            name = s.name.trim(),
            sku = s.sku,
            manufacturer = s.manufacturer,
            modelNumber = s.modelNumber.takeIf { s.isEdit },
            revision = s.revision.takeIf { s.isEdit },
            originCountryCode = s.originCountryCode.takeIf { s.isEdit },
            category = s.category,
            uomCode = s.uom,
            standardUnitCost = unitCost,
            costCurrency = s.currency.takeIf { unitCost != null },
            defaultReorderQty = reorderQty,
            abcClass = s.abcClass,
            gtin = s.barcode,
            notes = s.notes,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val current = stored
                if (current == null) {
                    val id = inventory.createProduct(draft)
                    if (initialQty != null && s.locationId != null) {
                        inventory.postStockDocument(
                            StockDocumentDraft(
                                kind = MovementKind.OPENING_BALANCE,
                                locationId = s.locationId,
                                lines = listOf(
                                    StockLineDraft(
                                        productId = id,
                                        quantity = initialQty,
                                        unitCost = unitCost,
                                        currency = draft.costCurrency,
                                    ),
                                ),
                                reference = "Initial stock",
                            ),
                        )
                    }
                } else {
                    inventory.updateProduct(current.id, draft)
                    if (s.archived != current.archived) inventory.setProductArchived(current.id, s.archived)
                }
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                Timber.e(e, "AddEditPartViewModel: save failed")
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(error = message) }
    }
}
