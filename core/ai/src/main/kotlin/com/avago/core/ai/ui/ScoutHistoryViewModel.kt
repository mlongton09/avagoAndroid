package com.avago.core.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.ai.ScoutRepository
import com.avago.core.data.db.entity.ScoutHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ScoutHistoryViewModel @Inject constructor(
    repository: ScoutRepository,
) : ViewModel() {
    val history: StateFlow<List<ScoutHistoryEntity>> =
        repository.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
