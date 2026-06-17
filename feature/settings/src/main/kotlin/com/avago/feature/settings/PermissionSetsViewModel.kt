package com.avago.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.PermissionSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PermissionSetsViewModel @Inject constructor(
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _permissionSets = MutableStateFlow<List<PermissionSet>>(emptyList())
    val permissionSets: StateFlow<List<PermissionSet>> = _permissionSets

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load() {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = client.listPermissionSets(accountId)
                if (result is NetworkResult.Success) {
                    _permissionSets.value = result.data
                }
            } catch (e: Exception) {
                Timber.e(e, "loadPermissionSets failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun create(name: String, permissions: List<String>) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val body = mapOf("name" to name, "permissions" to permissions)
                client.createPermissionSet(accountId, body)
                load()
            } catch (e: Exception) {
                Timber.e(e, "createPermissionSet failed")
            }
        }
    }

    fun delete(setId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                client.deletePermissionSet(accountId, setId)
                _permissionSets.value = _permissionSets.value.filter { it.id != setId }
            } catch (e: Exception) {
                Timber.e(e, "deletePermissionSet failed")
            }
        }
    }
}
