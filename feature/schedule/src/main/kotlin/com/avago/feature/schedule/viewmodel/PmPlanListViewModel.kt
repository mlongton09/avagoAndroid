package com.avago.feature.schedule.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.PmPlan
import com.avago.core.data.domain.PmRepository
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The PM plans of the account, or of one asset (`assetId`). Null while loading. */
@HiltViewModel
class PmPlanListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    pm: PmRepository,
    permissions: PermissionsManager,
) : ViewModel() {

    val assetId: String? = savedStateHandle.get<String>("assetId")?.takeIf { it.isNotBlank() }

    val plans: StateFlow<List<PmPlan>?> = pm.observePlans(assetId)
        .map<List<PmPlan>, List<PmPlan>?> { list -> list.sortedWith(compareBy<PmPlan> { !it.active }.thenBy { it.title.lowercase() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canManage: StateFlow<Boolean> = permissions.observeCan(Permissions.WO_MANAGE_RECURRING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissions.can(Permissions.WO_MANAGE_RECURRING))
}
