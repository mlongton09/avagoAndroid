package com.avago.feature.assets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.repository.AssetRepository
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.sync.SyncEngine
import com.avago.core.sync.SyncGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val identityManager: IdentityManager,
    private val assetRepository: AssetRepository,
    private val syncGate: SyncGate,
    private val client: AvagoServiceClient,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    init {
        viewModelScope.launch {
            // Mirror iOS seedFRESampleDataIfNeeded: once the initial sync finishes
            // (SyncGate opens), if the account has no assets, clone the server's
            // demo account so the user sees real data on first launch.
            syncGate.awaitOpen()
            val accountId = identityManager.getActiveAccountId() ?: return@launch
            val count = try {
                assetRepository.observeAssets(accountId).first().size
            } catch (e: Exception) {
                Timber.w(e, "[FRE] Could not read asset count")
                return@launch
            }
            if (count > 0) return@launch
            Timber.d("[FRE] Account $accountId has no assets — requesting sample data")
            val result = client.loadSampleData(accountId)
            if (result is NetworkResult.Success) {
                Timber.d("[FRE] Sample data loaded, triggering follow-up sync")
                syncEngine.sync()
            } else {
                Timber.w("[FRE] load-sample-data failed: $result")
            }
        }
    }

    /**
     * The asset count for the currently active account, reactive to account changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val assetCountFlow = identityManager.activeAccountId
        .flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(0)
            } else {
                try {
                    assetRepository.observeAssets(accountId).map { it.size }
                } catch (e: Exception) {
                    Timber.e(e, "[OnboardingViewModel] Failed to observe asset count for $accountId")
                    flowOf(0)
                }
            }
        }

    /**
     * True when the FRE banner should be visible:
     *  - user is signed in (non-null accountId)
     *  - fre_dismissed == false in DataStore
     *  - asset count == 0
     */
    val showBanner: StateFlow<Boolean> = combine(
        identityManager.activeAccountId,
        userPreferencesRepository.freDismissedFlow,
        assetCountFlow,
    ) { accountId, dismissed, count ->
        accountId != null && !dismissed && count == 0
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    /**
     * True when the full onboarding screen should be shown:
     *  - user is signed in
     *  - fre_completed == false in DataStore
     */
    val showOnboardingScreen: StateFlow<Boolean> = combine(
        identityManager.activeAccountId,
        userPreferencesRepository.freCompletedFlow,
    ) { accountId, completed ->
        accountId != null && !completed
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    /** Stores fre_dismissed = true and hides the banner permanently. */
    fun dismiss() {
        viewModelScope.launch {
            try {
                userPreferencesRepository.setFreDismissed()
                Timber.d("[OnboardingViewModel] FRE banner dismissed")
            } catch (e: Exception) {
                Timber.e(e, "[OnboardingViewModel] Failed to persist fre_dismissed")
            }
        }
    }

    /** Marks the full onboarding flow as complete. */
    fun completeOnboarding() {
        viewModelScope.launch {
            try {
                userPreferencesRepository.setFreCompleted()
                Timber.d("[OnboardingViewModel] Onboarding completed")
            } catch (e: Exception) {
                Timber.e(e, "[OnboardingViewModel] Failed to persist fre_completed")
            }
        }
    }
}
