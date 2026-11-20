package com.avago.feature.provider.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.provider.R
import com.avago.feature.provider.data.JobStatus
import com.avago.feature.provider.data.Load
import com.avago.feature.provider.data.Notice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shared busy flag and snackbar notices. */
abstract class NoticeViewModel : ViewModel() {
    protected val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    protected val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val notices: SharedFlow<Notice> = _notices

    /** Runs one mutation at a time; reports [doneRes] on success or the server error on failure. */
    protected fun <T> act(doneRes: Int?, call: suspend () -> ApiResult<T>, onOk: suspend (T) -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val r = call()) {
                    is ApiResult.Ok -> {
                        onOk(r.value)
                        doneRes?.let { _notices.emit(Notice.Done(it)) }
                    }
                    is ApiResult.Err -> _notices.emit(Notice.Failed(r.error))
                }
            } finally {
                _busy.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.provider_back))
                        }
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = floating,
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
internal fun NoticeEffect(notices: Flow<Notice>, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(notices) {
        notices.collect { n ->
            snackbar.showSnackbar(
                when (n) {
                    is Notice.Done -> resources.getString(n.messageRes)
                    is Notice.Failed -> errorText(ctx, n.error)
                },
            )
        }
    }
}

@Composable
internal fun <T> LoadBox(state: Load<T>, onRetry: () -> Unit, padding: PaddingValues, content: @Composable (T) -> Unit) {
    when (state) {
        is Load.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is Load.Failed -> Column(Modifier.fillMaxSize().padding(padding).padding(AvagoSpacing.lg)) {
            ErrorBanner(
                message = errorText(LocalContext.current, state.error),
                onRetry = onRetry,
                retryLabel = stringResource(R.string.provider_retry),
            )
        }
        is Load.Ready -> content(state.value)
    }
}

/** Friendly text for known provider error codes, else the server message. */
internal fun errorRes(e: ApiError): Int? = when {
    e.offline -> R.string.provider_err_offline
    e.code == "not_a_provider" -> R.string.provider_err_not_provider
    e.code == "provider_pro_required" || e.status == 402 -> R.string.provider_err_pro
    e.code == "job_not_completed" -> R.string.provider_err_job_not_completed
    e.code == "job_already_invoiced" -> R.string.provider_err_job_invoiced
    e.code == "invalid_job_transition" -> R.string.provider_err_transition
    e.code == "quote_has_no_lines" -> R.string.provider_err_quote_empty
    e.code == "invoice_total_zero" -> R.string.provider_err_invoice_zero
    e.code == "invoice_already_paid" -> R.string.provider_err_invoice_paid
    e.code == "invalid_quote_transition" || e.code == "invalid_invoice_transition" -> R.string.provider_err_doc_state
    e.status == 403 -> R.string.provider_err_forbidden
    e.status == 404 -> R.string.provider_err_not_found
    e.status == 429 -> R.string.provider_err_rate_limited
    else -> null
}

internal fun errorText(ctx: Context, e: ApiError): String = errorRes(e)?.let(ctx::getString)
    ?: e.message.takeIf { it.isNotBlank() && it != e.code }
    ?: ctx.getString(R.string.provider_err_generic)

internal object Fmt {
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun instant(iso: String?): Instant? = iso?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: runCatching { Instant.parse(it) }.getOrNull()
    }

    fun dateTime(iso: String?): String = instant(iso)?.atZone(ZoneId.systemDefault())?.format(dateTime) ?: date(iso)

    fun time(iso: String?): String? = instant(iso)?.atZone(ZoneId.systemDefault())?.format(time)

    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        instant(iso)?.let { return it.atZone(ZoneId.systemDefault()).format(date) }
        return runCatching { LocalDate.parse(iso.take(10)).format(date) }.getOrDefault(iso)
    }

    /** Minor units to a locale currency string using the currency's ISO 4217 digits (JPY 0, KWD 3). */
    fun money(minor: Long, currency: String): String = MoneyFormatter.format(minor, currency)

    fun number(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.', ',')
}

