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
            // Pre-filled / user-editable fields
            val name: String = "",
            val docType: String = "receipt",
            val vendor: String = "",
            val amount: String = "",
            val currency: String = "USD",
            val purchaseDateMs: Long? = null,
            val warrantyEndDateMs: Long? = null,
            val notes: String = "",
            // Edit mode — non-null means we are editing an existing doc
            val existingDocId: String? = null,
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

    /** Load an existing doc for editing — skips the scan/OCR pipeline entirely. */
    fun loadForEdit(docId: String) {
        val accountId = identity.activeAccountId.value ?: return
        viewModelScope.launch {
            try {
                val doc = dbFactory.get(accountId).docDao().getById(docId) ?: return@launch
                _state.value = UiState.Form(
                    rawText = doc.ocrRawText ?: "",
                    ocrResult = null,
                    pageUris = emptyList(),
                    name = doc.name,
                    docType = doc.docType ?: "receipt",
                    vendor = doc.vendor ?: "",
                    amount = doc.total?.toString() ?: "",
                    currency = doc.currency ?: "USD",
                    purchaseDateMs = doc.purchaseDate,
                    warrantyEndDateMs = doc.warrantyEndDate,
                    notes = doc.notes ?: "",
                    existingDocId = docId,
                )
            } catch (e: Exception) {
                Timber.e(e, "[DocAddViewModel] loadForEdit failed for docId=$docId")
                _state.value = UiState.Error("Failed to load document")
            }
        }
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
            } else null

            _state.value = UiState.Form(
                rawText = rawText,
                ocrResult = ocrResult,
                pageUris = pageUris,
                name = "",
                docType = docType.takeIf { it != "unknown" } ?: "receipt",
                vendor = ocrResult?.vendor ?: "",
                amount = ocrResult?.total?.toString() ?: "",
                purchaseDateMs = ocrResult?.date?.parseToEpochMs(),
                warrantyEndDateMs = ocrResult?.end_date?.parseToEpochMs(),
            )
        }
    }

    fun save(
        name: String,
        docType: String,
        vendor: String,
        amount: String,
        currency: String,
        purchaseDateMs: Long?,
        warrantyEndDateMs: Long?,
        notes: String,
        rawText: String,
        ocrResult: DocOcrResponse? = null,
        assetId: String? = null,
        existingDocId: String? = null,
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
                val isEdit = existingDocId != null
                val docId = existingDocId ?: UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val db = dbFactory.get(accountId)

                val existing = if (isEdit) db.docDao().getById(docId) else null

                db.docDao().upsert(
                    DocEntity(
                        docId = docId,
                        accountId = accountId,
                        assetId = existing?.assetId ?: assetId,
                        entityId = existing?.entityId,
                        entityType = existing?.entityType,
                        name = name.trim(),
                        docType = docType,
                        mimeType = existing?.mimeType,
                        storageKey = existing?.storageKey,
                        downloadUrl = existing?.downloadUrl,
                        fileHash = existing?.fileHash,
                        fileSize = existing?.fileSize,
                        ocrRawText = rawText.takeIf { it.isNotBlank() } ?: existing?.ocrRawText,
                        ocrExtractedJson = existing?.ocrExtractedJson,
                        vendor = vendor.trim().takeIf { it.isNotBlank() },
                        total = amount.toDoubleOrNull(),
                        currency = currency.trim().takeIf { it.isNotBlank() },
                        purchaseDate = purchaseDateMs,
                        warrantyEndDate = warrantyEndDateMs,
                        notes = notes.trim().takeIf { it.isNotBlank() },
                        uploadedBy = existing?.uploadedBy,
                        uploadedAt = existing?.uploadedAt,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        deletedAt = null,
                        serverVersion = existing?.serverVersion ?: 0,
                        seq = existing?.seq,
                    ),
                )
                db.syncQueueDao().enqueueWithDedup(
                    SyncQueueEntity(
                        queueId = "doc_$docId",
                        entityType = "doc",
                        entityId = docId,
                        operation = if (isEdit) "update" else "insert",
                        serverVersion = existing?.serverVersion,
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

    private fun String.parseToEpochMs(): Long? = try {
        LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
