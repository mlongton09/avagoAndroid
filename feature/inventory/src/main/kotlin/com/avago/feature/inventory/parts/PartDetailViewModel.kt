package com.avago.feature.inventory.parts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.InventoryEntity
import com.avago.core.data.db.entity.InventoryTransactionEntity
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.StockingLevelEntity
import com.avago.core.data.db.entity.VendorEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.PartBinLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class VendorSource(
    val vendorId: String,
    val vendorName: String,
    val sku: String? = null,
    val unitCost: Double? = null,
    val leadDays: Int? = null,
    val isPreferred: Boolean = false,
)

data class PartDetailUiState(
    val part: PartEntity? = null,
    val manufacturer: String? = null,
    val inventory: InventoryEntity? = null,
    val stockingLevel: StockingLevelEntity? = null,
    val transactions: List<InventoryTransactionEntity> = emptyList(),
    val partTransactions: List<PartTransaction> = emptyList(),
    val vendorSources: List<VendorSource> = emptyList(),
    val isLoading: Boolean = true,
    val showReceiveSheet: Boolean = false,
    val showUseSheet: Boolean = false,
    // Change 144: bin/location info
    val binLocations: List<PartBinLocation> = emptyList(),
    val binLocationsLoading: Boolean = false,
)

@HiltViewModel
class PartDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val partId: String = checkNotNull(savedStateHandle["partId"])

    private val _showReceive = MutableStateFlow(false)
    private val _showUse = MutableStateFlow(false)

    // Change 144: bin locations fetched on init
    private val _binLocations = MutableStateFlow<List<PartBinLocation>>(emptyList())
    private val _binLocationsLoading = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PartDetailUiState> = identityManager.activeAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(PartDetailUiState(isLoading = false))
        else {
            val db = dbFactory.get(accountId)
            @Suppress("UNCHECKED_CAST")
            combine(
                db.partDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.inventoryDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.stockingLevelDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.inventoryTransactionDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                db.vendorDao().observeAll(accountId) as kotlinx.coroutines.flow.Flow<Any?>,
                _showReceive as kotlinx.coroutines.flow.Flow<Any?>,
                _showUse as kotlinx.coroutines.flow.Flow<Any?>,
            ) { v ->
                val parts = v[0] as List<PartEntity>
                val inventories = v[1] as List<InventoryEntity>
                val levels = v[2] as List<StockingLevelEntity>
                val transactions = v[3] as List<InventoryTransactionEntity>
                val vendors = v[4] as List<VendorEntity>
                val showReceive = v[5] as Boolean
                val showUse = v[6] as Boolean

                val part = parts.find { it.partId == partId }
                val inv = inventories.find { it.partId == partId }
                val sl = levels.find { it.partId == partId }
                val txns = transactions
                    .filter { it.partId == partId }
                    .sortedByDescending { it.createdAt }

                // Parse manufacturer from attributes JSON (key "manufacturer")
                val manufacturer = part?.attributes?.let { attrs ->
                    try {
                        val json = org.json.JSONObject(attrs)
                        json.optString("manufacturer").takeIf { it.isNotBlank() }
                    } catch (_: Exception) { null }
                }

                // Map raw entities to PartTransaction for the card
                val partTransactions = txns.map { txn ->
                    PartTransaction(
                        transactionId = txn.transactionId,
                        type = txn.transactionType,
                        quantity = txn.quantity,
                        referenceId = txn.referenceId,
                        notes = txn.notes,
                        createdAt = formatTransactionDate(txn.createdAt),
                    )
                }

                // Build vendor sources from the part's defaultVendorId
                // (No VendorPartSource table exists; derive from the part's default vendor and PO history)
                val vendorSources = buildList {
                    part?.defaultVendorId?.let { vid ->
                        vendors.find { it.vendorId == vid }?.let { vendor ->
                            add(
                                VendorSource(
                                    vendorId = vendor.vendorId,
                                    vendorName = vendor.name,
                                    sku = part.sku,
                                    unitCost = part.cost,
                                    leadDays = null,
                                    isPreferred = true,
                                )
                            )
                        }
                    }
                }

                PartDetailUiState(
                    part = part,
                    manufacturer = manufacturer,
                    inventory = inv,
                    stockingLevel = sl,
                    transactions = txns,
                    partTransactions = partTransactions,
                    vendorSources = vendorSources,
                    isLoading = false,
                    showReceiveSheet = showReceive,
                    showUseSheet = showUse,
                )
            }
        }
    // Change 144: merge bin location state into the UI state
    }.combine(_binLocations) { state, bins ->
        state.copy(binLocations = bins)
    }.combine(_binLocationsLoading) { state, loading ->
        state.copy(binLocationsLoading = loading)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PartDetailUiState(),
    )

    init {
        // Change 144: load bin locations on VM creation
        loadBinLocations()
    }

    private fun loadBinLocations() {
        val accountId = identityManager.getActiveAccountId() ?: return
        viewModelScope.launch {
            _binLocationsLoading.value = true
            try {
                when (val result = serviceClient.getPartBinLocations(accountId, partId)) {
                    is NetworkResult.Success -> _binLocations.value = result.data
                    is NetworkResult.Error -> Timber.w("[PartDetailVM] getPartBinLocations: ${result.message}")
                    is NetworkResult.Unauthorized -> Timber.w("[PartDetailVM] getPartBinLocations: unauthorized")
                }
            } catch (e: Exception) {
                Timber.e(e, "[PartDetailVM] loadBinLocations failed")
            } finally {
                _binLocationsLoading.value = false
            }
        }
    }

    fun openReceive() { _showReceive.value = true }
    fun openUse() { _showUse.value = true }

    /** Alias for openReceive — triggers the receive bottom sheet. */
    fun showReceiveSheet() = openReceive()

    /** Alias for openUse — triggers the use/issue bottom sheet. */
    fun showUseSheet() = openUse()

    fun dismissSheet() {
        _showReceive.value = false
        _showUse.value = false
    }
}
