package com.avago.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private val Context.permissionDataStore by preferencesDataStore(name = "rbac_permissions")

@Singleton
class PermissionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceClientProvider: Provider<AvagoServiceClient>,
) {
    private val _permissions = MutableStateFlow<Set<String>>(emptySet())
    val permissions: StateFlow<Set<String>> = _permissions.asStateFlow()

    private val _isRoot = MutableStateFlow(false)
    val isRoot: StateFlow<Boolean> = _isRoot.asStateFlow()

    private var activeAccountId: String? = null
    private var activeRole: String? = null

    fun hasPermission(key: String): Boolean = _isRoot.value || _permissions.value.contains(key)

    fun observePermission(key: String): Flow<Boolean> =
        combine(_isRoot, _permissions) { isRoot, permissions -> isRoot || permissions.contains(key) }

    suspend fun activate(accountId: String?, role: String?, isRoot: Boolean = role == "root") = withContext(Dispatchers.IO) {
        activeAccountId = accountId
        activeRole = role
        _isRoot.value = isRoot
        if (accountId == null) {
            _permissions.value = emptySet()
            return@withContext
        }
        _permissions.value = readPersisted(accountId)
    }

    suspend fun refresh(accountId: String? = activeAccountId, role: String? = activeRole) = withContext(Dispatchers.IO) {
        if (accountId.isNullOrBlank()) return@withContext
        if (_isRoot.value) return@withContext

        when (val result = serviceClientProvider.get().pullRolePermissions(accountId)) {
            is NetworkResult.Success -> {
                val matrix = result.data.matrix
                val roleKey = role?.takeIf { it.isNotBlank() } ?: return@withContext
                // Server matrix is keyed by permission → { role → allowed }.
                // Collect all permission keys where the current role is allowed.
                val resolved = matrix.entries
                    .filter { (_, roles) -> roles[roleKey] == true }
                    .map { (permKey, _) -> permKey }
                    .toSet()
                _permissions.value = resolved
                persist(accountId, resolved)
                Timber.d("PermissionStore: refreshed ${resolved.size} permissions for $accountId role=$roleKey")
            }
            is NetworkResult.Error -> {
                Timber.w("PermissionStore: permission refresh failed for $accountId: ${result.message}")
            }
            is NetworkResult.Unauthorized -> {
                Timber.w("PermissionStore: permission refresh unauthorized for $accountId")
            }
        }
    }

    suspend fun clear(accountId: String?) = withContext(Dispatchers.IO) {
        if (activeAccountId == accountId) {
            activeAccountId = null
            activeRole = null
            _isRoot.value = false
            _permissions.value = emptySet()
        }
        if (!accountId.isNullOrBlank()) {
            context.permissionDataStore.edit { prefs -> prefs.remove(permissionKey(accountId)) }
        }
    }

    private suspend fun readPersisted(accountId: String): Set<String> =
        context.permissionDataStore.data.map { prefs -> prefs[permissionKey(accountId)] ?: emptySet() }.first()

    private suspend fun persist(accountId: String, permissions: Set<String>) {
        context.permissionDataStore.edit { prefs -> prefs[permissionKey(accountId)] = permissions }
    }

    private fun permissionKey(accountId: String) = stringSetPreferencesKey("${accountId}_permissions")
}
