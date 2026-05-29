package com.avago.core.permissions

import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.sync.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionsManager @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _permissions = MutableStateFlow<Set<String>>(emptySet())

    /** Live set of all granted permission keys for the active account. */
    val permissions: StateFlow<Set<String>> = _permissions.asStateFlow()

    init {
        scope.launch {
            identity.activeAccountId
                .filterNotNull()
                .collectLatest { accountId ->
                    val db = dbFactory.get(accountId)
                    db.accountRolePermissionsDao().observeAll(accountId).collectLatest { entities ->
                        val resolved = resolvePermissions(accountId, entities.firstOrNull()?.roleKey, entities)
                        _permissions.value = resolved
                        Timber.d("PermissionsManager: resolved ${resolved.size} permissions for $accountId")
                    }
                }
        }
    }

    fun can(permission: String): Boolean = _permissions.value.contains(permission)

    fun canAny(vararg perms: String): Boolean = perms.any { can(it) }

    fun canAll(vararg perms: String): Boolean = perms.all { can(it) }

    /** Returns a Flow that emits true whenever the active permission set contains [permission]. */
    fun observeCan(permission: String): kotlinx.coroutines.flow.Flow<Boolean> =
        permissions.map { it.contains(permission) }

    private suspend fun resolvePermissions(
        accountId: String,
        roleKey: String?,
        entities: List<com.avago.core.data.db.entity.AccountRolePermissionsEntity>,
    ): Set<String> {
        val accountPerms = entities
            .flatMap { it.permissions.splitPermissions() }
            .toMutableSet()

        if (!roleKey.isNullOrBlank()) {
            val defaults = runCatching {
                dbFactory.get(accountId).rolePermissionDefaultsDao().getById(roleKey)
            }.getOrNull()

            if (defaults != null) {
                accountPerms += defaults.permissions.splitPermissions()
            }
        }

        return accountPerms
    }

    private fun String.splitPermissions(): Set<String> =
        split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
