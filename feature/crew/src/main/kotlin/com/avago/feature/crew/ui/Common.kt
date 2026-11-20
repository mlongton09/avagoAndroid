package com.avago.feature.crew.ui

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.network.json.ApiError
import com.avago.core.ui.components.ErrorBanner
import com.avago.feature.crew.R
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.Notice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

/** Minimum touch target (Material / WCAG). */
internal val MinTouch = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrewScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState,
    actions: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.crew_back))
                        }
                    }
                },
                actions = { actions() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = fab,
        content = content,
    )
}

/** Shows [Notice]s from a view-model in [snackbar]. */
@Composable
internal fun NoticeEffect(notices: Flow<Notice>, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(notices) {
        notices.collect { n ->
            val text = when (n) {
                is Notice.Done -> resources.getString(n.messageRes)
                is Notice.Failed -> errorText(ctx, n.error)
            }
            snackbar.showSnackbar(text)
        }
    }
}

/** Loading spinner, error banner with retry, or [content] for a ready value. */
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
                retryLabel = stringResource(R.string.crew_retry),
            )
        }
        is Load.Ready -> content(state.value)
    }
}

/** User-facing text for a server or network error. */
internal fun errorText(ctx: Context, e: ApiError): String = when {
    e.offline -> ctx.getString(R.string.crew_err_offline)
    e.code == "unsupported_media_type" || e.code == "media_type_mismatch" -> ctx.getString(R.string.crew_err_photo_type)
    e.status == 403 -> ctx.getString(R.string.crew_err_forbidden)
    e.status == 404 -> ctx.getString(R.string.crew_err_not_found)
    e.status == 429 -> ctx.getString(R.string.crew_err_rate_limited)
    e.message.isNotBlank() -> e.message
    else -> ctx.getString(R.string.crew_err_generic)
}

internal object Fmt {
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun instant(iso: String?): Instant? = iso?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: runCatching { Instant.parse(it) }.getOrNull()
    }

    fun dateTime(iso: String?): String = instant(iso)?.atZone(ZoneId.systemDefault())?.format(dateTime) ?: date(iso)

    fun time(iso: String?): String = instant(iso)?.atZone(ZoneId.systemDefault())?.format(time) ?: "—"

    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        instant(iso)?.let { return it.atZone(ZoneId.systemDefault()).format(date) }
        return runCatching { LocalDate.parse(iso.take(10)).format(date) }.getOrDefault(iso)
    }

    fun hours(h: Double?): String = h?.let { "%.1f h".format(it) } ?: "—"

    /** Minor units to a locale currency string using the currency's ISO 4217 digits (JPY 0, KWD 3). */
    fun money(minor: Long, currency: String): String = MoneyFormatter.format(minor, currency)
}

/** Opens the system share sheet for [text]. */
internal fun shareText(ctx: Context, text: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        val dir = File(ctx.cacheDir, "crew").apply { mkdirs() }
        val file = File.createTempFile("photo_", ".jpg", dir)
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

/** Standard screen padding for scrolling content. */
internal fun screenPadding(inner: PaddingValues) = PaddingValues(
    top = inner.calculateTopPadding() + AvagoSpacing.md,
    bottom = inner.calculateBottomPadding() + AvagoSpacing.lg,
)

internal val ListGap = Arrangement.spacedBy(12.dp)

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
internal fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() },
    )
}

/** [SectionCard] with inner padding, for free-form content. */
@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) = com.avago.core.ui.components.SectionCard(modifier = modifier, title = title, trailing = trailing, contentPadding = true, content = content)

/** Inset caption used directly in a list, aligned with card content. */
@Composable
internal fun ListCaption(text: String) = Caption(text, Modifier.padding(horizontal = 20.dp))