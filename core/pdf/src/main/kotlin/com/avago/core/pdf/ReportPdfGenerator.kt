package com.avago.core.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.avago.core.reports.model.ReportRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates A4-landscape PDF documents for Avago reports.
 *
 * Page size: 842 × 595 pt (A4 landscape at 72 dpi).
 * Layout zones:
 *   [0, 0, 842, 48]   – brand header band (blue)
 *   [0, 48, 842, 72]  – column header band (light grey)
 *   [0, 72, 842, 575] – data rows (alternating white / off-white)
 *   [0, 578, 842, 595] – footer
 */
@Singleton
class ReportPdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        const val PAGE_WIDTH = 842f
        const val PAGE_HEIGHT = 595f
        const val HEADER_BAND_H = 48f
        const val COL_HEADER_BAND_H = 72f // bottom of col header strip
        const val ROW_START_Y = 90f
        const val ROW_HEIGHT = 16f
        const val FOOTER_Y = 588f
        const val DATA_BOTTOM = 562f    // rows stop before footer
        const val CELL_PADDING = 4f
        const val MAX_CELL_CHARS = 22   // truncate long text to keep cells readable

        val COLOR_BRAND = Color.parseColor("#1C8EF0")
        val COLOR_COL_HEADER_BG = Color.parseColor("#F0F4F8")
        val COLOR_COL_HEADER_TEXT = Color.parseColor("#1A2332")
        val COLOR_ROW_ALT = Color.parseColor("#F7FAFD")
        val COLOR_ROW_TEXT = Color.parseColor("#333333")
        val COLOR_FOOTER = Color.parseColor("#999999")
        val COLOR_GRID = Color.parseColor("#E0E0E0")
    }

    /**
     * Generate a multi-page PDF and return the raw bytes.
     *
     * @param title    Report name drawn in the header.
     * @param subtitle Optional subtitle (date range, filter) drawn below title.
     * @param headers  Column header labels.
     * @param rows     Data rows; each inner list maps 1:1 to [headers].
     * @param range    Used to print the date range in the footer.
     */
    fun generate(
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        range: ReportRange,
    ): ByteArray {
        val document = PdfDocument()
        val colWidth = PAGE_WIDTH / headers.size.coerceAtLeast(1)

        val generatedDate = run {
            val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}"
        }
        val footerText = "Generated $generatedDate | Avago | $subtitle"

        var pageNumber = 1
        var rowIndex = 0

        fun startPage(): Pair<PdfDocument.Page, Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber++
            ).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Header band
            paint.color = COLOR_BRAND
            canvas.drawRect(0f, 0f, PAGE_WIDTH, HEADER_BAND_H, paint)

            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText(title, 16f, 32f, paint)

            // Column header band
            paint.color = COLOR_COL_HEADER_BG
            canvas.drawRect(0f, HEADER_BAND_H, PAGE_WIDTH, COL_HEADER_BAND_H, paint)

            paint.color = COLOR_COL_HEADER_TEXT
            paint.textSize = 9f
            paint.isFakeBoldText = true
            headers.forEachIndexed { i, h ->
                canvas.drawText(h.take(MAX_CELL_CHARS), i * colWidth + CELL_PADDING, 65f, paint)
                // Vertical divider
                paint.color = COLOR_GRID
                canvas.drawLine((i + 1) * colWidth, HEADER_BAND_H, (i + 1) * colWidth, COL_HEADER_BAND_H, paint)
                paint.color = COLOR_COL_HEADER_TEXT
            }

            return Pair(page, canvas)
        }

        var (currentPage, canvas) = startPage()
        var y = ROW_START_Y
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        while (rowIndex < rows.size) {
            if (y > DATA_BOTTOM) {
                // Finish current page and start a new one
                drawFooter(canvas, paint, footerText)
                document.finishPage(currentPage)
                val next = startPage()
                currentPage = next.first
                canvas = next.second
                y = ROW_START_Y
            }

            val row = rows[rowIndex]

            // Alternating row background
            if (rowIndex % 2 == 1) {
                paint.color = COLOR_ROW_ALT
                canvas.drawRect(0f, y - ROW_HEIGHT + 4f, PAGE_WIDTH, y + 4f, paint)
            }

            // Grid lines
            paint.color = COLOR_GRID
            canvas.drawLine(0f, y + 4f, PAGE_WIDTH, y + 4f, paint)

            paint.color = COLOR_ROW_TEXT
            paint.textSize = 9f
            paint.isFakeBoldText = false
            row.forEachIndexed { i, cell ->
                canvas.drawText(cell.take(MAX_CELL_CHARS), i * colWidth + CELL_PADDING, y, paint)
                // Column divider
                paint.color = COLOR_GRID
                canvas.drawLine((i + 1) * colWidth, y - ROW_HEIGHT + 4f, (i + 1) * colWidth, y + 4f, paint)
                paint.color = COLOR_ROW_TEXT
            }

            y += ROW_HEIGHT
            rowIndex++
        }

        drawFooter(canvas, paint, footerText)
        document.finishPage(currentPage)

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun drawFooter(canvas: Canvas, paint: Paint, text: String) {
        paint.color = COLOR_FOOTER
        paint.textSize = 8f
        paint.isFakeBoldText = false
        // Horizontal rule above footer
        paint.color = COLOR_GRID
        canvas.drawLine(0f, FOOTER_Y - 10f, PAGE_WIDTH, FOOTER_Y - 10f, paint)
        paint.color = COLOR_FOOTER
        canvas.drawText(text, 16f, FOOTER_Y, paint)
    }
}
