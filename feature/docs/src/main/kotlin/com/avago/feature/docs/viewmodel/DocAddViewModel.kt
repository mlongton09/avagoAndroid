package com.avago.feature.docs.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.dao.DocDao
import com.avago.core.data.db.dao.SyncQueueDao
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
    private val docDao: DocDao,
    private val syncQueueDao: SyncQueueDao,
    private val identity: IdentityManager,
    private val textRecognizer: AvagoTextRecognizer,
    private val serviceClient: AvagoServiceClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    sealed class UiState {
        /** Initial state — user sees the "Scan" / "Import" picker. */
        object Idle : UiState()

        /** ML Kit scanner is open (or file picker is active). */
        object Scanning : UiState()

        /** OCR is running on [pageCount] pages. */
        data class OcrProcessing(val pageCount: Int) : UiState()

        /**
         * OCR completed.  The user sees the form to review/edit extracted data and
         * enter a name / doc type before saving.
         */
        data class Form(
            val rawText: String,
            val extractedJson: String?,
            val pageUris: List<Uri>,
        ) : UiState()

        /** Saving to Room + enqueueing sync item. */
        object Saving : UiState()

        /** Save completed — caller should navigate away. */
        object Done : UiState()

        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    // -------------------------------------------------------------------------
    // Scanning intent
    // -------------------------------------------------------------------------

    /** Called when the user taps "Scan" — UI should launch the scanner immediately. */
    fun onScanRequested() {
        _state.value = UiState.Scanning
    }

    /** Called when the user taps "Import" — UI launches system file picker. */
    fun onImportRequested() {
        _state.value = UiState.Scanning
    }

    // -------------------------------------------------------------------------
    // OCR pipeline
    // -------------------------------------------------------------------------

    /**
     * Processes scanned or imported pages through the OCR pipeline.
     *
     * 1. Runs [AvagoTextRecognizer.recognizeAll] on all [pageUris].
     * 2. Optionally calls the server-side AI extraction endpoint (wired in Phase 18;
     *    currently skipped with a null result).
     * 3. Transitions to [UiState.Form] with the raw text and (future) extracted JSON.
     */
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

            // Phase 18 wires Gemini / server-side extraction.
            // For now: attempt the server call but fall back gracefully.
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

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    /**
     * Persists the document to Room and enqueues a sync-queue insertion.
     *
     * @param name           User-provided document name.
     * @param docType        Selected doc type key (e.g. "receipt", "warranty").
     * @param rawText        Raw OCR output.
     * @param extractedJson  Structured JSON from the AI extraction step (may be null).
     * @param assetId        Optional linked asset ID.
     */
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
                docDao.upsert(
                    DocEntity(
                        docId = docId,
                        accountId = accountId,
                        assetId = assetId,
                        name = name.trim(),
                        docType = docType,
                        mimeType = null,
                        storageKey = null,
                        downloadUrl = null,
                        ocrRawText = rawText.takeIf { it.isNotBlank() },
                        ocrExtractedJson = extractedJson,
                        vendor = null,
                        total = null,
                        currency = null,
                        purchaseDate = null,
                        uploadedBy = null,
                        uploadedAt = null,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = 0,
                        seq = null,
                    ),
                )
                val queueId = UUID.randomUUID().toString()
                syncQueueDao.enqueueOrReplace(
                    SyncQueueEntity(
                        queueId = queueId,
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
