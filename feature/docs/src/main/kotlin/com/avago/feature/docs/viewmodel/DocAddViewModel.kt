package com.avago.feature.docs.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.DocEntity
import com.avago.core.data.db.entity.SyncQueueEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.ocr.AvagoTextRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DocAddViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val textRecognizer: AvagoTextRecognizer,
    private val serviceClient: AvagoServiceClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Scanning : UiState()
        data class OcrProcessing(val pageCount: Int) : UiState()
        data class Form(
            val rawText: String,
            val extractedJson: String?,
            val pageUris: List<Uri>,
        ) : UiState()
        object Saving : UiState()
        object Done : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onScanRequested() {
        _state.value = UiState.Scanning
    }

    fun onImportRequested() {
        _state.value = UiState.Scanning
    }

    fun processScannedPages(pageUris: List<Uri>) {
        if (pageUris.isEmpty()) {
            _state.value = UiState.Error("No pages received from scanner")
            return
        }
        viewModelScope.launch {
            _state.value = UiState.OcrProcessing(pageUris.size)

            val rawText = textRecognizer.recognizeAll(pageUris, context).getOrElse { e ->
                Timber.e(e, "[DocAddViewModel] OCR failed")
                _state.value = UiState.Error("OCR failed: ${e.message}")
                return@launch
            }

            val accountId = identity.activeAccountId.value
            val extractedJson: String? = if (accountId != null && rawText.isNotBlank()) {
                when (val result = serviceClient.extractDoc(accountId, rawText, "unknown")) {
                    is NetworkResult.Success -> result.data
                    else -> {
                        Timber.d("[DocAddViewModel] AI extraction unavailable — continuing without it")
                        null
                    }
                }
            } else {
                null
            }

            _state.value = UiState.Form(rawText, extractedJson, pageUris)
        }
    }

    fun save(
        name: String,
        docType: String,
        rawText: String,
        extractedJson: String?,
        assetId: String? = null,
    ) {
        val accountId = identity.activeAccountId.value
        if (accountId == null) {
            _state.value = UiState.Error("Not signed in")
            return
        }
        if (name.isBlank()) {
            _state.value = UiState.Error("Document name is required")
            return
        }
        viewModelScope.launch {
            _state.value = UiState.Saving
            try {
                val docId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val db = dbFactory.get(accountId)
                db.docDao().upsert(
                    DocEntity(
                        docId = docId,
                        accountId = accountId,
                        assetId = assetId,
                        entityId = null,
                        entityType = null,
                        name = name.trim(),
                        docType = docType,
                        mimeType = null,
                        storageKey = null,
                        downloadUrl = null,
                        fileHash = null,
                        fileSize = null,
                        ocrRawText = rawText.takeIf { it.isNotBlank() },
                        ocrExtractedJson = extractedJson,
                        vendor = null,
                        total = null,
                        currency = null,
                        purchaseDate = null,
                        warrantyEndDate = null,
                        uploadedBy = null,
                        uploadedAt = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = 0,
                        seq = null,
                    ),
                )
                db.syncQueueDao().enqueueWithDedup(
                    SyncQueueEntity(
                        queueId = "doc_$docId",
                        entityType = "doc",
                        entityId = docId,
                        operation = "insert",
                        serverVersion = null,
                        payload = null,
                        syncStatus = "pending",
                        attempts = 0,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                _state.value = UiState.Done
            } catch (e: Exception) {
                Timber.e(e, "[DocAddViewModel] Save failed")
                _state.value = UiState.Error("Save failed: ${e.message}")
            }
        }
    }

    fun resetToIdle() {
        _state.value = UiState.Idle
    }
}
