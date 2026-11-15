package com.avago.feature.growth.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.SectionCard
import com.avago.feature.growth.R
import com.avago.feature.growth.data.Load
import com.avago.feature.growth.data.Notice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
internal fun GrowthScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.growth_back))
                        }
                    }
                },
                actions = { actions() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
internal fun NoticeEffect(notices: Flow<Notice>, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    LaunchedEffect(notices) {
        notices.collect { n ->
            snackbar.showSnackbar(
                when (n) {
                    is Notice.Done -> ctx.getString(n.messageRes)
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
                retryLabel = stringResource(R.string.growth_retry),
            )
        }
        is Load.Ready -> content(state.value)
    }
}

internal fun errorText(ctx: Context, e: ApiError): String = when {
    e.offline -> ctx.getString(R.string.growth_err_offline)
    e.status == 403 -> ctx.getString(R.string.growth_err_forbidden)
    e.status == 404 -> ctx.getString(R.string.growth_err_not_found)
    e.status == 429 -> ctx.getString(R.string.growth_err_rate_limited)
    e.message.isNotBlank() -> e.message
    else -> ctx.getString(R.string.growth_err_generic)
}

internal object Fmt {
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun instant(iso: String?): Instant? = iso?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: runCatching { Instant.parse(it) }.getOrNull()
    }

    fun dateTime(iso: String?): String = instant(iso)?.atZone(ZoneId.systemDefault())?.format(dateTime) ?: date(iso)

    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        instant(iso)?.let { return it.atZone(ZoneId.systemDefault()).format(date) }
        return runCatching { LocalDate.parse(iso.take(10)).format(date) }.getOrDefault(iso)
    }

    fun epochDate(seconds: Long?): String = seconds?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(date) } ?: "—"

    /** Minor units to a locale currency string using the currency's ISO 4217 digits (JPY 0, KWD 3). */
    fun money(minor: Long, currency: String): String = MoneyFormatter.format(minor, currency)

    fun score(v: Double?): String = v?.let { "%.1f".format(it) } ?: "—"
}

internal fun shareText(ctx: Context, text: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun copyText(ctx: Context, label: String, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Opens an https link in the browser; false when nothing can handle it. */
internal fun openUrl(ctx: Context, url: String): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

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
internal fun ListCaption(text: String) = Caption(text, Modifier.padding(horizontal = 20.dp))

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = SectionCard(modifier = modifier, title = title, trailing = trailing, contentPadding = true, content = content)

/** Read-only star row; [value] 0–5. */
@Composable
internal fun Stars(value: Double?, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.growth_stars_cd, Fmt.score(value))
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = desc }, verticalAlignment = Alignment.CenterVertically) {
        val full = (value ?: 0.0).let { kotlin.math.round(it).toInt() }
        repeat(5) { i ->
            Icon(
                if (i < full) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
        Text(Fmt.score(value), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

/** 1–5 star picker with 48dp touch targets. */
@Composable
internal fun StarPicker(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row {
            for (i in 1..5) {
                val cd = stringResource(R.string.growth_rate_n_cd, label, i)
                IconButton(onClick = { onChange(i) }, modifier = Modifier.semantics { contentDescription = cd }) {
                    Icon(if (i <= value) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
