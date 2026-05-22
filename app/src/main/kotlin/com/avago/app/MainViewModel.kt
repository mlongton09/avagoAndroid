package com.avago.app

import androidx.lifecycle.ViewModel
import com.avago.core.sync.ConnectivityMonitor
import com.avago.core.ui.AvagoToast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor,
    val toast: AvagoToast,
) : ViewModel() {

    val isOffline = connectivityMonitor.networkStatus
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}