internal fun shareText(ctx: Context, text: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Opens a link or dial/mail URI; false when nothing can handle it. */
internal fun openUri(ctx: Context, uri: String): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

internal fun screenPadding(inner: PaddingValues) = PaddingValues(
    top = inner.calculateTopPadding() + AvagoSpacing.md,
    bottom = inner.calculateBottomPadding() + 88.dp,
)

internal val ListGap = Arrangement.spacedBy(12.dp)

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = SectionCard(modifier = modifier, title = title, trailing = trailing, contentPadding = true, content = content)

internal fun statusRes(status: String): Int? = when (status) {
    JobStatus.SCHEDULED -> R.string.provider_st_scheduled
    JobStatus.IN_PROGRESS -> R.string.provider_st_in_progress
    JobStatus.ON_HOLD -> R.string.provider_st_on_hold
    JobStatus.COMPLETED -> R.string.provider_st_completed
    JobStatus.CANCELLED -> R.string.provider_st_cancelled
    "draft" -> R.string.provider_st_draft
    "sent" -> R.string.provider_st_sent
    "approved" -> R.string.provider_st_approved
    "declined" -> R.string.provider_st_declined
    "expired" -> R.string.provider_st_expired
    "converted" -> R.string.provider_st_converted
    "paid" -> R.string.provider_st_paid
    "void" -> R.string.provider_st_void
    else -> null
}

internal fun statusTone(status: String): StatusTone = when (status) {
    JobStatus.IN_PROGRESS, "sent" -> StatusTone.Info
    JobStatus.ON_HOLD, "expired" -> StatusTone.Warning
    JobStatus.COMPLETED, "approved", "paid", "converted" -> StatusTone.Success
    JobStatus.CANCELLED, "declined", "void" -> StatusTone.Danger
    JobStatus.SCHEDULED -> StatusTone.Accent
    else -> StatusTone.Neutral
}

@Composable
internal fun statusLabel(status: String): String = statusRes(status)?.let { stringResource(it) } ?: status.replace('_', ' ')

@Composable
internal fun StatusChip(status: String) = StatusPill(text = statusLabel(status), tone = statusTone(status))

@Composable
internal fun serviceTypeLabel(type: String): String = when (type) {
    "auto" -> stringResource(R.string.provider_svc_auto)
    "diesel" -> stringResource(R.string.provider_svc_diesel)
    "marine" -> stringResource(R.string.provider_svc_marine)
    "small_engine" -> stringResource(R.string.provider_svc_small_engine)
    "hydraulics" -> stringResource(R.string.provider_svc_hydraulics)
    "calibration" -> stringResource(R.string.provider_svc_calibration)
    "equipment" -> stringResource(R.string.provider_svc_equipment)
    else -> stringResource(R.string.provider_svc_other)
}

@Composable
internal fun meterUnitLabel(unit: String): String = when (unit) {
    "mi" -> stringResource(R.string.provider_unit_mi)
    "km" -> stringResource(R.string.provider_unit_km)
    else -> stringResource(R.string.provider_unit_hours)
}

/** Camera capture that hands back a downscaled JPEG (≤1600 px, EXIF rotation applied). */
@Composable
internal fun rememberPhotoCapture(onJpeg: (ByteArray) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val target = remember { mutableListOf<File>() }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = target.removeLastOrNull() ?: return@rememberLauncherForActivityResult
        if (!ok) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = Jpeg.normalize(file)
            file.delete()
            bytes?.let(onJpeg)
        }
    }
    return {
        val dir = File(ctx.cacheDir, "provider").apply { mkdirs() }
        val file = File.createTempFile("job_", ".jpg", dir)
        target += file
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
        launcher.launch(uri)
    }
}

internal object Jpeg {
    private const val MAX_EDGE = 1600

    suspend fun normalize(file: File, quality: Int = 82): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
        val rotation = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val out = if (scale < 1f || rotation != 0f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true) else decoded
        val bytes = ByteArrayOutputStream().use { s ->
            out.compress(Bitmap.CompressFormat.JPEG, quality, s)
            s.toByteArray()
        }
        if (out !== decoded) decoded.recycle()
        out.recycle()
        bytes
    }
}
