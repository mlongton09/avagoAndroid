package com.avago.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.FeatureFlags
import com.avago.feature.chat.data.ChatRepository
import com.avago.feature.workorders.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class NavFlagsViewModel @Inject constructor(
    private val featureFlags: FeatureFlags,
    chatRepository: ChatRepository,
    workOrderRepository: WorkOrderRepository,
    identityManager: IdentityManager,
) : ViewModel() {

    val chatEnabled: StateFlow<Boolean> = featureFlags.observeChatEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), featureFlags.chatEnabled)

    val workOrdersEnabled: StateFlow<Boolean> = featureFlags.observeWorkOrdersEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), featureFlags.workOrdersEnabled)

    val purchaseOrdersEnabled: StateFlow<Boolean> = featureFlags.observePurchaseOrdersEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), featureFlags.purchaseOrdersEnabled)

    /**
     * Unread @-mention count for the chat tab pill. Matches iOS
     * ChatTabRootViewController.refreshBadge which shows only mentions
     * (not aggregate unread) on the tab bar icon.
     */
    val unreadChatMentionCount: StateFlow<Int> = chatRepository.observeUnreadMentionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * "Upcoming + mine + now" work order count for the bottom-nav Work
     * Orders pill. Matches iOS MainTabBarController.refreshWoBadge which
     * calls `UnifiedWorkOrdersRepository.upcoming(horizon: .now, scope: .mine)`.
     *
     * Recomputes the 7-day upper bound every 60s via a ticker so the badge
     * stays accurate as time advances without a full UI refresh.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val upcomingMineWorkOrderCount: StateFlow<Int> =
        combine(identityManager.activeAccountId, identityManager.activeUserId, ticker(Duration.ofSeconds(60))) {
            account, user, upper -> Triple(account, user, upper)
        }.flatMapLatest { (account, user, upper) ->
            if (account.isNullOrEmpty() || user.isNullOrEmpty()) flowOf(0)
            else workOrderRepository.observeUpcomingMineCount(account, user, upper)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** Emits "today + 7 days" in epoch ms immediately, then re-emits every [period]. */
private fun ticker(period: Duration): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis() + Duration.ofDays(7).toMillis())
        delay(period.toMillis())
    }
}
