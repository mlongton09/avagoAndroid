package com.avago.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.FeatureFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NavFlagsViewModel @Inject constructor(
    private val featureFlags: FeatureFlags,
) : ViewModel() {

    val chatEnabled: StateFlow<Boolean> = featureFlags.observeChatEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), featureFlags.chatEnabled)

    val workOrdersEnabled: StateFlow<Boolean> = featureFlags.observeWorkOrdersEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), featureFlags.workOrdersEnabled)

    val purchaseOrdersEnabled: StateFlow<Boolean> = featureFlags.observePurchaseOrdersEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), featureFlags.purchaseOrdersEnabled)
}
