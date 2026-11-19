package com.avago.feature.log.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Worker
import com.avago.core.data.domain.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

data class PerformedByPickerUiState(
    val members: List<Worker> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: String = "",
)

/** Lists the account's people (workers linked to a user) from the v2 store. */
@HiltViewModel
class PerformedByPickerViewModel @Inject constructor(
    workers: WorkerRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _freeText = MutableStateFlow("")

    val freeText: StateFlow<String> = _freeText.asStateFlow()

    val uiState: StateFlow<PerformedByPickerUiState> =
        combine(workers.observeWorkers(), _query) { all, query ->
            PerformedByPickerUiState(
                members = filterMembers(all, query),
                isLoading = false,
                query = query,
            )
        }
            .catch { e ->
                Timber.e(e, "[PerformedByPickerViewModel] observe failed")
                emit(PerformedByPickerUiState(isLoading = false, error = e.message))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformedByPickerUiState())

    fun onQueryChanged(q: String) {
        _query.value = q
    }

    fun onFreeTextChanged(name: String) {
        _freeText.value = name
    }

    companion object {
        /** Active people (workers with a user account) matching [query], by name. */
        fun filterMembers(all: List<Worker>, query: String): List<Worker> = all
            .filter { it.userId != null && it.status == "active" }
            .filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
            .sortedBy { it.displayName.lowercase() }
    }
}
