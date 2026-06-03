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
import java.time.Duration
import javax.inject.Inject

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
     * Count of work orders whose due date falls within the "Now" 7-day window:
     * 7 days in the past through 7 days in the future from today. Matches the iOS
     * AVTabStrip badge logic (AssetDetailViewController using horizon: .now).
     * WOs with no due date are excluded — they are not in the window.
     */
    val openCount: StateFlow<Int> = workOrders
        .map { list ->
            val now = System.currentTimeMillis()
            val windowStart = now - Duration.ofDays(7).toMillis()
            val windowEnd   = now + Duration.ofDays(7).toMillis()
            list.count { wo ->
                val due = wo.dueDate ?: return@count false
                due in windowStart..windowEnd
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
