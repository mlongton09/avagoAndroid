package com.avago.feature.assets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.CreateRentalCustomerRequest
import com.avago.core.network.model.RentalCustomer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RentalCustomersViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _customers = MutableStateFlow<List<RentalCustomer>>(emptyList())
    val customers: StateFlow<List<RentalCustomer>> = _customers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = serviceClient.getRentalCustomers(accountId)) {
                    is NetworkResult.Success -> {
                        _customers.value = result.data.sortedBy { it.name }
                        Timber.d("[RentalCustomersVM] Loaded ${result.data.size} customers")
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[RentalCustomersVM] Error: ${result.code} ${result.message}")
                        _error.value = result.message
                    }
                    is NetworkResult.Unauthorized -> {
                        _error.value = "Unauthorized"
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[RentalCustomersVM] Exception")
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

@HiltViewModel
class RentalCustomerFormViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _customer = MutableStateFlow<RentalCustomer?>(null)
    val customer: StateFlow<RentalCustomer?> = _customer.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadCustomer(customerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            try {
                when (val result = serviceClient.getRentalCustomer(accountId, customerId)) {
                    is NetworkResult.Success -> _customer.value = result.data
                    is NetworkResult.Error -> _error.value = result.message
                    is NetworkResult.Unauthorized -> _error.value = "Unauthorized"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun saveCustomer(
        customerId: String?,
        name: String,
        email: String?,
        phone: String?,
        company: String?,
        address: String?,
        notes: String?,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _isSaving.value = true
            _error.value = null
            try {
                val request = CreateRentalCustomerRequest(
                    name = name,
                    email = email?.takeIf { it.isNotBlank() },
                    phone = phone?.takeIf { it.isNotBlank() },
                    company = company?.takeIf { it.isNotBlank() },
                    address = address?.takeIf { it.isNotBlank() },
                    notes = notes?.takeIf { it.isNotBlank() },
                )
                val result = if (customerId == null) {
                    serviceClient.createRentalCustomer(accountId, request)
                } else {
                    serviceClient.updateRentalCustomer(accountId, customerId, request)
                }
                when (result) {
                    is NetworkResult.Success -> {
                        Timber.d("[RentalCustomerFormVM] Saved customer ${result.data.rental_customer_id}")
                        onSuccess()
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[RentalCustomerFormVM] Error saving: ${result.message}")
                        _error.value = result.message
                    }
                    is NetworkResult.Unauthorized -> {
                        _error.value = "Unauthorized"
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[RentalCustomerFormVM] Exception saving")
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
