package com.avago.feature.reports.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.avago.core.csv.CsvWriter
import com.avago.core.pdf.ReportPdfGenerator
import com.avago.core.reports.model.ReportRange
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File

@Composable
fun ExportButtons(
    title: String,
    headers: List<String>,
    rows: List<List<String>>,
    range: ReportRange,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pdfGenerator = remember(context) { ReportPdfGenerator(context) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(onClick = { exportCsv(context, title, headers, rows) }) {
            Icon(Icons.Default.TableChart, contentDescription = null)
            Text("CSV")
        }
        FilledTonalButton(
            onClick = { exportPdf(context, title, headers, rows, range, pdfGenerator) },
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
            Text("PDF")
        }
    }
}

private fun exportCsv(
    context: Context,
    title: String,
    headers: List<String>,
    rows: List<List<String>>,
) {
    val bytes = CsvWriter.write(headers, rows)
    val filename = "${title.replace(' ', '_')}.csv"
    shareBytes(context, bytes, filename, "text/csv")
}

private fun exportPdf(
    context: Context,
    title: String,
    headers: List<String>,
    rows: List<List<String>>,
    range: ReportRange,
    generator: ReportPdfGenerator,
) {
    val subtitle = buildRangeSubtitle(range)
    val bytes = generator.generate(title, subtitle, headers, rows, range)
    val filename = "${title.replace(' ', '_')}.pdf"
    shareBytes(context, bytes, filename, "application/pdf")
}

private fun shareBytes(context: Context, bytes: ByteArray, filename: String, mimeType: String) {
    val file = File(context.cacheDir, filename)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share $filename"))
}

private fun buildRangeSubtitle(range: ReportRange): String {
    fun fmt(ms: Long): String {
        val dt = Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}"
    }
    return "${fmt(range.start.toEpochMilliseconds())} – ${fmt(range.end.toEpochMilliseconds())}"
}
