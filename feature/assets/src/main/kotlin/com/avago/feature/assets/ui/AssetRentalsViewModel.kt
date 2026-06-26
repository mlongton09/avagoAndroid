package com.avago.feature.assets.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.CreateRentalRequest
import com.avago.core.network.model.RentalReservation
import com.avago.core.network.model.RentalResponse
import com.avago.core.auth.AccountPreferencesSync
import com.avago.core.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AssetRentalsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serviceClient: AvagoServiceClient,
    private val identityManager: IdentityManager,
    private val accountPreferencesSync: AccountPreferencesSync,
    private val userPrefsRepository: UserPreferencesRepository,
) : ViewModel() {

    private val assetId: String = savedStateHandle["assetId"] ?: ""

    /** The account-level default rate unit for new rentals (e.g. "hour", "day", "week", "month"). */
    val rentalDefaultRateUnit: String
        get() = accountPreferencesSync.prefs.value.rentalDefaultRateUnit

    /** User's preferred currency code, reactive to preference changes. */
    val currencyCode: StateFlow<String> = userPrefsRepository.currencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    private val _rentals = MutableStateFlow<List<RentalResponse>>(emptyList())
    val rentals: StateFlow<List<RentalResponse>> = _rentals.asStateFlow()

    private val _reservations = MutableStateFlow<List<RentalReservation>>(emptyList())
    val reservations: StateFlow<List<RentalReservation>> = _reservations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _startSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val startSuccess: SharedFlow<Unit> = _startSuccess.asSharedFlow()

    private val _endSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val endSuccess: SharedFlow<Unit> = _endSuccess.asSharedFlow()

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
                // Load rentals and reservations in parallel
                val rentalsDeferred = async { serviceClient.getRentalsForAsset(accountId, assetId) }
                val reservationsDeferred = async { serviceClient.getReservationsForAsset(accountId, assetId) }

                when (val result = rentalsDeferred.await()) {
                    is NetworkResult.Success -> {
                        val active = result.data.filter { it.end_at == null }
                            .sortedByDescending { it.start_at }
                        val ended = result.data.filter { it.end_at != null }
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

                when (val result = reservationsDeferred.await()) {
                    is NetworkResult.Success -> {
                        _reservations.value = result.data
                            .filter { it.status != "cancelled" }
                            .sortedBy { it.reserved_from }
                        Timber.d("[AssetRentalsVM] Loaded ${result.data.size} reservations")
                    }
                    is NetworkResult.Error -> {
                        Timber.w("[AssetRentalsVM] Could not load reservations: ${result.message}")
                    }
                    is NetworkResult.Unauthorized -> {
                        Timber.w("[AssetRentalsVM] Unauthorized loading reservations")
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

    fun endRental(rentalId: String, meterEnd: Double? = null, condition: String? = null, conditionNotes: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: run {
                Timber.w("[AssetRentalsVM] No active account — cannot end rental")
                return@launch
            }
            val endAt = Instant.now().toString()
            _error.value = null
            try {
                when (val result = serviceClient.endRental(accountId, rentalId, endAt, meterEnd, condition, conditionNotes)) {
                    is NetworkResult.Success -> {
                        Timber.d("[AssetRentalsVM] Rental $rentalId ended successfully")
                        _endSuccess.emit(Unit)
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

    /** Create an invoice for an ended rental period. */
    fun createInvoice(rentalId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _error.value = null
            try {
                when (val result = serviceClient.createRentalInvoice(accountId, rentalId)) {
                    is NetworkResult.Success -> {
                        Timber.d("[AssetRentalsVM] Invoice created: ${result.data.rental_invoice_id}")
                        // Reload so status changes from "ended" -> "invoiced" are reflected
                        load()
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[AssetRentalsVM] Error creating invoice: ${result.message}")
                        _error.value = result.message
                    }
                    is NetworkResult.Unauthorized -> _error.value = "Unauthorized"
                }
            } catch (e: Exception) {
                Timber.e(e, "[AssetRentalsVM] Exception creating invoice")
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    /** Convert a reservation to an active rental. */
    fun startReservation(reservationId: String, rate: Double, rateUnit: String, meterStart: Double? = null, meterUnit: String? = null, condition: String? = null, conditionNotes: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            _error.value = null
            try {
                when (val result = serviceClient.startReservation(accountId, reservationId, rate, rateUnit, meterStart, meterUnit, condition, conditionNotes)) {
                    is NetworkResult.Success -> {
                        Timber.d("[AssetRentalsVM] Reservation $reservationId started as rental ${result.data.rental_id}")
                        _startSuccess.emit(Unit)
                        load()
                    }
                    is NetworkResult.Error -> {
                        Timber.e("[AssetRentalsVM] Error starting reservation: ${result.message}")
                        _error.value = result.message
                    }
                    is NetworkResult.Unauthorized -> _error.value = "Unauthorized"
                }
            } catch (e: Exception) {
                Timber.e(e, "[AssetRentalsVM] Exception starting reservation")
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
