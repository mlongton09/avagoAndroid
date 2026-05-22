package com.avago.feature.log.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
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
    private val databaseFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
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
        val accountId = identityManager.getActiveAccountId() ?: run {
            Timber.w("[MaintenanceScannerViewModel] No active account — ignoring scan")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val db = databaseFactory.get(accountId)
                when (_scanMode.value) {
                    ScanMode.ASSET_TAG -> {
                        val asset = db.assetDao().getByBarcode(barcode)
                        if (asset != null) {
                            _scanResult.value = ScanResult.AssetResult(
                                assetId = asset.assetId,
                                assetName = asset.name,
                                assetType = asset.assetType ?: "",
                            )
                            Timber.d("[MaintenanceScannerViewModel] Asset found: ${asset.assetId}")
                        } else {
                            setError("Not found: $barcode")
                        }
                    }
                    ScanMode.PART_BARCODE -> {
                        val part = db.partDao().getByBarcode(barcode)
                        if (part != null) {
                            _scanResult.value = ScanResult.PartResult(
                                partId = part.partId,
                                partName = part.name,
                                sku = part.sku,
                            )
                            Timber.d("[MaintenanceScannerViewModel] Part found: ${part.partId}")
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
