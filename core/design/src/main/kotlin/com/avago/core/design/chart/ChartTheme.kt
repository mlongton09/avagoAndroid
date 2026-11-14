package com.avago.core.design.chart

import android.graphics.Paint
import android.graphics.Typeface
import android.icu.text.CompactDecimalFormat
import android.icu.text.NumberFormat
import android.icu.util.Currency
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

/**
 * Avago chart theme for Compose Canvas charts. Mirrors the web palette
 * (AvagoWeb src/components/charts/palette.ts) and the rules in
 * AvagoWeb docs/chart-style-guide.md:
 *  - light horizontal gridlines only, emphasised zero baseline, no chart junk;
 *  - 8-step colour-blind-ordered categorical palette (never cycle — group "Other");
 *  - red/green reserved for variance and always paired with a sign/parentheses;
 *  - tabular, muted tick/data labels; compact locale-aware currency on axes and
 *    labels, full values for accessibility; accounting negatives where the
 *    locale uses them.
 */
@Immutable
data class ChartColors(
    val isDark: Boolean,
    val text: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val grid: Color,
    val zeroLine: Color,
    val categorical: List<Color>,
    val positive: Color,
    val negative: Color,
    val neutral: Color,
    val target: Color,
    val forecast: Color,
    val aging: List<Color>,
) {
    fun categorical(index: Int): Color = categorical[Math.floorMod(index, categorical.size)]
}

object ChartTheme {

    val Light = ChartColors(
        isDark = false,
        text = Color(0xFF1F2328),
        textMuted = Color(0xFF57606A),
        textSubtle = Color(0xFF656D76),
        grid = Color(0x141F2328), // rgba(31,35,40,0.08)
        zeroLine = Color(0xFF6E7781),
        categorical = listOf(
            Color(0xFF2563EB), // blue
            Color(0xFFC2410C), // orange
            Color(0xFF0F766E), // teal
            Color(0xFF7C3AED), // violet
            Color(0xFFBE185D), // magenta
            Color(0xFFA16207), // ochre
            Color(0xFF475569), // slate
            Color(0xFF4D7C0F), // olive
        ),
        positive = Color(0xFF047857),
        negative = Color(0xFFB91C1C),
        neutral = Color(0xFF475569),
        target = Color(0xFFB45309),
        forecast = Color(0xFF6E7781),
        aging = listOf(
            Color(0xFF0F766E), Color(0xFFA16207), Color(0xFFC2410C),
            Color(0xFFB91C1C), Color(0xFF86198F),
        ),
    )

    val Dark = ChartColors(
        isDark = true,
        text = Color(0xFFE6EDF3),
        textMuted = Color(0xFFADBAC7),
        textSubtle = Color(0xFF909DAB),
        grid = Color(0x1ACDD9E5), // rgba(205,217,229,0.10)
        zeroLine = Color(0xFF768390),
        categorical = listOf(
            Color(0xFF60A5FA), // blue
            Color(0xFFFB923C), // orange
            Color(0xFF2DD4BF), // teal
            Color(0xFFA78BFA), // violet
            Color(0xFFF472B6), // magenta
            Color(0xFFFACC15), // yellow
            Color(0xFF94A3B8), // slate
            Color(0xFFA3E635), // lime
        ),
        positive = Color(0xFF34D399),
        negative = Color(0xFFF87171),
        neutral = Color(0xFF94A3B8),
        target = Color(0xFFFBBF24),
        forecast = Color(0xFF909DAB),
        aging = listOf(
            Color(0xFF2DD4BF), Color(0xFFFACC15), Color(0xFFFB923C),
            Color(0xFFF87171), Color(0xFFE879F9),
        ),
    )

    // Geometry — matches web: bar max 44, radius 3, line 2, grid 1.
    val BarMaxWidth: Dp = 44.dp
    val BarCornerRadius: Dp = 3.dp
    val LineWidth: Dp = 2.dp
    val GridLineWidth: Dp = 1.dp
    val PointRadius: Dp = 3.dp

