package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkOrderTemplate
import com.avago.core.data.domain.WorkOrderTemplateDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WoTemplateListViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
) : ViewModel() {

    private val _templates = MutableStateFlow<List<WorkOrderTemplate>>(emptyList())
    val templates: StateFlow<List<WorkOrderTemplate>> = _templates.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTemplates()
                .catch { throwable ->
                    _error.value = throwable.message
                    emit(emptyList())
                }
                .collect { list ->
                    _templates.value = list
                    _isLoading.value = false
                }
        }
    }

    fun createTemplate(title: String, description: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.createTemplate(
                    WorkOrderTemplateDraft(title = trimmedTitle, descriptionTemplate = description.trim().ifBlank { null }),
                )
                _error.value = null
            }.onFailure { _error.value = it.message }
        }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteTemplate(templateId)
                _error.value = null
            }.onFailure { _error.value = it.message }
        }
    }
}
