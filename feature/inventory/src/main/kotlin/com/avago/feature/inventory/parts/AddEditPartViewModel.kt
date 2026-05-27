package com.avago.feature.inventory.parts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.InventoryEntity
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

val UOM_OPTIONS = listOf("each", "box", "case", "pair", "liter", "gallon", "quart", "oz", "lb", "ft", "m", "roll", "set")
val STATUS_OPTIONS = listOf("active", "discontinued", "on_order", "archived")

data class AddEditPartUiState(
    val name: String = "",
    val sku: String = "",
    val category: String = "",
    val description: String = "",
    val unitCost: String = "",
    val currency: String = "USD",
    val minQty: String = "",
    val maxQty: String = "",
    val safetyStock: String = "",
    val locationId: String = "",
    val binId: String = "",
    val barcode: String = "",
    val manufacturer: String = "",
    val uom: String = "each",
    val reorderQty: String = "",
    val notes: String = "",
    val status: String = "active",
    val initialQty: String = "",
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class AddEditPartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val partId: String? = savedStateHandle["partId"]

    private val _state = MutableStateFlow(AddEditPartUiState())
    val state: StateFlow<AddEditPartUiState> = _state.asStateFlow()

    init {
        partId?.let { loadPart(it) }
    }

    private fun loadPart(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val accountId = identityManager.getActiveAccountId() ?: run {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }
            val part = dbFactory.get(accountId).partDao().getById(id)
            if (part != null) {
                // Prefer dedicated columns; fall back to legacy attributes JSON for migrated rows.
                val attrs = if (part.manufacturer == null && part.status == null)
                    parseAttributes(part.attributes) else emptyMap()
                _state.value = _state.value.copy(
                    name = part.name,
                    sku = part.sku ?: "",
                    category = part.category ?: "",
                    description = part.description ?: "",
                    unitCost = part.cost?.toString() ?: "",
                    currency = part.currency ?: "USD",
                    minQty = "",
                    maxQty = "",
                    safetyStock = "",
                    barcode = "",
                    manufacturer = part.manufacturer ?: attrs["manufacturer"] ?: "",
                    uom = part.unitOfMeasure ?: "each",
                    reorderQty = part.reorderQuantity?.toString() ?: "",
                    notes = part.notes ?: "",
                    status = part.status ?: attrs["status"] ?: "active",
                    isLoading = false,
                )
            } else {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    private fun parseAttributes(attributes: String?): Map<String, String> {
        if (attributes.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(attributes)
            buildMap {
                json.keys().forEach { key -> put(key, json.optString(key)) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun buildAttributesJson(manufacturer: String, status: String): String? {
        if (manufacturer.isBlank() && status == "active") return null
        return JSONObject().apply {
            if (manufacturer.isNotBlank()) put("manufacturer", manufacturer)
            put("status", status)
        }.toString()
    }

    fun setName(v: String) { _state.value = _state.value.copy(name = v, nameError = null) }
    fun setSku(v: String) { _state.value = _state.value.copy(sku = v) }
    fun setCategory(v: String) { _state.value = _state.value.copy(category = v) }
    fun setDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun setUnitCost(v: String) { _state.value = _state.value.copy(unitCost = v) }
    fun setCurrency(v: String) { _state.value = _state.value.copy(currency = v) }
    fun setMinQty(v: String) { _state.value = _state.value.copy(minQty = v) }
    fun setMaxQty(v: String) { _state.value = _state.value.copy(maxQty = v) }
    fun setSafetyStock(v: String) { _state.value = _state.value.copy(safetyStock = v) }
    fun setBarcode(v: String) { _state.value = _state.value.copy(barcode = v) }
    fun setManufacturer(v: String) { _state.value = _state.value.copy(manufacturer = v) }
    fun setUom(v: String) { _state.value = _state.value.copy(uom = v) }
    fun setReorderQty(v: String) { _state.value = _state.value.copy(reorderQty = v) }
    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }
    fun setStatus(v: String) { _state.value = _state.value.copy(status = v) }
    fun setInitialQty(v: String) { _state.value = _state.value.copy(initialQty = v) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.value = s.copy(nameError = "Name is required")
            return
        }
        val accountId = identityManager.getActiveAccountId() ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val now = System.currentTimeMillis()
                val id = partId ?: UUID.randomUUID().toString()
                val part = PartEntity(
                    partId = id,
                    accountId = accountId,
                    name = s.name.trim(),
                    sku = s.sku.takeIf { it.isNotBlank() },
                    category = s.category.takeIf { it.isNotBlank() },
                    description = s.description.takeIf { it.isNotBlank() },
                    cost = s.unitCost.toDoubleOrNull(),
                    currency = s.currency.takeIf { it.isNotBlank() },
                    unitOfMeasure = s.uom.takeIf { it.isNotBlank() },
                    defaultVendorId = null,
                    attributes = buildAttributesJson(s.manufacturer, s.status),
                    manufacturer = s.manufacturer.takeIf { it.isNotBlank() },
                    reorderQuantity = s.reorderQty.toIntOrNull(),
                    status = s.status,
                    entityType = null,
                    entityId = null,
                    quantity = null,
                    gtin = s.barcode.takeIf { it.isNotBlank() },
                    serialNumber = null,
                    notes = s.notes.takeIf { it.isNotBlank() },
                    baseAmount = null,
                    exchangeRateUsed = null,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = 0L,
                    seq = null,
                )
                val db = dbFactory.get(accountId)
                db.partDao().upsert(part)

                // If creating a new part and an initial quantity was provided, create an inventory record
                if (partId == null && s.initialQty.isNotBlank()) {
                    val initialQtyValue = s.initialQty.toDoubleOrNull() ?: 0.0
                    if (initialQtyValue > 0.0) {
                        val inventoryId = UUID.randomUUID().toString()
                        db.inventoryDao().upsert(
                            InventoryEntity(
                                inventoryId = inventoryId,
                                accountId = accountId,
                                partId = id,
                                locationId = s.locationId.takeIf { it.isNotBlank() },
                                binId = s.binId.takeIf { it.isNotBlank() },
                                quantityOnHand = initialQtyValue,
                                status = "active",
                                lastTransactionId = null,
                                createdAt = now,
                                updatedAt = now,
                                deletedAt = null,
                                serverVersion = 0L,
                                seq = null,
                            ),
                        )
                        db.syncQueueDao().enqueueWithDedup(
                            SyncQueueEntity(
                                queueId = "inventory_$inventoryId",
                                entityType = "inventory",
                                entityId = inventoryId,
                                operation = "insert",
                                serverVersion = 0L,
                                payload = null,
                                syncStatus = "pending",
                                attempts = 0L,
                                lastError = null,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }
                }

                db.syncQueueDao().enqueueWithDedup(
                    SyncQueueEntity(
                        queueId = "part_$id",
                        entityType = "part",
                        entityId = id,
                        operation = if (partId == null) "insert" else "update",
                        serverVersion = 0L,
                        payload = null,
                        syncStatus = "pending",
                        attempts = 0L,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                _state.value = _state.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                Timber.e(e, "AddEditPartViewModel: save failed")
                _state.value = _state.value.copy(isSaving = false, error = e.message)
            }
        }
    }
}
