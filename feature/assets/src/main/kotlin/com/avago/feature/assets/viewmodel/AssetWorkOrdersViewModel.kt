package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.WorkOrderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

/** Statuses that count as "open" (not yet resolved) for badge display purposes. */
private val OPEN_WO_STATUSES = setOf("open", "assigned", "in_progress", "on_hold", "pending_parts")

@HiltViewModel
class AssetWorkOrdersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val assetId: String = requireNotNull(savedStateHandle["assetId"]) {
        "AssetWorkOrdersViewModel requires assetId in SavedStateHandle"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val workOrders: StateFlow<List<WorkOrderEntity>> = identityManager.activeAccountId
        .flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList())
            else dbFactory.get(accountId).workOrderDao().observeByAsset(assetId)
                .catch { e ->
                    Timber.e(e, "[AssetWorkOrdersVM] flow error for asset $assetId")
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Count of work orders that are currently active (not completed or cancelled).
     * Mirrors the open-WO pill badge iOS shows on the Work Orders tab strip in
     * AssetDetailViewController (AVTabStrip.setBadge(at:count:)).
     */
    val openCount: StateFlow<Int> = workOrders
        .map { list -> list.count { it.status in OPEN_WO_STATUSES } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
