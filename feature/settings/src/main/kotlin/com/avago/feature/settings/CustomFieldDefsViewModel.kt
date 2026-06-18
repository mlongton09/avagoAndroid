package com.avago.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CustomFieldDefsViewModel @Inject constructor(
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _fields = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val fields: StateFlow<List<Map<String, Any>>> = _fields

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load() {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = client.listCustomFieldDefs(accountId)
                if (result is com.avago.core.network.NetworkResult.Success) {
                    _fields.value = (result.data["custom_field_defs"] as? List<*>)
                        ?.filterIsInstance<Map<String, Any>>()
                        ?: emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "loadCustomFieldDefs failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun create(entityType: String, fieldType: String, name: String, options: List<String>) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                val body = buildMap<String, Any> {
                    put("entity_type", entityType)
                    put("field_type", fieldType)
                    put("name", name)
                    if (options.isNotEmpty()) put("options", options)
                }
                client.createCustomFieldDef(accountId, body)
                load()
            } catch (e: Exception) {
                Timber.e(e, "createCustomFieldDef failed")
            }
        }
    }

    fun delete(fieldId: String) {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            try {
                client.deleteCustomFieldDef(accountId, fieldId)
                _fields.value = _fields.value.filter { it["id"] != fieldId }
            } catch (e: Exception) {
                Timber.e(e, "deleteCustomFieldDef failed")
            }
        }
    }
}
