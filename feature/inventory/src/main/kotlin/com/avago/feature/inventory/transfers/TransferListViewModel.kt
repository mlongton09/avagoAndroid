package com.avago.feature.inventory.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AdminRepository
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.MovementStatus
import com.avago.core.data.domain.StockMovement
import com.avago.core.data.domain.TransferRequestDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

data class TransferListItem(
    val transfer: StockMovement,
    val fromName: String?,
    val toName: String?,
) {
    /** A transfer request still awaiting a decision or posting. */
    val isOpenRequest: Boolean
        get() = transfer.kind == MovementKind.TRANSFER &&
            transfer.status in setOf(MovementStatus.DRAFT, MovementStatus.REQUESTED, MovementStatus.APPROVED)

    /** The decisions open to a manager: approve/decline while pending, post once approved, cancel either way. */
    val decisions: List<MovementStatus>
        get() = when {
            !isOpenRequest -> emptyList()
            transfer.status == MovementStatus.APPROVED -> listOf(MovementStatus.POSTED, MovementStatus.CANCELLED)
            else -> listOf(MovementStatus.APPROVED, MovementStatus.DECLINED, MovementStatus.POSTED, MovementStatus.CANCELLED)
        }
}

/** A new transfer request being filled in. */
data class TransferRequestForm(
    val productId: String? = null,
    val productName: String? = null,
    val fromLocationId: String? = null,
    val fromLocationName: String? = null,
    val toLocationId: String? = null,
    val toLocationName: String? = null,
    val quantity: String = "",
    val notes: String = "",
) {
    val parsedQuantity: BigDecimal? get() = quantity.trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    val isComplete: Boolean
        get() = productId != null && fromLocationId != null && toLocationId != null &&
            fromLocationId != toLocationId && parsedQuantity != null
}

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
 * Inter-location transfers and transfer requests synced from the v2 store. Stock holders raise
 * requests (`stock.transfer_requests.create`); managers approve, decline, cancel or post them
 * (`stock.transfer_requests.decide`). Direct moves are posted from the warehouse move screen.
 */
@HiltViewModel
class TransferListViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    locations: LocationRepository,
    admin: AdminRepository,
) : ViewModel() {

    private val selectedStatus = MutableStateFlow<MovementStatus?>(null)

    private val holdsStock = combine(admin.observeAllowed(USE_STOCK), admin.observeAllowed(RECEIVE_STOCK)) { use, receive ->
        use || receive
    }

    val canRequest: StateFlow<Boolean> = holdsStock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Deciding needs a stock permission and the manager role. */
    val canDecide: StateFlow<Boolean> = combine(holdsStock, admin.observeIsManager()) { holds, manager -> holds && manager }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _form = MutableStateFlow<TransferRequestForm?>(null)
    val form: StateFlow<TransferRequestForm?> = _form.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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

    fun startRequest() {
        _form.value = TransferRequestForm()
    }

    fun updateForm(transform: (TransferRequestForm) -> TransferRequestForm) {
        _form.value = _form.value?.let(transform)
    }

    fun closeForm() {
        _form.value = null
    }

    fun submitRequest() {
        val f = _form.value?.takeIf { it.isComplete } ?: return
        val draft = TransferRequestDraft(
            productId = f.productId ?: return,
            quantity = f.parsedQuantity ?: return,
            fromLocationId = f.fromLocationId ?: return,
            toLocationId = f.toLocationId ?: return,
            notes = f.notes,
        )
        _form.value = null
        act { inventory.requestTransfer(draft) }
    }

    fun decide(item: TransferListItem, status: MovementStatus, note: String?) =
        act { inventory.decideTransferRequest(item.transfer.id, status, note) }

    fun clearError() {
        _error.value = null
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                Timber.e(e, "TransferListViewModel: action failed")
                _error.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    private companion object {
        const val USE_STOCK = "inventory.use_stock"
        const val RECEIVE_STOCK = "inventory.receive_stock"
    }
}
