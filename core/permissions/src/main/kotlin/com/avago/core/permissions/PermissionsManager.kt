package com.avago.core.permissions

import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.AccountRolePermissionsDao
import com.avago.core.data.db.dao.RolePermissionDefaultsDao
import com.avago.core.sync.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton cache of the active account's resolved permissions.
 *
 * Resolution order (union):
 *  1. `AccountRolePermissionsEntity.permissions` — account-level overrides stored by the sync engine.
 *  2. `RolePermissionDefaultsEntity.permissions` — server-vended defaults for the account's role.
 *
 * The cache is updated automatically whenever the Room rows change, so callers can
 * query [can] synchronously without a coroutine.
 */
@Singleton
class PermissionsManager @Inject constructor(
    private val accountRolePermissionsDao: AccountRolePermissionsDao,
    private val rolePermissionDefaultsDao: RolePermissionDefaultsDao,
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
                    accountRolePermissionsDao.observeAll(accountId).collectLatest { entities ->
                        val resolved = resolvePermissions(entities.firstOrNull()?.roleKey, entities)
                        _permissions.value = resolved
                        Timber.d("PermissionsManager: resolved ${resolved.size} permissions for $accountId")
                    }
                }
        }
    }

    /**
     * Synchronous check — safe to call from any thread, including the main thread.
     */
    fun can(permission: String): Boolean = _permissions.value.contains(permission)

    /**
     * Returns true if the user has **any** of the supplied permissions.
     */
    fun canAny(vararg perms: String): Boolean = perms.any { can(it) }

    /**
     * Returns true only if the user has **all** of the supplied permissions.
     */
    fun canAll(vararg perms: String): Boolean = perms.all { can(it) }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Merges account-level overrides with role defaults.
     *
     * The `permissions` column in both entities is a comma-delimited string
     * (e.g. `"assets.view,assets.edit,work_orders.view"`).  We split, trim,
     * and union them so that role defaults fill in anything the account row
     * omits.
     */
    private suspend fun resolvePermissions(
        roleKey: String?,
        entities: List<com.avago.core.data.db.entity.AccountRolePermissionsEntity>,
    ): Set<String> {
        // Gather account-level permissions from all rows for this account
        // (normally there is exactly one row, but we union all to be safe).
        val accountPerms = entities
            .flatMap { it.permissions.splitPermissions() }
            .toMutableSet()

        // Merge in role defaults if a roleKey is available
        if (!roleKey.isNullOrBlank()) {
            val defaults = runCatching {
                rolePermissionDefaultsDao.getById(roleKey)
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
