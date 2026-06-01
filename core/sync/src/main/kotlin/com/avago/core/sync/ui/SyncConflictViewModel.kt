package com.avago.core.sync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.sync.SyncConflict
import com.avago.core.sync.SyncConflictCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncConflictViewModel @Inject constructor(
    private val coordinator: SyncConflictCoordinator,
) : ViewModel() {

    val conflicts: StateFlow<List<SyncConflict>> = coordinator.conflicts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun keepLocal(conflict: SyncConflict) {
        viewModelScope.launch { coordinator.keepLocal(conflict) }
    }

    fun acceptServer(conflict: SyncConflict) {
        viewModelScope.launch { coordinator.acceptServer(conflict) }
    }

    fun keepAllLocal() {
        viewModelScope.launch { coordinator.keepAllLocal() }
    }

    fun acceptAllServer() {
        viewModelScope.launch { coordinator.acceptAllServer() }
    }

    fun dismiss(conflict: SyncConflict) {
        coordinator.removeConflict(conflict.queueId)
    }
}
