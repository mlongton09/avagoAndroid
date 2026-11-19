package com.avago.feature.inventory.partissues

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.MovementStatus
import com.avago.core.data.domain.StockMovement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Issue-history filter: issues/returns vs. transfers. */
enum class IssueFilter(val kinds: Set<MovementKind>) {
    ALL(ISSUE_KINDS + TRANSFER_KINDS),
    ISSUE(ISSUE_KINDS),
    TRANSFER(TRANSFER_KINDS),
}

private val ISSUE_KINDS get() = setOf(MovementKind.ISSUE, MovementKind.RETURN)
private val TRANSFER_KINDS get() = setOf(MovementKind.TRANSFER, MovementKind.TRANSFER_DISPATCH, MovementKind.TRANSFER_RECEIPT)

val StockMovement.isTransfer: Boolean get() = kind in TRANSFER_KINDS

data class PartIssueListUiState(
    val issues: List<StockMovement> = emptyList(),
    val locationNames: Map<String, String> = emptyMap(),
    val filter: IssueFilter = IssueFilter.ALL,
    val isLoading: Boolean = true,
)

/** Posted issues, returns and transfers, newest first. */
@HiltViewModel
class PartIssueListViewModel @Inject constructor(
    inventory: InventoryRepository,
    locations: LocationRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(IssueFilter.ALL)

    val state: StateFlow<PartIssueListUiState> = combine(
        inventory.observeMovements(IssueFilter.ALL.kinds, setOf(MovementStatus.POSTED)),
        locations.observeLocations(includeArchived = true),
        filter,
    ) { movements, locs, f ->
        PartIssueListUiState(
            issues = movements.filter { it.kind in f.kinds }.sortedByDescending { it.effectiveAt ?: it.createdAt },
            locationNames = locs.associate { it.id to it.name },
            filter = f,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PartIssueListUiState())

    fun setFilter(f: IssueFilter) { filter.value = f }
}

data class PartIssueDetailUiState(
    val issue: StockMovement? = null,
    val locationNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PartIssueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    inventory: InventoryRepository,
    locations: LocationRepository,
) : ViewModel() {

    private val issueId = MutableStateFlow(savedStateHandle.get<String>("issueId"))

    val state: StateFlow<PartIssueDetailUiState> = combine(
        issueId.filterNotNull().flatMapLatest(inventory::observeMovement),
        locations.observeLocations(includeArchived = true).map { locs -> locs.associate { it.id to it.name } },
    ) { movement, names ->
        PartIssueDetailUiState(issue = movement, locationNames = names, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PartIssueDetailUiState())

    fun load(id: String) { issueId.value = id }
}
