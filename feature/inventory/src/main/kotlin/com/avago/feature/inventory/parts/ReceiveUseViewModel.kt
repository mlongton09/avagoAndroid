package com.avago.feature.inventory.parts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.FormFillRouter
import com.avago.core.data.domain.Bin
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import com.avago.core.data.domain.StockPosition
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.domain.toQtyOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class ReceiveUseMode { RECEIVE, USE }

/** Reason codes offered for receipts and issues (free-text `reason_code` on the stock line). */
val INVENTORY_REASON_CODES = listOf("purchase", "use", "return", "waste", "initial_count")

/** The editable form of the receive/use sheet. */
data class ReceiveUseForm(
    val quantity: String = "",
    val notes: String = "",
    val reasonCode: String? = null,
    /** Receive: the destination location and optional bin. */
    val locationId: String? = null,
    val binId: String? = null,
    /** Use: the position stock is taken from. */
    val positionId: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val isDone: Boolean = false,
)

data class ReceiveUseUiState(
    val form: ReceiveUseForm = ReceiveUseForm(),
    val locations: List<Location> = emptyList(),
    val bins: List<Bin> = emptyList(),
    val positions: List<StockPosition> = emptyList(),
) {
    val quantity get() = form.quantity
    val notes get() = form.notes
    val reasonCode get() = form.reasonCode
    val isSubmitting get() = form.isSubmitting
    val error get() = form.error
    val isDone get() = form.isDone
    val selectedLocation: Location? get() = locations.firstOrNull { it.id == form.locationId }
    val selectedBin: Bin? get() = bins.firstOrNull { it.id == form.binId }
    val selectedPosition: StockPosition? get() = positions.firstOrNull { it.id == form.positionId }
}

/**
 * Receives stock into, or issues stock from, one product's positions as a posted stock document
 * (`stock.documents.create_and_post`). Stock levels update optimistically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiveUseViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    locations: LocationRepository,
    private val formFillRouter: FormFillRouter,
) : ViewModel() {

    private val productId = MutableStateFlow<String?>(null)
    private var mode = ReceiveUseMode.RECEIVE
    private val form = MutableStateFlow(ReceiveUseForm())

    private val positions = productId.filterNotNull().flatMapLatest { id ->
        inventory.observePositions(productId = id).map { all ->
            all.filter { it.onHand.signum() > 0 }.sortedByDescending { it.onHand }
        }
    }
    private val bins = form.map { it.locationId }.flatMapLatest { locationId ->
        if (locationId == null) flowOf(emptyList()) else locations.observeBins(locationId)
    }

    private val allLocations = locations.observeLocations()

    val state: StateFlow<ReceiveUseUiState> = combine(form, allLocations, bins, positions) { f, locs, b, p ->
        ReceiveUseUiState(form = f, locations = locs, bins = b, positions = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiveUseUiState())

    init {
        viewModelScope.launch {
            combine(allLocations, positions, ::Pair).collect { (locs, p) -> form.update { it.withDefaults(locs, p) } }
        }
    }

    fun start(productId: String, mode: ReceiveUseMode) {
        if (this.productId.value == productId && this.mode == mode) return
        this.mode = mode
        this.productId.value = productId
        form.value = ReceiveUseForm()
    }

    fun setQuantity(q: String) = form.update { it.copy(quantity = q, error = null) }
    fun setNotes(n: String) = form.update { it.copy(notes = n) }
    fun setReasonCode(code: String) = form.update { it.copy(reasonCode = code, error = null) }
    fun selectLocation(id: String) = form.update { it.copy(locationId = id, binId = null, error = null) }
    fun selectBin(id: String?) = form.update { it.copy(binId = id) }
    fun selectPosition(id: String) = form.update { it.copy(positionId = id, error = null) }

    fun registerFormFill(screenId: String) {
        formFillRouter.register(screenId) { fields -> applyScoutFields(fields) }
    }

    fun unregisterFormFill(screenId: String) {
        formFillRouter.unregister(screenId)
    }

    private fun applyScoutFields(fields: Map<String, String?>): List<String> {
        val touched = mutableListOf<String>()
        form.update { s ->
            var updated = s
            (fields["quantity"] ?: fields["qty"])?.toQtyOrNull()?.let {
                updated = updated.copy(quantity = it.toPlainString()); touched.add("quantity")
            }
            fields["notes"]?.trim()?.let {
                updated = updated.copy(notes = it); touched.add("notes")
            }
            updated
        }
        return touched
    }

    fun submit() {
        val product = productId.value ?: return
        val current = state.value
        val qty = current.form.quantity.toQtyOrNull()
        val position = current.selectedPosition
        val error = when {
            qty == null -> "Invalid quantity"
            qty.signum() <= 0 -> "Quantity must be positive"
            mode == ReceiveUseMode.RECEIVE && current.form.locationId == null -> "Choose a location"
            mode == ReceiveUseMode.USE && position == null -> "Choose where the stock is taken from"
            mode == ReceiveUseMode.USE && position != null && qty > position.onHand -> "Only ${position.onHand.toPlainString()} on hand there"
            else -> null
        }
        if (error != null || qty == null) {
            form.update { it.copy(error = error) }
            return
        }
        val notes = current.form.notes.takeIf { it.isNotBlank() }
        val draft = when (mode) {
            ReceiveUseMode.RECEIVE -> StockDocumentDraft(
                kind = MovementKind.RECEIPT,
                locationId = current.form.locationId!!,
                notes = notes,
                lines = listOf(
                    StockLineDraft(productId = product, quantity = qty, binId = current.form.binId, reasonCode = current.form.reasonCode),
                ),
            )
            ReceiveUseMode.USE -> {
                val position = current.selectedPosition!!
                StockDocumentDraft(
                    kind = MovementKind.ISSUE,
                    locationId = position.locationId,
                    notes = notes,
                    lines = listOf(
                        StockLineDraft(
                            productId = product, quantity = qty, binId = position.binId, lotId = position.lotId,
                            condition = position.condition, reasonCode = current.form.reasonCode,
                        ),
                    ),
                )
            }
        }
        viewModelScope.launch {
            form.update { it.copy(isSubmitting = true, error = null) }
            try {
                inventory.postStockDocument(draft)
                form.update { it.copy(isSubmitting = false, isDone = true) }
            } catch (e: StoreNotReadyException) {
                form.update { it.copy(isSubmitting = false, error = "Not signed in yet — try again in a moment") }
            } catch (e: IllegalArgumentException) {
                form.update { it.copy(isSubmitting = false, error = e.message) }
            } catch (e: Exception) {
                Timber.e(e, "ReceiveUseViewModel: submit failed")
                form.update { it.copy(isSubmitting = false, error = e.message ?: "Could not save") }
            }
        }
    }

    /** Pre-selects the busiest position (use) or its location (receive) until the user picks. */
    private fun ReceiveUseForm.withDefaults(locations: List<Location>, positions: List<StockPosition>): ReceiveUseForm = when (mode) {
        ReceiveUseMode.USE -> if (positionId == null && positions.isNotEmpty()) copy(positionId = positions.first().id) else this
        ReceiveUseMode.RECEIVE -> if (locationId == null) {
            val preferred = positions.firstOrNull()?.locationId ?: locations.firstOrNull { it.isPrimary }?.id ?: locations.firstOrNull()?.id
            preferred?.let { id -> copy(locationId = id, binId = positions.firstOrNull { it.locationId == id }?.binId) } ?: this
        } else {
            this
        }
    }
}
