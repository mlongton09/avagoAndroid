package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.feature.workorders.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CostLinesEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val woId: String = requireNotNull(savedStateHandle["woId"]) {
        "CostLinesEditorViewModel requires woId in SavedStateHandle"
    }

    private val _accountId: StateFlow<String?> = identityManager.activeAccountId
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.getActiveAccountId())

    @OptIn(ExperimentalCoroutinesApi::class)
    val costLines: StateFlow<List<LogCostLineEntity>> = _accountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else repository.observeCostLinesForWo(accountId, woId)
                .catch { e -> Timber.e(e, "[CostLinesVM] flow error"); emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Double> = costLines
        .map { lines -> lines.sumOf { it.quantity * it.unitCost } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun addLine(
        description: String,
        kind: String,
        quantity: Double,
        unitCost: Double,
        glCode: String,
    ) {
        val accountId = _accountId.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val now = System.currentTimeMillis()
                val nextOrder = (costLines.value.maxOfOrNull { it.displayOrder } ?: 0L) + 1L
                val entity = LogCostLineEntity(
                    lineId = UUID.randomUUID().toString(),
                    accountId = accountId,
                    logId = "",
                    kind = kind,
                    displayOrder = nextOrder,
                    inventoryId = null,
                    userId = null,
                    description = description.ifBlank { null },
                    quantity = quantity,
                    unitCost = unitCost,
                    taxAmount = null,
                    glCode = glCode.ifBlank { null },
                    notes = null,
                    woId = woId,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    serverVersion = 0L,
                    seq = null,
                )
                repository.upsertCostLine(accountId, entity)
            } catch (e: Exception) {
                Timber.e(e, "[CostLinesVM] addLine failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateLine(
        existing: LogCostLineEntity,
        description: String,
        kind: String,
        quantity: Double,
        unitCost: Double,
        glCode: String,
    ) {
        val accountId = _accountId.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updated = existing.copy(
                    description = description.ifBlank { null },
                    kind = kind,
                    quantity = quantity,
                    unitCost = unitCost,
                    glCode = glCode.ifBlank { null },
                    updatedAt = System.currentTimeMillis(),
                )
                repository.upsertCostLine(accountId, updated)
            } catch (e: Exception) {
                Timber.e(e, "[CostLinesVM] updateLine failed")
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun removeLine(lineId: String) {
        val accountId = _accountId.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteCostLine(accountId, lineId)
            } catch (e: Exception) {
                Timber.e(e, "[CostLinesVM] removeLine failed")
                _error.value = e.message
            }
        }
    }

    fun clearError() { _error.value = null }
}
