package com.avago.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.identity.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.KpiSummaryResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class KpiDashboardViewModel @Inject constructor(
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _kpis = MutableStateFlow<KpiSummaryResponse?>(null)
    val kpis: StateFlow<KpiSummaryResponse?> = _kpis

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        load()
    }

    fun load() {
        val accountId = identity.accountId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = client.getReportKPIs(accountId)
                when (result) {
                    is NetworkResult.Success -> _kpis.value = result.data
                    is NetworkResult.Error -> _error.value = result.message
                }
            } catch (e: Exception) {
                Timber.e(e, "loadKpis failed")
                _error.value = "Unable to load KPIs. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
