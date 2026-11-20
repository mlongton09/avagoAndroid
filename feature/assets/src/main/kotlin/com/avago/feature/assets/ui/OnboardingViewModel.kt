package com.avago.feature.assets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.AssetFilter
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.SyncRepository
import com.avago.core.data.repository.UserPreferencesRepository
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.assets.data.SampleDataService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface SampleDataState {
    data object Idle : SampleDataState
    data object Loading : SampleDataState
    data class Failed(val error: ApiError) : SampleDataState
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val identityManager: IdentityManager,
    assets: AssetRepository,
    private val sampleData: SampleDataService,
    private val sync: SyncRepository,
    permissions: PermissionsManager,
) : ViewModel() {

    /** Loading sample data adds rows to the account, so it is offered to users who can create assets. */
    val canLoadSampleData: StateFlow<Boolean> = permissions.observeCan(Permissions.ASSETS_CREATE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissions.can(Permissions.ASSETS_CREATE))

    private val _sampleData = MutableStateFlow<SampleDataState>(SampleDataState.Idle)
    val sampleDataState: StateFlow<SampleDataState> = _sampleData.asStateFlow()

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

    /**
     * Clones the server's demo data into the account (`POST load-sample-data`), then syncs so the
     * new rows show; the welcome overlay hides once assets exist.
     */
    fun loadSampleData() {
        if (_sampleData.value == SampleDataState.Loading) return
        _sampleData.value = SampleDataState.Loading
        viewModelScope.launch {
            _sampleData.value = when (val r = sampleData.load()) {
                is ApiResult.Ok -> {
                    if (!sync.syncNow()) Timber.w("[OnboardingViewModel] sync after sample data did not complete")
                    SampleDataState.Idle
                }
                is ApiResult.Err -> SampleDataState.Failed(r.error)
            }
        }
    }

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
