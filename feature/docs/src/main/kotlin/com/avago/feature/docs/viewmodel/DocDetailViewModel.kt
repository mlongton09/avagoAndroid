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
class DocDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val textRecognizer: AvagoTextRecognizer,
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
            db.syncQueueDao().enqueueOrReplace(
                SyncQueueEntity(
                    queueId = UUID.randomUUID().toString(),
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
                val now = System.currentTimeMillis()
                val updated = current.copy(ocrRawText = rawText, updatedAt = now)
                val accountId = identity.getActiveAccountId() ?: return@launch
                val db = dbFactory.get(accountId)
                db.docDao().upsert(updated)
                _doc.value = updated
                db.syncQueueDao().enqueueOrReplace(
                    SyncQueueEntity(
                        queueId = UUID.randomUUID().toString(),
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
}
