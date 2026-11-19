package com.avago.feature.inventory.cyclecounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.StockMovement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

data class ScannedItem(
    val partName: String,
    val barcode: String,
    val count: Int,
)

/**
 * Scan-to-count: each scan of a product's code adds one to its count line and records the new
 * total with `stock.cycle_counts.count_line`, so every scan reaches the server through the outbox.
 */
@HiltViewModel
class CycleCountFloorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inventory: InventoryRepository,
) : ViewModel() {

    private val cycleCountId: String = checkNotNull(savedStateHandle["cycleCountId"])

    val count: StateFlow<StockMovement?> = inventory.observeMovement(cycleCountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _recentScans = MutableStateFlow<List<ScannedItem>>(emptyList())
    val recentScans: StateFlow<List<ScannedItem>> = _recentScans.asStateFlow()

    private val _lastScannedBarcode = MutableStateFlow<String?>(null)
    val lastScannedBarcode: StateFlow<String?> = _lastScannedBarcode.asStateFlow()

    private val _scanFeedback = MutableStateFlow<String?>(null)
    val scanFeedback: StateFlow<String?> = _scanFeedback.asStateFlow()

    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()

    // Rapid scans of the same code must each add one; serialise read-modify-write.
    private val scanLock = Mutex()
    private var feedbackJob: Job? = null

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _lastScannedBarcode.value = barcode
            try {
                scanLock.withLock { countScan(barcode) }
            } catch (e: Exception) {
                Timber.e(e, "CycleCountFloorViewModel: scan of $barcode failed")
                showFeedback(e.message ?: "Error scanning barcode")
            }
        }
    }

    private suspend fun countScan(barcode: String) {
        val product = inventory.productByCode(barcode)
        if (product == null) {
            showFeedback("Barcode not found: $barcode")
            return
        }
        val current = inventory.observeMovement(cycleCountId).first()
        val line = current?.lines?.firstOrNull { it.productId == product.id }
        if (line == null) {
            showFeedback("${product.name} is not part of this count")
            return
        }
        val newQty = (line.countedQty ?: BigDecimal.ZERO) + BigDecimal.ONE
        inventory.recordCount(line.id, newQty)

        val scans = _recentScans.value.toMutableList()
        val existing = scans.indexOfFirst { it.barcode == barcode }
        val item = if (existing >= 0) scans.removeAt(existing).let { it.copy(count = it.count + 1) }
        else ScannedItem(partName = product.name, barcode = barcode, count = 1)
        scans.add(0, item)
        _recentScans.value = scans.take(5)
        showFeedback("${product.name}: ${newQty.stripTrailingZeros().toPlainString()}")
    }

    fun markComplete(onComplete: () -> Unit) {
        if (_isCompleting.value) return
        viewModelScope.launch {
            _isCompleting.value = true
            try {
                scanLock.withLock { inventory.completeCycleCount(cycleCountId) }
                onComplete()
            } catch (e: Exception) {
                Timber.e(e, "CycleCountFloorViewModel: complete failed for $cycleCountId")
                showFeedback(e.message ?: "Error completing cycle count")
            } finally {
                _isCompleting.value = false
            }
        }
    }

    private fun showFeedback(message: String) {
        _scanFeedback.value = message
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(2_000)
            _scanFeedback.value = null
        }
    }
}
