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
import com.avago.core.network.model.DocOcrResponse
import com.avago.core.ocr.AvagoTextRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
            val ocrResult: DocOcrResponse?,
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

    fun processScannedPages(pageUris: List<Uri>, docType: String = "unknown") {
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
            val ocrResult: DocOcrResponse? = if (accountId != null && rawText.isNotBlank()) {
                // Use the dedicated doc OCR extraction endpoint (mirrors iOS extractDocOcr).
                // Falls back to null gracefully so the user can still fill in fields manually.
                when (val result = serviceClient.extractDocOcr(
                    accountId = accountId,
                    ocrRawText = rawText,
                    documentType = docType,
                )) {
                    is NetworkResult.Success -> result.data
                    else -> {
                        Timber.d("[DocAddViewModel] Doc OCR extraction unavailable — continuing without it")
                        null
                    }
                }
            } else {
                null
            }

            _state.value = UiState.Form(rawText, ocrResult, pageUris)
        }
    }

    fun save(
        name: String,
        docType: String,
        rawText: String,
        ocrResult: DocOcrResponse? = null,
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
                        // Persist full OCR JSON for future reference / re-extraction.
                        ocrExtractedJson = null,
                        // Structured fields extracted by the server-side OCR pipeline.
                        vendor = ocrResult?.vendor,
                        total = ocrResult?.total,
                        currency = null,
                        purchaseDate = ocrResult?.date?.parseToEpochMs(),
                        warrantyEndDate = ocrResult?.end_date?.parseToEpochMs(),
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

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Parses an ISO-8601 date string (yyyy-MM-dd) returned by the OCR extraction
     * endpoint into milliseconds since epoch.  Returns null on any parse failure so
     * that a bad server response never blocks a save.
     */
    private fun String.parseToEpochMs(): Long? = try {
        LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
