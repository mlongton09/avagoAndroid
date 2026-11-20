package com.avago.feature.docs.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.DocumentDraft
import com.avago.core.data.domain.DocumentKind
import com.avago.core.data.domain.DocumentOwner
import com.avago.core.data.domain.DocumentRepository
import com.avago.core.data.domain.LocalFileRef
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.ocr.AvagoTextRecognizer
import com.avago.feature.docs.nav.DocsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * Adds a scanned or imported document to an asset or a service record: on-device OCR, optional server-side field
 * extraction (a compute endpoint that degrades to an empty form), then `core.documents.create`
 * with the file uploaded through E12.
 */
@HiltViewModel
class DocAddViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val identity: IdentityManager,
    private val textRecognizer: AvagoTextRecognizer,
    private val serviceClient: AvagoServiceClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val owner: DocumentOwner = run {
        val id = requireNotNull(savedStateHandle.get<String>("ownerId"))
        when (val type = savedStateHandle.get<String>("ownerType")) {
            DocsRoute.OWNER_ASSET -> DocumentOwner.Asset(id)
            DocsRoute.OWNER_ACTIVITY -> DocumentOwner.Activity(id)
            else -> error("unknown document owner type: $type")
        }
    }

    data class Form(
        val file: Uri,
        val mediaType: String,
        val rawText: String,
        val kind: DocumentKind = DocumentKind.RECEIPT,
        val issuerName: String = "",
        val amount: String = "",
        val currency: String = defaultCurrency(),
        val issuedOn: LocalDate? = null,
        val warrantyEndsOn: LocalDate? = null,
    )

    sealed interface UiState {
        data object Idle : UiState
        data object Picking : UiState
        data class Recognizing(val pageCount: Int) : UiState
        data class Editing(val form: Form) : UiState
        data class Saving(val form: Form) : UiState
        data object Done : UiState
        data class Error(val message: String, val form: Form? = null) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onPicking() {
        _state.value = UiState.Picking
    }

    fun resetToIdle() {
        _state.value = UiState.Idle
    }

    /** A scan yields page images for OCR plus one PDF, which is the uploaded file. */
    fun onScanned(pdf: Uri?, pages: List<Uri>) {
        val file = pdf ?: pages.singleOrNull()
        if (file == null) {
            _state.value = UiState.Error("The scanner did not produce a document")
            return
        }
        recognize(file, if (pdf != null) PDF else JPEG, pages)
    }

    fun onImported(uri: Uri) {
        val mediaType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        recognize(uri, mediaType, if (mediaType.startsWith("image/")) listOf(uri) else emptyList())
    }

    private fun recognize(file: Uri, mediaType: String, pages: List<Uri>) {
        viewModelScope.launch {
            _state.value = UiState.Recognizing(pages.size)
            val rawText = if (pages.isEmpty()) "" else textRecognizer.recognizeAll(pages, context).getOrElse { e ->
                Timber.w(e, "[DocAddViewModel] on-device OCR failed; continuing with an empty form")
                ""
            }
            val extracted = extract(rawText)
            _state.value = UiState.Editing(
                Form(
                    file = file,
                    mediaType = mediaType,
                    rawText = rawText,
                    issuerName = extracted?.vendor.orEmpty(),
                    amount = extracted?.totalAmount()?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    currency = extracted?.currency?.trim()?.uppercase()?.takeIf { it.length == 3 } ?: defaultCurrency(),
                    issuedOn = extracted?.documentDate(),
                    warrantyEndsOn = extracted?.warrantyEndDate(),
                ),
            )
        }
    }

    private suspend fun extract(rawText: String) = identity.activeAccountId.value
        ?.takeIf { rawText.isNotBlank() }
        ?.let { accountId ->
            when (val result = serviceClient.extractDocOcr(accountId = accountId, ocrRawText = rawText, documentType = DocumentKind.RECEIPT.wire)) {
                is NetworkResult.Success -> result.data
                else -> null
            }
        }

    fun save(form: Form, title: String, notes: String) {
        val amount = form.amount.trim().takeIf { it.isNotEmpty() }?.let {
            it.replace(',', '.').toBigDecimalOrNull() ?: run {
                _state.value = UiState.Error("Amount is not a number", form)
                return
            }
        }
        val draft = DocumentDraft(
            owner = owner,
            kind = form.kind,
            file = LocalFileRef(form.file.toString(), form.mediaType),
            title = title,
            issuedOn = form.issuedOn,
            issuerName = form.issuerName,
            total = amount,
            currency = form.currency.takeIf { amount != null },
            warrantyEndsOn = form.warrantyEndsOn,
            notes = notes,
        )
        viewModelScope.launch {
            _state.value = UiState.Saving(form)
            try {
                documents.create(draft)
                _state.value = UiState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[DocAddViewModel] save failed")
                _state.value = UiState.Error(e.message ?: "Save failed", form)
            }
        }
    }

    /** Back from an error to the form it came from (or to the source picker). */
    fun dismissError() {
        val current = _state.value
        _state.value = if (current is UiState.Error && current.form != null) UiState.Editing(current.form) else UiState.Idle
    }

    private companion object {
        const val PDF = "application/pdf"
        const val JPEG = "image/jpeg"

        fun defaultCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"
    }
}
