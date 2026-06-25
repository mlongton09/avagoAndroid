package com.avago.feature.assets.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.CreateReservationRequest
import com.avago.core.network.model.RentalCustomer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RentalBookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val assetId: String = savedStateHandle["assetId"] ?: ""

    private val _customers = MutableStateFlow<List<RentalCustomer>>(emptyList())
    val customers: StateFlow<List<RentalCustomer>> = _customers.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<RentalCustomer?>(null)
    val selectedCustomer: StateFlow<RentalCustomer?> = _selectedCustomer.asStateFlow()

    private val _startDate = MutableStateFlow(LocalDate.now())
    val startDate: StateFlow<LocalDate> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<LocalDate?>(null)
    val endDate: StateFlow<LocalDate?> = _endDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadCustomers()
    }

    private fun loadCustomers() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            when (val result = serviceClient.getRentalCustomers(accountId)) {
                is NetworkResult.Success -> _customers.value = result.data.sortedBy { it.name }
                is NetworkResult.Error -> Timber.w("[RentalBookingVM] Could not load customers: ${result.message}")
                is NetworkResult.Unauthorized -> Timber.w("[RentalBookingVM] Unauthorized loading customers")
            }
        }
    }

    fun refreshCustomers() = loadCustomers()

    fun setSelectedCustomer(customer: RentalCustomer?) { _selectedCustomer.value = customer }
    fun setStartDate(date: LocalDate) { _startDate.value = date }
    fun setEndDate(date: LocalDate?) { _endDate.value = date }

    fun createReservation(request: CreateReservationRequest, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: run {
                _error.value = "No active account"
                _isLoading.value = false
                return@launch
            }
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = serviceClient.createReservation(accountId, request)) {
                    is NetworkResult.Success -> {
                        Timber.d("[RentalBookingVM] Reservation created: ${result.data.reservation_id}")
                        withContext(Dispatchers.Main) { onSuccess() }
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[RentalBookingVM] Error creating reservation: ${result.message}")
                        _error.value = result.message
                    }
                    is NetworkResult.Unauthorized -> {
                        _error.value = "Unauthorized"
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[RentalBookingVM] Exception")
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
