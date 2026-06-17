package com.avago.feature.inventory.partissues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.PartIssueEntity
import com.avago.core.data.db.entity.PartIssueLineEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Part Issue List
// ---------------------------------------------------------------------------

data class PartIssueListUiState(
    val issues: List<PartIssueEntity> = emptyList(),
    val filterType: String? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PartIssueListViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PartIssueListUiState())
    val state: StateFlow<PartIssueListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            identityManager.activeAccountId
                .filterNotNull()
                .flatMapLatest { accountId -> dbFactory.get(accountId).partIssueDao().observeAll(accountId) }
                .collect { issues ->
                    _state.value = _state.value.copy(issues = issues, isLoading = false)
                }
        }
    }

    fun setFilter(type: String?) { _state.value = _state.value.copy(filterType = type) }

    fun filteredIssues(): List<PartIssueEntity> {
        val s = _state.value
        return if (s.filterType == null) s.issues
        else s.issues.filter { it.issueType == s.filterType }
    }
}

// ---------------------------------------------------------------------------
// Part Issue Detail
// ---------------------------------------------------------------------------

data class PartIssueDetailUiState(
    val issue: PartIssueEntity? = null,
    val lines: List<PartIssueLineEntity> = emptyList(),
    val parts: Map<String, PartEntity> = emptyMap(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PartIssueDetailViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PartIssueDetailUiState())
    val state: StateFlow<PartIssueDetailUiState> = _state.asStateFlow()

    fun load(issueId: String) {
        viewModelScope.launch {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val db = dbFactory.get(accountId)
            val issue = db.partIssueDao().getById(issueId)
            _state.value = _state.value.copy(issue = issue)

            db.partIssueLineDao().observeByIssueId(issueId).collect { lines ->
                val partIds = lines.mapNotNull { it.partId }.toSet()
                val partsMap = partIds.associateWith { pid ->
                    db.partDao().getById(pid)
                }.filterValues { it != null }.mapValues { it.value!! }
                _state.value = _state.value.copy(lines = lines, parts = partsMap, isLoading = false)
            }
        }
    }

    fun partName(partId: String): String =
        _state.value.parts[partId]?.name ?: partId
}
