package com.avago.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.PmPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PmPlansViewModel @Inject constructor(
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _plans = MutableStateFlow<List<PmPlan>>(emptyList())
    val plans: StateFlow<List<PmPlan>> = _plans

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(assetId: String) {
        val accountId = identity.activeAccountId.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = client.listPmPlans(accountId)) {
                    is NetworkResult.Success ->
                        _plans.value = result.data.filter { it.asset_id == assetId }
                    is NetworkResult.Error ->
                        Timber.e("listPmPlans error: ${result.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "listPmPlans failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlan(planId: String) {
        val accountId = identity.activeAccountId.value ?: return
        viewModelScope.launch {
            try {
                client.deletePmPlan(accountId, planId)
                _plans.value = _plans.value.filter { it.id != planId }
            } catch (e: Exception) {
                Timber.e(e, "deletePmPlan failed")
            }
        }
    }
}
