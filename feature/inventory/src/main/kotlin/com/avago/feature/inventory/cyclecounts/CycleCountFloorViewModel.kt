package com.avago.feature.inventory.cyclecounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.network.AvagoServiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ScannedItem(
    val partName: String,
    val barcode: String,
    val count: Int,
)

@HiltViewModel
class CycleCountFloorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val databaseFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val cycleCountId: String = checkNotNull(savedStateHandle["cycleCountId"])

    private val _recentScans = MutableStateFlow<List<ScannedItem>>(emptyList())
    val recentScans: StateFlow<List<ScannedItem>> = _recentScans.asStateFlow()

    private val _lastScannedBarcode = MutableStateFlow<String?>(null)
    val lastScannedBarcode: StateFlow<String?> = _lastScannedBarcode.asStateFlow()

    private val _scanFeedback = MutableStateFlow<String?>(null)
    val scanFeedback: StateFlow<String?> = _scanFeedback.asStateFlow()

    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting.asStateFlow()

    fun onBarcodeScanned(barcode: String) {
        val accountId = identityManager.getActiveAccountId() ?: return

        viewModelScope.launch {
            _lastScannedBarcode.value = barcode
            Timber.d("[CycleCountFloorViewModel] Barcode scanned: $barcode for count: $cycleCountId")

            try {
                val db = databaseFactory.get(accountId)
                val part = db.partDao().getByBarcode(barcode)

                if (part == null) {
                    Timber.d("[CycleCountFloorViewModel] Part not found for barcode: $barcode")
                    _scanFeedback.value = "Barcode not found: $barcode"
                    clearFeedbackAfterDelay()
                    return@launch
                }

                // Look up the cycle count line for this part — take a one-shot snapshot
                val allLines = db.cycleCountLineDao().observeAll(accountId).first()
                val line = allLines.find { it.cycleCountId == cycleCountId && it.partId == part.partId }

                if (line == null) {
                    Timber.d("[CycleCountFloorViewModel] No cycle count line found for part: ${part.partId}")
                    _scanFeedback.value = "Barcode not found: $barcode"
                    clearFeedbackAfterDelay()
                    return@launch
                }

                // Increment the counted quantity
                val now = System.currentTimeMillis()
                val newQty = (line.countedQty ?: 0.0) + 1.0
                val updatedLine = line.copy(
                    countedQty = newQty,
                    isCounted = true,
                    countedAt = now,
                    countedBy = accountId,
                    updatedAt = now,
                )
                db.cycleCountLineDao().upsert(updatedLine)
                Timber.d("[CycleCountFloorViewModel] Updated counted qty to $newQty for part ${part.name}")

                // Update recent scans list (keep last 5, deduplicate by barcode updating count)
                val currentScans = _recentScans.value.toMutableList()
                val existingIndex = currentScans.indexOfFirst { it.barcode == barcode }
                if (existingIndex >= 0) {
                    currentScans[existingIndex] = currentScans[existingIndex].copy(
                        count = currentScans[existingIndex].count + 1,
                    )
                    // Move to front
                    val updated = currentScans.removeAt(existingIndex)
                    currentScans.add(0, updated)
                } else {
                    currentScans.add(0, ScannedItem(partName = part.name, barcode = barcode, count = 1))
                }
                _recentScans.value = currentScans.take(5)

                _scanFeedback.value = "${part.name}: +1"
                clearFeedbackAfterDelay()
            } catch (e: Exception) {
                Timber.e(e, "[CycleCountFloorViewModel] Error processing barcode: $barcode")
                _scanFeedback.value = "Error scanning barcode"
                clearFeedbackAfterDelay()
            }
        }
    }


    fun markComplete(onComplete: () -> Unit) {
        val accountId = identityManager.getActiveAccountId() ?: return
        if (_isCompleting.value) return

        viewModelScope.launch {
            _isCompleting.value = true
            try {
                serviceClient.completeCycleCount(accountId, cycleCountId)
                onComplete()
            } catch (e: Exception) {
                Timber.e(e, "[CycleCountFloorViewModel] Complete failed for count: $cycleCountId")
                _scanFeedback.value = e.message ?: "Error completing cycle count"
                clearFeedbackAfterDelay()
            } finally {
                _isCompleting.value = false
            }
        }
    }

    private fun clearFeedbackAfterDelay() {
        viewModelScope.launch {
            delay(2_000)
            _scanFeedback.value = null
        }
    }
}
