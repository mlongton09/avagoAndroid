package com.avago.feature.workorders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SignatureCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val identityManager: IdentityManager,
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val woId: String = requireNotNull(savedStateHandle["woId"])

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _savedPhotoUrl = MutableStateFlow<String?>(null)
    val savedPhotoUrl: StateFlow<String?> = _savedPhotoUrl.asStateFlow()

    fun saveSignature(bytes: ByteArray) {
        val accountId = identityManager.getActiveAccountId() ?: run {
            _error.value = "No active account"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                val photoId = UUID.randomUUID().toString()
                val uploadUrlResponse = when (
                    val result = serviceClient.getPhotoUploadUrl(
                        accountId = accountId,
                        photoId = photoId,
                        entityId = woId,
                        entityType = "work_order",
                    )
                ) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Error -> {
                        _error.value = result.message ?: "Failed to prepare upload"
                        return@launch
                    }
                    is NetworkResult.Unauthorized -> {
                        _error.value = "Unauthorized"
                        return@launch
                    }
                }

                when (val uploadResult = serviceClient.uploadPhotoBinary(uploadUrlResponse.upload_url, bytes)) {
                    is NetworkResult.Success -> {
                        _savedPhotoUrl.value = uploadUrlResponse.upload_url.substringBefore('?')
                    }
                    is NetworkResult.Error -> {
                        _error.value = uploadResult.message ?: "Failed to upload signature"
                    }
                    is NetworkResult.Unauthorized -> {
                        _error.value = "Unauthorized"
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[SignatureCaptureVM] saveSignature failed")
                _error.value = "Failed to upload signature"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onSavedHandled() {
        _savedPhotoUrl.value = null
    }
}
