package com.avago.feature.assets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.AssetFilter
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val identityManager: IdentityManager,
    assets: AssetRepository,
) : ViewModel() {

    private val assetCountFlow = assets.observeAssets(AssetFilter())
        .map { it.size }
        .catch { e ->
            Timber.e(e, "[OnboardingViewModel] Failed to observe asset count")
            emit(0)
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

    /** True when the motivational quote banner should be visible (disable_quotes == false). */
    val showQuotes: StateFlow<Boolean> = userPreferencesRepository.disableQuotesFlow
        .map { disabled -> !disabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
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
