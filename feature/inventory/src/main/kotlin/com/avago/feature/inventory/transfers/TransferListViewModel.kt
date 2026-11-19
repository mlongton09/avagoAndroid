package com.avago.feature.inventory.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.MovementStatus
import com.avago.core.data.domain.StockMovement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TransferListItem(
    val transfer: StockMovement,
    val fromName: String?,
    val toName: String?,
)

data class TransferListUiState(
    val items: List<TransferListItem> = emptyList(),
    val selectedStatus: MovementStatus? = null,
    val isLoading: Boolean = true,
)

val TRANSFER_KINDS: Set<MovementKind> =
    setOf(MovementKind.TRANSFER, MovementKind.TRANSFER_DISPATCH, MovementKind.TRANSFER_RECEIPT)

val TRANSFER_STATUSES: List<MovementStatus> = listOf(
    MovementStatus.REQUESTED, MovementStatus.APPROVED, MovementStatus.DECLINED,
    MovementStatus.POSTED, MovementStatus.CANCELLED,
)

/**
 * Inter-location transfers and transfer requests synced from the v2 store. Requests are raised and
 * approved on the web; direct moves are posted from the warehouse move screen.
 */
@HiltViewModel
class TransferListViewModel @Inject constructor(
    inventory: InventoryRepository,
    locations: LocationRepository,
) : ViewModel() {

    private val selectedStatus = MutableStateFlow<MovementStatus?>(null)

    val state: StateFlow<TransferListUiState> = combine(
        inventory.observeMovements(TRANSFER_KINDS),
        locations.observeLocations(),
        selectedStatus,
    ) { transfers, locs, status ->
        val names = locs.associate { it.id to it.name }
        TransferListUiState(
            items = transfers
                .filter { status == null || it.status == status }
                .sortedByDescending { it.effectiveAt ?: it.createdAt }
                .map { TransferListItem(it, names[it.locationId], it.toLocationId?.let(names::get)) },
            selectedStatus = status,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferListUiState())

    fun setStatus(status: MovementStatus?) {
        selectedStatus.value = status
    }
}
