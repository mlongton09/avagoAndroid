package com.avago.feature.docs.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

@HiltViewModel
class DocDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val textRecognizer: AvagoTextRecognizer,
    private val serviceClient: AvagoServiceClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val docId: String = requireNotNull(savedStateHandle["docId"]) {
        "DocDetailViewModel requires docId in SavedStateHandle"
    }

    private val _doc = MutableStateFlow<DocEntity?>(null)
    val doc: StateFlow<DocEntity?> = _doc.asStateFlow()

    private val _isReScanningOcr = MutableStateFlow(false)
    val isReScanningOcr: StateFlow<Boolean> = _isReScanningOcr.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val accountId = identity.getActiveAccountId() ?: return@launch
            _doc.value = dbFactory.get(accountId).docDao().getById(docId)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val accountId = identity.getActiveAccountId() ?: return@launch
            val db = dbFactory.get(accountId)
            val now = System.currentTimeMillis()
            db.docDao().softDelete(docId, now)
            db.syncQueueDao().enqueueWithDedup(
                SyncQueueEntity(
                    queueId = "doc_$docId",
                    entityType = "doc",
                    entityId = docId,
                    operation = "delete",
                    serverVersion = _doc.value?.serverVersion,
                    payload = null,
                    syncStatus = "pending",
                    attempts = 0,
                    lastError = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            onDeleted()
        }
    }

    fun reScanOcr(pageUris: List<Uri>) {
        if (pageUris.isEmpty()) {
            _errorMessage.value = "No pages available for re-scan"
            return
        }
        viewModelScope.launch {
            _isReScanningOcr.value = true
            _errorMessage.value = null
            try {
                val rawText = textRecognizer.recognizeAll(pageUris, context).getOrElse { e ->
                    _errorMessage.value = "OCR failed: ${e.message}"
                    return@launch
                }
                val current = _doc.value ?: return@launch
                val accountId = identity.getActiveAccountId() ?: return@launch

                // Re-run server-side structured extraction after re-scan, mirroring
                // the iOS reScan path which also calls extractDocOcr after re-OCRing.
                val ocrResult = if (rawText.isNotBlank()) {
                    when (val result = serviceClient.extractDocOcr(
                        accountId = accountId,
                        ocrRawText = rawText,
                        documentType = current.docType ?: "unknown",
                        assetId = current.assetId,
                    )) {
                        is NetworkResult.Success -> result.data
                        else -> {
                            Timber.d("[DocDetailViewModel] OCR re-extraction unavailable — keeping existing fields")
                            null
                        }
                    }
                } else null

                val now = System.currentTimeMillis()
                val updated = if (ocrResult != null) {
                    current.copy(
                        ocrRawText = rawText,
                        vendor = ocrResult.vendor ?: current.vendor,
                        total = ocrResult.total ?: current.total,
                        purchaseDate = ocrResult.date?.parseToEpochMs() ?: current.purchaseDate,
                        warrantyEndDate = ocrResult.end_date?.parseToEpochMs() ?: current.warrantyEndDate,
                        updatedAt = now,
                    )
                } else {
                    current.copy(ocrRawText = rawText, updatedAt = now)
                }

                val db = dbFactory.get(accountId)
                db.docDao().upsert(updated)
                _doc.value = updated
                db.syncQueueDao().enqueueWithDedup(
                    SyncQueueEntity(
                        queueId = "doc_$docId",
                        entityType = "doc",
                        entityId = docId,
                        operation = "update",
                        serverVersion = current.serverVersion,
                        payload = null,
                        syncStatus = "pending",
                        attempts = 0,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } finally {
                _isReScanningOcr.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Parses an ISO-8601 date string (yyyy-MM-dd) from the OCR response into
     * milliseconds since epoch.  Returns null on any parse failure.
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
