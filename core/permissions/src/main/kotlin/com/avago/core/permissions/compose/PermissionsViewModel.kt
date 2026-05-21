package com.avago.core.permissions.compose

import androidx.lifecycle.ViewModel
import com.avago.core.permissions.PermissionsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin ViewModel wrapper that exposes [PermissionsManager.permissions] to Compose.
 *
 * Obtain via `hiltViewModel<PermissionsViewModel>()` or use the
 * [rememberCanPermission] / [rememberPermissions] helpers defined in
 * [PermissionsCompose].
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    /** All currently granted permission keys. Backed by [PermissionsManager]. */
    val permissions: StateFlow<Set<String>> = permissionsManager.permissions
}
