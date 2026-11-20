package com.avago.feature.docs.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.Document
import com.avago.core.data.domain.DocumentEdit
import com.avago.core.data.domain.DocumentKind
import com.avago.core.data.domain.DocumentRepository
import com.avago.core.data.domain.DocumentRevision
import com.avago.core.data.domain.FileRepository
import com.avago.core.data.domain.FileUnavailableException
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.data.domain.StoredFile
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.DocOcrResponse
import com.avago.core.ocr.AvagoTextRecognizer
import com.avago.core.permissions.Permissions
import com.avago.core.permissions.PermissionsManager
import com.avago.feature.docs.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * One maintenance document: its metadata (`core.documents.update`), its file revisions (a new
 * upload becomes the current revision), deletion, and opening a revision's file through the
 * download cache (`files/{id}/download`).
 */
@HiltViewModel
class DocDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val files: FileRepository,
    private val identity: IdentityManager,
    private val serviceClient: AvagoServiceClient,
    private val textRecognizer: AvagoTextRecognizer,
    permissions: PermissionsManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val docId: String = requireNotNull(savedStateHandle.get<String>("docId"))

    sealed interface State {
        data object Loading : State
        data object Missing : State
        data class Loaded(val document: Document) : State
    }

    sealed interface Event {
        data class Open(val file: File, val mimeType: String) : Event
        data class Failed(val messageRes: Int) : Event
        data object Deleted : Event

        /** Freshly extracted fields merged over the stored ones, for review before saving. */
        data class Extracted(val edit: DocumentEdit) : Event
    }

    val state: StateFlow<State> = documents.observeDocument(docId)
        .map<Document?, State> { it?.let(State::Loaded) ?: State.Missing }
        .catch { e -> Timber.e(e, "[DocDetailViewModel] document flow error"); emit(State.Missing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State.Loading)

    val revisions: StateFlow<List<DocumentRevision>> = documents.observeRevisions(docId)
        .catch { e -> Timber.e(e, "[DocDetailViewModel] revision flow error"); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canEdit: StateFlow<Boolean> = permissions.observeCan(Permissions.DOCUMENTS_EDIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissions.can(Permissions.DOCUMENTS_EDIT))

    val canDelete: StateFlow<Boolean> = permissions.observeCan(Permissions.DOCUMENTS_DELETE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), permissions.can(Permissions.DOCUMENTS_DELETE))

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events

    private val current: Document? get() = (state.value as? State.Loaded)?.document

    /** Opens [file]: this device's copy when it has one, otherwise the downloaded bytes. */
    fun open(file: StoredFile) {
        val local = file.localFile?.takeIf { it.exists() }
        if (local == null && !file.isDownloadable) {
            _events.tryEmit(Event.Failed(R.string.doc_detail_file_uploading))
            return
        }
        run(R.string.doc_detail_file_unavailable) {
            val bytes = local ?: files.fetch(file.id)
            _events.emit(Event.Open(bytes, file.mimeType ?: GENERIC_TYPE))
        }
    }

    fun save(edit: DocumentEdit, onSaved: () -> Unit) {
        val stored = current ?: return
        run(R.string.doc_detail_save_failed) {
            documents.update(stored, edit)
            onSaved()
        }
    }

    fun replaceFile(uri: Uri) {
        val stored = current ?: return
        val mediaType = context.contentResolver.getType(uri) ?: GENERIC_TYPE
        run(R.string.doc_detail_save_failed) { documents.replaceFile(stored, LocalFileRef(uri.toString(), mediaType)) }
    }

    /**
     * Re-runs field extraction on the server (online action). An image file is read again on the
     * device so the server gets fresh text; for a PDF the server re-uses the text of the last run.
     */
    fun reextract() {
        val stored = current ?: return
        val type = stored.kind.extractionType ?: return
        val file = stored.file ?: return
        val accountId = identity.getActiveAccountId() ?: return
        run(R.string.doc_detail_reextract_failed) {
            val text = if (file.mimeType?.startsWith("image/") == true) recognizeText(file) else null
            when (val r = serviceClient.reextractDocOcr(accountId, stored.id, text, type, Locale.getDefault().toLanguageTag())) {
                is NetworkResult.Success -> _events.emit(Event.Extracted(stored.withExtraction(r.data.extraction)))
                is NetworkResult.Error -> _events.emit(Event.Failed(reextractFailure(r.code)))
                NetworkResult.Unauthorized -> _events.emit(Event.Failed(R.string.doc_detail_reextract_failed))
            }
        }
    }

    private suspend fun recognizeText(file: StoredFile): String? {
        val local = file.localFile?.takeIf { it.exists() } ?: files.fetch(file.id)
        return textRecognizer.recognize(Uri.fromFile(local), context)
            .onFailure { e -> Timber.w(e, "[DocDetailViewModel] on-device OCR failed; the server re-uses its stored text") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun delete() {
        val stored = current ?: return
        run(R.string.doc_detail_delete_failed) {
            documents.delete(stored)
            _events.emit(Event.Deleted)
        }
    }

    private fun run(failure: Int, block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileUnavailableException) {
                Timber.w(e, "[DocDetailViewModel] file unavailable")
                _events.emit(Event.Failed(R.string.doc_detail_file_unavailable))
            } catch (e: Exception) {
                Timber.e(e, "[DocDetailViewModel] action failed")
                _events.emit(Event.Failed(failure))
            } finally {
                _busy.value = false
            }
        }
    }

    internal companion object {
        private const val GENERIC_TYPE = "application/octet-stream"

        /** The extractor's document type; documents of other kinds cannot be re-extracted. */
        val DocumentKind.extractionType: String?
            get() = when (this) {
                DocumentKind.RECEIPT -> "receipt"
                DocumentKind.INVOICE -> "invoice"
                DocumentKind.WARRANTY -> "warranty"
                DocumentKind.HOWTO -> "how_to"
                DocumentKind.OTHER -> null
            }

        /** The stored metadata with every field the extractor found replaced. */
        fun Document.withExtraction(ex: DocOcrResponse): DocumentEdit {
            val total = ex.totalAmount()
            return DocumentEdit(
                kind = kind,
                title = ex.title?.takeIf { it.isNotBlank() } ?: title,
                issuedOn = ex.documentDate() ?: issuedOn,
                issuerName = ex.vendor?.takeIf { it.isNotBlank() } ?: issuerName,
                total = total ?: this.total,
                currency = ex.currency?.trim()?.uppercase()?.takeIf { it.length == 3 } ?: currency,
                warrantyEndsOn = ex.warrantyEndDate() ?: warrantyEndsOn,
                notes = notes,
            )
        }

        fun reextractFailure(status: Int): Int = when (status) {
            HTTP_BAD_REQUEST -> R.string.doc_detail_reextract_no_text
            HTTP_CONFLICT -> R.string.doc_detail_reextract_no_file
            HTTP_UNAVAILABLE -> R.string.doc_detail_reextract_unavailable
            else -> R.string.doc_detail_reextract_failed
        }

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_CONFLICT = 409
        private const val HTTP_UNAVAILABLE = 503
    }
}
