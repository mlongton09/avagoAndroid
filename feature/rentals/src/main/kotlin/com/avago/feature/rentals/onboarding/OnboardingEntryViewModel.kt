package com.avago.feature.rentals.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.PermissionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val STOP_OBSERVING_MS = 5000L

/** Entry visibility reads existing RBAC only; it neither enables rentals nor loads the wizard APIs. */
@HiltViewModel
class OnboardingEntryViewModel @Inject constructor(permissions: PermissionStore) : ViewModel() {
    val allowed = combine(permissions.permissions, permissions.isRoot) { _, _ ->
        listOf(
            "rental.view", "rental.catalog", "rental.settings", "rental.portal_manage",
            "rental.marketplace_manage", "assets.create",
        ).all(permissions::hasPermission)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_OBSERVING_MS), false)
}
