package com.avago.feature.assets.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.PayInvoiceRequest
import com.avago.core.network.model.RentalInvoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RentalInvoiceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val invoiceId: String = savedStateHandle["invoiceId"] ?: ""

    private val _invoice = MutableStateFlow<RentalInvoice?>(null)
    val invoice: StateFlow<RentalInvoice?> = _invoice.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadInvoice()
    }

    private fun loadInvoice() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = serviceClient.getRentalInvoice(accountId, invoiceId)) {
                    is NetworkResult.Success -> {
                        _invoice.value = result.data
                        Timber.d("[RentalInvoiceVM] Loaded invoice $invoiceId")
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[RentalInvoiceVM] Error loading: ${result.message}")
                        _error.value = result.message
                    }
                    is NetworkResult.Unauthorized -> _error.value = "Unauthorized"
                }
            } catch (e: Exception) {
                Timber.e(e, "[RentalInvoiceVM] Exception loading invoice")
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendInvoice() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _isLoading.value = true
            try {
                when (val result = serviceClient.sendRentalInvoice(accountId, invoiceId)) {
                    is NetworkResult.Success -> {
                        _invoice.value = result.data
                        Timber.d("[RentalInvoiceVM] Invoice sent")
                    }
                    is NetworkResult.Error -> _error.value = result.message
                    is NetworkResult.Unauthorized -> _error.value = "Unauthorized"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun payInvoice(paymentMethod: String?, paymentNotes: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _isLoading.value = true
            try {
                val request = PayInvoiceRequest(
                    payment_method = paymentMethod,
                    payment_notes = paymentNotes,
                )
                when (val result = serviceClient.payRentalInvoice(accountId, invoiceId, request)) {
                    is NetworkResult.Success -> {
                        _invoice.value = result.data
                        Timber.d("[RentalInvoiceVM] Invoice paid")
                    }
                    is NetworkResult.Error -> _error.value = result.message
                    is NetworkResult.Unauthorized -> _error.value = "Unauthorized"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun voidInvoice() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _isLoading.value = true
            try {
                when (val result = serviceClient.voidRentalInvoice(accountId, invoiceId)) {
                    is NetworkResult.Success -> {
                        _invoice.value = result.data
                        Timber.d("[RentalInvoiceVM] Invoice voided")
                    }
                    is NetworkResult.Error -> _error.value = result.message
                    is NetworkResult.Unauthorized -> _error.value = "Unauthorized"
                }
            } catch (e: Exception) {
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
