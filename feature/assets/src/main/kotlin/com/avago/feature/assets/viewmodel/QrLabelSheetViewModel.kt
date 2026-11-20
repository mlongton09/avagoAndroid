package com.avago.feature.assets.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.assets.data.AssetQrService
import com.avago.feature.assets.model.AssetQr
import com.avago.feature.assets.model.QrLabelJob
import com.avago.feature.assets.model.QrLabelLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class QrLabelSheetState(
    val layout: QrLabelLayout = QrLabelLayout.LETTER,
    val busy: Boolean = false,
    val job: QrLabelJob? = null,
    /** The downloaded sheet of [job]. */
    val file: File? = null,
    val error: ApiError? = null,
)

/**
 * Bulk QR labels (online action, L38): renders one PDF label sheet for many assets through the
 * label job endpoints, then downloads it into the cache for the system PDF viewer.
 */
@HiltViewModel
class QrLabelSheetViewModel @Inject constructor(
    private val service: AssetQrService,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(QrLabelSheetState())
    val state: StateFlow<QrLabelSheetState> = _state.asStateFlow()

    private val _sheets = MutableSharedFlow<File>(extraBufferCapacity = 1)

    /** Downloaded label sheets, ready to open. */
    val sheets: SharedFlow<File> = _sheets

    fun selectLayout(layout: QrLabelLayout) = _state.update { it.copy(layout = layout) }

    /** Renders and downloads labels for the first [AssetQr.MAX_BATCH_ASSETS] of [assetIds]. */
    fun generate(assetIds: List<String>) {
        if (assetIds.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true, job = null, file = null, error = null) }
        viewModelScope.launch {
            try {
                val job = when (val r = service.createLabelSheet(assetIds.take(AssetQr.MAX_BATCH_ASSETS), _state.value.layout)) {
                    is ApiResult.Ok -> awaitCompletion(r.value)
                    is ApiResult.Err -> return@launch _state.update { it.copy(error = r.error) }
                } ?: return@launch
                _state.update { it.copy(job = job) }
                when (val pdf = service.downloadLabelSheet(job.id)) {
                    is ApiResult.Ok -> save(job, pdf.value).let { file ->
                        _state.update { it.copy(file = file) }
                        _sheets.emit(file)
                    }
                    is ApiResult.Err -> _state.update { it.copy(error = pdf.error) }
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** The job once it completed (rendering is normally synchronous); null after reporting a failure. */
    private suspend fun awaitCompletion(created: QrLabelJob): QrLabelJob? {
        var job = created
        repeat(MAX_POLLS) {
            if (job.completed) return job
            if (job.status !in RUNNING) return null.also { reportFailed(job) }
            _state.update { it.copy(job = job) }
            delay(POLL_INTERVAL_MS)
            job = when (val r = service.labelJob(job.id)) {
                is ApiResult.Ok -> r.value
                is ApiResult.Err -> return null.also { _state.update { it.copy(error = r.error) } }
            }
        }
        return job.takeIf { it.completed } ?: null.also { reportFailed(job) }
    }

    private fun reportFailed(job: QrLabelJob) =
        _state.update { it.copy(job = job, error = ApiError(-1, "label_job_${job.status}", job.status)) }

    private suspend fun save(job: QrLabelJob, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "qr_labels").apply { mkdirs() }
        File(dir, "qr-labels-${job.id}.pdf").apply { writeBytes(bytes) }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_POLLS = 30
        val RUNNING = setOf("pending")
    }
}
