package com.avago.feature.assets.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.CreateRentalRequest
import com.avago.core.network.model.RentalResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AssetRentalsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val assetId: String = savedStateHandle["assetId"] ?: ""

    private val _rentals = MutableStateFlow<List<RentalResponse>>(emptyList())
    val rentals: StateFlow<List<RentalResponse>> = _rentals.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: run {
                Timber.w("[AssetRentalsVM] No active account — cannot load rentals")
                return@launch
            }
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = serviceClient.getRentalsForAsset(accountId, assetId)) {
                    is NetworkResult.Success -> {
                        // Active first, then ended; within each group newest start_at first
                        val active = result.data.filter { it.status == "active" }
                            .sortedByDescending { it.start_at }
                        val ended = result.data.filter { it.status != "active" }
                            .sortedByDescending { it.start_at }
                        _rentals.value = active + ended
                        Timber.d("[AssetRentalsVM] Loaded ${result.data.size} rentals for asset $assetId")
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[AssetRentalsVM] Error loading rentals: ${result.code} ${result.message}")
                        _error.value = result.message
                    }
                    is NetworkResult.Unauthorized -> {
                        Timber.w("[AssetRentalsVM] Unauthorized loading rentals")
                        _error.value = "Unauthorized"
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[AssetRentalsVM] Exception loading rentals")
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun endRental(rentalId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: run {
                Timber.w("[AssetRentalsVM] No active account — cannot end rental")
                return@launch
            }
            val endAt = Instant.now().toString()
            _error.value = null
            try {
                when (val result = serviceClient.endRental(accountId, rentalId, endAt)) {
                    is NetworkResult.Success -> {
                        Timber.d("[AssetRentalsVM] Rental $rentalId ended successfully")
                        load()
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[AssetRentalsVM] Error ending rental $rentalId: ${result.code} ${result.message}")
                        _error.value = result.message
                    }
                    is NetworkResult.Unauthorized -> {
                        Timber.w("[AssetRentalsVM] Unauthorized ending rental")
                        _error.value = "Unauthorized"
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[AssetRentalsVM] Exception ending rental $rentalId")
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun createRental(request: CreateRentalRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: run {
                Timber.w("[AssetRentalsVM] No active account — cannot create rental")
                return@launch
            }
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = serviceClient.createRental(accountId, request)) {
                    is NetworkResult.Success -> {
                        Timber.d("[AssetRentalsVM] Rental created: ${result.data.rental_id}")
                        load()
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[AssetRentalsVM] Error creating rental: ${result.code} ${result.message}")
                        _error.value = result.message
                        _isLoading.value = false
                    }
                    is NetworkResult.Unauthorized -> {
                        Timber.w("[AssetRentalsVM] Unauthorized creating rental")
                        _error.value = "Unauthorized"
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[AssetRentalsVM] Exception creating rental")
                _error.value = e.message ?: "Unknown error"
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
