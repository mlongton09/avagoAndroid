package com.avago.feature.inventory.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.FormFillRouter
import com.avago.core.data.domain.Bin
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.Location
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.Product
import com.avago.core.data.domain.StockDocumentDraft
import com.avago.core.data.domain.StockLineDraft
import com.avago.core.data.domain.StockPosition
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.domain.toQtyOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What a warehouse stock screen posts. */
enum class WarehouseOperation { RECEIVE, ISSUE, MOVE }

data class WarehouseForm(
    val partSearch: String = "",
    val productId: String? = null,
    val quantity: String = "",
    /** Issue/move: the position the stock leaves. */
    val fromPositionId: String? = null,
    /** Receive/move: the destination location and bin. */
    val toLocationId: String? = null,
    val toBinId: String? = null,
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

data class WarehouseUiState(
    val operation: WarehouseOperation,
    val form: WarehouseForm = WarehouseForm(),
    val products: List<Product> = emptyList(),
    val locations: List<Location> = emptyList(),
    val toBins: List<Bin> = emptyList(),
    val positions: List<StockPosition> = emptyList(),
) {
    val selectedProduct: Product? get() = products.firstOrNull { it.id == form.productId }
    val fromPosition: StockPosition? get() = positions.firstOrNull { it.id == form.fromPositionId }
    val toLocation: Location? get() = locations.firstOrNull { it.id == form.toLocationId }
    val toBin: Bin? get() = toBins.firstOrNull { it.id == form.toBinId }
    val matchingProducts: List<Product>
        get() = if (form.partSearch.isBlank() || form.productId != null) emptyList()
        else products.filter {
            it.name.contains(form.partSearch, ignoreCase = true) || it.sku.contains(form.partSearch, ignoreCase = true)
        }.take(25)
    val needsSource: Boolean get() = operation != WarehouseOperation.RECEIVE
    val needsDestination: Boolean get() = operation != WarehouseOperation.ISSUE
}

/**
 * Receive / issue / move one product as a posted stock document. Shared by the three warehouse
 * screens; [operation] picks the document kind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class WarehouseStockViewModel(
    val operation: WarehouseOperation,
    private val inventory: InventoryRepository,
    locations: LocationRepository,
    private val formFillRouter: FormFillRouter,
    private val formFillScreenId: String?,
) : ViewModel() {

    private val form = MutableStateFlow(WarehouseForm())

    private val positions: Flow<List<StockPosition>> = form.map { it.productId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else inventory.observePositions(productId = id).map { all ->
            all.filter { it.onHand.signum() > 0 }.sortedByDescending { it.onHand }
        }
    }
    private val toBins: Flow<List<Bin>> = form.map { it.toLocationId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else locations.observeBins(id)
    }
    private val products = inventory.observeProductStock().map { list -> list.map { it.product } }

    val state: StateFlow<WarehouseUiState> = combine(
        form, products, locations.observeLocations(), toBins, positions,
    ) { f, p, l, b, pos ->
        WarehouseUiState(operation, f, p, l, b, pos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WarehouseUiState(operation))

    init {
        formFillScreenId?.let { id -> formFillRouter.register(id) { fields -> applyScoutFields(fields) } }
        if (operation != WarehouseOperation.RECEIVE) {
            viewModelScope.launch {
                positions.collect { list ->
                    form.update { f ->
                        if (f.fromPositionId == null || list.none { it.id == f.fromPositionId }) {
                            f.copy(fromPositionId = list.firstOrNull()?.id)
                        } else {
                            f
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        formFillScreenId?.let(formFillRouter::unregister)
        super.onCleared()
    }

    private fun applyScoutFields(fields: Map<String, String?>): List<String> {
        val touched = mutableListOf<String>()
        form.update { s ->
            var updated = s
            (fields["quantity"] ?: fields["qty"])?.toQtyOrNull()?.let {
                updated = updated.copy(quantity = it.toPlainString()); touched.add("quantity")
            }
            fields["notes"]?.trim()?.let { updated = updated.copy(notes = it); touched.add("notes") }
            updated
        }
        return touched
    }

    fun setPartSearch(q: String) = form.update { it.copy(partSearch = q, productId = null, fromPositionId = null, error = null) }
    fun selectProduct(product: Product) = form.update { it.copy(productId = product.id, partSearch = product.name, error = null) }
    fun setQuantity(q: String) = form.update { it.copy(quantity = q, error = null) }
    fun selectFromPosition(id: String) = form.update { it.copy(fromPositionId = id, error = null) }
    fun selectToLocation(id: String) = form.update { it.copy(toLocationId = id, toBinId = null, error = null) }
    fun selectToBin(id: String?) = form.update { it.copy(toBinId = id) }
    fun setNotes(v: String) = form.update { it.copy(notes = v) }

    fun submit() {
        val s = state.value
        val qty = s.form.quantity.toQtyOrNull()
        val from = s.fromPosition
        val error = when {
            s.form.productId == null -> "Select a part"
            qty == null || qty.signum() <= 0 -> "Invalid quantity"
            s.needsSource && from == null -> "Choose where the stock is taken from"
            s.needsSource && qty > from!!.onHand -> "Only ${from.onHand.toPlainString()} on hand there"
            s.needsDestination && s.form.toLocationId == null -> "Choose a destination location"
            operation == WarehouseOperation.MOVE && from?.locationId == s.form.toLocationId && from?.binId == s.form.toBinId ->
                "Source and destination are the same"
            else -> null
        }
        if (error != null || qty == null) {
            form.update { it.copy(error = error) }
            return
        }
        val productId = s.form.productId!!
        val notes = s.form.notes.takeIf { it.isNotBlank() }
        val draft = when (operation) {
            WarehouseOperation.RECEIVE -> StockDocumentDraft(
                kind = MovementKind.RECEIPT,
                locationId = s.form.toLocationId!!,
                notes = notes,
                lines = listOf(StockLineDraft(productId = productId, quantity = qty, binId = s.form.toBinId)),
            )
            WarehouseOperation.ISSUE -> StockDocumentDraft(
                kind = MovementKind.ISSUE,
                locationId = from!!.locationId,
                notes = notes,
                lines = listOf(
                    StockLineDraft(
                        productId = productId, quantity = qty, binId = from.binId, lotId = from.lotId, condition = from.condition,
                    ),
                ),
            )
            WarehouseOperation.MOVE -> StockDocumentDraft(
                kind = MovementKind.TRANSFER,
                locationId = from!!.locationId,
                toLocationId = s.form.toLocationId,
                notes = notes,
                lines = listOf(
                    StockLineDraft(
                        productId = productId, quantity = qty, binId = from.binId, toBinId = s.form.toBinId,
                        lotId = from.lotId, condition = from.condition,
                    ),
                ),
            )
        }
        viewModelScope.launch {
            form.update { it.copy(isSubmitting = true, error = null) }
            val failure = try {
                inventory.postStockDocument(draft)
                null
            } catch (e: StoreNotReadyException) {
                "Not signed in yet — try again in a moment"
            } catch (e: IllegalArgumentException) {
                e.message
            } catch (e: Exception) {
                Timber.e(e, "Warehouse %s failed", operation)
                e.message ?: "Could not save"
            }
            form.update { it.copy(isSubmitting = false, isDone = failure == null, error = failure) }
        }
    }
}

@HiltViewModel
class WarehouseReceiveViewModel @Inject constructor(
    inventory: InventoryRepository,
    locations: LocationRepository,
    formFillRouter: FormFillRouter,
) : WarehouseStockViewModel(WarehouseOperation.RECEIVE, inventory, locations, formFillRouter, "receive_inventory")

@HiltViewModel
class WarehouseIssueViewModel @Inject constructor(
    inventory: InventoryRepository,
    locations: LocationRepository,
    formFillRouter: FormFillRouter,
) : WarehouseStockViewModel(WarehouseOperation.ISSUE, inventory, locations, formFillRouter, "use_inventory")

@HiltViewModel
class WarehouseMoveViewModel @Inject constructor(
    inventory: InventoryRepository,
    locations: LocationRepository,
    formFillRouter: FormFillRouter,
) : WarehouseStockViewModel(WarehouseOperation.MOVE, inventory, locations, formFillRouter, null)
