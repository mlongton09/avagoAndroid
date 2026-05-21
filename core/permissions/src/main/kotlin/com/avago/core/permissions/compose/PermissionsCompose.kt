package com.avago.core.permissions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Returns true if the current user has the given [permission].
 *
 * This composable recomposes automatically whenever the permission set changes
 * (e.g. after a background sync).
 *
 * Usage:
 * ```kotlin
 * val canEdit = rememberCanPermission(Permissions.ASSETS_EDIT)
 * if (canEdit) { EditButton() }
 * ```
 */
@Composable
fun rememberCanPermission(permission: String): Boolean {
    val viewModel = hiltViewModel<PermissionsViewModel>()
    return viewModel.permissions.collectAsState().value.contains(permission)
}

/**
 * Returns the full set of granted permissions as reactive Compose state.
 *
 * Prefer [rememberCanPermission] for single-key checks; use this when you need
 * to test multiple permissions in one composable without creating multiple
 * ViewModels.
 */
@Composable
fun rememberPermissions(): Set<String> {
    val viewModel = hiltViewModel<PermissionsViewModel>()
    return viewModel.permissions.collectAsState().value
}
