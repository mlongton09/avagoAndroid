package com.avago.feature.inventory.parts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

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
                    isLoading = false,
                )
            } else {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
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
    fun setLocationId(v: String) { _state.value = _state.value.copy(locationId = v) }
    fun setBinId(v: String) { _state.value = _state.value.copy(binId = v) }
    fun setBarcode(v: String) { _state.value = _state.value.copy(barcode = v) }

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
                    unitOfMeasure = null,
                    defaultVendorId = null,
                    attributes = null,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = 0L,
                    seq = null,
                )
                val db = dbFactory.get(accountId)
                db.partDao().upsert(part)
                db.syncQueueDao().enqueueOrReplace(
                    SyncQueueEntity(
                        queueId = UUID.randomUUID().toString(),
                        entityType = "part",
                        entityId = id,
                        operation = if (partId == null) "create" else "update",
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
