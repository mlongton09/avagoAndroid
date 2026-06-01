package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.WoTemplateEntity
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WoTemplateListViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val accountIdFlow = identityManager.activeAccountId
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.getActiveAccountId())

    private val _templates = MutableStateFlow<List<WoTemplateEntity>>(emptyList())
    val templates: StateFlow<List<WoTemplateEntity>> = _templates.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            accountIdFlow
                .flatMapLatest { accountId ->
                    if (accountId == null) {
                        _isLoading.value = false
                        flowOf(emptyList())
                    } else {
                        repository.observeTemplates(accountId)
                    }
                }
                .catch { throwable ->
                    _error.value = throwable.message ?: "Failed to load templates"
                    _isLoading.value = false
                    emit(emptyList())
                }
                .collect { entities ->
                    _templates.value = entities
                    _isLoading.value = false
                }
        }
    }

    fun createTemplate(title: String, description: String) {
        val accountId = accountIdFlow.value ?: return
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                repository.upsertTemplate(
                    accountId = accountId,
                    entity = WoTemplateEntity(
                        templateId = UUID.randomUUID().toString(),
                        accountId = accountId,
                        title = trimmedTitle,
                        description = description.trim().ifBlank { null },
                        category = null,
                        checklistItems = null,
                        estimatedEffortMinutes = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = 0L,
                    ),
                )
                _error.value = null
            }.onFailure {
                _error.value = it.message ?: "Failed to create template"
            }
        }
    }

    fun deleteTemplate(templateId: String) {
        val accountId = accountIdFlow.value ?: return
        viewModelScope.launch {
            runCatching {
                repository.deleteTemplate(accountId, templateId)
                _error.value = null
            }.onFailure {
                _error.value = it.message ?: "Failed to delete template"
            }
        }
    }
}
