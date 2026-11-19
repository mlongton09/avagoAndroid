package com.avago.feature.log.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Scan result model
// ---------------------------------------------------------------------------

sealed class ScanResult {
    data class AssetResult(
        val assetId: String,
        val assetName: String,
        val assetType: String,
    ) : ScanResult()

    data class PartResult(
        val partId: String,
        val partName: String,
        val sku: String?,
    ) : ScanResult()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class MaintenanceScannerViewModel @Inject constructor(
    private val assets: AssetRepository,
    private val inventory: InventoryRepository,
) : ViewModel() {

    enum class ScanMode { ASSET_TAG, PART_BARCODE }

    private val _scanMode = MutableStateFlow(ScanMode.ASSET_TAG)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var errorClearJob: Job? = null

    fun setScanMode(mode: ScanMode) {
        if (_scanMode.value != mode) {
            _scanMode.value = mode
            clearResult()
        }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (_scanMode.value) {
                    ScanMode.ASSET_TAG -> {
                        val asset = assets.findByCode(barcode)
                        if (asset != null) {
                            _scanResult.value = ScanResult.AssetResult(
                                assetId = asset.id,
                                assetName = asset.name,
                                assetType = asset.assetTypeCode.orEmpty(),
                            )
                        } else {
                            setError("Not found: $barcode")
                        }
                    }
                    ScanMode.PART_BARCODE -> {
                        val product = inventory.productByCode(barcode)
                        if (product != null) {
                            _scanResult.value = ScanResult.PartResult(
                                partId = product.id,
                                partName = product.name,
                                sku = product.sku,
                            )
                        } else {
                            setError("Not found: $barcode")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[MaintenanceScannerViewModel] Error looking up barcode: $barcode")
                setError("Not found: $barcode")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearResult() {
        _scanResult.value = null
        _error.value = null
        errorClearJob?.cancel()
    }

    private fun setError(message: String) {
        _error.value = message
        errorClearJob?.cancel()
        errorClearJob = viewModelScope.launch {
            delay(3_000)
            _error.value = null
        }
    }
}