    // Typography — ticks/legend 11sp, data labels 11sp bold, empty state 13sp.
    val TickTextSize: TextUnit = 11.sp
    val DataLabelTextSize: TextUnit = 11.sp
    val EmptyTextSize: TextUnit = 13.sp

    /** Chart colours for the current Material theme (follows the app's light/dark setting). */
    @Composable
    fun colors(): ChartColors =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Dark else Light
}

// ---------------------------------------------------------------------------
// DrawScope helpers
// ---------------------------------------------------------------------------

/** Text paint for ticks / data labels with tabular (monospaced) digits. */
fun DrawScope.chartTextPaint(
    color: Color,
    size: TextUnit = ChartTheme.TickTextSize,
    align: Paint.Align = Paint.Align.CENTER,
    bold: Boolean = false,
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    textSize = size.toPx()
    textAlign = align
    fontFeatureSettings = "tnum"
    if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

/** Light horizontal gridline (the only gridlines charts draw). */
fun DrawScope.drawChartGridLine(y: Float, startX: Float, endX: Float, colors: ChartColors) {
    drawLine(colors.grid, Offset(startX, y), Offset(endX, y), strokeWidth = ChartTheme.GridLineWidth.toPx())
}

/** Emphasised zero baseline. */
fun DrawScope.drawChartZeroLine(y: Float, startX: Float, endX: Float, colors: ChartColors) {
    drawLine(colors.zeroLine, Offset(startX, y), Offset(endX, y), strokeWidth = ChartTheme.GridLineWidth.toPx())
}

/** Loading skeleton: muted placeholder columns at the chart height (no spinner-only view). */
fun DrawScope.drawChartSkeleton(colors: ChartColors, bars: Int = 6) {
    val slot = size.width / bars
    val barW = (slot * 0.6f).coerceAtMost(ChartTheme.BarMaxWidth.toPx())
    val heights = floatArrayOf(0.45f, 0.7f, 0.55f, 0.85f, 0.6f, 0.4f)
    val fill = colors.grid.copy(alpha = (colors.grid.alpha * 2.5f).coerceAtMost(1f))
    for (i in 0 until bars) {
        val h = size.height * 0.8f * heights[i % heights.size]
        drawRoundRect(
            color = fill,
            topLeft = Offset(slot * i + (slot - barW) / 2f, size.height - h),
            size = androidx.compose.ui.geometry.Size(barW, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(ChartTheme.BarCornerRadius.toPx()),
        )
    }
}

/** Centred muted empty-state message. */
fun DrawScope.drawChartEmpty(message: String, colors: ChartColors) {
    drawContext.canvas.nativeCanvas.drawText(
        message,
        size.width / 2f,
        size.height / 2f,
        chartTextPaint(colors.textMuted, ChartTheme.EmptyTextSize),
    )
}

// ---------------------------------------------------------------------------
// Layout rules — pure functions so they are unit-testable on the JVM
// ---------------------------------------------------------------------------

object ChartLayout {

    /** Data labels only for a single series with at most this many bars (style guide § 7). */
    const val MAX_LABELLED_BARS = 16

    /** Doughnuts show at most this many slices; the tail is grouped into "Other". */
    const val MAX_DONUT_SLICES = 6

    /**
     * Category-axis auto-skip: draw every Nth label so labels never overlap
     * and are never rotated. [widestLabelPx] includes the desired gap.
     */
    fun labelStep(widestLabelPx: Float, slotPx: Float): Int {
        if (slotPx <= 0f || widestLabelPx <= slotPx) return 1
        return kotlin.math.ceil(widestLabelPx / slotPx).toInt().coerceAtLeast(1)
    }

    /** Whether a centred data label fits its bar slot without colliding with neighbours. */
    fun dataLabelFits(labelWidthPx: Float, slotPx: Float, bars: Int): Boolean =
        bars in 1..MAX_LABELLED_BARS && labelWidthPx <= slotPx * 0.95f

    /**
     * Group the tail of a composition into an "Other" slice: keeps the
     * [maxSlices] - 1 largest parts (in descending order) and sums the rest.
     * Fewer parts than [maxSlices] are returned sorted, unchanged.
     */
    fun <K> groupTail(parts: List<Pair<K, Double>>, maxSlices: Int, other: K): List<Pair<K, Double>> {
        val sorted = parts.sortedByDescending { it.second }
        if (sorted.size <= maxSlices) return sorted
        val head = sorted.take(maxSlices - 1)
        return head + (other to sorted.drop(maxSlices - 1).sumOf { it.second })
    }
}

// ---------------------------------------------------------------------------
// Number formatting — locale + currency aware (ICU), mirrors web formatters.ts
// ---------------------------------------------------------------------------

object ChartFormat {

    const val MISSING = "\u2014"

    private fun currencyOrNull(code: String?): Currency? =
        code?.takeIf { it.isNotBlank() }?.let { runCatching { Currency.getInstance(it.uppercase()) }.getOrNull() }

    /** Does this locale write negative accounting amounts in parentheses (en-US yes, de-DE no)? */
    fun usesAccountingParens(locale: Locale = Locale.getDefault(), currencyCode: String = "USD"): Boolean {
        val f = NumberFormat.getInstance(locale, NumberFormat.ACCOUNTINGCURRENCYSTYLE)
        currencyOrNull(currencyCode)?.let { f.currency = it }
        return f.format(-1.0).contains('(')
    }

    private fun negative(absText: String, parens: Boolean) = if (parens) "($absText)" else "-$absText"

    /** Full currency for accessibility / detail: whole units at >= 1,000; accounting negatives. */
    fun money(value: Double?, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        if (value == null || !value.isFinite()) return MISSING
        val f = NumberFormat.getInstance(locale, NumberFormat.ACCOUNTINGCURRENCYSTYLE)
        currencyOrNull(currencyCode)?.let { f.currency = it }
        if (abs(value) >= 1000) {
            f.minimumFractionDigits = 0
            f.maximumFractionDigits = 0
        }
        return f.format(value)
    }

    /** Compact number in the locale's own abbreviations ("1.2K", "1,2 Mio."). */
    fun numberCompact(value: Double?, locale: Locale = Locale.getDefault()): String {
        if (value == null || !value.isFinite()) return MISSING
        val f = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
        f.maximumFractionDigits = if (abs(value) >= 1000) 1 else 0
        return f.format(value)
    }

    /** Compact currency for axes / data labels: "$950", "$1.2K", "($3.4M)"; de-DE "1,2 Mio. €". */
    fun moneyCompact(value: Double?, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        if (value == null || !value.isFinite()) return MISSING
        val number = numberCompact(abs(value), locale)
        val f = NumberFormat.getCurrencyInstance(locale)
        val currency = currencyOrNull(currencyCode)
        currency?.let { f.currency = it }
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 0
        val pattern = f.format(0.0)
        val text = if (pattern.count { it == '0' } == 1) {
            pattern.replace("0", number)
        } else {
            (currency?.getSymbol(locale) ?: currencyCode) + number
        }
        return if (value < 0) negative(text, usesAccountingParens(locale, currencyCode)) else text
    }

    /** Grouped number with up to [fractionDigits] decimals. */
    fun number(value: Double?, fractionDigits: Int = 0, locale: Locale = Locale.getDefault()): String {
        if (value == null || !value.isFinite()) return MISSING
        val f = NumberFormat.getNumberInstance(locale)
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = fractionDigits
        return f.format(value)
    }

    /** Percent from a ratio (0.125 → "12.5%"). */
    fun percent(ratio: Double?, fractionDigits: Int = 1, locale: Locale = Locale.getDefault()): String {
        if (ratio == null || !ratio.isFinite()) return MISSING
        val f = NumberFormat.getPercentInstance(locale)
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = fractionDigits
        return f.format(ratio)
    }
}
