package com.avago.core.permissions

import com.avago.core.auth.PermissionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionsManager @Inject constructor(
    private val permissionStore: PermissionStore,
) {
    val permissions: StateFlow<Set<String>> = permissionStore.permissions

    fun can(permission: String): Boolean = permissionStore.hasPermission(permission)

    fun canAny(vararg perms: String): Boolean = perms.any { can(it) }

    fun canAll(vararg perms: String): Boolean = perms.all { can(it) }

    fun observeCan(permission: String): Flow<Boolean> = permissionStore.observePermission(permission)
}
